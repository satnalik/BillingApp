package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.InventoryDTO.*;
import com.pahal.billingApp.entity.StockMovement;
import com.pahal.billingApp.enums.StockMovementType;
import com.pahal.billingApp.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Store stock adjustments, movement history, and opening stock. Manager/admin access.")
public class InventoryController {
    private final InventoryService inventory;
    public InventoryController(InventoryService inventory) { this.inventory = inventory; }

    @PostMapping("/adjustments")
    @Operation(summary = "Adjust stock with a reason", description = "ADD, REMOVE, or SET_COUNT. The logged-in user is recorded. SET_COUNT requires expectedStockQuantity.")
    public StockMovement adjust(@RequestBody AdjustmentRequest request) { return inventory.adjust(request); }

    @GetMapping("/movements")
    @Operation(summary = "View stock movement history")
    public MovementPage history(@RequestParam(required = false) Long productId,
                                @RequestParam(required = false) StockMovementType type,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "25") int size) {
        return inventory.history(productId, type, from, to, page, size);
    }

    @PostMapping("/opening-stock/preview")
    @Operation(summary = "Validate opening stock rows without changing inventory")
    public OpeningPreview preview(@RequestBody OpeningRequest request) { return inventory.previewOpening(request); }

    @PostMapping("/opening-stock/import")
    @Operation(summary = "Import validated opening stock", description = "All rows are revalidated under stock locks and saved in one transaction. Products must have zero stock and no previous activity. Maximum 1,000 rows.")
    public ImportResult importOpening(@RequestBody OpeningRequest request) { return inventory.importOpening(request); }
}
