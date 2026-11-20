package com.tripmate.shopping;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoppingClaimRepository extends JpaRepository<ShoppingClaim, Long> {

    // 줄마다 산 사람 이름이 필요해서 member 를 같이 읽는다(N+1 막기)
    @EntityGraph(attributePaths = "member")
    List<ShoppingClaim> findByTripId(Long tripId);

    Optional<ShoppingClaim> findByTripIdAndItem(Long tripId, ShoppingItem item);
}
