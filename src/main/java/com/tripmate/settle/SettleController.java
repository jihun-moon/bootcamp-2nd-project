package com.tripmate.settle;

import com.tripmate.trip.TripAccess;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class SettleController {

    private final SettleService settleService;
    private final TripAccess tripAccess;

    public SettleController(SettleService settleService, TripAccess tripAccess) {
        this.settleService = settleService;
        this.tripAccess = tripAccess;
    }

    // GET: /api/trips/1/settlement   방 사람 누구나
    @GetMapping("/settlement")
    public ResponseEntity<SettleDto.Board> board(@PathVariable Long tripId) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(settleService.board(tripId));
    }

    // POST: /api/trips/1/expenses  body = { "kind": "FUEL", "memo": "주유 + 톨비", "amount": 64000 }   낸 사람은 본인
    @PostMapping("/expenses")
    public ResponseEntity<SettleDto.Board> add(@PathVariable Long tripId, @Valid @RequestBody SettleDto.ExpenseRequest req) {
        var me = tripAccess.me(tripId);
        return ResponseEntity.status(HttpStatus.CREATED).body(settleService.add(tripId, me, req));
    }

    // DELETE: /api/trips/1/expenses/3   낸 사람 본인이나 방장
    @DeleteMapping("/expenses/{expenseId}")
    public ResponseEntity<SettleDto.Board> remove(@PathVariable Long tripId, @PathVariable Long expenseId) {
        var me = tripAccess.me(tripId);
        return ResponseEntity.ok(settleService.remove(tripId, expenseId, me));
    }
}
