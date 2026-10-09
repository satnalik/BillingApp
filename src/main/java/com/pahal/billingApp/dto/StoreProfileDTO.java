package com.pahal.billingApp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreProfileDTO {
    private String storeName;
    private String address;
    private String phoneNumber;
    private String gstin;
    private String invoiceHeader;
    private String invoiceFooter;
}
