package com.pahal.billingApp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SupplierStatementDTO(Long supplierId, String supplierName, String supplierCode,
        String address, String phoneNumber, LocalDate from, LocalDate to,
        double openingBalance, double closingBalance, double increases, double decreases,
        double purchaseDue, double supplierCredit, List<Entry> entries) {
    public record Entry(LocalDateTime date, String type, Long purchaseBillId, String billNumber,
                        String reference, String description, double increase, double decrease, double balance) {}
}
