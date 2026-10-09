package com.pahal.billingApp.licensing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.entity.*;
import com.pahal.billingApp.repository.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;

@Service
public class TenantLicenseService {
    public record Snapshot(String tenantId, String deploymentId, String status, String planCode,
                           String licenseId, long revision, String validUntil, String graceUntil,
                           Set<Feature> features, int maxUsers, int maxCounters, boolean signingKeyConfigured) {
        public boolean operational() { return Set.of("ACTIVE", "GRACE", "LEGACY").contains(status); }
    }
    private final TenantLicenseRepository licenses;
    private final LicenseInstallationRepository installations;
    private final LicenseAuditRepository audit;
    private final UserRepository users;
    private final LicensedCounterRepository counters;
    private final EntityManager em;
    private final ObjectMapper mapper;
    private final byte[] publicKey;

    public TenantLicenseService(TenantLicenseRepository licenses, LicenseInstallationRepository installations,
            LicenseAuditRepository audit, UserRepository users, LicensedCounterRepository counters,
            EntityManager em, ObjectMapper mapper) throws java.io.IOException {
        this.licenses = licenses; this.installations = installations; this.audit = audit; this.users = users;
        this.counters = counters; this.em = em; this.mapper = mapper;
        var resource = new ClassPathResource("licensing/owner-public-key.der");
        this.publicKey = resource.exists() ? resource.getContentAsByteArray() : new byte[0];
    }

    /** Database identity survives app/Windows updates; restoring a database also restores this ID. */
    @Transactional
    public void bootstrap() {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended('pahal-license-bootstrap', 0))").getSingleResult();
        if (!installations.existsById(1)) {
            var installation = new LicenseInstallation(); installation.setId(1);
            installation.setDeploymentId(UUID.randomUUID().toString()); installations.saveAndFlush(installation);
        }
        // Preserve existing stores. Fresh provisioning below never gets this migration grant.
        for (String tenant : users.findDistinctTenantIds()) if (!licenses.existsById(tenant)) {
            var license = new TenantLicense(); license.setTenantId(tenant); license.setDeploymentId(installationId());
            license.setLegacyAccess(true); license.setLegacyMigrated(true); licenses.save(license);
            record(tenant, "LEGACY_MIGRATION", "Existing store retains access until the owner installs a signed license.");
        }
    }
    public String installationId() { return installations.findById(1).orElseThrow(() -> new IllegalStateException("Installation identity is not ready.")).getDeploymentId(); }
    public boolean ready() { return installations.existsById(1); }
    public Map<String, Object> installation() { return Map.of("deploymentId", installationId(), "signingKeyConfigured", publicKey.length > 0); }
    public Snapshot snapshot(String tenant) {
        var stored = licenses.findById(tenant).orElse(null);
        if (stored == null) return new Snapshot(tenant, installationId(), "UNLICENSED", null, null, 0, null, null, Set.of(), 0, 0, publicKey.length > 0);
        if (stored.getSignedLicense() == null && stored.isLegacyAccess())
            return new Snapshot(tenant, stored.getDeploymentId(), "LEGACY", "LEGACY", null, 0, null, null, EnumSet.allOf(Feature.class), 10000, 1000, publicKey.length > 0);
        if (stored.getSignedLicense() == null) return new Snapshot(tenant, stored.getDeploymentId(), "UNLICENSED", null, null, 0, null, null, Set.of(), 0, 0, publicKey.length > 0);
        try {
            var p = verified(stored.getSignedLicense());
            if (!p.tenantId().equals(tenant) || !p.deploymentId().equals(stored.getDeploymentId()) || !p.deploymentId().equals(installationId()))
                throw new IllegalArgumentException("License installation mismatch.");
            var today = LocalDate.now(); var end = LocalDate.parse(p.validUntil()); var grace = end.plusDays(p.graceDays());
            var status = today.isBefore(LocalDate.parse(p.validFrom())) ? "NOT_STARTED" : !today.isAfter(end) ? "ACTIVE" : !today.isAfter(grace) ? "GRACE" : "EXPIRED";
            return new Snapshot(tenant, p.deploymentId(), status, p.planCode(), p.licenseId(), p.revision(), p.validUntil(), grace.toString(), p.features(), p.maxUsers(), p.maxCounters(), true);
        } catch (IllegalArgumentException | LicenseAccessException e) {
            return new Snapshot(tenant, stored.getDeploymentId(), "INVALID", null, null, 0, null, null, Set.of(), 0, 0, publicKey.length > 0);
        }
    }
    private LicenseFormat.Payload verified(String file) {
        if (publicKey.length == 0) throw new LicenseAccessException("LICENSE_KEY_MISSING", "The application owner must configure the public verification key before signed licenses can be imported.");
        return LicenseFormat.verify(file, publicKey, mapper);
    }
    @Transactional
    public Snapshot importLicense(String tenant, String file) {
        var candidate = verified(file);
        if (!candidate.tenantId().equals(tenant) || !candidate.deploymentId().equals(installationId()))
            throw new IllegalArgumentException("This license belongs to another tenant or installation.");
        if (LocalDate.now().isBefore(LocalDate.parse(candidate.validFrom())) || LocalDate.now().isAfter(LocalDate.parse(candidate.validUntil())))
            throw new IllegalArgumentException("Import a license that is currently valid.");
        lockTenant(tenant);
        var stored = licenses.lock(tenant).orElseGet(() -> {
            var item = new TenantLicense(); item.setTenantId(tenant); item.setDeploymentId(installationId()); return item;
        });
        if (stored.getSignedLicense() != null) {
            LicenseFormat.Payload previous = null;
            try { previous = verified(stored.getSignedLicense()); } catch (IllegalArgumentException ignored) { /* Allow a legitimate signed replacement to repair a damaged file. */ }
            String previousId = previous == null ? stored.getAcceptedLicenseId() : previous.licenseId();
            long previousRevision = previous == null ? stored.getAcceptedRevision() : previous.revision();
            if (previousId == null) throw new IllegalArgumentException("Damaged license metadata requires application-owner recovery.");
            if (!previousId.equals(candidate.licenseId())) throw new IllegalArgumentException("Renewals must retain the license ID and increase its revision.");
            if (candidate.revision() <= previousRevision) {
                // Signature-verified exact payload replay is safe, including a retried import.
                if (candidate.equals(previous)) return snapshot(tenant);
                throw new IllegalArgumentException("Import a newer license revision.");
            }
            try {
                ArrayList<String> history = stored.getSignedHistory() == null ? new ArrayList<>() : mapper.readValue(stored.getSignedHistory(), mapper.getTypeFactory().constructCollectionType(ArrayList.class, String.class));
                if (history.size() >= 1000) throw new IllegalArgumentException("License history requires owner maintenance before another import.");
                if (previous != null) history.add(stored.getSignedLicense());
                stored.setSignedHistory(mapper.writeValueAsString(history));
            } catch (java.io.IOException e) { throw new IllegalArgumentException("License history could not be read. Contact the application owner."); }
        }
        stored.setSignedLicense(file); stored.setLegacyAccess(false);
        stored.setAcceptedLicenseId(candidate.licenseId()); stored.setAcceptedRevision(candidate.revision()); licenses.saveAndFlush(stored);
        record(tenant, "LICENSE_IMPORTED", candidate.licenseId() + " revision " + candidate.revision() + ", plan " + candidate.planCode()
                + ", active users " + users.countByTenantIdAndActiveTrue(tenant) + "/" + candidate.maxUsers()
                + ", counters " + counters.countByTenantIdAndActiveTrue(tenant) + "/" + candidate.maxCounters());
        return snapshot(tenant);
    }
    /** Serialize imports, user creation and counter registration for a tenant. Lock order: tenant then user/stock. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockTenant(String tenant) {
        if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid tenant ID.");
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .setParameter("key", "pahal-license:" + tenant).getSingleResult();
    }
    /** Historical workspace access is derived from verified older grants, never from an editable plan name. */
    public boolean everLicensed(String tenant, Feature feature) {
        var stored = licenses.findById(tenant).orElse(null);
        if (stored == null) return false;
        if (stored.isLegacyMigrated()) return true;
        if (fileGrants(stored.getSignedLicense(), tenant, feature)) return true;
        try {
            ArrayList<String> files = stored.getSignedHistory() == null ? new ArrayList<>() : mapper.readValue(stored.getSignedHistory(), mapper.getTypeFactory().constructCollectionType(ArrayList.class, String.class));
            for (String file : files) {
                if (fileGrants(file, tenant, feature)) return true;
            }
        } catch (RuntimeException | java.io.IOException ignored) { return false; }
        return false;
    }
    private boolean fileGrants(String file, String tenant, Feature feature) {
        if (file == null) return false;
        try {
            var p = verified(file);
            return tenant.equals(p.tenantId()) && installationId().equals(p.deploymentId()) && p.features().contains(feature);
        } catch (RuntimeException ignored) { return false; }
    }
    @Transactional
    public void requireUserCapacity(String tenant) {
        lockTenant(tenant); var current = snapshot(tenant);
        requireOperational(current);
        if (users.countByTenantIdAndActiveTrue(tenant) >= current.maxUsers())
            throw new LicenseAccessException("USER_LIMIT", "Active user limit reached. Deactivate a staff account or request a larger license.");
    }
    public static void requireOperational(Snapshot current) {
        if (!current.operational()) throw new LicenseAccessException("LICENSE_" + current.status(), "License is " + current.status().toLowerCase(Locale.ROOT) + ". Import a valid license to start new transactions. Existing records and settlements remain available.");
    }
    public void record(String tenant, String action, String details) {
        var item = new LicenseAudit(); item.setTenantId(tenant); item.setAction(action); item.setDetails(details);
        var auth = SecurityContextHolder.getContext().getAuthentication(); item.setActor(auth == null ? "installation-operator" : auth.getName());
        item.setCreatedAt(LocalDateTime.now()); audit.save(item);
    }
}
