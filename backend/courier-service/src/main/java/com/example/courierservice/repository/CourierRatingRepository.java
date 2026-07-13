package com.example.courierservice.repository;

import com.example.courierservice.entity.CourierRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourierRatingRepository extends JpaRepository<CourierRating, Long> {

    boolean existsByOrderId(Long orderId);

    long countByCourierId(Long courierId);

    @Query("SELECT AVG(r.value) FROM CourierRating r WHERE r.courierId = :courierId")
    Double findAverageRatingByCourierId(@Param("courierId") Long courierId);
}