package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
}