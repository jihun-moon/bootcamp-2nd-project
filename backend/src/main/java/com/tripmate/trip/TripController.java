package com.tripmate.trip;

import com.tripmate.role.Role;
import com.tripmate.role.RoleRepository;
import jakarta.validation.Valid;
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
@RequestMapping("/api/trips")
public class TripController {

    private final TripRepository tripRepository;
    private final RoleRepository roleRepository;
    private final TripEvents tripEvents;

    public TripController(TripRepository tripRepository, RoleRepository roleRepository, TripEvents tripEvents) {
        this.tripRepository = tripRepository;
        this.roleRepository = roleRepository;
        this.tripEvents = tripEvents;
    }

    // GET: /api/trips/1/events   (브라우저: new EventSource("/api/trips/1/events"))
    // 받는 것: event: change, data: roles | members
    @GetMapping(path = "/{tripId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable Long tripId) throws IOException {
        if (!tripRepository.existsById(tripId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        // 중간 서버(Next 프록시, nginx)가 gzip 으로 묶거나 모아 두면 알림이 바로 안 간다. no-transform 이면 압축을 건너뛴다
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(tripEvents.subscribe(tripId));
    }

    // GET: /api/trips/1
    @GetMapping("/{tripId}")
    @Transactional(readOnly = true)
    public ResponseEntity<TripDto.Response> getTrip(@PathVariable Long tripId) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(TripDto.Response.from(trip));
    }

    // POST: /api/trips  body = TripDto.CreateRequest 위 주석의 JSON
    @PostMapping
    @Transactional
    public ResponseEntity<TripDto.Response> createTrip(@Valid @RequestBody TripDto.CreateRequest req) {
        var trip = new Trip(req.title(), req.destination(), req.startDate(), req.endDate());
        // 로그인 붙기 전이라 만든 사람을 이름 없이 방장으로 넣는다
        trip.addMember("방장", true);
        tripRepository.save(trip);
        roleRepository.saveAll(Role.defaultsFor(trip));
        return ResponseEntity.status(HttpStatus.CREATED).body(TripDto.Response.from(trip));
    }
}
