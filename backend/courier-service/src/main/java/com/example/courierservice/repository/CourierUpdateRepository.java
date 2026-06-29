package com.example.courierservice.repository;

import com.example.courierservice.entity.CourierUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierUpdateRepository extends JpaRepository<CourierUpdate, Long> {
}