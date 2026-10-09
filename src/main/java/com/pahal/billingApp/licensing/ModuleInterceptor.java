package com.pahal.billingApp.licensing;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.*;

/** Central HTTP coverage includes exports and direct Swagger calls; transaction services also guard new postings. */
@Component
public class ModuleInterceptor implements HandlerInterceptor {
    private final ModuleAccessService access;
    public ModuleInterceptor(ModuleAccessService access) { this.access = access; }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getMethod().equals("OPTIONS")) return true;
        var path = request.getServletPath(); var method = request.getMethod();
        boolean read = method.equals("GET") || method.equals("HEAD");
        // Existing documents, payments, returns, shift close and tax adjustments are recovery/settlement operations.
        if (path.startsWith("/api/inventory/")) {
            if (!read) access.require(path.contains("opening-stock") ? Feature.OPENING_STOCK_IMPORT : Feature.STOCK_ADJUSTMENTS);
            else access.requireHistory(Feature.STOCK_ADJUSTMENTS);
        } else if (path.equals("/api/purchases") && !read) access.require(Feature.PURCHASES);
        else if (path.startsWith("/api/suppliers") && !read) access.require(Feature.PURCHASES);
        else if (path.equals("/api/gst/purchase-quote")) access.require(Feature.PURCHASES);
        else if (path.matches("/api/suppliers/[^/]+/statement")) access.requireHistory(Feature.SUPPLIER_STATEMENTS);
        else if (path.equals("/api/gst/report") || path.equals("/api/gst/audit")) access.require(Feature.GST_ACCOUNTING);
        else if (path.equals("/api/gst/history")) access.requireHistory(Feature.GST_ACCOUNTING);
        else if (path.matches("/api/gst/products/[^/]+/tax") && !read) access.require(Feature.PRODUCTS);
        else if (path.startsWith("/api/products") && !read) access.require(Feature.PRODUCTS);
        else if (path.startsWith("/api/salesman") && !read) access.require(Feature.PRODUCTS);
        else if (path.equals("/api/gst/adjustments") || path.endsWith("/review") || path.equals("/api/gst/periods")) {
            // Historical GST settlement remains available to the already-authorized accountant roles.
        } else if (path.equals("/api/reports/sales-profit")) access.require(Feature.PROFIT_REPORTS);
        else if (path.equals("/api/reports/product-sales") || path.equals("/api/reports/inventory-stock")) access.require(Feature.ADVANCED_REPORTS);
        else if (path.equals("/api/reports/stock-movements")) access.requireHistory(Feature.STOCK_ADJUSTMENTS);
        else if (path.startsWith("/api/shifts") && "true".equalsIgnoreCase(request.getParameter("all"))) access.requireHistory(Feature.CASH_RECONCILIATION);
        return true;
    }
}
