package com.tripmate.settle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // 줄마다 낸 사람 이름이 필요하다(N+1 막기)
    @EntityGraph(attributePaths = "payer")
    List<Expense> findByTripIdOrderById(Long tripId);

    Optional<Expense> findByIdAndTripId(Long id, Long tripId);

    void deleteByPayerId(Long payerId);
}
