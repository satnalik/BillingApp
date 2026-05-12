package com.pahal.billingApp.controller;

import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.security.CustomUserDetails;
import com.pahal.billingApp.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suppliers")
@Tag(name = "Supplier API", description = "Endpoints for managing supplier master data")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    @Operation(summary = "Create Supplier", description = "Creates a supplier for the current tenant.")
    @PostMapping
    public Supplier createSupplier(@RequestBody Supplier supplier) {
        return supplierService.createSupplier(supplier);
    }

    @Operation(summary = "Get All Suppliers", description = "Retrieves all suppliers for the current tenant.")
    @GetMapping
    public List<Supplier> getAllSuppliers() {
        return supplierService.getAllSuppliers();
    }

    @Operation(summary = "Search Suppliers", description = "Searches suppliers by name for the current tenant.")
    @GetMapping("/search")
    public ResponseEntity<List<Supplier>> searchSuppliers(@RequestParam String name) {
        return ResponseEntity.ok(supplierService.searchSuppliers(name));
    }

    @Operation(summary = "Update Supplier", description = "Updates supplier master details.")
    @PutMapping("/{id}")
    public ResponseEntity<Supplier> updateSupplier(
            @PathVariable Long id,
            @RequestBody Supplier supplier,
            Authentication authentication) {
        String tenantId = getTenantId(authentication);
        return ResponseEntity.ok(supplierService.updateSupplier(id, supplier, tenantId));
    }

    @Operation(summary = "Update Supplier Active Status", description = "Activates or deactivates a supplier.")
    @PatchMapping("/{id}/active")
    public ResponseEntity<Supplier> updateActiveStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload,
            Authentication authentication) {
        String tenantId = getTenantId(authentication);
        boolean active = payload.getOrDefault("active", Boolean.TRUE);
        return ResponseEntity.ok(supplierService.setActiveStatus(id, tenantId, active));
    }

    private String getTenantId(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getTenantId();
    }
}
