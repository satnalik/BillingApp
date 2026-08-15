package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.ProductBarcode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, Long> {
    @EntityGraph(attributePaths = "product")
    Optional<ProductBarcode> findByBarcode(String barcode);

    List<ProductBarcode> findByProductIdOrderByPrimaryBarcodeDescIdAsc(Long productId);

    Optional<ProductBarcode> findFirstByProductIdAndPrimaryBarcodeTrueOrderByIdAsc(Long productId);

    Optional<ProductBarcode> findFirstByProductIdOrderByIdAsc(Long productId);
}
