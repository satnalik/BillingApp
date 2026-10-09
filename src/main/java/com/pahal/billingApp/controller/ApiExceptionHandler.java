package com.pahal.billingApp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(com.pahal.billingApp.licensing.LicenseAccessException.class)
    public ResponseEntity<Map<String, String>> handleLicense(com.pahal.billingApp.licensing.LicenseAccessException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }

    @ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class,
            jakarta.persistence.PessimisticLockException.class, jakarta.persistence.LockTimeoutException.class})
    public ResponseEntity<Map<String, String>> handleStockLockConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "STOCK_BUSY", "message", "Another operation is updating this stock. Refresh and try again."));
    }

    @ExceptionHandler(com.pahal.billingApp.service.ShiftConflictException.class)
    public ResponseEntity<Map<String, String>> handleShiftConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "SHIFT_CONFLICT", "message", exception.getMessage()));
    }

    @ExceptionHandler(com.pahal.billingApp.service.BillSubmissionConflictException.class)
    public ResponseEntity<Map<String, String>> handleBillSubmissionConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "BILL_REQUEST_CONFLICT", "message", exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage() == null ? "Access denied." : exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage() != null ? exception.getMessage() : "Invalid request."));
    }
}
