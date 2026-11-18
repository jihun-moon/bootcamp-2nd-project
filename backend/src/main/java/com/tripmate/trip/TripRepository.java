package com.tripmate.trip;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    // 참여자를 따로 부르면 사람 수만큼 쿼리가 나간다. 한 번에 같이 읽는다
    @EntityGraph(attributePaths = "members")
    Optional<Trip> findWithMembersById(Long id);

    @EntityGraph(attributePaths = "members")
    Optional<Trip> findByInviteCode(String inviteCode);
}
