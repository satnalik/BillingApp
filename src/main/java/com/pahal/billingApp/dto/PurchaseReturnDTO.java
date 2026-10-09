package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import java.util.List;

public final class PurchaseReturnDTO {
    private PurchaseReturnDTO() {}
    public record Line(Long purchaseItemId, Double quantity, Double expectedReturnedQuantity) {}
    public record Request(String requestKey, String reason, List<Line> items) {}
    public record RefundRequest(String requestKey, Double amount, Double expectedCreditAmount,
                                PaymentMethod method, String reference) {}
}
