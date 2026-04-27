package com.pahal.billingApp.controller;

import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.security.CustomUserDetails;
import com.pahal.billingApp.service.ProductService;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/products")
public class ProductController {


    @Autowired
    private ProductService productService;

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productService.addNewProduct(product);
    }

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
