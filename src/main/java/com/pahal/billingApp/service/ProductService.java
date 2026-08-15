package com.pahal.billingApp.service;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.dto.AddProductBarcodeRequest;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.ProductBarcode;
import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.repository.ProductBarcodeRepository;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductBarcodeRepository productBarcodeRepository;

    @Transactional
    public Product addNewProduct(Product product){
        resolveAndApplySupplier(product);
        normalizeSellingPriceFields(product);
        normalizeTaxFields(product);
        Product saved = productRepository.save(product);
        ensurePrimaryBarcode(saved);
        return saved;
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
        String normalizedBarcode = normalizeBarcode(barcode);
        return productBarcodeRepository.findByBarcode(normalizedBarcode)
                .map(ProductBarcode::getProduct)
                .orElseGet(() -> productRepository.findByBarcode(normalizedBarcode));
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

    @Transactional
    public Product upsertByBarcode(Product request) {
        if (request.getBarcode() == null || request.getBarcode().isBlank()) {
            throw new RuntimeException("Barcode is required");
        }
        String normalizedBarcode = normalizeBarcode(request.getBarcode());
        request.setBarcode(normalizedBarcode);

        Product existing = productBarcodeRepository.findByBarcode(normalizedBarcode)
                .map(ProductBarcode::getProduct)
                .orElseGet(() -> productRepository.findByBarcode(normalizedBarcode));
        if (existing == null) {
            resolveAndApplySupplier(request);
            normalizeSellingPriceFields(request);
            normalizeTaxFields(request);
            Product saved = productRepository.save(request);
            ensurePrimaryBarcode(saved);
            return saved;
        }

        existing.setName(request.getName());
        existing.setCategory(request.getCategory());
        existing.setCostPrice(request.getCostPrice());
        existing.setLandingPrice(request.getLandingPrice());
        existing.setMrp(request.getMrp());
        existing.setSellingPrice(request.getSellingPrice());
        existing.setPrice(request.getPrice());
        existing.setStockQuantity(request.getStockQuantity());
        existing.setHsnCode(request.getHsnCode());
        existing.setGstRate(request.getGstRate());

        Supplier supplier = resolveSupplier(request.getSupplier());
        existing.setSupplier(supplier);
        if (supplier != null) {
            existing.setSupplierName(supplier.getName());
        } else {
            existing.setSupplierName(request.getSupplierName());
        }

        normalizeSellingPriceFields(existing);
        normalizeTaxFields(existing);
        Product saved = productRepository.save(existing);
        ensurePrimaryBarcode(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ProductBarcode> getBarcodes(Long productId) {
        findProduct(productId);
        return productBarcodeRepository.findByProductIdOrderByPrimaryBarcodeDescIdAsc(productId);
    }

    @Transactional
    public ProductBarcode addBarcode(Long productId, AddProductBarcodeRequest request) {
        if (request == null) {
            throw new RuntimeException("Barcode request is required");
        }
        Product product = findProduct(productId);
        ProductBarcode barcode = saveBarcodeForProduct(
                product,
                request.getBarcode(),
                request.getQuantityPerScan(),
                request.getBarcodeType(),
                Boolean.TRUE.equals(request.getPrimaryBarcode()));

        if (barcode.isPrimaryBarcode()) {
            product.setBarcode(barcode.getBarcode());
            productRepository.save(product);
        }
        return barcode;
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

    private Product findProduct(Long productId) {
        if (productId == null) {
            throw new RuntimeException("Product id is required");
        }

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
        }

        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    private void ensurePrimaryBarcode(Product product) {
        if (product == null || product.getBarcode() == null || product.getBarcode().isBlank()) {
            return;
        }
        saveBarcodeForProduct(product, product.getBarcode(), 1.0, null, true);
    }

    private ProductBarcode saveBarcodeForProduct(
            Product product,
            String barcodeValue,
            Double quantityPerScan,
            String barcodeType,
            boolean primaryBarcode) {
        if (barcodeValue == null || barcodeValue.isBlank()) {
            throw new RuntimeException("Barcode is required");
        }
        String normalizedBarcode = normalizeBarcode(barcodeValue);

        ProductBarcode existing = productBarcodeRepository.findByBarcode(normalizedBarcode).orElse(null);
        if (existing != null) {
            if (existing.getProduct() == null || existing.getProduct().getId() == null
                    || !existing.getProduct().getId().equals(product.getId())) {
                throw new RuntimeException("Barcode already belongs to another product");
            }
            existing.setQuantityPerScan(normalizeQuantityPerScan(quantityPerScan));
            existing.setBarcodeType(normalizeBlank(barcodeType));
            if (primaryBarcode) {
                markExistingBarcodesNonPrimary(product.getId());
                existing.setPrimaryBarcode(true);
            }
            return productBarcodeRepository.save(existing);
        }

        boolean shouldBePrimary = primaryBarcode
                || productBarcodeRepository.findFirstByProductIdAndPrimaryBarcodeTrueOrderByIdAsc(product.getId()).isEmpty();
        if (shouldBePrimary) {
            markExistingBarcodesNonPrimary(product.getId());
        }

        ProductBarcode productBarcode = new ProductBarcode();
        productBarcode.setProduct(product);
        productBarcode.setBarcode(normalizedBarcode);
        productBarcode.setQuantityPerScan(normalizeQuantityPerScan(quantityPerScan));
        productBarcode.setBarcodeType(normalizeBlank(barcodeType));
        productBarcode.setPrimaryBarcode(shouldBePrimary);
        return productBarcodeRepository.save(productBarcode);
    }

    private void markExistingBarcodesNonPrimary(Long productId) {
        for (ProductBarcode barcode : productBarcodeRepository.findByProductIdOrderByPrimaryBarcodeDescIdAsc(productId)) {
            if (barcode.isPrimaryBarcode()) {
                barcode.setPrimaryBarcode(false);
                productBarcodeRepository.save(barcode);
            }
        }
    }

    private static String normalizeBarcode(String barcode) {
        String normalized = barcode != null ? barcode.trim() : null;
        if (normalized == null || normalized.isBlank()) {
            throw new RuntimeException("Barcode is required");
        }
        return normalized;
    }

    private static Double normalizeQuantityPerScan(Double quantityPerScan) {
        if (quantityPerScan == null) {
            return 1.0;
        }
        if (quantityPerScan <= 0.0) {
            throw new RuntimeException("Quantity per scan must be > 0");
        }
        return quantityPerScan;
    }

    private static String normalizeBlank(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void normalizeSellingPriceFields(Product product) {
        // Keep legacy `price` and new `sellingPrice` in sync for older UI/backend code paths.
        if (product.getSellingPrice() == null && product.getPrice() != null) {
            product.setSellingPrice(product.getPrice());
        } else if (product.getPrice() == null && product.getSellingPrice() != null) {
            product.setPrice(product.getSellingPrice());
        }
    }

    private void normalizeTaxFields(Product product) {
        if (product.getHsnCode() != null) {
            String hsnCode = product.getHsnCode().trim();
            product.setHsnCode(hsnCode.isBlank() ? null : hsnCode);
        }

        if (product.getGstRate() == null) {
            return;
        }

        double gstRate = product.getGstRate();
        if (gstRate > 1.0) {
            gstRate = gstRate / 100.0;
        }
        if (gstRate < 0.0 || gstRate > 1.0) {
            throw new RuntimeException("Product GST rate must be between 0 and 100 percent");
        }
        product.setGstRate(gstRate);
    }
}
