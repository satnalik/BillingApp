package com.pahal.billingApp.licensing;

import com.pahal.billingApp.entity.LicensedCounter;
import com.pahal.billingApp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;
import java.util.*;

@Service
public class CounterService {
    private final LicensedCounterRepository counters;
    private final CashierShiftRepository shifts;
    private final TenantLicenseService licenses;
    private final ModuleAccessService access;
    public CounterService(LicensedCounterRepository counters, CashierShiftRepository shifts, TenantLicenseService licenses, ModuleAccessService access) {
        this.counters = counters; this.shifts = shifts; this.licenses = licenses; this.access = access;
    }
    public List<LicensedCounter> list() { return counters.findByTenantIdOrderByName(access.tenant()); }
    @Transactional
    public LicensedCounter register(String name) {
        String tenant = access.tenant(); licenses.lockTenant(tenant);
        var current = licenses.snapshot(tenant); TenantLicenseService.requireOperational(current);
        if (name == null || name.isBlank() || name.trim().length() > 120) throw new IllegalArgumentException("Enter a counter name up to 120 characters.");
        if (counters.countByTenantIdAndActiveTrue(tenant) >= current.maxCounters()) throw new LicenseAccessException("COUNTER_LIMIT", "Registered counter limit reached. Deactivate an unused counter or upgrade the license.");
        if (counters.findByTenantIdOrderByName(tenant).stream().anyMatch(c -> c.getName().equalsIgnoreCase(name.trim())))
            throw new IllegalArgumentException("A counter with this name already exists.");
        var counter = new LicensedCounter(); counter.setId(UUID.randomUUID().toString()); counter.setTenantId(tenant); counter.setName(name.trim());
        counters.saveAndFlush(counter); licenses.record(tenant, "COUNTER_REGISTERED", counter.getId() + " " + counter.getName()); return counter;
    }
    @Transactional
    public LicensedCounter active(String id, boolean active) {
        String tenant = access.tenant(); licenses.lockTenant(tenant);
        var counter = counters.findByIdAndTenantId(id, tenant).orElseThrow(() -> new IllegalArgumentException("Counter not found."));
        if (counter.isActive() == active) return counter;
        if (active) {
            var current = licenses.snapshot(tenant); TenantLicenseService.requireOperational(current);
            if (counters.countByTenantIdAndActiveTrue(tenant) >= current.maxCounters()) throw new LicenseAccessException("COUNTER_LIMIT", "Counter limit reached.");
        } else if (shifts.existsByTenantIdAndLicenseCounterIdAndClosedAtIsNull(tenant, id)) {
            throw new IllegalArgumentException("Close the active cashier shift before deactivating this counter.");
        }
        counter.setActive(active); licenses.record(tenant, "COUNTER_UPDATED", id + " active=" + active); return counters.save(counter);
    }
    /** Called inside posting transactions. Registered IDs represent named counters, not browser tabs. */
    public LicensedCounter requireForNewTransaction() {
        var tenant = access.tenant(); var current = licenses.snapshot(tenant);
        if (current.status().equals("LEGACY")) return null;
        TenantLicenseService.requireOperational(current);
        licenses.lockTenant(tenant);
        if (counters.countByTenantIdAndActiveTrue(tenant) > current.maxCounters())
            throw new LicenseAccessException("COUNTER_LIMIT", "This store exceeds its counter limit. Close unused shifts and deactivate surplus counters before new billing.");
        var attributes = RequestContextHolder.getRequestAttributes();
        String id = attributes instanceof ServletRequestAttributes request ? request.getRequest().getHeader("X-Counter-ID") : null;
        return counters.findByIdAndTenantId(id == null ? "" : id, tenant).filter(LicensedCounter::isActive)
                .orElseThrow(() -> new LicenseAccessException("COUNTER_REQUIRED", "Select a registered counter in Cashier shifts before starting new billing."));
    }
    public void requireForBill(com.pahal.billingApp.entity.CashierShift shift) {
        var counter = requireForNewTransaction();
        if (counter != null && !counter.getId().equals(shift.getLicenseCounterId()))
            throw new LicenseAccessException("COUNTER_MISMATCH", "Select the counter assigned to your open shift.");
    }
}
