package com.pahal.billingApp.owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.licensing.*;
import javax.swing.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.awt.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.time.LocalDate;
import java.util.*;

/** Owner-only desktop utility. This source set is excluded from customer boot JARs. */
public final class OwnerLicenseManager extends JFrame {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path home = Path.of(Optional.ofNullable(System.getenv("LOCALAPPDATA")).orElse(System.getProperty("user.home")), "PahalLicenseManager");
    private final JTextField tenant = new JTextField(), installation = new JTextField();
    private final JTextField licenseId = new JTextField("LIC-" + UUID.randomUUID()), revision = new JTextField("1");
    private final JTextField users = new JTextField("2"), counters = new JTextField("1");
    private final JTextField start = new JTextField(LocalDate.now().toString()), end = new JTextField(LocalDate.now().plusYears(1).minusDays(1).toString());
    private final JTextField grace = new JTextField("7");
    private final JComboBox<String> plan = new JComboBox<>(new String[]{"BASIC", "PLUS", "PRO", "PLUS_PRO"});
    private final EnumMap<Feature, JCheckBox> features = new EnumMap<>(Feature.class);
    private final JLabel status = new JLabel("Create signing keys once, export the public key, then issue customer licenses.");
    private record ProtectedKey(int version, String salt, String iv, String ciphertext) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); new OwnerLicenseManager().setVisible(true); }
            catch (Exception e) { JOptionPane.showMessageDialog(null, e.getMessage(), "Pahal License Manager", JOptionPane.ERROR_MESSAGE); }
        });
    }
    private OwnerLicenseManager() throws Exception {
        super("Pahal License Manager — Application Owner");
        Files.createDirectories(home); setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(820, 760); setLocationRelativeTo(null);
        var body = new JPanel(new BorderLayout(16, 16)); body.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        var form = new JPanel(new GridLayout(0, 2, 12, 9));
        field(form, "Tenant ID", tenant); field(form, "Installation ID (from customer)", installation);
        field(form, "License ID (retain for renewals)", licenseId); field(form, "Revision (increase for changes)", revision);
        field(form, "Plan", plan); field(form, "Maximum active users (includes administrator)", users);
        field(form, "Maximum registered counters", counters); field(form, "Valid from (YYYY-MM-DD)", start);
        field(form, "Valid through (YYYY-MM-DD)", end); field(form, "Grace days", grace);
        var modules = new JPanel(new GridLayout(0, 2, 8, 8));
        modules.setBorder(BorderFactory.createTitledBorder("Modules — select add-ons or disable optional modules"));
        for (var feature : Feature.values()) {
            var box = new JCheckBox(feature.name().replace('_', ' ')); box.setEnabled(!Feature.CORE.contains(feature)); features.put(feature, box); modules.add(box);
        }
        var content = new JPanel(new BorderLayout(12, 12)); content.add(form, BorderLayout.NORTH); content.add(modules, BorderLayout.CENTER);
        body.add(content, BorderLayout.CENTER);
        var actions = new JPanel(new GridLayout(0, 2, 10, 10));
        button(actions, "Create signing keys (once)", this::initializeKeys);
        button(actions, "Export public verification key", this::exportPublic);
        button(actions, "Open existing license for renewal", this::openLicense);
        button(actions, "Generate signed license", this::issue);
        var footer = new JPanel(new BorderLayout(10, 10)); footer.add(actions, BorderLayout.CENTER); footer.add(status, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.SOUTH); setContentPane(body);
        plan.addActionListener(e -> applyPlan()); applyPlan();
    }
    private void field(JPanel panel, String label, JComponent value) { panel.add(new JLabel(label)); panel.add(value); }
    private interface Action { void run() throws Exception; }
    private void button(JPanel panel, String title, Action action) {
        var button = new JButton(title); button.addActionListener(event -> {
            try { action.run(); } catch (Exception e) { JOptionPane.showMessageDialog(this, e.getMessage(), "License operation failed", JOptionPane.ERROR_MESSAGE); }
        }); panel.add(button);
    }
    private void applyPlan() {
        var definition = PlanCatalog.plan((String) plan.getSelectedItem());
        features.forEach((feature, box) -> box.setSelected(definition.features().contains(feature)));
        users.setText(Integer.toString(definition.suggestedUsers())); counters.setText(Integer.toString(definition.suggestedCounters()));
    }
    private char[] password(boolean creating) {
        var password = new JPasswordField(); var confirm = new JPasswordField();
        var panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.add(new JLabel("Signing-key password (never given to customers)")); panel.add(password);
        if (creating) { panel.add(new JLabel("Confirm password")); panel.add(confirm); }
        if (JOptionPane.showConfirmDialog(this, panel, "Unlock signing authority", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
        var value = password.getPassword();
        if (value.length < 12) { Arrays.fill(value, '\0'); throw new IllegalArgumentException("Use a password with at least 12 characters."); }
        if (creating) {
            var confirmation = confirm.getPassword(); boolean matches = Arrays.equals(value, confirmation); Arrays.fill(confirmation, '\0');
            if (!matches) { Arrays.fill(value, '\0'); throw new IllegalArgumentException("Passwords do not match."); }
        }
        return value;
    }
    private SecretKey encryptionKey(char[] password, byte[] salt) throws Exception {
        var spec = new PBEKeySpec(password, salt, 600000, 256);
        try { return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES"); }
        finally { spec.clearPassword(); }
    }
    private void initializeKeys() throws Exception {
        var keyFile = home.resolve("owner-private-key.encrypted.json");
        if (Files.exists(keyFile) || Files.exists(home.resolve("owner-public-key.der"))) throw new IllegalArgumentException("Signing keys already exist. Back them up; do not replace them for renewals.");
        var password = password(true); if (password == null) return;
        byte[] encoded = null;
        try {
            var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072); var pair = generator.generateKeyPair();
            var salt = new byte[32]; var iv = new byte[12]; var random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(password, salt), new GCMParameterSpec(128, iv));
            encoded = pair.getPrivate().getEncoded();
            var protectedKey = new ProtectedKey(1, b64(salt), b64(iv), b64(cipher.doFinal(encoded)));
            Files.writeString(keyFile, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(protectedKey), StandardOpenOption.CREATE_NEW);
            Files.write(home.resolve("owner-public-key.der"), pair.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW);
            status.setText("Keys created in " + home + ". Export the public key and back up the encrypted private key.");
        } finally { Arrays.fill(password, '\0'); if (encoded != null) Arrays.fill(encoded, (byte)0); }
    }
    private PrivateKey unlock(char[] password) throws Exception {
        try {
            var key = JSON.readValue(Files.readString(home.resolve("owner-private-key.encrypted.json")), ProtectedKey.class);
            if (key.version() != 1) throw new IllegalArgumentException("Unsupported signing key format.");
            var cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, encryptionKey(password, decode(key.salt())), new GCMParameterSpec(128, decode(key.iv())));
            var encoded = cipher.doFinal(decode(key.ciphertext()));
            try { return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded)); }
            finally { Arrays.fill(encoded, (byte)0); }
        } catch (AEADBadTagException e) { throw new IllegalArgumentException("Incorrect password or damaged signing key."); }
        finally { Arrays.fill(password, '\0'); }
    }
    private Path choose(String suggested, boolean save) {
        var chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File(suggested));
        int result = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }
    private void writeNew(Path file, byte[] bytes) throws Exception {
        if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "Replace " + file.getFileName() + "?", "Existing file", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Files.write(file, bytes);
    }
    private void exportPublic() throws Exception {
        byte[] bytes = Files.readAllBytes(home.resolve("owner-public-key.der")); var file = choose("owner-public-key.der", true);
        if (file != null) { writeNew(file, bytes); status.setText("Public key exported. Customer builds must include this exact verification key."); }
    }
    private void openLicense() throws Exception {
        var file = choose("customer.pahal-license", false); if (file == null) return;
        var p = LicenseFormat.verify(Files.readString(file), Files.readAllBytes(home.resolve("owner-public-key.der")), JSON);
        plan.setSelectedItem(p.planCode()); features.forEach((feature, box) -> box.setSelected(p.features().contains(feature)));
        tenant.setText(p.tenantId()); installation.setText(p.deploymentId()); licenseId.setText(p.licenseId()); revision.setText(Long.toString(Math.addExact(p.revision(), 1)));
        users.setText(Integer.toString(p.maxUsers())); counters.setText(Integer.toString(p.maxCounters()));
        start.setText(LocalDate.now().toString()); end.setText(LocalDate.now().plusYears(1).minusDays(1).toString()); grace.setText(Integer.toString(p.graceDays()));
        status.setText("Existing license loaded. Review all details before issuing its next revision.");
    }
    private void issue() throws Exception {
        var selected = EnumSet.noneOf(Feature.class); features.forEach((feature, box) -> { if (box.isSelected()) selected.add(feature); });
        var payload = new LicenseFormat.Payload(1, licenseId.getText().trim(), Long.parseLong(revision.getText().trim()), tenant.getText().trim(),
                installation.getText().trim(), (String)plan.getSelectedItem(), 1, selected, Integer.parseInt(users.getText().trim()), Integer.parseInt(counters.getText().trim()),
                start.getText().trim(), end.getText().trim(), Integer.parseInt(grace.getText().trim()));
        LicenseFormat.validate(payload);
        var history = home.resolve("issued").resolve(payload.licenseId()); Files.createDirectories(history);
        var record = history.resolve(payload.revision() + ".pahal-license");
        if (Files.exists(record)) throw new IllegalArgumentException("This license revision was already issued. Increase the revision or reuse the saved file.");
        var password = password(false); if (password == null) return;
        var signed = LicenseFormat.sign(payload, unlock(password), JSON);
        LicenseFormat.verify(signed, Files.readAllBytes(home.resolve("owner-public-key.der")), JSON);
        var destination = choose(payload.tenantId() + "-r" + payload.revision() + ".pahal-license", true); if (destination == null) return;
        Files.writeString(record, signed, StandardOpenOption.CREATE_NEW); writeNew(destination, signed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        status.setText("Issued revision " + payload.revision() + ". Local copy: " + record);
    }
    private static String b64(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private static byte[] decode(String value) { return Base64.getDecoder().decode(value); }
}
