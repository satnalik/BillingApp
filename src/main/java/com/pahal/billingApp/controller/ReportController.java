package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.DailyReportResponse;
import com.pahal.billingApp.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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
}

