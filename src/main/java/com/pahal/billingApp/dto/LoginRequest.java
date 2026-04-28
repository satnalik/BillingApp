package com.pahal.billingApp.dto; // Or .request

import lombok.Data;

@Data // Generates getters and setters
public class LoginRequest {
    private String userId;
    private String password;
}