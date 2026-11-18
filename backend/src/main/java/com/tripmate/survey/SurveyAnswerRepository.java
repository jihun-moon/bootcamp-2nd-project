package com.tripmate.survey;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {

    Optional<SurveyAnswer> findByMemberId(Long memberId);

    // 매칭은 방 전체 설문을 한 번에 읽는다. avoid 는 점수에 쓰니 같이 읽는다
    @EntityGraph(attributePaths = {"member", "avoid"})
    List<SurveyAnswer> findByMemberTripId(Long tripId);
}
