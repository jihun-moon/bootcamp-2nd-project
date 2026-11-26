package com.tripmate.repository.survey;

import com.tripmate.entity.survey.SurveyAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {

    Optional<SurveyAnswer> findByMemberId(Long memberId);

    // 매칭과 그룹 취향은 방 전체 설문을 한 번에 읽는다. avoid·preferences 를 따로 부르면 사람 수만큼 쿼리가 나간다
    @EntityGraph(attributePaths = {"member", "avoid", "preferences"})
    List<SurveyAnswer> findByMemberTripId(Long tripId);
}
