package com.tripmate.trip;

import com.tripmate.role.Role;
import com.tripmate.role.RoleRepository;
import com.tripmate.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class TripController {

    private final TripRepository tripRepository;
    private final RoleRepository roleRepository;
    private final TripEvents tripEvents;
    private final TripAccess tripAccess;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    public TripController(TripRepository tripRepository, RoleRepository roleRepository, TripEvents tripEvents,
                          TripAccess tripAccess, CurrentUser currentUser, ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.roleRepository = roleRepository;
        this.tripEvents = tripEvents;
        this.tripAccess = tripAccess;
        this.currentUser = currentUser;
        this.events = events;
    }

    // GET: /api/trips/1/events   (브라우저: new EventSource("/api/trips/1/events"))
    // 받는 것: event: change, data: roles | members | stays
    @GetMapping(path = "/trips/{tripId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable Long tripId) throws IOException {
        tripAccess.me(tripId);
        // 중간 서버(Next 프록시, nginx)가 gzip 으로 묶거나 모아 두면 알림이 바로 안 간다. no-transform 이면 압축을 건너뛴다
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(tripEvents.subscribe(tripId));
    }

    // GET: /api/trips/1
    @GetMapping("/trips/{tripId}")
    @Transactional(readOnly = true)
    public ResponseEntity<TripDto.Response> getTrip(@PathVariable Long tripId) {
        var me = tripAccess.me(tripId);
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(TripDto.Response.of(trip, me));
    }

    // POST: /api/trips  body = TripDto.CreateRequest 위 주석의 JSON   만든 사람이 방장이 된다
    @PostMapping("/trips")
    @Transactional
    public ResponseEntity<TripDto.Response> createTrip(@Valid @RequestBody TripDto.CreateRequest req) {
        var user = currentUser.get();
        var trip = new Trip(req.title(), req.destination(), req.startDate(), req.endDate());
        var me = trip.addMember(user, user.getNickname(), true);
        tripRepository.save(trip);
        roleRepository.saveAll(Role.defaultsFor(trip));
        return ResponseEntity.status(HttpStatus.CREATED).body(TripDto.Response.of(trip, me));
    }

    public record Joined(Long tripId) {
    }

    // POST: /api/invites/3f9a1c2b7d4e   초대 링크로 들어온 사람을 참여자로 넣는다. 이미 있으면 그대로 둔다
    @PostMapping("/invites/{inviteCode}")
    @Transactional
    public ResponseEntity<Joined> join(@PathVariable String inviteCode) {
        var user = currentUser.get();
        var trip = tripRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var already = trip.getMembers().stream().anyMatch(m -> m.belongsTo(user));
        if (!already) {
            trip.addMember(user, user.getNickname(), false);
            events.publishEvent(new TripChanged(trip.getId(), "members"));
        }
        return ResponseEntity.ok(new Joined(trip.getId()));
    }
}
