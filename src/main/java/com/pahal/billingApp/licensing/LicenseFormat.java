package com.pahal.billingApp.licensing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.*;

/** Sign exact payload bytes, avoiding JSON reserialization and canonicalization ambiguity. */
public final class LicenseFormat {
    private LicenseFormat() {}
    public static final String ALGORITHM = "RSA_SHA256";
    public record Payload(int schemaVersion, String licenseId, long revision, String tenantId,
                          String deploymentId, String planCode, int planVersion, Set<Feature> features,
                          int maxUsers, int maxCounters, String validFrom, String validUntil, int graceDays) {}
    public record Envelope(String algorithm, String payload, String signature) {}

    public static Payload verify(String file, byte[] publicKey, ObjectMapper mapper) {
        if (file == null || file.length() > 65536) throw new IllegalArgumentException("License file is missing or too large.");
        try {
            var envelope = mapper.readValue(file, Envelope.class);
            if (!ALGORITHM.equals(envelope.algorithm())) throw new IllegalArgumentException("Unsupported license signature algorithm.");
            var payload = Base64.getDecoder().decode(envelope.payload());
            var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey)));
            verifier.update(payload);
            if (!verifier.verify(Base64.getDecoder().decode(envelope.signature())))
                throw new IllegalArgumentException("License signature is invalid. Request the original file from the application owner.");
            var result = mapper.readValue(payload, Payload.class);
            validate(result);
            return result;
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Cannot verify this license file."); }
    }
    public static String sign(Payload payload, PrivateKey key, ObjectMapper mapper) throws Exception {
        validate(payload);
        var bytes = mapper.writeValueAsBytes(payload);
        var signer = Signature.getInstance("SHA256withRSA"); signer.initSign(key); signer.update(bytes);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Envelope(ALGORITHM,
                Base64.getEncoder().encodeToString(bytes), Base64.getEncoder().encodeToString(signer.sign())));
    }
    public static void validate(Payload p) {
        if (p == null || p.schemaVersion() != 1 || p.planVersion() != 1 || p.revision() < 1)
            throw new IllegalArgumentException("Unsupported license version or revision.");
        if (p.licenseId() == null || !p.licenseId().matches("[A-Za-z0-9_-]{1,100}"))
            throw new IllegalArgumentException("Invalid license ID.");
        if (p.tenantId() == null || !p.tenantId().matches("[A-Za-z0-9_-]{1,100}"))
            throw new IllegalArgumentException("Tenant ID must use 1-100 letters, numbers, underscores or hyphens.");
        try { UUID.fromString(p.deploymentId()); } catch (Exception e) { throw new IllegalArgumentException("Invalid installation ID."); }
        PlanCatalog.plan(p.planCode()); PlanCatalog.validate(p.features());
        if (p.maxUsers() < 1 || p.maxUsers() > 10000 || p.maxCounters() < 1 || p.maxCounters() > 1000 || p.graceDays() < 0 || p.graceDays() > 30)
            throw new IllegalArgumentException("Invalid license limits or grace period.");
        try {
            if (LocalDate.parse(p.validUntil()).isBefore(LocalDate.parse(p.validFrom())))
                throw new IllegalArgumentException("License expiry must follow its start date.");
        } catch (java.time.format.DateTimeParseException | NullPointerException e) { throw new IllegalArgumentException("Invalid license dates."); }
    }
}
