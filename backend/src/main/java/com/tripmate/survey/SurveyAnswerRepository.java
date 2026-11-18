package com.tripmate.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {

    Optional<SurveyAnswer> findByMemberId(Long memberId);
}
