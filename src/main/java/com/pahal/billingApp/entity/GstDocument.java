package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Original tax amounts/identity are snapshots. Only accountant review metadata is editable. */
@Entity @Getter @Setter
@Table(name = "gst_documents", indexes = @Index(name = "idx_gst_documents_tenant_date", columnList = "tenant_id, document_date"),
    uniqueConstraints = @UniqueConstraint(name = "uk_gst_document_source", columnNames = {"tenant_id", "source_key"}))
public class GstDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false, length = 100, updatable = false) private String tenantId;
    @Column(name = "source_key", nullable = false, length = 120, updatable = false) private String sourceKey;
    @Column(nullable = false, length = 30, updatable = false) private String documentType;
    @Column(nullable = false, length = 64, updatable = false) private String documentNumber;
    @Column(name = "document_date", nullable = false, updatable = false) private LocalDate documentDate;
    @Column(updatable = false) private Long sourceId;
    @Column(updatable = false) private Long originalDocumentId;
    @Column(length = 64, updatable = false) private String originalDocumentNumber;
    @Column(length = 20, updatable = false) private String registrationMode;
    @Column(length = 15, updatable = false) private String storeGstin;
    @Column(length = 160, updatable = false) private String storeName;
    @Column(length = 1000, updatable = false) private String storeAddress;
    @Column(length = 2, updatable = false) private String storeState;
    @Column(length = 160, updatable = false) private String partyName;
    @Column(length = 15, updatable = false) private String partyGstin;
    @Column(length = 1000, updatable = false) private String partyAddress;
    @Column(length = 1000, updatable = false) private String deliveryAddress;
    @Column(length = 2, updatable = false) private String placeOfSupply;
    @Column(length = 12, updatable = false) private String priceMode;
    @Column(length = 1000, updatable = false) private String reason;
    @Column(length = 64, updatable = false) private String requestFingerprint;
    @Column(precision = 19, scale = 2, updatable = false) private BigDecimal taxableAmount;
    @Column(precision = 19, scale = 2, updatable = false) private BigDecimal cgstAmount;
    @Column(precision = 19, scale = 2, updatable = false) private BigDecimal sgstAmount;
    @Column(precision = 19, scale = 2, updatable = false) private BigDecimal igstAmount;
    @Column(precision = 19, scale = 2, updatable = false) private BigDecimal totalAmount;
    @Column(nullable = false, updatable = false) private LocalDateTime recordedAt;
    @Column(length = 160, updatable = false) private String actorUserId;
    @Column(length = 16) private String itcStatus = "NOT_APPLICABLE";
    @Column(length = 1000) private String reviewNotes;
    @Column(length = 64) private String supplierNoteNumber;
    private LocalDate supplierNoteDate;
    private LocalDateTime reviewedAt;
    @Column(length = 160) private String reviewedBy;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "gst_document_lines", joinColumns = @JoinColumn(name = "document_id"))
    @OrderColumn(name = "line_index")
    private List<Line> lines = new ArrayList<>();

    /** Goods-return record stays immutable; confirmed supplier note controls the inward working period. */
    @Transient
    public LocalDate getReportingDate() {
        return "PURCHASE_CREDIT".equals(documentType) && sourceKey != null && sourceKey.startsWith("purchase-return:")
                && supplierNoteDate != null ? supplierNoteDate : documentDate;
    }

    @Embeddable @Getter @Setter
    public static class Line {
        private Long sourceItemId;
        private Long productId;
        @Column(length = 255) private String productName;
        @Column(length = 64) private String barcode;
        @Column(length = 8) private String hsnCode;
        @Column(length = 8) private String unitCode;
        @Column(length = 20) private String taxCategory;
        @Column(precision = 19, scale = 2) private BigDecimal quantity;
        @Column(precision = 9, scale = 6) private BigDecimal gstRate;
        @Column(precision = 19, scale = 2) private BigDecimal taxableAmount;
        @Column(precision = 19, scale = 2) private BigDecimal cgstAmount;
        @Column(precision = 19, scale = 2) private BigDecimal sgstAmount;
        @Column(precision = 19, scale = 2) private BigDecimal igstAmount;
        @Column(precision = 19, scale = 2) private BigDecimal discountAmount;
    }
}
