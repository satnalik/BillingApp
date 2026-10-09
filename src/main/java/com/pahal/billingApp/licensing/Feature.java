package com.pahal.billingApp.licensing;

import java.util.Set;

/** Stable commercial feature identifiers. Invoice tax and essential shifts are core. */
public enum Feature {
    BILLING, PRODUCTS, CUSTOMER_DUES, SALES_RETURNS, BASIC_REPORTS,
    PURCHASES, SUPPLIER_STATEMENTS, STOCK_ADJUSTMENTS, OPENING_STOCK_IMPORT,
    CASH_RECONCILIATION, PROFIT_REPORTS, ADVANCED_REPORTS, GST_ACCOUNTING;

    public static final Set<Feature> CORE = Set.of(BILLING, PRODUCTS, CUSTOMER_DUES, SALES_RETURNS, BASIC_REPORTS);
    public Set<Feature> dependencies() {
        return switch (this) {
            case SUPPLIER_STATEMENTS -> Set.of(PURCHASES);
            case OPENING_STOCK_IMPORT -> Set.of(STOCK_ADJUSTMENTS);
            case PROFIT_REPORTS, ADVANCED_REPORTS, GST_ACCOUNTING, CASH_RECONCILIATION -> Set.of(BILLING);
            default -> Set.of();
        };
    }
}
