package com.example.courierservice.repository;

import com.example.courierservice.entity.CourierUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourierUpdateRepository extends JpaRepository<CourierUpdate, Long> {
}