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
    @Query("""
            select p.id as productId, p.name as productName, p.barcode as barcode,
                   p.category as category, p.supplierName as supplierName,
                   p.stockQuantity as stockQuantity, p.costPrice as costPrice
            from Product p where p.tenantId = :tenantId order by p.name, p.id
            """)
    List<com.pahal.billingApp.dto.OperationalReportDTO.StockRow> findStockReportRows(
            @Param("tenantId") String tenantId);

    Product findByName(String ProductName);
    // Standard JPA methods like findById, save, and delete will now
    // all be tenant-aware thanks to our Filter and Aspect.
    // Search products by name AND tenantId
    List<Product> findByNameContainingIgnoreCaseAndTenantId(String name, String tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND UPPER(p.name) LIKE UPPER(CONCAT('%', :name, '%'))")
    List<Product> searchByNameAndTenant(@Param("name") String name, @Param("tenantId") String tenantId);

    Product findByBarcode(String barcode);

    Optional<Product> findByIdAndTenantId(Long id, String tenantId);

    Optional<Product> findByBarcodeAndTenantId(String barcode, String tenantId);

    List<Product> findBySupplierId(Long supplierId);

    List<Product> findBySupplierIdAndNameContainingIgnoreCase(Long supplierId, String name);

    long countByStockQuantityLessThanEqual(Double threshold);

    List<Product> findTop10ByStockQuantityLessThanEqualOrderByStockQuantityAsc(Double threshold);
}
