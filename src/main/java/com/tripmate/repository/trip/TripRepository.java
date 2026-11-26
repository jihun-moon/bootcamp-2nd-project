package com.tripmate.repository.trip;

import com.tripmate.entity.trip.Trip;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    // 참여자와 그 계정(프로필 사진)을 따로 부르면 사람 수만큼 쿼리가 나간다. 한 번에 같이 읽는다
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<Trip> findWithMembersById(Long id);

    @EntityGraph(attributePaths = "members")
    Optional<Trip> findByInviteCode(String inviteCode);

    // 나가기·빼기를 한 줄로 세운다. 두 사람이 동시에 나가면 둘 다 "혼자가 아님"을 보고 빈 방이 되거나
    // 방장과 다음 방장이 같이 나가 방장 없는 방이 남았다(리뷰 #10). 같은 방 나가기는 차례로만
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> lockById(@Param("id") Long id);

    // 이 방에서 이어 만든 다음 여행 방(있으면 하나)
    @EntityGraph(attributePaths = "members")
    Optional<Trip> findByPreviousTripId(Long previousTripId);

    // 방 목록에 인원·설문 수를 같이 보여 주려고 참여자까지 한 번에 읽는다
    @EntityGraph(attributePaths = "members")
    List<Trip> findByIdIn(List<Long> ids);
}
