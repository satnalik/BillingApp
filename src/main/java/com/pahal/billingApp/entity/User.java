package com.pahal.billingApp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pahal.billingApp.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "users")
@Data

@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private Role role;

    private String userId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password; // Encrypted

    @Column(name = "tenant_id")
    private String tenantId; // Link to their specific store
    private String name;
}