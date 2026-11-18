package com.tripmate.role;

import com.tripmate.survey.SurveyAnswer;
import com.tripmate.survey.SurveyAnswerRepository;
import com.tripmate.trip.TripRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trips/{tripId}/roles")
public class RoleController {

    // 필수 역할의 첫 칸에만 얹는다. 설문 점수(한 자리)보다 훨씬 커서 사람이 모자라도 필수 역할이 먼저 찬다
    private static final int REQUIRED_BONUS = 100;

    private final TripRepository tripRepository;
    private final RoleRepository roleRepository;
    private final AssignmentRepository assignmentRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;

    public RoleController(TripRepository tripRepository, RoleRepository roleRepository,
                          AssignmentRepository assignmentRepository, SurveyAnswerRepository surveyAnswerRepository) {
        this.tripRepository = tripRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
    }

    // GET: /api/trips/1/roles
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<RoleDto.Board> getBoard(@PathVariable Long tripId) {
        if (!tripRepository.existsById(tripId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(RoleDto.Board.of(roleRepository.findByTripIdOrderById(tripId),
                assignmentRepository.findByRoleTripIdOrderById(tripId)));
    }

    // POST: /api/trips/1/roles/recommend
    // 손으로 옮긴 배정까지 지우고 설문으로 처음부터 다시 짠다
    @PostMapping("/recommend")
    @Transactional
    public ResponseEntity<RoleDto.Board> recommend(@PathVariable Long tripId) {
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
        return ResponseEntity.ok(RoleDto.Board.of(roles, created));
    }
}
