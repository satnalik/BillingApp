package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.DayEndReportResponse;
import com.pahal.billingApp.dto.DailyReportResponse;
import com.pahal.billingApp.dto.SalesReportDTO;
import com.pahal.billingApp.dto.SalesReportResponse;
import com.pahal.billingApp.service.ReportService;
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
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily")
    public ResponseEntity<DailyReportResponse> daily(@RequestParam("date") String date) {
        LocalDate day = LocalDate.parse(date);
        return ResponseEntity.ok(reportService.buildDailyReport(day));
    }

    @GetMapping("/monthly")
    public ResponseEntity<SalesReportResponse> monthly(@RequestParam("month") String month) {
        YearMonth yearMonth = YearMonth.parse(month); // YYYY-MM
        return ResponseEntity.ok(reportService.buildMonthlyReport(yearMonth));
    }

    @GetMapping("/range")
    public ResponseEntity<SalesReportResponse> range(
            @RequestParam("from") String from,
            @RequestParam("to") String to
    ) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        return ResponseEntity.ok(reportService.buildRangeReport(fromDate, toDate));
    }

    @GetMapping("/sales")
    public ResponseEntity<List<SalesReportDTO>> getSalesReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        return ResponseEntity.ok(
                reportService.getSalesReport(startDate, endDate)
        );
    }

    /**
     * Day-end closing: collections by payment mode + pending credit for a store/date.
     * Tenant/store is derived from the logged-in user's tenant context.
     * Example: /api/reports/day-end?date=2026-05-02
     */
    @GetMapping("/day-end")
    public ResponseEntity<DayEndReportResponse> dayEnd(
            @RequestParam("date") String date
    ) {
        LocalDate day = LocalDate.parse(date);
        return ResponseEntity.ok(reportService.buildDayEndReport(day));
    }
}
