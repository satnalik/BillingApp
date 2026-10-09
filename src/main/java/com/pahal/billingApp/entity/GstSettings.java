package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Table(name = "gst_settings") @Getter @Setter
public class GstSettings {
    @Id @Column(length = 100) private String tenantId;
    @Version private Long version;
    @Column(nullable = false, length = 20) private String registrationMode = "NOT_CONFIGURED";
    @Column(length = 15) private String gstin;
    @Column(length = 160) private String legalName;
    @Column(length = 1000) private String address;
    @Column(length = 2) private String stateCode;
    private LocalDate effectiveFrom;
    @Column(length = 12) private String priceMode = "EXCLUSIVE";
    @Column(length = 4) private String invoicePrefix = "PR";
}
