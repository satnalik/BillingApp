package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class BillRegisterResponse {
    private List<Item> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    @Getter
    @Setter
    public static class Item {
        private Long id;
        private String billNumber;
        private LocalDateTime createdAt;
        private String customerName;
        private String contactInfo;
        private String salesmanEmployeeId;
        private String salesmanName;
        private int itemsCount;
        private Double totalAmount;
        private Double paidAmount;
        private Double dueAmount;
        private List<PaymentMethod> paymentMethods;
    }
}
