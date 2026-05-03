package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;

    public Product addNewProduct(Product product){
        return productRepository.save(product);
    }
    public List<Product> getAllProducts(){
        return productRepository.findAll();
    }
    public List<Product> searchProducts(String name, String tenantId) {
        return productRepository.findByNameContainingIgnoreCaseAndTenantId(name, tenantId);
    }

    public Product getByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new RuntimeException("Barcode is required");
        }
        return productRepository.findByBarcode(barcode.trim());
    }

    public Product upsertByBarcode(Product request) {
        if (request.getBarcode() == null || request.getBarcode().isBlank()) {
            throw new RuntimeException("Barcode is required");
        }
        String normalizedBarcode = request.getBarcode().trim();
        request.setBarcode(normalizedBarcode);

        Product existing = productRepository.findByBarcode(normalizedBarcode);
        if (existing == null) {
            normalizeSellingPriceFields(request);
            return productRepository.save(request);
        }

        existing.setName(request.getName());
        existing.setCategory(request.getCategory());
        existing.setSupplierName(request.getSupplierName());
        existing.setCostPrice(request.getCostPrice());
        existing.setLandingPrice(request.getLandingPrice());
        existing.setMrp(request.getMrp());
        existing.setSellingPrice(request.getSellingPrice());
        existing.setPrice(request.getPrice());
        existing.setStockQuantity(request.getStockQuantity());

        normalizeSellingPriceFields(existing);
        return productRepository.save(existing);
    }

    private void normalizeSellingPriceFields(Product product) {
        // Keep legacy `price` and new `sellingPrice` in sync for older UI/backend code paths.
        if (product.getSellingPrice() == null && product.getPrice() != null) {
            product.setSellingPrice(product.getPrice());
        } else if (product.getPrice() == null && product.getSellingPrice() != null) {
            product.setPrice(product.getSellingPrice());
        }
    }
}
