package com.pahal.billingApp.controller;

import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.security.CustomUserDetails;
import com.pahal.billingApp.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/products")
@Tag(name = "Product API", description = "Endpoints for managing products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Operation(summary = "Get All Products", description = "Retrieves all products for the current tenant. Tenant scoping is enforced by Hibernate filter.")
    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @Operation(summary = "Create a New Product", description = "Creates a new product with the provided details. The product will be associated with the tenant from the JWT token.")
    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productService.addNewProduct(product);
    }

    /**
     * Fetch a product by barcode (barcode scanning support).
     * Tenant scoping is enforced by Hibernate filter.
     */
    @Operation(summary = "Get Product by Barcode", description = "Retrieves a product by its barcode. Tenant scoping is enforced by Hibernate filter.")
    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<Product> getByBarcode(@PathVariable String barcode) {
        Product p = productService.getByBarcode(barcode);
        if (p == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(p);
    }

    /**
     * Create or update a product by barcode.
     * Useful for "scan then add details" inventory flow.
     */
    @Operation(summary = "Upsert Product by Barcode", description = "Creates or updates a product based on its barcode. Useful for 'scan then add details' inventory flow.")
    @PostMapping("/barcode")
    public ResponseEntity<Product> upsertByBarcode(@RequestBody Product product) {
        return ResponseEntity.ok(productService.upsertByBarcode(product));
    }

    @Operation(summary = "Search Products", description = "Searches for products by name. Tenant scoping is enforced by Hibernate filter.")
    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProducts(
            @RequestParam String name,
            Authentication authentication) {

        // Extract tenantId from the JWT claims
        // (Assuming your CustomUserDetails stores tenantId)
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String tenantId = userDetails.getTenantId();

        List<Product> products = productService.searchProducts(name, tenantId);
        return ResponseEntity.ok(products);
    }
}
