package com.pahal.billingApp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CashierShiftDTO {
    private CashierShiftDTO() {}
    public record OpenRequest(String requestKey, BigDecimal openingCash, String counterName) {}
    public record CashRequest(String requestKey, Boolean cashIn, BigDecimal amount, String reason, String reference) {}
    public record CloseRequest(String requestKey, BigDecimal countedCash, String expectedRevision, String notes) {}
    public record Workspace(String cashierUserId, String cashierName, boolean canReview, Detail currentShift) {}
    public record Status(Long shiftId, String cashierName, String counterName, LocalDateTime openedAt) {}
    public record MethodTotals(BigDecimal received, BigDecimal refunded, BigDecimal net) {}
    public record Entry(String key, LocalDateTime date, String type, String method, BigDecimal amount,
                        Long billId, String reference, String description, String actorName) {}
    public record Summary(Long id, String cashierUserId, String cashierName, String counterName,
            LocalDateTime openedAt, LocalDateTime closedAt, BigDecimal openingCash, BigDecimal expectedCash,
            BigDecimal countedCash, BigDecimal variance, String closedByName, String closeNotes) {}
    public record Detail(Summary shift, Map<String, MethodTotals> payments, BigDecimal manualCashIn,
                         BigDecimal manualCashOut, BigDecimal expectedCash, String revision, List<Entry> entries) {}
    public record History(List<Summary> items, int page, int totalPages, long totalElements) {}
}
