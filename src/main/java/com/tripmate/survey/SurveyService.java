package com.tripmate.survey;

import com.tripmate.role.Chore;
import com.tripmate.stay.Amenity;
import com.tripmate.trip.Member;
import com.tripmate.trip.MemberRepository;
import com.tripmate.trip.TripChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@Service
public class SurveyService {

    private final MemberRepository memberRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final ApplicationEventPublisher events;

    public SurveyService(MemberRepository memberRepository, SurveyAnswerRepository surveyAnswerRepository,
                         ApplicationEventPublisher events) {
        this.memberRepository = memberRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.events = events;
    }

    // 처음이면 새로 만들고, 다시 하면 그 한 장을 고쳐 쓴다
    @Transactional
    public SurveyDto.Response save(Long tripId, Member member, SurveyDto.Request req) {
        var survey = surveyAnswerRepository.findByMemberId(member.getId())
                .orElseGet(() -> surveyAnswerRepository.save(new SurveyAnswer(member)));
        var avoid = (req.avoid() == null) ? Set.<Chore>of() : req.avoid();
        var preferences = (req.preferences() == null) ? Map.<Amenity, Integer>of() : req.preferences();
        survey.update(req.activity(), req.planning(), req.hasCar(), req.separateDeparture(),
                req.mobilityLimited(), avoid, preferences);
        // 방 개요의 "설문 완료"가 바뀌니 같은 방 화면들에 알린다
        events.publishEvent(new TripChanged(tripId, "members"));
        return SurveyDto.Response.of(survey, true);
    }

    // 이동 제약(mobilityLimited)은 본인과 방장에게만 보인다
    @Transactional(readOnly = true)
    public SurveyDto.Response find(Long tripId, Long memberId, Member viewer) {
        memberRepository.findByIdAndTripId(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var survey = surveyAnswerRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var showPrivate = viewer.getId().equals(memberId) || viewer.isHost();
        return SurveyDto.Response.of(survey, showPrivate);
    }
}
