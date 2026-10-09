package com.pahal.billingApp.controller;

import com.pahal.billingApp.licensing.*;
import com.pahal.billingApp.repository.*;
import com.pahal.billingApp.entity.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api")
public class LicenseController {
    public record ImportRequest(String licenseFile) {}
    public record CounterRequest(String name) {}
    public record ActiveRequest(boolean active) {}
    private final TenantLicenseService licenses;
    private final ModuleAccessService access;
    private final CounterService counters;
    private final UserRepository users;
    private final LicensedCounterRepository counterRepository;
    private final LicenseAuditRepository audit;
    public LicenseController(TenantLicenseService licenses, ModuleAccessService access, CounterService counters,
            UserRepository users, LicensedCounterRepository counterRepository, LicenseAuditRepository audit) {
        this.licenses = licenses; this.access = access; this.counters = counters; this.users = users; this.counterRepository = counterRepository; this.audit = audit;
    }
    @GetMapping("/me/capabilities") public Map<String, Object> capabilities() { return access.capabilities(); }
    @GetMapping("/license") public Map<String, Object> license() {
        var tenant = access.tenant(); var current = licenses.snapshot(tenant);
        return Map.of("license", current, "plans", PlanCatalog.plans(), "features", Feature.values(),
                "usage", Map.of("activeUsers", users.countByTenantIdAndActiveTrue(tenant), "activeCounters", counterRepository.countByTenantIdAndActiveTrue(tenant)),
                "audit", audit.findTop100ByTenantIdOrderByIdDesc(tenant));
    }
    @PostMapping("/license/import") public TenantLicenseService.Snapshot importLicense(@RequestBody ImportRequest request) { return licenses.importLicense(access.tenant(), request.licenseFile()); }
    @GetMapping("/license/counters") public List<LicensedCounter> counters() { return counters.list(); }
    @PostMapping("/license/counters") public LicensedCounter counter(@RequestBody CounterRequest request) { return counters.register(request.name()); }
    @PatchMapping("/license/counters/{id}") public LicensedCounter counter(@PathVariable String id, @RequestBody ActiveRequest request) { return counters.active(id, request.active()); }
}
