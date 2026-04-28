package com.pahal.billingApp.dto;


import com.pahal.billingApp.enums.Role;
import lombok.Data;

@Data
public class UserDetailsDTO {

    private Long id;
    private String userId;
    private Role role;
}
