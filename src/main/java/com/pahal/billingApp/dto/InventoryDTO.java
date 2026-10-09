package com.pahal.billingApp.dto;

import com.pahal.billingApp.entity.StockMovement;
import com.pahal.billingApp.enums.StockAdjustmentReason;
import java.util.List;

public final class InventoryDTO {
    private InventoryDTO() {}
    public enum AdjustmentMode { ADD, REMOVE, SET_COUNT }
    public record AdjustmentRequest(Long productId, AdjustmentMode mode, Double quantity,
                                    StockAdjustmentReason reason, String notes, Double expectedStockQuantity) {}
    public record OpeningRow(Long productId, String barcode, Double quantity, String notes) {}
    public record OpeningRequest(String sourceName, List<OpeningRow> rows) {}
    public record PreviewRow(int rowNumber, Long productId, String productName, String barcode,
                             Double quantity, Double currentQuantity, String notes, List<String> errors) {}
    public record OpeningPreview(List<PreviewRow> rows, boolean valid, double totalQuantity) {}
    public record ImportResult(int importedRows, double totalQuantity, String reference) {}
    public record MovementPage(List<StockMovement> items, int page, int size, long totalElements, int totalPages) {}
}
