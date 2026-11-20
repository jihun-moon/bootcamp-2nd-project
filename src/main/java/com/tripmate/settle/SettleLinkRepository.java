package com.tripmate.settle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettleLinkRepository extends JpaRepository<SettleLink, Long> {

    Optional<SettleLink> findByTripId(Long tripId);

    // 화면 머리에 방 이름·날짜가 필요하다
    @EntityGraph(attributePaths = "trip")
    Optional<SettleLink> findByCode(String code);
}
