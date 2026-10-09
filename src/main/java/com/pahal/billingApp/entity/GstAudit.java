package com.pahal.billingApp.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "gst_audit") @Getter @Setter
public class GstAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String tenantId;
    @Column(nullable = false, length = 40) private String action;
    @Column(length = 120) private String reference;
    @Column(columnDefinition = "text") private String details;
    private LocalDateTime recordedAt;
    @Column(length = 160) private String actorUserId;
}
