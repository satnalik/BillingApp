package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DayEndReportResponse {
    private String storeId;
    private String date; // YYYY-MM-DD

    /**
     * Sum of payments collected by method for the day (excluding CREDIT).
     */
    private Map<PaymentMethod, Double> collectionsByMethod = new EnumMap<>(PaymentMethod.class);

    /**
     * Total of non-credit collections for the day.
     */
    private Double totalCollected = 0.0;

    /**
     * Sum of CREDIT amounts for the day (pending / due).
     */
    private Double pendingCredit = 0.0;

    /**
     * Per-salesman breakdown for the day (within the tenant).
     */
    private List<SalesmanDayEnd> salesmen = new ArrayList<>();

    @Getter
    @Setter
    public static class SalesmanDayEnd {
        private String employeeId;
        private String name;
        private Map<PaymentMethod, Double> collectionsByMethod = new EnumMap<>(PaymentMethod.class);
        private Double totalCollected = 0.0;
        private Double pendingCredit = 0.0;
    }
}
