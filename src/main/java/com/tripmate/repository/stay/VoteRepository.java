package com.tripmate.repository.stay;

import com.tripmate.entity.stay.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    List<Vote> findByStayTripId(Long tripId);

    Optional<Vote> findByMemberId(Long memberId);
}
