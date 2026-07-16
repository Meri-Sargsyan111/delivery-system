package com.example.authservice.repository;

import com.example.authservice.entity.Role;
import com.example.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByRole(Role role);

    List<User> findByRole(Role role);

    Optional<User> findByIdAndRole(UUID id, Role role);
}
