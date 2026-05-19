package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.Supplier;
import org.springframework.stereotype.Service;

@Service
public class BarcodeService {

    public String generateProductBarcode(Product product, Supplier supplier) {
        if (product == null || product.getId() == null) {
            throw new RuntimeException("Product must be saved before barcode generation");
        }
        if (supplier == null || supplier.getSupplierCode() == null || supplier.getSupplierCode().isBlank()) {
            throw new RuntimeException("Supplier code is required for barcode generation");
        }
        return supplier.getSupplierCode().trim().toUpperCase() + "-" + String.format("%06d", product.getId());
    }
}
