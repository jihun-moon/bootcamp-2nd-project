package com.tripmate.settle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 줄마다 보낸·받은·누른 사람 이름이 필요하다(N+1 막기)
    @EntityGraph(attributePaths = {"sender", "receiver", "markedBy"})
    List<Payment> findByTripIdOrderById(Long tripId);

    Optional<Payment> findByIdAndTripId(Long id, Long tripId);

    // 방을 나간 사람이 보내거나 받거나 누른 표시
    void deleteBySenderIdOrReceiverIdOrMarkedById(Long senderId, Long receiverId, Long markedById);
}
