package com.tripmate.shopping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShoppingPlanRepository extends JpaRepository<ShoppingPlan, Long> {

    Optional<ShoppingPlan> findByTripId(Long tripId);
}
