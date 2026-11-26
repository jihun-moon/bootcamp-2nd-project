package com.tripmate.repository.settle;

import com.tripmate.entity.settle.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 줄마다 보낸·받은·누른 사람 이름이 필요하다(N+1 막기)
    @EntityGraph(attributePaths = {"sender", "receiver", "markedBy"})
    List<Payment> findByTripIdOrderById(Long tripId);

    Optional<Payment> findByIdAndTripId(Long id, Long tripId);
}
