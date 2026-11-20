package com.tripmate.settle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DuesRepository extends JpaRepository<Dues, Long> {

    // 판에 총무 이름이 필요하다
    @EntityGraph(attributePaths = "collector")
    Optional<Dues> findByTripId(Long tripId);
}
