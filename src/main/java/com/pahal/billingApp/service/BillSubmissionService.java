package com.pahal.billingApp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.dto.CreateBillRequest;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.security.CustomUserDetails;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** PostgreSQL transaction locks serialize the same request across threads/processes. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class BillSubmissionService {
    private final BillRepository bills;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public BillSubmissionService(BillRepository bills, JdbcTemplate jdbc, ObjectMapper json) {
        this.bills = bills;
        this.jdbc = jdbc;
        this.json = json;
    }

    public Submission begin(CreateBillRequest request) {
        String key = requestKey(request.getRequestKey());
        String fingerprint = fingerprint(request);
        Optional<Bill> existing = findForCurrentCashier(key);
        if (existing.isPresent() && !fingerprint.equals(existing.get().getCreationFingerprint())) {
            throw new BillSubmissionConflictException("This submission already has an invoice with different details. Recover the saved invoice before starting another bill.");
        }
        return new Submission(key, fingerprint, existing.orElse(null));
    }

    public Optional<Bill> findForCurrentCashier(String rawKey) {
        String key = requestKey(rawKey);
        String tenant = StockService.requireTenant();
        String user = cashierUserId();
        byte[] digest = sha256(("bill-submission-v1:" + tenant.length() + ":" + tenant + ":" + key).getBytes(StandardCharsets.UTF_8));
        long lockId = ByteBuffer.wrap(digest).getLong();
        // Same DataSource/transaction as JPA. The lock releases on commit or rollback, including process failure.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, lockId);
                statement.execute();
            }
            return null;
        });
        Optional<Bill> existing = bills.findSubmission(tenant, key);
        if (existing.isPresent() && !user.equals(existing.get().getCashierUserId())) {
            throw new AccessDeniedException("This submission belongs to another cashier.");
        }
        return existing;
    }

    private String fingerprint(CreateBillRequest request) {
        // Fixed field set keeps the fingerprint independent of JSON property order and request key casing.
        Map<String, Object> fields = new TreeMap<>();
        fields.put("customerName", request.getCustomerName());
        fields.put("contactInfo", request.getContactInfo());
        // Preserve fingerprints of submissions created before these optional tax fields existed.
        if (request.getCustomerGstin() != null || request.getCustomerAddress() != null || request.getPlaceOfSupply() != null) {
            fields.put("customerGstin", request.getCustomerGstin()); fields.put("customerAddress", request.getCustomerAddress()); fields.put("placeOfSupply", request.getPlaceOfSupply());
        }
        fields.put("salesmanEmployeeId", request.getSalesmanEmployeeId());
        if (request.getDeliveryAddress() != null) fields.put("deliveryAddress", request.getDeliveryAddress());
        fields.put("instantDiscountAmount", request.getInstantDiscountAmount());
        fields.put("items", request.getItems());
        fields.put("payments", request.getPayments());
        try {
            return HexFormat.of().formatHex(sha256(json.writeValueAsBytes(fields)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid bill submission.", exception);
        }
    }

    private static String cashierUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            throw new AccessDeniedException("Sign in before saving or recovering a bill.");
        }
        return user.getUsername();
    }

    private static String requestKey(String raw) {
        if (raw == null || !raw.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("A UUID requestKey is required. Reuse it with the same bill details when retrying.");
        }
        return UUID.fromString(raw).toString();
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record Submission(String key, String fingerprint, Bill existing) {}
}
