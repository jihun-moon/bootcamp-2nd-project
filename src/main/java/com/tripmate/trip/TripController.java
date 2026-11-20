package com.tripmate.trip;

import com.tripmate.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TripController {

    private final TripService tripService;
    private final TripEvents tripEvents;
    private final TripAccess tripAccess;
    private final CurrentUser currentUser;

    public TripController(TripService tripService, TripEvents tripEvents, TripAccess tripAccess, CurrentUser currentUser) {
        this.tripService = tripService;
        this.tripEvents = tripEvents;
        this.tripAccess = tripAccess;
        this.currentUser = currentUser;
    }

    // GET: /api/trips/1
    @GetMapping("/trips/{tripId}")
    public ResponseEntity<TripDto.Response> getTrip(@PathVariable Long tripId) {
        var me = tripAccess.me(tripId);
        return ResponseEntity.ok(tripService.find(tripId, me));
    }

    // PUT: /api/trips/1/rules  body = { "driverPaysFuel": false, "refundNoShow": false, "drinksSeparate": true }
    // 방장만. 참여자는 403
    @PutMapping("/trips/{tripId}/rules")
    public ResponseEntity<TripDto.Response> updateRules(@PathVariable Long tripId, @RequestBody TripDto.Rules rules) {
        var host = tripAccess.host(tripId);
        return ResponseEntity.ok(tripService.updateRules(tripId, host, rules));
    }

    // GET: /api/me/trips   첫 화면의 "내 여행방". 로그인 안 했으면 401(화면은 이걸로 로그인 여부도 안다)
    @GetMapping("/me/trips")
    public ResponseEntity<List<TripDto.Summary>> myTrips() {
        return ResponseEntity.ok(tripService.myTrips(currentUser.get()));
    }

    // POST: /api/trips  body = { "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15", "endDate": "2027-01-17" }
    @PostMapping("/trips")
    public ResponseEntity<TripDto.Response> createTrip(@Valid @RequestBody TripDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.create(currentUser.get(), req));
    }

    // POST: /api/invites/3f9a1c2b7d4e
    @PostMapping("/invites/{inviteCode}")
    public ResponseEntity<TripDto.Joined> join(@PathVariable String inviteCode) {
        return ResponseEntity.ok(tripService.join(currentUser.get(), inviteCode));
    }

    // GET: /api/trips/1/events   (화면: new EventSource("/api/trips/1/events"))
    // 받는 것: event: change, data: roles | members | stays
    @GetMapping(path = "/trips/{tripId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable Long tripId) throws IOException {
        tripAccess.me(tripId);
        // 배포 때 앞에 둔 중간 서버(nginx 등)가 gzip 으로 묶거나 모아 두면 알림이 바로 안 간다. no-transform 이면 압축을 건너뛴다
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(tripEvents.subscribe(tripId));
    }
}
