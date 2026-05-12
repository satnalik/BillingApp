package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.DayEndReportResponse;
import com.pahal.billingApp.dto.DailyReportResponse;
import com.pahal.billingApp.dto.SalesReportDTO;
import com.pahal.billingApp.dto.SalesReportResponse;
import com.pahal.billingApp.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Report API", description = "Endpoints for generating sales and day-end reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Daily Sales Report", description = "Generates a sales report for a specific day. The report includes total sales, number of bills, and payment mode breakdown.")
    @GetMapping("/daily")
    public ResponseEntity<DailyReportResponse> daily(@RequestParam("date") String date) {
        LocalDate day = LocalDate.parse(date);
        return ResponseEntity.ok(reportService.buildDailyReport(day));
    }

    @Operation(summary = "Monthly Sales Report", description = "Generates a sales report for a specific month. The report includes total sales, number of bills, and payment mode breakdown.")
    @GetMapping("/monthly")
    public ResponseEntity<SalesReportResponse> monthly(@RequestParam("month") String month) {
        YearMonth yearMonth = YearMonth.parse(month); // YYYY-MM
        return ResponseEntity.ok(reportService.buildMonthlyReport(yearMonth));
    }

    @Operation(summary = "Date Range Sales Report", description = "Generates a sales report for a specific date range. The report includes total sales, number of bills, and payment mode breakdown.")
    @GetMapping("/range")
    public ResponseEntity<SalesReportResponse> range(
            @RequestParam("from") String from,
            @RequestParam("to") String to) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        return ResponseEntity.ok(reportService.buildRangeReport(fromDate, toDate));
    }

    @Operation(summary = "Sales Report", description = "Generates a sales report for a specific date range.")
    @GetMapping("/sales")
    public ResponseEntity<List<SalesReportDTO>> getSalesReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        return ResponseEntity.ok(
                reportService.getSalesReport(startDate, endDate));
    }

    @Operation(summary = "Day-End Report", description = "Generates a day-end report for a specific date. The report includes collections by payment mode and pending credit for a store/date.")
    @GetMapping("/day-end")
    public ResponseEntity<DayEndReportResponse> dayEnd(
            @RequestParam("date") String date) {
        LocalDate day = LocalDate.parse(date);
        return ResponseEntity.ok(reportService.buildDayEndReport(day));
    }
}
