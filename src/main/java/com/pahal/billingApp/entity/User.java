package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String password; // Encrypted

    @Column(name = "tenant_id")
    private String tenantId; // Link to their specific store
}