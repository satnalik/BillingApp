package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.CashierShiftDTO.*;
import com.pahal.billingApp.service.CashierShiftService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/shifts")
@Tag(name = "Cashier shifts", description = "Opening floats, cash movements, cashier reconciliation and saved close reports")
public class CashierShiftController {
    private final CashierShiftService shifts;
    public CashierShiftController(CashierShiftService shifts) { this.shifts = shifts; }
    @GetMapping("/workspace") public Workspace workspace() { return shifts.workspace(); }
    @GetMapping("/status") public Status status() { return shifts.status(); }
    @PostMapping public Detail open(@RequestBody OpenRequest request) { return shifts.open(request); }
    @GetMapping public History history(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String cashierUserId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean all, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return shifts.history(from, to, cashierUserId, status, all, page, size);
    }
    @GetMapping("/{id}") public Detail get(@PathVariable Long id) { return shifts.get(id); }
    @PostMapping("/{id}/cash-movements") public Detail cash(@PathVariable Long id, @RequestBody CashRequest request) { return shifts.cashMovement(id, request); }
    @PostMapping("/{id}/close") public Detail close(@PathVariable Long id, @RequestBody CloseRequest request) { return shifts.close(id, request); }
}
