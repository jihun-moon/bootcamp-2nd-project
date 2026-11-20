package com.tripmate.trip;

import com.tripmate.user.CurrentUser;
import com.tripmate.user.GuestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final GuestService guestService;

    public TripController(TripService tripService, TripEvents tripEvents, TripAccess tripAccess, CurrentUser currentUser,
                          GuestService guestService) {
        this.tripService = tripService;
        this.tripEvents = tripEvents;
        this.tripAccess = tripAccess;
        this.currentUser = currentUser;
        this.guestService = guestService;
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

    // POST: /api/trips/1/next   같은 멤버로 다음 여행(참여자 누구나). 만들면 201, 이미 있던 방이면 200
    @PostMapping("/trips/{tripId}/next")
    public ResponseEntity<TripDto.Next> next(@PathVariable Long tripId) {
        tripAccess.me(tripId);
        var next = tripService.next(tripId, currentUser.get());
        return ResponseEntity.status(next.created() ? HttpStatus.CREATED : HttpStatus.OK).body(next);
    }

    // 두 사람이 같은 순간에 눌러 다음 방이 두 번 만들어지려 하면 unique 가 막는다. 다시 누르면 먼저 생긴 방으로 간다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("방금 다른 사람이 만들었어요. 다시 눌러 주세요.");
    }

    // DELETE: /api/trips/1/members/7   본인이면 나가기, 방장이면 남을 빼기. 그 밖은 403. -> 204
    @DeleteMapping("/trips/{tripId}/members/{memberId}")
    public ResponseEntity<Void> leave(@PathVariable Long tripId, @PathVariable Long memberId) {
        tripAccess.selfOrHost(tripId, memberId);
        tripService.leave(tripId, memberId);
        return ResponseEntity.noContent().build();
    }

    // PUT: /api/trips/1/members/2/drinks  body = { "drinks": false }   본인이나 방장(못 오는 사람 대신 끄기)
    @PutMapping("/trips/{tripId}/members/{memberId}/drinks")
    public ResponseEntity<TripDto.Response> drinks(@PathVariable Long tripId, @PathVariable Long memberId,
                                                   @RequestBody TripDto.DrinksRequest req) {
        var me = tripAccess.selfOrHost(tripId, memberId);
        return ResponseEntity.ok(tripService.setDrinks(tripId, memberId, req.drinks(), me));
    }

    // PUT: /api/trips/1  body = { "title": "강릉 우정 여행", "destination": "강릉" }   방장만
    @PutMapping("/trips/{tripId}")
    public ResponseEntity<TripDto.Response> rename(@PathVariable Long tripId, @Valid @RequestBody TripDto.RenameRequest req) {
        var host = tripAccess.host(tripId);
        return ResponseEntity.ok(tripService.rename(tripId, host, req));
    }

    // DELETE: /api/trips/1   방장만. 설문·역할·숙소·장보기·정산까지 모두 지워지고 되돌릴 수 없다 -> 204
    @DeleteMapping("/trips/{tripId}")
    public ResponseEntity<Void> delete(@PathVariable Long tripId) {
        tripAccess.host(tripId);
        tripService.delete(tripId);
        return ResponseEntity.noContent().build();
    }

    // PUT: /api/trips/1/host  body = { "memberId": 3 }   방장만
    @PutMapping("/trips/{tripId}/host")
    public ResponseEntity<TripDto.Response> handOver(@PathVariable Long tripId, @Valid @RequestBody TripDto.HostRequest req) {
        var host = tripAccess.host(tripId);
        return ResponseEntity.ok(tripService.handOver(tripId, host, req.memberId()));
    }

    // POST: /api/trips/1/room-draw  body = { "rooms": 2 }   방 사람 누구나(몇 번째로 누가 뽑았는지 남는다)
    @PostMapping("/trips/{tripId}/room-draw")
    public ResponseEntity<TripDto.Response> drawRooms(@PathVariable Long tripId, @Valid @RequestBody TripDto.DrawRequest req) {
        var me = tripAccess.me(tripId);
        return ResponseEntity.ok(tripService.drawRooms(tripId, me, req.rooms()));
    }

    // PUT: /api/trips/1/meet  body = { "time": "08:00", "place": "합정역 2번 출구" }   방장만
    @PutMapping("/trips/{tripId}/meet")
    public ResponseEntity<TripDto.Response> meet(@PathVariable Long tripId, @Valid @RequestBody TripDto.Meet meet) {
        var host = tripAccess.host(tripId);
        return ResponseEntity.ok(tripService.updateMeet(tripId, host, meet));
    }

    // GET: /api/trips/1/dates   날짜 맞추기 판(참여자 누구나)
    @GetMapping("/trips/{tripId}/dates")
    public ResponseEntity<TripDto.DatesBoard> dates(@PathVariable Long tripId) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(tripService.dates(tripId));
    }

    // PUT: /api/trips/1/members/2/dates  body = { "dates": ["2027-01-15", "2027-01-16"] }   본인 것만
    @PutMapping("/trips/{tripId}/members/{memberId}/dates")
    public ResponseEntity<TripDto.DatesBoard> saveDates(@PathVariable Long tripId, @PathVariable Long memberId,
                                                        @Valid @RequestBody TripDto.DatesRequest req) {
        tripAccess.self(tripId, memberId);
        return ResponseEntity.ok(tripService.saveDates(tripId, memberId, req));
    }

    // PUT: /api/trips/1/period  body = { "startDate": "2027-01-15", "endDate": "2027-01-17" }   방장만
    @PutMapping("/trips/{tripId}/period")
    public ResponseEntity<TripDto.Response> setPeriod(@PathVariable Long tripId, @Valid @RequestBody TripDto.PeriodRequest req) {
        var host = tripAccess.host(tripId);
        return ResponseEntity.ok(tripService.setPeriod(tripId, host, req));
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

    // POST: /api/invites/3f9a1c2b7d4e/guest  body = { "name": "민재" }   로그인 없이 이름만(docs/19).
    // 손님 계정을 만들어 이 브라우저를 로그인시키고(기억 쿠키 포함) 방에 넣는다
    @PostMapping("/invites/{inviteCode}/guest")
    public ResponseEntity<TripDto.Joined> joinAsGuest(@PathVariable String inviteCode, @Valid @RequestBody TripDto.GuestRequest req,
                                                      HttpServletRequest request, HttpServletResponse response) {
        tripService.checkGuestRoom(inviteCode);
        var user = guestService.signIn(req.name(), request, response);
        return ResponseEntity.ok(tripService.join(user, inviteCode));
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
        var me = tripAccess.me(tripId);
        // 배포 때 앞에 둔 중간 서버(nginx 등)가 gzip 으로 묶거나 모아 두면 알림이 바로 안 간다. no-transform 이면 압축을 건너뛴다
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(tripEvents.subscribe(tripId, me.getId()));
    }
}
