package com.tripmate.survey;

import com.tripmate.role.Chore;
import com.tripmate.stay.Amenity;
import com.tripmate.trip.Member;
import com.tripmate.trip.MemberRepository;
import com.tripmate.trip.TripChanged;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/trips/{tripId}/members/{memberId}/survey")
public class SurveyController {

    private final MemberRepository memberRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final ApplicationEventPublisher events;

    public SurveyController(MemberRepository memberRepository, SurveyAnswerRepository surveyAnswerRepository,
                            ApplicationEventPublisher events) {
        this.memberRepository = memberRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.events = events;
    }

    // PUT: /api/trips/1/members/2/survey  body = SurveyDto.Request 위 주석의 JSON
    // 다시 하면 고쳐 쓰니 POST 대신 PUT. 처음이든 다시든 같은 요청이다
    @PutMapping
    @Transactional
    public ResponseEntity<SurveyDto.Response> saveSurvey(@PathVariable Long tripId, @PathVariable Long memberId,
                                                         @Valid @RequestBody SurveyDto.Request req) {
        var member = findMember(tripId, memberId);
        var survey = surveyAnswerRepository.findByMemberId(memberId)
                .orElseGet(() -> surveyAnswerRepository.save(new SurveyAnswer(member)));
        var avoid = (req.avoid() == null) ? Set.<Chore>of() : req.avoid();
        var preferences = (req.preferences() == null) ? Map.<Amenity, Integer>of() : req.preferences();
        survey.update(req.activity(), req.planning(), req.hasCar(), req.separateDeparture(),
                req.mobilityLimited(), avoid, preferences);
        events.publishEvent(new TripChanged(tripId, "members"));
        return ResponseEntity.ok(SurveyDto.Response.of(survey, true));
    }

    // GET: /api/trips/1/members/2/survey   헤더 X-Member-Id: 1 (보는 사람)
    // shortcut: 보는 사람을 헤더로 믿는다. 카카오 로그인 붙이면 세션의 사용자로 바꾼다
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<SurveyDto.Response> getSurvey(@PathVariable Long tripId, @PathVariable Long memberId,
                                                        @RequestHeader("X-Member-Id") Long viewerId) {
        findMember(tripId, memberId);
        var viewer = findMember(tripId, viewerId);
        var survey = surveyAnswerRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var showPrivate = viewer.getId().equals(memberId) || viewer.isHost();
        return ResponseEntity.ok(SurveyDto.Response.of(survey, showPrivate));
    }

    private Member findMember(Long tripId, Long memberId) {
        return memberRepository.findByIdAndTripId(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
