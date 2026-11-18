package com.tripmate.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 다른 방 참여자 id 를 넣어도 통과하지 않게 방까지 같이 본다
    Optional<Member> findByIdAndTripId(Long id, Long tripId);
}
