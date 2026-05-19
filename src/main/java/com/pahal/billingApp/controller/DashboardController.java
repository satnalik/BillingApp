package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.DashboardTodayResponse;
import com.pahal.billingApp.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard API", description = "Endpoints for dashboard summary cards")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Today's Dashboard", description = "Returns sales, payment split, dues, and low stock summary.")
    @GetMapping("/today")
    public ResponseEntity<DashboardTodayResponse> today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Double lowStockThreshold) {
        return ResponseEntity.ok(dashboardService.buildTodayDashboard(date, lowStockThreshold));
    }
}
