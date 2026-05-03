package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;

public interface PaymentMethodAggProjection {
    PaymentMethod getMethod();
    Double getAmount();
}

