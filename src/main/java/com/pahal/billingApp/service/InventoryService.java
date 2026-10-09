package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.InventoryDTO.*;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.StockMovement;
import com.pahal.billingApp.enums.StockAdjustmentReason;
import com.pahal.billingApp.enums.StockMovementType;
import com.pahal.billingApp.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class InventoryService {
    private final StockService stock;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final ProductBarcodeRepository barcodes;
    private final BillRepository bills;
    private final PurchaseBillRepository purchases;

    public InventoryService(StockService stock, StockMovementRepository movements, ProductRepository products,
                            ProductBarcodeRepository barcodes, BillRepository bills, PurchaseBillRepository purchases) {
        this.stock = stock;
        this.movements = movements;
        this.products = products;
        this.barcodes = barcodes;
        this.bills = bills;
        this.purchases = purchases;
    }

    @Transactional
    @com.pahal.billingApp.licensing.RequiresFeature(com.pahal.billingApp.licensing.Feature.STOCK_ADJUSTMENTS)
    public StockMovement adjust(AdjustmentRequest request) {
        if (request == null || request.mode() == null || request.reason() == null) {
            throw new IllegalArgumentException("Product, adjustment mode, quantity, and reason are required.");
        }
        String notes = validateNotes(request.notes());
        if (request.reason() == StockAdjustmentReason.OTHER && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException("Explain the adjustment when the reason is Other.");
        }
        double quantity = StockService.validateQuantity(request.quantity(), request.mode() == AdjustmentMode.SET_COUNT);
        Product product = stock.lockProduct(request.productId());
        double before = StockService.stockQuantity(product);
        if (request.mode() == AdjustmentMode.SET_COUNT && request.expectedStockQuantity() == null) {
            throw new IllegalArgumentException("Refresh current stock before submitting a physical stock count.");
        }
        if (request.expectedStockQuantity() != null) {
            if (!Double.isFinite(request.expectedStockQuantity())) {
                throw new IllegalArgumentException("Refresh current stock before submitting the adjustment.");
            }
            double expected = StockService.round2(request.expectedStockQuantity());
            if (Math.abs(before - expected) > 0.0000001) {
                throw new IllegalArgumentException("Stock has changed. Refresh the product and submit the adjustment again.");
            }
        }
        double delta = switch (request.mode()) {
            case ADD -> quantity;
            case REMOVE -> -quantity;
            case SET_COUNT -> StockService.round2(quantity - before);
        };
        if (delta == 0) throw new IllegalArgumentException("The counted quantity already matches current stock.");
        return stock.changeStock(product, delta, StockMovementType.ADJUSTMENT, request.reason().name(), notes, null, null);
    }

    @Transactional(readOnly = true)
    public MovementPage history(Long productId, StockMovementType type, LocalDate from, LocalDate to, int page, int size) {
        var result = movements.findAll(movementFilter(productId, type, from, to),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "id")));
        return new MovementPage(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** All matching entries for report viewing/export, using the operational history's same filter. */
    @Transactional(readOnly = true)
    public com.pahal.billingApp.dto.OperationalReportDTO.MovementReport reportMovements(Long productId, StockMovementType type, LocalDate from, LocalDate to) {
        var items = movements.findAll(movementFilter(productId, type, from, to), Sort.by(Sort.Direction.DESC, "id"));
        return new com.pahal.billingApp.dto.OperationalReportDTO.MovementReport(java.time.LocalDateTime.now(), items);
    }

    private Specification<StockMovement> movementFilter(Long productId, StockMovementType type, LocalDate from, LocalDate to) {
        String tenant = StockService.requireTenant();
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("From date must be on or before To date.");
        }
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenant));
            if (productId != null) predicates.add(builder.equal(root.get("productId"), productId));
            if (type != null) predicates.add(builder.equal(root.get("type"), type));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) predicates.add(builder.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    @com.pahal.billingApp.licensing.RequiresFeature(com.pahal.billingApp.licensing.Feature.OPENING_STOCK_IMPORT)
    public OpeningPreview previewOpening(OpeningRequest request) {
        Preparation preparation = prepareOpening(request, false);
        return new OpeningPreview(preparation.rows(), preparation.valid(), preparation.total());
    }

    @Transactional
    @com.pahal.billingApp.licensing.RequiresFeature(com.pahal.billingApp.licensing.Feature.OPENING_STOCK_IMPORT)
    public ImportResult importOpening(OpeningRequest request) {
        Preparation preparation = prepareOpening(request, true);
        if (!preparation.valid()) {
            PreviewRow invalid = preparation.rows().stream().filter(row -> !row.errors().isEmpty()).findFirst().orElseThrow();
            throw new IllegalArgumentException("Nothing imported. Row " + invalid.rowNumber() + ": " + invalid.errors().get(0)
                    + " Preview the file again after correcting it.");
        }
        String reference = "OPEN-" + UUID.randomUUID();
        for (PreviewRow row : preparation.rows()) {
            Product product = preparation.products().get(row.productId());
            StockMovement movement = stock.changeStock(product, row.quantity(), StockMovementType.OPENING_STOCK,
                    "Opening stock import", row.notes(), reference, null);
            movement.setSourceName(request.sourceName() == null ? null : request.sourceName().trim());
        }
        return new ImportResult(preparation.rows().size(), preparation.total(), reference);
    }

    private Preparation prepareOpening(OpeningRequest request, boolean lock) {
        String tenant = StockService.requireTenant();
        if (request == null || request.rows() == null || request.rows().isEmpty() || request.rows().size() > 1000) {
            throw new IllegalArgumentException("The file must contain between 1 and 1,000 product rows.");
        }
        if (request.sourceName() != null && request.sourceName().length() > 240) {
            throw new IllegalArgumentException("File name must be at most 240 characters.");
        }
        List<PreviewRow> rows = new ArrayList<>();
        Map<Long, Product> resolved = new TreeMap<>();
        Set<Long> duplicates = new HashSet<>();
        for (int index = 0; index < request.rows().size(); index++) {
            OpeningRow input = request.rows().get(index);
            Product product = null;
            Double currentQuantity = null;
            List<String> errors = new ArrayList<>();
            try {
                if (input == null) throw new IllegalArgumentException("Product row is empty.");
                product = resolveOpeningProduct(input, tenant);
                if (resolved.putIfAbsent(product.getId(), product) != null) duplicates.add(product.getId());
                currentQuantity = StockService.stockQuantity(product);
                StockService.validateQuantity(input.quantity(), true);
                validateNotes(input.notes());
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
            rows.add(new PreviewRow(index + 2, product == null ? null : product.getId(),
                    product == null ? null : product.getName(), product == null ? (input == null ? null : input.barcode()) : product.getBarcode(),
                    input == null ? null : input.quantity(), currentQuantity,
                    input == null ? null : input.notes(), errors));
        }
        // Lock in ID order before rechecking eligibility. Import is atomic and shares sale/purchase locks.
        if (lock) {
            for (Long id : new ArrayList<>(resolved.keySet())) resolved.put(id, stock.lockProduct(id));
        }
        Set<Long> active = new HashSet<>();
        if (!resolved.isEmpty()) {
            active.addAll(movements.findTrackedProductIds(tenant, resolved.keySet()));
            active.addAll(bills.findProductsWithStockActivity(tenant, resolved.keySet()));
            active.addAll(purchases.findProductsWithStockActivity(tenant, resolved.keySet()));
        }
        List<PreviewRow> checkedRows = new ArrayList<>();
        for (PreviewRow row : rows) {
            Product product = row.productId() == null ? null : resolved.get(row.productId());
            Double currentQuantity = null;
            if (product != null) {
                if (duplicates.contains(product.getId())) row.errors().add("This product appears more than once in the file.");
                try {
                    currentQuantity = StockService.stockQuantity(product);
                    if (active.contains(product.getId()) || currentQuantity != 0) {
                        row.errors().add("Opening stock is only allowed for zero-stock products with no previous stock activity. Use an adjustment for this product.");
                    }
                } catch (IllegalArgumentException exception) {
                    if (!row.errors().contains(exception.getMessage())) row.errors().add(exception.getMessage());
                }
            }
            checkedRows.add(new PreviewRow(row.rowNumber(), row.productId(), row.productName(), row.barcode(), row.quantity(),
                    currentQuantity, row.notes(), row.errors()));
        }
        return new Preparation(checkedRows, resolved);
    }

    private Product resolveOpeningProduct(OpeningRow row, String tenant) {
        String barcode = row.barcode() == null ? "" : row.barcode().trim();
        Product product = null;
        if (row.productId() != null) {
            product = products.findByIdAndTenantId(row.productId(), tenant)
                    .orElseThrow(() -> new IllegalArgumentException("Product ID not found in this store."));
        }
        if (!barcode.isBlank()) {
            if (barcode.length() > 64) throw new IllegalArgumentException("Barcode must be at most 64 characters.");
            Product barcodeProduct = barcodes.findByBarcodeAndTenantId(barcode, tenant)
                    .map(barcodeRow -> barcodeRow.getProduct())
                    .orElseGet(() -> products.findByBarcodeAndTenantId(barcode, tenant).orElse(null));
            if (barcodeProduct == null) throw new IllegalArgumentException("Barcode not found. Create the product before importing stock.");
            if (product != null && !product.getId().equals(barcodeProduct.getId())) {
                throw new IllegalArgumentException("Barcode and product ID identify different products.");
            }
            product = barcodeProduct;
        }
        if (product == null) throw new IllegalArgumentException("Supply a product ID or barcode.");
        return product;
    }

    private static String validateNotes(String notes) {
        if (notes != null && notes.length() > 500) throw new IllegalArgumentException("Notes must be at most 500 characters.");
        return notes == null ? null : notes.trim();
    }

    private record Preparation(List<PreviewRow> rows, Map<Long, Product> products) {
        boolean valid() { return rows.stream().allMatch(row -> row.errors().isEmpty()); }
        double total() { return StockService.round2(rows.stream().filter(row -> row.errors().isEmpty()).mapToDouble(PreviewRow::quantity).sum()); }
    }
}
