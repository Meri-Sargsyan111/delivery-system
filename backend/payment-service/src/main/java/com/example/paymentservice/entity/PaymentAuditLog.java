package com.example.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row per payment operation (created/verified/webhook received/refunded/failed/security violation). */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payment_audit_log", indexes = {
        @Index(name = "idx_payment_audit_log_payment_id", columnList = "paymentId")
})
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID paymentId;

    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String details;

    private LocalDateTime createdAt;
}
