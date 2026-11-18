package com.tripmate.role;

import com.tripmate.trip.TripAccess;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/roles")
public class RoleController {

    private final TripAccess tripAccess;
    private final RoleService roleService;

    public RoleController(TripAccess tripAccess, RoleService roleService) {
        this.tripAccess = tripAccess;
        this.roleService = roleService;
    }

    // GET: /api/trips/1/roles
    @GetMapping
    public ResponseEntity<RoleDto.Board> getBoard(@PathVariable Long tripId) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(roleService.board(tripId));
    }

    // PATCH: /api/trips/1/roles/assignments/2  body = { "roleId": 4, "version": 0 }
    // 보드는 같이 쓰는 것이라 방 참여자면 누구 카드든 옮길 수 있다
    @PatchMapping("/assignments/{memberId}")
    public ResponseEntity<RoleDto.Board> move(@PathVariable Long tripId, @PathVariable Long memberId,
                                              @RequestBody RoleDto.MoveRequest req) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(roleService.move(tripId, memberId, req));
    }

    // POST: /api/trips/1/roles/recommend   모두의 배정을 갈아엎으니 방장만
    @PostMapping("/recommend")
    public ResponseEntity<RoleDto.Board> recommend(@PathVariable Long tripId) {
        tripAccess.host(tripId);
        return ResponseEntity.ok(roleService.recommend(tripId));
    }

    // version 비교와 저장 사이에 다른 요청이 끼어들면 DB 단계에서 걸린다. 둘 다 "누가 먼저 옮김"이다
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
