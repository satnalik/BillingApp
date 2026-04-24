package com.pahal.billingApp.entity;

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

    private String email;
    private String password; // Encrypted

    @Column(name = "tenant_id")
    private String tenantId; // Link to their specific store
    private String name;
}