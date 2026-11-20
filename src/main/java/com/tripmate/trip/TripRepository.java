package com.tripmate.trip;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    // 참여자와 그 계정(프로필 사진)을 따로 부르면 사람 수만큼 쿼리가 나간다. 한 번에 같이 읽는다
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<Trip> findWithMembersById(Long id);

    @EntityGraph(attributePaths = "members")
    Optional<Trip> findByInviteCode(String inviteCode);

    // 이 방에서 이어 만든 다음 여행 방(있으면 하나)
    @EntityGraph(attributePaths = "members")
    Optional<Trip> findByPreviousTripId(Long previousTripId);

    // 방 목록에 인원·설문 수를 같이 보여 주려고 참여자까지 한 번에 읽는다
    @EntityGraph(attributePaths = "members")
    List<Trip> findByIdIn(List<Long> ids);
}
