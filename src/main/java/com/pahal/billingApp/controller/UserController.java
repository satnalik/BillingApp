package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.UserDetailsDTO;
import com.pahal.billingApp.dto.CreateUserRequest;
import com.pahal.billingApp.security.CustomUserDetails;
import com.pahal.billingApp.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User API", description = "Endpoints for managing users, including creation and retrieval of user details")
public class UserController {
    public record ActiveRequest(boolean active) {}
    @org.springframework.web.bind.annotation.GetMapping
    public Object staff(@AuthenticationPrincipal CustomUserDetails principal) { return userService.staff(principal.getTenantId()); }
    @org.springframework.web.bind.annotation.PatchMapping("/{userId}/active")
    public Object active(@org.springframework.web.bind.annotation.PathVariable String userId, @RequestBody ActiveRequest request,
                         @AuthenticationPrincipal CustomUserDetails principal) {
        userService.activeStaff(userId, principal.getTenantId(), request.active()); return java.util.Map.of("updated", true);
    }

    @Autowired
    private UserService userService;

    @Operation(summary = "Add Staff User", description = "Creates a cashier or manager for the authenticated admin's tenant. Tenant ID and admin role cannot be supplied by the caller.")
    @PostMapping("/adduser")
    public ResponseEntity<?> addNewUser(
            @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserDetailsDTO createdUser = userService.addStaffUser(request, principal.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }
}
