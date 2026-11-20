package com.tripmate.survey;

import com.tripmate.trip.TripDeleting;
import org.springframework.context.event.EventListener;
import com.tripmate.trip.MemberLeaving;
import com.tripmate.role.Chore;
import com.tripmate.role.RoleService;
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
    private final RoleService roleService;
    private final ApplicationEventPublisher events;

    public SurveyService(MemberRepository memberRepository, SurveyAnswerRepository surveyAnswerRepository,
                         RoleService roleService, ApplicationEventPublisher events) {
        this.memberRepository = memberRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.roleService = roleService;
        this.events = events;
    }

    // 처음이면 새로 만들고, 다시 하면 그 한 장을 고쳐 쓴다
    @Transactional
    public SurveyDto.Response save(Long tripId, Member me, SurveyDto.Request req) {
        // 컨트롤러가 준 me 는 이 트랜잭션 밖에서 읽은 것이라 고쳐도 저장되지 않는다("설문 완료"가 안 켜졌다). 여기서 다시 읽는다
        var member = memberRepository.findByIdAndTripId(me.getId(), tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var survey = surveyAnswerRepository.findByMemberId(member.getId())
                .orElseGet(() -> surveyAnswerRepository.save(new SurveyAnswer(member)));
        var avoid = (req.avoid() == null) ? Set.<Chore>of() : req.avoid();
        var preferences = (req.preferences() == null) ? Map.<Amenity, Integer>of() : req.preferences();
        // 차가 없으면 출발 동네는 길찾기에 안 쓰니 남기지 않는다
        var departure = (req.hasCar() && req.departure() != null && !req.departure().isBlank())
                ? req.departure().strip() : null;
        survey.update(req.activity(), req.planning(), req.hasCar(), req.separateDeparture(),
                departure, req.mobilityLimited(), avoid, preferences);
        // 방 개요의 "설문 완료"가 바뀌니 같은 방 화면들에 알린다
        events.publishEvent(new TripChanged(tripId, "members"));
        roleService.recommendWhenAllDone(tripId);
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

    // 방을 지우면 그 방 설문도 지운다
    @EventListener
    public void onTripDeleting(TripDeleting e) {
        surveyAnswerRepository.deleteAll(surveyAnswerRepository.findByMemberTripId(e.tripId()));
    }

    // 방을 나간 사람의 설문. 숙소 취향 평균과 역할 점수에서 빠진다
    @EventListener
    public void onMemberLeaving(MemberLeaving e) {
        surveyAnswerRepository.findByMemberId(e.memberId()).ifPresent(surveyAnswerRepository::delete);
    }
}
