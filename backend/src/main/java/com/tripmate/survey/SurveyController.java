package com.tripmate.survey;

import com.tripmate.role.Chore;
import com.tripmate.stay.Amenity;
import com.tripmate.trip.MemberRepository;
import com.tripmate.trip.TripAccess;
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

    private final TripAccess tripAccess;
    private final MemberRepository memberRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final ApplicationEventPublisher events;

    public SurveyController(TripAccess tripAccess, MemberRepository memberRepository,
                            SurveyAnswerRepository surveyAnswerRepository, ApplicationEventPublisher events) {
        this.tripAccess = tripAccess;
        this.memberRepository = memberRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.events = events;
    }

    // PUT: /api/trips/1/members/2/survey  body = SurveyDto.Request 위 주석의 JSON
    // 다시 하면 고쳐 쓰니 POST 대신 PUT. 처음이든 다시든 같은 요청이다. 본인 설문만 쓸 수 있다
    @PutMapping
    @Transactional
    public ResponseEntity<SurveyDto.Response> saveSurvey(@PathVariable Long tripId, @PathVariable Long memberId,
                                                         @Valid @RequestBody SurveyDto.Request req) {
        var member = tripAccess.self(tripId, memberId);
        var survey = surveyAnswerRepository.findByMemberId(memberId)
                .orElseGet(() -> surveyAnswerRepository.save(new SurveyAnswer(member)));
        var avoid = (req.avoid() == null) ? Set.<Chore>of() : req.avoid();
        var preferences = (req.preferences() == null) ? Map.<Amenity, Integer>of() : req.preferences();
        survey.update(req.activity(), req.planning(), req.hasCar(), req.separateDeparture(),
                req.mobilityLimited(), avoid, preferences);
        events.publishEvent(new TripChanged(tripId, "members"));
        return ResponseEntity.ok(SurveyDto.Response.of(survey, true));
    }

    // GET: /api/trips/1/members/2/survey   보는 사람은 로그인한 본인
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<SurveyDto.Response> getSurvey(@PathVariable Long tripId, @PathVariable Long memberId) {
        var viewer = tripAccess.me(tripId);
        memberRepository.findByIdAndTripId(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var survey = surveyAnswerRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var showPrivate = viewer.getId().equals(memberId) || viewer.isHost();
        return ResponseEntity.ok(SurveyDto.Response.of(survey, showPrivate));
    }
}
