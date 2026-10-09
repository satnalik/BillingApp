package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.StoreProfileDTO;
import com.pahal.billingApp.security.CustomUserDetails;
import com.pahal.billingApp.service.StoreProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store-profile")
public class StoreProfileController {
    private final StoreProfileService service;

    public StoreProfileController(StoreProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<StoreProfileDTO> get(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(service.getForTenant(principal.getTenantId()));
    }

    @PutMapping
    public ResponseEntity<StoreProfileDTO> save(@AuthenticationPrincipal CustomUserDetails principal,
                                                @RequestBody StoreProfileDTO request) {
        return ResponseEntity.ok(service.saveForTenant(principal.getTenantId(), request));
    }
}
