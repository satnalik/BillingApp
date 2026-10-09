package com.pahal.billingApp.service;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.StockMovement;
import com.pahal.billingApp.enums.StockMovementType;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.StockMovementRepository;
import com.pahal.billingApp.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class StockService {
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final EntityManager entityManager;

    public StockService(ProductRepository products, StockMovementRepository movements, EntityManager entityManager) {
        this.products = products;
        this.movements = movements;
        this.entityManager = entityManager;
    }

    public Product lockProduct(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("A valid product ID is required.");
        Product product = products.findByIdAndTenantId(id, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Product not found in this store."));
        // Refresh while acquiring the lock: barcode resolution may have already loaded a stale entity.
        // A product already locked by this transaction retains its pending stock changes.
        if (entityManager.getLockMode(product) != LockModeType.PESSIMISTIC_WRITE) {
            entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        }
        return product;
    }

    @CacheEvict(cacheNames = "reports", allEntries = true)
    public StockMovement changeStock(Product product, double delta, StockMovementType type,
                                     String reason, String notes, String reference, Long referenceId) {
        product = lockProduct(product.getId());
        if (!Double.isFinite(delta) || (round2(delta) == 0 && type != StockMovementType.OPENING_STOCK)) {
            throw new IllegalArgumentException("Stock change must be a finite, non-zero quantity.");
        }
        double before = stockQuantity(product);
        double after = round2(before + delta);
        if (!Double.isFinite(after) || after < 0 || after > 1_000_000_000d) {
            throw new IllegalArgumentException("Insufficient stock or invalid resulting quantity for: " + product.getName());
        }
        if (before != 0 && !movements.existsByTenantIdAndProductId(requireTenant(), product.getId())) {
            saveMovement(product, 0, before, StockMovementType.BALANCE_BROUGHT_FORWARD,
                    "Existing stock", "Balance carried forward when movement tracking began.", null, null, true);
        }
        product.setStockQuantity(after);
        return saveMovement(product, before, after, type, reason, notes, reference, referenceId, false);
    }

    @CacheEvict(cacheNames = "reports", allEntries = true)
    public void recordProductOpening(Product savedProduct, double quantity) {
        // New products are saved at zero stock; their initial quantity is recorded separately.
        if (quantity > 0) {
            changeStock(savedProduct, quantity, StockMovementType.OPENING_STOCK,
                    "Initial stock", "Opening stock entered while creating the product.", "Product creation", null);
        }
    }

    private StockMovement saveMovement(Product product, double before, double after, StockMovementType type,
                                        String reason, String notes, String reference, Long referenceId, boolean baseline) {
        StockMovement movement = new StockMovement();
        movement.setTenantId(requireTenant());
        movement.setProductId(product.getId());
        movement.setProductName(product.getName());
        movement.setBarcode(product.getBarcode());
        movement.setType(type);
        movement.setReason(reason);
        movement.setNotes(trimTo(notes, 500));
        movement.setQuantityBefore(before);
        movement.setQuantityChange(round2(after - before));
        movement.setQuantityAfter(after);
        movement.setReference(trimTo(reference, 240));
        movement.setReferenceId(referenceId);
        movement.setCreatedAt(LocalDateTime.now());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!baseline && authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user) {
            movement.setActorUserId(trimTo(user.getUsername(), 160));
            movement.setActorName(trimTo(user.getDisplayName(), 160));
        } else {
            movement.setActorName("System");
        }
        return movements.save(movement);
    }

    public static String requireTenant() {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.isBlank()) throw new IllegalArgumentException("A signed-in store is required.");
        return tenant;
    }

    public static double stockQuantity(Product product) {
        double value = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid existing stock for: " + product.getName());
        return round2(value);
    }

    public static double validateQuantity(Double quantity, boolean allowZero) {
        if (quantity == null || !Double.isFinite(quantity) || quantity < 0 || (!allowZero && quantity == 0)
                || quantity > 1_000_000_000d || BigDecimal.valueOf(quantity).stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Quantity must be " + (allowZero ? "zero or positive" : "positive")
                    + ", with at most two decimal places (maximum 1,000,000,000).");
        }
        return round2(quantity);
    }

    public static double round2(double value) { return Math.round(value * 100.0) / 100.0; }
    private static String trimTo(String value, int maximum) {
        if (value == null) return null;
        String text = value.trim();
        return text.length() > maximum ? text.substring(0, maximum) : text;
    }
}
