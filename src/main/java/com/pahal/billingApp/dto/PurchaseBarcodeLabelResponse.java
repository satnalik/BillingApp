package com.pahal.billingApp.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PurchaseBarcodeLabelResponse {
    private Long purchaseBillId;
    private String billNumber;
    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long productId;
        private String productName;
        private String barcode;
        private Double quantityReceived;
        private Integer labelsToPrint;
        private Double sellingPrice;
        private Double mrp;
        private String category;
        private String supplierName;
    }
}
