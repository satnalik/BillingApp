package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.CashierShiftDTO.*;
import com.pahal.billingApp.entity.*;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.repository.*;
import com.pahal.billingApp.security.CustomUserDetails;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CashierShiftService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final CashierShiftRepository shifts;
    private final ShiftCashMovementRepository movements;
    private final BillPaymentRepository payments;
    private final UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired private com.pahal.billingApp.licensing.CounterService licensedCounters;
    @org.springframework.beans.factory.annotation.Autowired private com.pahal.billingApp.licensing.TenantLicenseService tenantLicenses;
    @org.springframework.beans.factory.annotation.Autowired private com.pahal.billingApp.licensing.ModuleAccessService moduleAccess;
    public CashierShiftService(CashierShiftRepository shifts, ShiftCashMovementRepository movements,
                               BillPaymentRepository payments, UserRepository users) {
        this.shifts = shifts; this.movements = movements; this.payments = payments; this.users = users;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Workspace workspace() {
        var user = actor();
        var current = shifts.findFirstByTenantIdAndCashierUserIdAndClosedAtIsNullOrderByIdDesc(tenant(), user.getUsername());
        return new Workspace(user.getUsername(), user.getDisplayName(), canReview(), current.map(this::detail).orElse(null));
    }

    @Transactional(readOnly = true)
    public Status status() {
        var current = shifts.findFirstByTenantIdAndCashierUserIdAndClosedAtIsNullOrderByIdDesc(tenant(), actor().getUsername());
        return current.map(s -> new Status(s.getId(), s.getCashierName(), s.getCounterName(), s.getOpenedAt()))
                .orElse(new Status(null, actor().getDisplayName(), null, null));
    }

    @Transactional
    public Detail open(OpenRequest request) {
        if (request == null) throw invalid("Opening details are required.");
        String key = requestKey(request.requestKey());
        BigDecimal opening = amount(request.openingCash(), true);
        String counter = text(request.counterName(), 120, false, "Counter name");
        String fingerprint = fingerprint(opening + "|" + counter);
        var user = actor();
        tenantLicenses.lockTenant(tenant());
        lockUser(user.getUsername());
        var previous = shifts.findByTenantIdAndCashierUserIdAndOpenRequestKey(tenant(), user.getUsername(), key);
        if (previous.isPresent()) { matching(previous.get().getOpenFingerprint(), fingerprint); return detail(previous.get()); }
        // Essential settlement shifts can open after expiry; they cannot post new bills.
        var license = tenantLicenses.snapshot(tenant());
        var registered = license.operational() ? licensedCounters.requireForNewTransaction() : null;
        if (registered != null && shifts.existsByTenantIdAndLicenseCounterIdAndClosedAtIsNull(tenant(), registered.getId()))
            throw conflict("This counter already has an open cashier shift. Close it before assigning another cashier.");
        if (shifts.findFirstByTenantIdAndCashierUserIdAndClosedAtIsNullOrderByIdDesc(tenant(), user.getUsername()).isPresent()) {
            throw conflict("You already have an open shift. Refresh to continue it.");
        }
        CashierShift shift = new CashierShift();
        shift.setTenantId(tenant()); shift.setCashierUserId(user.getUsername()); shift.setCashierName(actorName());
        shift.setCounterName(registered == null ? counter : registered.getName());
        shift.setLicenseCounterId(registered == null ? null : registered.getId());
        shift.setOpeningCash(opening); shift.setOpenedAt(LocalDateTime.now());
        shift.setOpenRequestKey(key); shift.setOpenFingerprint(fingerprint);
        return detail(shifts.saveAndFlush(shift));
    }

    /** Acquire this BEFORE locking bills/products; close and payment posting share the same lock order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public CashierShift requireOpenForPosting() {
        var user = actor();
        lockUser(user.getUsername());
        var open = shifts.findFirstByTenantIdAndCashierUserIdAndClosedAtIsNullOrderByIdDesc(tenant(), user.getUsername())
                .orElseThrow(() -> conflict("Open your cashier shift with opening cash before billing, collecting dues, or processing returns/cancellations. Go to Cashier shifts."));
        return shifts.findLocked(open.getId(), tenant()).orElseThrow(() -> conflict("Refresh your shift and try again."));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assignNewPayments(CashierShift shift, Bill bill) {
        if (shift.getClosedAt() != null || !shift.getCashierUserId().equals(actor().getUsername())) throw conflict("This shift is closed or belongs to another cashier.");
        if (bill.getPayments() == null) return;
        for (var payment : bill.getPayments()) {
            if (payment.getId() != null) continue;
            if (payment.getMethod() == null) throw invalid("Payment method is required.");
            validatePaymentAmount(payment.getAmount());
            payment.setShiftId(shift.getId()); payment.setCashierUserId(shift.getCashierUserId());
        }
    }

    @Transactional
    public Detail cashMovement(Long id, CashRequest request) {
        if (request == null) throw invalid("Cash movement details are required.");
        if (request.cashIn() == null) throw invalid("Choose cash in or cash out.");
        String key = requestKey(request.requestKey());
        BigDecimal amount = amount(request.amount(), false);
        String reason = text(request.reason(), 500, true, "Cash movement reason");
        String reference = text(request.reference(), 240, false, "Reference");
        String fingerprint = fingerprint(request.cashIn() + "|" + amount + "|" + reason + "|" + reference);
        CashierShift shift = lockAccessible(id, false);
        var previous = movements.findByTenantIdAndShiftIdAndRequestKey(tenant(), id, key);
        if (previous.isPresent()) { matching(previous.get().getFingerprint(), fingerprint); return detail(shift); }
        requireNotClosed(shift);
        Detail current = detail(shift);
        if (!request.cashIn() && current.expectedCash().compareTo(amount) < 0) throw invalid("Cash out exceeds expected cash. Check the amount or record missing cash in first.");
        ShiftCashMovement movement = new ShiftCashMovement();
        movement.setTenantId(tenant()); movement.setShiftId(id); movement.setCashIn(request.cashIn()); movement.setAmount(amount);
        movement.setReason(reason); movement.setReference(reference); movement.setRequestKey(key); movement.setFingerprint(fingerprint);
        movement.setActorUserId(actor().getUsername()); movement.setActorName(actorName()); movement.setCreatedAt(LocalDateTime.now());
        movements.saveAndFlush(movement);
        return detail(shift);
    }

    @Transactional
    public Detail close(Long id, CloseRequest request) {
        if (request == null) throw invalid("Closing details are required.");
        String key = requestKey(request.requestKey());
        BigDecimal counted = amount(request.countedCash(), true);
        String notes = text(request.notes(), 1000, false, "Close notes");
        String revision = text(request.expectedRevision(), 64, true, "Expected revision");
        String fingerprint = fingerprint(counted + "|" + revision + "|" + notes);
        CashierShift shift = lockAccessible(id, true);
        if (shift.getClosedAt() != null) {
            if (key.equals(shift.getCloseRequestKey())) { matching(shift.getCloseFingerprint(), fingerprint); return detail(shift); }
            throw conflict("This shift is already closed. Refresh the history.");
        }
        Detail current = detail(shift);
        if (!current.revision().equals(revision)) throw conflict("Shift transactions changed while you were counting. Refresh, review the totals, and count again before closing.");
        BigDecimal variance = counted.subtract(current.expectedCash());
        if ((variance.signum() != 0 || !shift.getCashierUserId().equals(actor().getUsername())) && notes == null) {
            throw invalid("Explain the cash difference or manager close in the closing notes.");
        }
        freezeMethod(shift, current.payments());
        shift.setManualCashIn(current.manualCashIn()); shift.setManualCashOut(current.manualCashOut());
        shift.setExpectedCash(current.expectedCash()); shift.setCountedCash(counted); shift.setVariance(variance);
        shift.setCloseNotes(notes); shift.setClosedByUserId(actor().getUsername()); shift.setClosedByName(actorName());
        shift.setClosedAt(LocalDateTime.now()); shift.setActiveMarker(null); shift.setCloseRequestKey(key); shift.setCloseFingerprint(fingerprint);
        return detail(shifts.saveAndFlush(shift));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail get(Long id) {
        var shift = shifts.findByIdAndTenantId(id, tenant()).orElseThrow(() -> invalid("Shift not found."));
        authorize(shift.getCashierUserId(), true);
        if (!shift.getCashierUserId().equals(actor().getUsername()))
            moduleAccess.requireHistory(com.pahal.billingApp.licensing.Feature.CASH_RECONCILIATION);
        return detail(shift);
    }

    @Transactional(readOnly = true)
    public History history(LocalDate from, LocalDate to, String cashierUserId, String status, boolean all, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) throw invalid("Start date must be before end date.");
        String requestedUser = cashierUserId == null || cashierUserId.isBlank() ? null : cashierUserId.trim();
        if (all && !canReview()) throw new AccessDeniedException("Only managers and administrators can review all shifts.");
        if (all) moduleAccess.requireHistory(com.pahal.billingApp.licensing.Feature.CASH_RECONCILIATION);
        if (!all) {
            if (requestedUser != null && !requestedUser.equals(actor().getUsername())) throw new AccessDeniedException("You can review only your own shifts.");
            requestedUser = actor().getUsername();
        }
        String filterUser = requestedUser, tenant = tenant();
        String state = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!state.isEmpty() && !List.of("OPEN", "CLOSED").contains(state)) throw invalid("Unknown shift status.");
        Specification<CashierShift> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), tenant));
            if (filterUser != null) predicates.add(cb.equal(root.get("cashierUserId"), filterUser));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("openedAt"), from.atStartOfDay()));
            if (to != null) predicates.add(cb.lessThan(root.get("openedAt"), to.plusDays(1).atStartOfDay()));
            if (state.equals("OPEN")) predicates.add(cb.isNull(root.get("closedAt")));
            if (state.equals("CLOSED")) predicates.add(cb.isNotNull(root.get("closedAt")));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = shifts.findAll(spec, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by(Sort.Direction.DESC, "id")));
        return new History(result.getContent().stream().map(this::summary).toList(), result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    private Detail detail(CashierShift shift) {
        Map<String, MethodTotals> totals = new LinkedHashMap<>();
        for (String method : List.of("CASH", "UPI", "CARD")) totals.put(method, new MethodTotals(ZERO, ZERO, ZERO));
        List<Entry> entries = new ArrayList<>();
        StringBuilder revision = new StringBuilder(shift.getId() + "|" + shift.getOpeningCash());
        for (var p : payments.findShiftPayments(shift.getId(), tenant())) {
            revision.append("|P:").append(p.getId()).append(':').append(p.getMethod()).append(':').append(p.getAmount());
            if (p.getMethod() == PaymentMethod.CREDIT) continue;
            BigDecimal value = decimal(p.getAmount());
            if (value.signum() == 0) continue;
            String method = p.getMethod().name();
            var before = totals.get(method);
            BigDecimal received = before.received().add(value.max(ZERO)), refunded = before.refunded().add(value.negate().max(ZERO));
            totals.put(method, new MethodTotals(received, refunded, received.subtract(refunded)));
            entries.add(new Entry("P-" + p.getId(), p.getCreatedAt(), value.signum() > 0 ? "COLLECTION" : "REFUND", method, value,
                    p.getBill().getId(), "INV-" + String.format("%08d", p.getBill().getId()), p.getReference(), shift.getCashierName()));
        }
        BigDecimal cashIn = ZERO, cashOut = ZERO;
        for (var m : movements.findByTenantIdAndShiftIdOrderByIdAsc(tenant(), shift.getId())) {
            revision.append("|M:").append(m.getId()).append(':').append(m.isCashIn()).append(':').append(m.getAmount());
            if (m.isCashIn()) cashIn = cashIn.add(m.getAmount()); else cashOut = cashOut.add(m.getAmount());
            entries.add(new Entry("M-" + m.getId(), m.getCreatedAt(), m.isCashIn() ? "CASH_IN" : "CASH_OUT", "CASH",
                    m.isCashIn() ? m.getAmount() : m.getAmount().negate(), null, m.getReference(), m.getReason(), m.getActorName()));
        }
        BigDecimal expected = shift.getOpeningCash().add(totals.get("CASH").net()).add(cashIn).subtract(cashOut);
        if (shift.getClosedAt() != null) {
            totals.put("CASH", methodTotals(shift.getCashReceived(), shift.getCashRefunded()));
            totals.put("UPI", methodTotals(shift.getUpiReceived(), shift.getUpiRefunded()));
            totals.put("CARD", methodTotals(shift.getCardReceived(), shift.getCardRefunded()));
            expected = shift.getExpectedCash(); cashIn = shift.getManualCashIn(); cashOut = shift.getManualCashOut();
        }
        entries.sort(Comparator.comparing(Entry::date).thenComparing(Entry::key));
        return new Detail(summary(shift), totals, cashIn, cashOut, expected, fingerprint(revision.toString()), entries);
    }
    private Summary summary(CashierShift s) {
        return new Summary(s.getId(), s.getCashierUserId(), s.getCashierName(), s.getCounterName(), s.getOpenedAt(), s.getClosedAt(),
                s.getOpeningCash(), s.getExpectedCash(), s.getCountedCash(), s.getVariance(), s.getClosedByName(), s.getCloseNotes());
    }
    private void freezeMethod(CashierShift s, Map<String, MethodTotals> values) {
        s.setCashReceived(values.get("CASH").received()); s.setCashRefunded(values.get("CASH").refunded());
        s.setUpiReceived(values.get("UPI").received()); s.setUpiRefunded(values.get("UPI").refunded());
        s.setCardReceived(values.get("CARD").received()); s.setCardRefunded(values.get("CARD").refunded());
    }
    private static MethodTotals methodTotals(BigDecimal received, BigDecimal refunded) { return new MethodTotals(received, refunded, received.subtract(refunded)); }
    private CashierShift lockAccessible(Long id, boolean managerAllowed) {
        String owner = shifts.findOwner(id, tenant()).orElseThrow(() -> invalid("Shift not found."));
        authorize(owner, managerAllowed);
        lockUser(owner);
        return shifts.findLocked(id, tenant()).orElseThrow(() -> invalid("Shift not found."));
    }
    private void lockUser(String user) { users.lockCashier(user, tenant()).orElseThrow(() -> invalid("Cashier account not found.")); }
    private void authorize(String owner, boolean managerAllowed) {
        if (!owner.equals(actor().getUsername()) && !(managerAllowed && canReview())) throw new AccessDeniedException("You cannot change or view another cashier's shift.");
    }
    private static void requireNotClosed(CashierShift s) { if (s.getClosedAt() != null) throw conflict("This shift is closed. Open a new shift for further cash movements."); }
    private static String tenant() { return StockService.requireTenant(); }
    private static CustomUserDetails actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails user)) throw new AccessDeniedException("Sign in to use cashier shifts.");
        return user;
    }
    private static String actorName() { String name = actor().getDisplayName(); return name.length() > 160 ? name.substring(0, 160) : name; }
    private static boolean canReview() { return actor().getAuthorities().stream().anyMatch(a -> List.of("ROLE_ADMIN", "ROLE_MANAGER").contains(a.getAuthority())); }
    private static BigDecimal amount(BigDecimal amount, boolean zeroAllowed) {
        if (amount == null || amount.signum() < 0 || (!zeroAllowed && amount.signum() == 0) || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(BigDecimal.valueOf(1_000_000_000_000L)) > 0) throw invalid("Enter a " + (zeroAllowed ? "zero or positive" : "positive") + " amount with at most two decimal places (maximum 1,000,000,000,000).");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
    private static BigDecimal decimal(Double amount) {
        if (amount == null || !Double.isFinite(amount)) throw invalid("Invalid payment amount in this shift.");
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
    public static double validatePaymentAmount(Double amount) {
        if (amount == null || !Double.isFinite(amount)) throw invalid("A finite payment amount is required.");
        BigDecimal value = BigDecimal.valueOf(amount);
        if (value.abs().compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0 || value.stripTrailingZeros().scale() > 2) {
            throw invalid("Payment amounts must have at most two decimal places (maximum 1,000,000,000).");
        }
        return amount;
    }
    private static String text(String value, int maximum, boolean required, String label) {
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if ((required && normalized == null) || (normalized != null && normalized.length() > maximum)) throw invalid(label + " is " + (required ? "required, " : "") + "maximum " + maximum + " characters.");
        return normalized;
    }
    private static String requestKey(String value) { try { return UUID.fromString(value).toString(); } catch (Exception e) { throw invalid("A UUID request key is required."); } }
    private static String fingerprint(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void matching(String old, String current) { if (!current.equals(old)) throw invalid("This request key has already been used with different details."); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static ShiftConflictException conflict(String message) { return new ShiftConflictException(message); }
}
