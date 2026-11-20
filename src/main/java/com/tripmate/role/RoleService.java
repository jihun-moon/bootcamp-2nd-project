package com.tripmate.role;

import com.tripmate.survey.SurveyAnswer;
import com.tripmate.survey.SurveyAnswerRepository;
import com.tripmate.trip.MemberRepository;
import com.tripmate.trip.TripChanged;
import com.tripmate.trip.TripRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoleService {

    // 필수 역할의 첫 칸에만 얹는다. 설문 점수(한 자리)보다 훨씬 커서 사람이 모자라도 필수 역할이 먼저 찬다
    private static final int REQUIRED_BONUS = 100;

    private final TripRepository tripRepository;
    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final AssignmentRepository assignmentRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final ApplicationEventPublisher events;

    public RoleService(TripRepository tripRepository, MemberRepository memberRepository, RoleRepository roleRepository,
                       AssignmentRepository assignmentRepository, SurveyAnswerRepository surveyAnswerRepository,
                       ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public RoleDto.Board board(Long tripId) {
        return RoleDto.Board.of(roleRepository.findByTripIdOrderById(tripId),
                assignmentRepository.findByRoleTripIdOrderById(tripId));
    }

    // 화면이 본 version 과 지금 version 이 다르면 그 사이 누가 옮긴 것이라 409 로 막는다
    @Transactional
    public RoleDto.Board move(Long tripId, Long memberId, RoleDto.MoveRequest req) {
        var member = memberRepository.findByIdAndTripId(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var target = (req.roleId() == null) ? null : roleRepository.findByIdAndTripId(req.roleId(), tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var current = assignmentRepository.findByMemberId(memberId);

        if (!Objects.equals(current.map(Assignment::getVersion).orElse(null), req.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        if (current.isEmpty() && target != null) {
            assignmentRepository.save(new Assignment(member, target, List.of()));
        } else if (current.isPresent() && target == null) {
            assignmentRepository.delete(current.get());
        } else if (current.isPresent()) {
            current.get().moveTo(target);
        }
        // version 이 올라간 값을 응답에 담으려면 먼저 DB 에 내보내야 한다
        assignmentRepository.flush();
        events.publishEvent(new TripChanged(tripId, "roles"));
        return board(tripId);
    }

    // 이동·회비 근거에 쓸 값만 꺼낸다. 길찾기는 몇 초 걸리니 그동안 DB 연결을 붙잡지 않게 여기서 트랜잭션을 끝낸다
    // 차를 가져가는 사람과 운전 칸에 들어간 사람을 같이 담는다(손으로 옮겨 차 없는 사람이 운전 칸에 있을 수도 있다)
    @Transactional(readOnly = true)
    public TravelDto.Plan travelPlan(Long tripId) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Set<Long> drivers = assignmentRepository.findByRoleTripIdOrderById(tripId).stream()
                .filter(a -> a.getRole().getKind() == RoleKind.DRIVER)
                .map(a -> a.getMember().getId())
                .collect(Collectors.toSet());
        Map<Long, SurveyAnswer> surveys = surveyAnswerRepository.findByMemberTripId(tripId).stream()
                .collect(Collectors.toMap(s -> s.getMember().getId(), Function.identity()));

        var cars = trip.getMembers().stream()
                .filter(m -> drivers.contains(m.getId()) || (surveys.containsKey(m.getId()) && surveys.get(m.getId()).isHasCar()))
                .map(m -> new TravelDto.Car(m.getId(), m.getName(),
                        surveys.containsKey(m.getId()) ? surveys.get(m.getId()).getDeparture() : null,
                        drivers.contains(m.getId())))
                .toList();
        return new TravelDto.Plan(trip.getDestination(), trip.getMembers().size(), cars);
    }

    // 손으로 옮긴 배정까지 지우고 설문으로 처음부터 다시 짠다. 점수 규칙은 RoleScorer, 배정은 HungarianAssigner
    @Transactional
    public RoleDto.Board recommend(Long tripId) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var members = trip.getMembers();
        var roles = roleRepository.findByTripIdOrderById(tripId);
        Map<Long, SurveyAnswer> surveys = surveyAnswerRepository.findByMemberTripId(tripId).stream()
                .collect(Collectors.toMap(s -> s.getMember().getId(), Function.identity()));

        // 운전 2명처럼 capacity 만큼 칸을 펼친다
        var slots = new ArrayList<Role>();
        var bonus = new ArrayList<Integer>();
        for (var role : roles) {
            for (var k = 0; k < role.getCapacity(); k++) {
                slots.add(role);
                bonus.add(role.isRequired() && k == 0 ? REQUIRED_BONUS : 0);
            }
        }

        var scores = new RoleScorer.Score[members.size()][roles.size()];
        var table = new int[members.size()][slots.size()];
        for (var i = 0; i < members.size(); i++) {
            var survey = surveys.get(members.get(i).getId());
            for (var r = 0; r < roles.size(); r++) scores[i][r] = RoleScorer.score(survey, roles.get(r).getKind());
            for (var j = 0; j < slots.size(); j++) table[i][j] = scores[i][roles.indexOf(slots.get(j))].value() + bonus.get(j);
        }
        var picks = HungarianAssigner.assign(table);

        // member 유일 제약이라 지운 것을 먼저 DB 에 내보내야 새로 넣을 수 있다
        assignmentRepository.deleteAll(assignmentRepository.findByRoleTripIdOrderById(tripId));
        assignmentRepository.flush();

        var created = new ArrayList<Assignment>();
        for (var i = 0; i < members.size(); i++) {
            if (picks[i] < 0) continue;
            var role = slots.get(picks[i]);
            created.add(new Assignment(members.get(i), role, scores[i][roles.indexOf(role)].reasons()));
        }
        assignmentRepository.saveAll(created);
        events.publishEvent(new TripChanged(tripId, "roles"));
        return RoleDto.Board.of(roles, created);
    }
}
