package com.example.aiservice.repository;

import com.example.aiservice.entity.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EstimateRepository extends JpaRepository<Estimate, UUID> {

    /**
     * Atomic claim: a single conditional UPDATE, not a read-then-write, so two concurrent
     * claim attempts for the same estimate can't both succeed (the same TOCTOU class of
     * race payment-service's own order-creation claim guards against). Returns the number
     * of rows updated - 1 means this call won the claim, 0 means it was already
     * consumed/expired.
     */
    @Modifying
    @Query("UPDATE Estimate e SET e.consumed = true, e.consumedAt = CURRENT_TIMESTAMP " +
            "WHERE e.id = :id AND e.consumed = false AND e.expiresAt > CURRENT_TIMESTAMP")
    int claim(@Param("id") UUID id);
}
