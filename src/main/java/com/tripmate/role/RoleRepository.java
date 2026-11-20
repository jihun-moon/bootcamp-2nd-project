package com.tripmate.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByTripIdOrderById(Long tripId);

    Optional<Role> findByIdAndTripId(Long id, Long tripId);

    long countByTripId(Long tripId);
}
