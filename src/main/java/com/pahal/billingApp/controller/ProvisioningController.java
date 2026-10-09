package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.ProvisionTenantOwnerRequest;
import com.pahal.billingApp.dto.UserDetailsDTO;
import com.pahal.billingApp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/provisioning")
@Tag(name = "Platform Provisioning", description = "Operator-only first-owner provisioning for a new tenant")
public class ProvisioningController {
    @org.springframework.beans.factory.annotation.Autowired private com.pahal.billingApp.licensing.TenantLicenseService licenses;
    @org.springframework.web.bind.annotation.GetMapping("/installation")
    public ResponseEntity<?> installation(@RequestHeader(name = "X-Provisioning-Key", required = false) String suppliedKey) {
        if (provisioningKey == null || provisioningKey.length() < 32 || suppliedKey == null || !MessageDigest.isEqual(
                provisioningKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Valid operator provisioning key required.");
        return ResponseEntity.ok(licenses.installation());
    }

    private final UserService userService;
    private final String provisioningKey;
    private final boolean desktopProfile;

    public ProvisioningController(
            UserService userService,
            @Value("${app.provisioning.key:}") String provisioningKey,
            Environment environment) {
        this.userService = userService;
        this.provisioningKey = provisioningKey;
        this.desktopProfile = environment.acceptsProfiles(Profiles.of("desktop"));
    }

    @Operation(summary = "Provision the first tenant owner", description = "Use from Swagger with the operator provisioning key. Desktop mode allows only the first administrator for its entire store database.")
    @PostMapping("/tenants/{tenantId}/owner")
    public ResponseEntity<?> provisionOwner(
            @PathVariable String tenantId,
            @RequestHeader(name = "X-Provisioning-Key", required = false)
            @Parameter(description = "APP_PROVISIONING_KEY configured by the application operator") String suppliedKey,
            @RequestBody ProvisionTenantOwnerRequest request) {
        if (provisioningKey == null || provisioningKey.length() < 32) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Owner provisioning is disabled: configure APP_PROVISIONING_KEY with at least 32 characters.");
        }
        if (suppliedKey == null || !MessageDigest.isEqual(
                provisioningKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid provisioning key.");
        }
        UserDetailsDTO created = desktopProfile
                ? userService.provisionFirstDesktopOwner(request, tenantId)
                : userService.provisionTenantOwner(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
