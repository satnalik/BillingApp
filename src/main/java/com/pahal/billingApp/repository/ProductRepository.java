package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Product findByName(String ProductName);
    // Standard JPA methods like findById, save, and delete will now
    // all be tenant-aware thanks to our Filter and Aspect.
}
