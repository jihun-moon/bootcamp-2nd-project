package com.tripmate.stay;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StayRepository extends JpaRepository<Stay, Long> {

    // 매트릭스는 숙소마다 조건을 다 보여 준다. 따로 부르면 숙소 수만큼 쿼리가 나간다
    @EntityGraph(attributePaths = "amenities")
    List<Stay> findByTripIdOrderById(Long tripId);

    Optional<Stay> findByIdAndTripId(Long id, Long tripId);
}
