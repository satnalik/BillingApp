package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.LoginRequest;
import com.pahal.billingApp.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        // 1. In a real app, verify email/password against Azure DB
        // 2. Fetch the user's assigned tenantId
        String mockTenantId = "Bankura_Store";

        // 3. Return the token containing the tenantId
        return jwtService.generateToken(request.getEmail(), mockTenantId);
    }
}