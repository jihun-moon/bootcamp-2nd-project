package com.tripmate.controller.role;

import com.tripmate.component.TripAccess;
import com.tripmate.dto.RoleDto;
import com.tripmate.dto.TravelDto;
import com.tripmate.service.role.RoleService;
import com.tripmate.service.role.TravelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/roles")
public class RoleController {

    private final TripAccess tripAccess;
    private final RoleService roleService;
    private final TravelService travelService;

    public RoleController(TripAccess tripAccess, RoleService roleService, TravelService travelService) {
        this.tripAccess = tripAccess;
        this.roleService = roleService;
        this.travelService = travelService;
    }

    // GET: /api/trips/1/roles
    @GetMapping
    public ResponseEntity<RoleDto.Board> getBoard(@PathVariable Long tripId) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(roleService.board(tripId));
    }

    // PATCH: /api/trips/1/roles/assignments/2  body = { "roleId": 4, "version": 0 }
    // 보드는 같이 쓰는 것이라 방 참여자면 누구 카드든 옮길 수 있다. 그 사이 누가 먼저 옮겼으면 409
    // (version 이 다르면 RoleService 가, 저장하는 순간에 겹치면 GlobalExceptionHandler 가)
    @PatchMapping("/assignments/{memberId}")
    public ResponseEntity<RoleDto.Board> move(@PathVariable Long tripId, @PathVariable Long memberId,
                                              @RequestBody RoleDto.MoveRequest req) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(roleService.move(tripId, memberId, req));
    }

    // POST: /api/trips/1/roles  body = { "title": "설거지", "required": false, "capacity": 2, "tasks": ["저녁 설거지"] }
    // 역할을 더하고 고치고 지우는 건 모두의 보드를 바꾸니 방장만
    @PostMapping
    public ResponseEntity<RoleDto.Board> addRole(@PathVariable Long tripId, @Valid @RequestBody RoleDto.SaveRequest req) {
        tripAccess.host(tripId);
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.addRole(tripId, req));
    }

    // PATCH: /api/trips/1/roles/4  body = POST 와 같다(칸을 다 보낸다)
    @PatchMapping("/{roleId}")
    public ResponseEntity<RoleDto.Board> updateRole(@PathVariable Long tripId, @PathVariable Long roleId,
                                                    @Valid @RequestBody RoleDto.SaveRequest req) {
        tripAccess.host(tripId);
        return ResponseEntity.ok(roleService.updateRole(tripId, roleId, req));
    }

    // DELETE: /api/trips/1/roles/4   그 역할에 있던 사람은 미배정으로
    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long tripId, @PathVariable Long roleId) {
        tripAccess.host(tripId);
        roleService.deleteRole(tripId, roleId);
        return ResponseEntity.noContent().build();
    }

    // POST: /api/trips/1/roles/recommend   모두의 배정을 갈아엎으니 방장만
    @PostMapping("/recommend")
    public ResponseEntity<RoleDto.Board> recommend(@PathVariable Long tripId) {
        tripAccess.host(tripId);
        return ResponseEntity.ok(roleService.recommend(tripId));
    }

    // GET: /api/trips/1/roles/travel   차마다 거리·시간·통행료·기름값과 운전하는 차로 센 1인 교통비
    // 카카오를 부르므로 보드를 읽을 때마다가 아니라 화면에서 "계산하기"를 누를 때만 부른다
    @GetMapping("/travel")
    public ResponseEntity<TravelDto.Summary> travel(@PathVariable Long tripId) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(travelService.estimate(roleService.travelPlan(tripId)));
    }
}
