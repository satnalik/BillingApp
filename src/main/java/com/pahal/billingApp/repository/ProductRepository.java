package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Product findByName(String ProductName);
    // Standard JPA methods like findById, save, and delete will now
    // all be tenant-aware thanks to our Filter and Aspect.
    // Search products by name AND tenantId
    List<Product> findByNameContainingIgnoreCaseAndTenantId(String name, String tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND UPPER(p.name) LIKE UPPER(CONCAT('%', :name, '%'))")
    List<Product> searchByNameAndTenant(@Param("name") String name, @Param("tenantId") String tenantId);

    Product findByBarcode(String barcode);

    Optional<Product> findByIdAndTenantId(Long id, String tenantId);

    long countByStockQuantityLessThanEqual(Double threshold);

    List<Product> findTop10ByStockQuantityLessThanEqualOrderByStockQuantityAsc(Double threshold);
}
