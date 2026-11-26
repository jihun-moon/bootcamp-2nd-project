package com.tripmate.repository.shopping;

import com.tripmate.entity.shopping.ShoppingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShoppingPlanRepository extends JpaRepository<ShoppingPlan, Long> {

    Optional<ShoppingPlan> findByTripId(Long tripId);
}
