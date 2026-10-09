package com.pahal.billingApp.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name = "license_installation") @Getter @Setter
public class LicenseInstallation {
    @Id private int id;
    @Column(nullable = false, length = 36) private String deploymentId;
}
