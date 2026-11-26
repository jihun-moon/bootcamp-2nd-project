package com.tripmate.service.role;

import com.tripmate.dto.RoleDto;
import com.tripmate.dto.TravelDto;
import com.tripmate.entity.role.Assignment;
import com.tripmate.entity.role.Role;
import com.tripmate.entity.role.RoleKind;
import com.tripmate.entity.survey.SurveyAnswer;
import com.tripmate.entity.trip.Member;
import com.tripmate.repository.role.AssignmentRepository;
import com.tripmate.repository.role.RoleRepository;
import com.tripmate.repository.survey.SurveyAnswerRepository;
import com.tripmate.repository.trip.MemberRepository;
import com.tripmate.repository.trip.TripRepository;
import com.tripmate.service.trip.MemberLeaving;
import com.tripmate.service.trip.MemberLeft;
import com.tripmate.service.trip.TripChanged;
import com.tripmate.service.trip.TripDeleting;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
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
    // 기본 6개 + 방장이 더한 것. 추천은 (사람 + 정원 칸)^3 이라 끝없이 늘지 않게 막는다
    private static final int MAX_ROLES = 12;

    private final TripRepository tripRepository;
    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final AssignmentRepository assignmentRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final CarService carService;
    private final ApplicationEventPublisher events;

    public RoleService(TripRepository tripRepository, MemberRepository memberRepository, RoleRepository roleRepository,
                       AssignmentRepository assignmentRepository, SurveyAnswerRepository surveyAnswerRepository,
                       CarService carService, ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.carService = carService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public RoleDto.Board board(Long tripId) {
        return RoleDto.Board.of(roleRepository.findByTripIdOrderById(tripId),
                assignmentRepository.findByRoleTripIdOrderById(tripId));
    }

    // POST 역할 더하기. 방장만(컨트롤러에서 막는다)
    @Transactional
    public RoleDto.Board addRole(Long tripId, RoleDto.SaveRequest req) {
        var trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (roleRepository.countByTripId(tripId) >= MAX_ROLES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "역할은 " + MAX_ROLES + "개까지 둘 수 있어요");
        }
        roleRepository.save(new Role(trip, req.title().strip(), req.required(), req.capacity(), strip(req.tasks())));
        events.publishEvent(new TripChanged(tripId, "roles"));
        return board(tripId);
    }

    // 정원을 지금 인원보다 줄여도 배정은 그대로 둔다. 손으로 옮길 때처럼 "3/2"로 보이고 사람이 고른다
    @Transactional
    public RoleDto.Board updateRole(Long tripId, Long roleId, RoleDto.SaveRequest req) {
        var role = roleRepository.findByIdAndTripId(roleId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        role.update(req.title().strip(), req.required(), req.capacity(), strip(req.tasks()));
        events.publishEvent(new TripChanged(tripId, "roles"));
        return board(tripId);
    }

    // 그 역할에 있던 사람은 미배정으로 돌아간다
    @Transactional
    public void deleteRole(Long tripId, Long roleId) {
        var role = roleRepository.findByIdAndTripId(roleId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assignmentRepository.deleteByRoleId(role.getId());
        roleRepository.delete(role);
        events.publishEvent(new TripChanged(tripId, "roles"));
    }

    private static List<String> strip(List<String> tasks) {
        return tasks.stream().map(String::strip).toList();
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
            assignmentRepository.save(Assignment.placedByHand(member, target));
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
        // 차 나누기대로 차마다 탄 사람(운전자 빼고). 운전 칸이 비었으면 빈 표
        var riders = new HashMap<Long, List<Long>>();
        carService.seating(tripId).cars().forEach((driver, seats) ->
                riders.put(driver, seats.stream().filter(id -> !id.equals(driver)).sorted().toList()));
        return new TravelDto.Plan(trip.getDestination(), trip.getMembers().size(), cars, riders);
    }

    // 방을 지우면 역할 카드와 역할 칸도 지운다(카드가 칸을 가리켜 카드부터)
    @EventListener
    public void onTripDeleting(TripDeleting e) {
        assignmentRepository.deleteAll(assignmentRepository.findByRoleTripIdOrderById(e.tripId()));
        roleRepository.deleteAll(roleRepository.findByTripIdOrderById(e.tripId()));
    }

    // 방을 나간 사람의 역할 카드. 빈자리는 보드에서 다시 추천하거나 손으로 채운다
    @EventListener
    public void onMemberLeaving(MemberLeaving e) {
        assignmentRepository.findByMemberId(e.memberId()).ifPresent(assignmentRepository::delete);
    }

    // 나간 사람이 마지막 설문 전 사람이었으면 이제 다 모인 것이라 자동 추천을 다시 본다(리뷰 #10)
    @EventListener
    public void onMemberLeft(MemberLeft e) {
        recommendWhenAllDone(e.tripId());
    }

    // 마지막 사람이 설문을 끝내면 방장이 단추를 누르지 않아도 역할이 정해진다.
    // 혼자 있는 방은 아직 부를 친구가 남아 기다린다.
    // 방장과 제일 빠른 친구 둘이 먼저 끝내면 그때 "모두 끝남"이라 둘만 배정되고, 저녁에 들어온 다섯은 빈손이었다(7명 시뮬레이션).
    // 그래서 배정 없는 사람이 생기면 다시 짠다. 다만 누가 카드를 손으로 옮겼으면 그 뜻을 지우지 않게 둔다
    @Transactional
    public void recommendWhenAllDone(Long tripId) {
        var members = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).getMembers();
        var allDone = members.size() >= 2 && members.stream().allMatch(Member::isSurveyDone);
        if (!allDone) return;
        var assignments = assignmentRepository.findByRoleTripIdOrderById(tripId);
        var someoneLeftOut = assignments.size() < members.size();
        var movedByHand = assignments.stream().anyMatch(Assignment::isByHand);
        if (assignments.isEmpty() || someoneLeftOut && !movedByHand) recommend(tripId);
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
