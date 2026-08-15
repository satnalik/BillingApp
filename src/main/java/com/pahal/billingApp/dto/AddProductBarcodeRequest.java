package com.pahal.billingApp.dto;

import lombok.Data;

@Data
public class AddProductBarcodeRequest {
    private String barcode;
    private Double quantityPerScan;
    private String barcodeType;
    private Boolean primaryBarcode;
}
