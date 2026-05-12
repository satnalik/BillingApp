package com.pahal.billingApp.controller;

import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.repository.SalesManRepository;
import com.pahal.billingApp.security.CustomUserDetails;
import com.pahal.billingApp.service.SalesManService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/salesman")
@Tag(name = "Salesman API", description = "Endpoints for managing salesmen, including creation and active status updates")
public class SalesManController {

    @Autowired
    private SalesManService salesManService;

    @Operation(summary = "Add New Salesman", description = "Creates a new salesman with the provided details. The salesman will be associated with the tenant from the JWT token.")
    @PostMapping(path = "/addsalesman")
    public ResponseEntity<?> addNewSalesMan(@RequestBody Salesman salesman) {
        salesManService.addNewSalesMan(salesman);
        return new ResponseEntity<>("SalesMan created successfully. Employee_ID is: " + salesman.getEmployeeId(),
                HttpStatus.OK);
    }

    @Operation(summary = "Get All Salesmen", description = "Retrieves all salesmen for the current tenant. Tenant scoping is enforced by Hibernate filter.")
    @GetMapping
    public ResponseEntity<?> getSalesmen(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
        }
        // Extract tenantId from your custom UserDetails
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String tenantId = userDetails.getTenantId();

        List<Salesman> list = salesManService.getAllSalesmenByTenant(tenantId);
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{employeeId}/active")
    public ResponseEntity<?> updateActiveStatus(
            @PathVariable String employeeId,
            @RequestBody Map<String, Boolean> payload,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String tenantId = userDetails.getTenantId();
        boolean active = payload.getOrDefault("active", Boolean.TRUE);

        Salesman updated = salesManService.setActiveStatus(employeeId, tenantId, active);
        return ResponseEntity.ok(updated);
    }
}
