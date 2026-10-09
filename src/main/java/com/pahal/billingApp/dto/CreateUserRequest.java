package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {
    private String userId;
    private String password;
    private String name;
    private Role role;
}
