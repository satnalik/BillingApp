package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;

public interface SalesmanPaymentAggProjection {
    String getEmployeeId();
    String getName();
    PaymentMethod getMethod();
    Double getAmount();
}

