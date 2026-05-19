package com.pahal.billingApp.service;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    public Product addNewProduct(Product product){
        resolveAndApplySupplier(product);
        normalizeSellingPriceFields(product);
        return productRepository.save(product);
    }
    public List<Product> getAllProducts(){
        return productRepository.findAll();
    }

    public List<Product> getProductsBySupplier(Long supplierId) {
        if (supplierId == null) {
            return getAllProducts();
        }
        return productRepository.findBySupplierId(supplierId);
    }

    public List<Product> searchProducts(String name, String tenantId) {
        return productRepository.findByNameContainingIgnoreCaseAndTenantId(name, tenantId);
    }

    public List<Product> searchProductsBySupplier(Long supplierId, String name) {
        if (supplierId == null) {
            String tenantId = TenantContext.getCurrentTenant();
            return tenantId != null
                    ? searchProducts(name, tenantId)
                    : productRepository.findByNameContainingIgnoreCaseAndTenantId(name, null);
        }
        return productRepository.findBySupplierIdAndNameContainingIgnoreCase(supplierId, name);
    }

    public Product getByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new RuntimeException("Barcode is required");
        }
        return productRepository.findByBarcode(barcode.trim());
    }

    public Product getByBarcode(String barcode, Long supplierId) {
        Product product = getByBarcode(barcode);
        if (product == null || supplierId == null) {
            return product;
        }
        if (product.getSupplier() == null || product.getSupplier().getId() == null) {
            return null;
        }
        return product.getSupplier().getId().equals(supplierId) ? product : null;
    }

    public Product upsertByBarcode(Product request) {
        if (request.getBarcode() == null || request.getBarcode().isBlank()) {
            throw new RuntimeException("Barcode is required");
        }
        String normalizedBarcode = request.getBarcode().trim();
        request.setBarcode(normalizedBarcode);

        Product existing = productRepository.findByBarcode(normalizedBarcode);
        if (existing == null) {
            resolveAndApplySupplier(request);
            normalizeSellingPriceFields(request);
            return productRepository.save(request);
        }

        existing.setName(request.getName());
        existing.setCategory(request.getCategory());
        existing.setCostPrice(request.getCostPrice());
        existing.setLandingPrice(request.getLandingPrice());
        existing.setMrp(request.getMrp());
        existing.setSellingPrice(request.getSellingPrice());
        existing.setPrice(request.getPrice());
        existing.setStockQuantity(request.getStockQuantity());

        Supplier supplier = resolveSupplier(request.getSupplier());
        existing.setSupplier(supplier);
        if (supplier != null) {
            existing.setSupplierName(supplier.getName());
        } else {
            existing.setSupplierName(request.getSupplierName());
        }

        normalizeSellingPriceFields(existing);
        return productRepository.save(existing);
    }

    private void resolveAndApplySupplier(Product product) {
        Supplier supplier = resolveSupplier(product.getSupplier());
        product.setSupplier(supplier);
        if (supplier != null) {
            product.setSupplierName(supplier.getName());
        }
    }

    private Supplier resolveSupplier(Supplier supplier) {
        if (supplier == null || supplier.getId() == null) {
            return null;
        }

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return supplierRepository.findById(supplier.getId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found"));
        }

        return supplierRepository.findByIdAndTenantId(supplier.getId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
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
