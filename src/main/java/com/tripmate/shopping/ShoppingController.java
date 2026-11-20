package com.tripmate.shopping;

import com.tripmate.trip.TripAccess;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/shopping")
public class ShoppingController {

    private final ShoppingService shoppingService;
    private final TripAccess tripAccess;

    public ShoppingController(ShoppingService shoppingService, TripAccess tripAccess) {
        this.shoppingService = shoppingService;
        this.tripAccess = tripAccess;
    }

    // GET: /api/trips/1/shopping   방 사람 누구나
    @GetMapping
    public ResponseEntity<ShoppingDto.Board> board(@PathVariable Long tripId) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(shoppingService.board(tripId));
    }

    // PUT: /api/trips/1/shopping/plan  body = { "drinkers": 6, "meatGrams": 300 }   방장만
    @PutMapping("/plan")
    public ResponseEntity<ShoppingDto.Board> plan(@PathVariable Long tripId, @Valid @RequestBody ShoppingDto.PlanRequest req) {
        tripAccess.host(tripId);
        return ResponseEntity.ok(shoppingService.savePlan(tripId, req));
    }

    // PUT: /api/trips/1/shopping/items/PORK_BELLY  body = { "amount": null }   "내가 살게"(산 뒤엔 amount 를 적어 다시)
    @PutMapping("/items/{item}")
    public ResponseEntity<ShoppingDto.Board> claim(@PathVariable Long tripId, @PathVariable ShoppingItem item,
                                                   @Valid @RequestBody ShoppingDto.ClaimRequest req) {
        var me = tripAccess.me(tripId);
        return ResponseEntity.ok(shoppingService.claim(tripId, item, me, req));
    }

    // 둘이 같은 줄을 거의 동시에 누르면 (trip, item) 유일 제약에 걸린다. 늦은 쪽은 409, 화면이 다시 읽어 누가 맡았는지 보인다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Void> taken() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    // DELETE: /api/trips/1/shopping/items/PORK_BELLY   맡은 사람이나 방장이 내려놓기
    @DeleteMapping("/items/{item}")
    public ResponseEntity<ShoppingDto.Board> release(@PathVariable Long tripId, @PathVariable ShoppingItem item) {
        var me = tripAccess.me(tripId);
        return ResponseEntity.ok(shoppingService.release(tripId, item, me));
    }
}
