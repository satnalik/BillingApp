package com.pahal.billingApp.licensing;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
@Component
public class LicenseBootstrap implements CommandLineRunner {
    private final TenantLicenseService licenses;
    public LicenseBootstrap(TenantLicenseService licenses) { this.licenses = licenses; }
    @Override public void run(String... args) { licenses.bootstrap(); }
}
