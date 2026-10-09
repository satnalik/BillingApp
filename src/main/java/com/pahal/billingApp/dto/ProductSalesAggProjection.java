package com.pahal.billingApp.dto;

public interface ProductSalesAggProjection {
    Long getProductId();
    String getProductName();
    String getBarcode();
    String getCategory();
    String getHsnCode();
    Double getCurrentStock();
    Double getQuantitySold();
    Long getBillsCount();
    Double getGrossRevenue();
    Double getDiscountAmount();
    Double getTaxableRevenue();
    Double getGstAmount();
    Double getNetRevenue();
}
