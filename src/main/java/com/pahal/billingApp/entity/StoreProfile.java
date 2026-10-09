package com.pahal.billingApp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One business identity and receipt configuration per tenant. */
@Entity
@Table(name = "store_profiles")
@Getter
@Setter
public class StoreProfile {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "store_name", nullable = false, length = 160)
    private String storeName;

    @Column(length = 1000)
    private String address;

    @Column(name = "phone_number", length = 40)
    private String phoneNumber;

    @Column(name = "gstin", length = 20)
    private String gstin;

    @Column(name = "invoice_header", length = 120)
    private String invoiceHeader;

    @Column(name = "invoice_footer", length = 500)
    private String invoiceFooter;
}
