package com.tripmate.repository.trip;

import com.tripmate.entity.trip.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 다른 방 참여자 id 를 넣어도 통과하지 않게 방까지 같이 본다
    Optional<Member> findByIdAndTripId(Long id, Long tripId);

    Optional<Member> findByTripIdAndUserId(Long tripId, Long userId);

    // 첫 화면 "내 여행방". 오래된 방까지 끝없이 읽지 않게 최근 50개만
    List<Member> findTop50ByUserIdOrderByIdDesc(Long userId);

    List<Member> findByUserId(Long userId);
}
