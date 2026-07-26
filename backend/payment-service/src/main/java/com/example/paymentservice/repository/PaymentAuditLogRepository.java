package com.example.paymentservice.repository;

import com.example.paymentservice.entity.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {
}
