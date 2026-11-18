package com.tripmate.trip;

import com.tripmate.role.Role;
import com.tripmate.role.RoleRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripRepository tripRepository;
    private final RoleRepository roleRepository;

    public TripController(TripRepository tripRepository, RoleRepository roleRepository) {
        this.tripRepository = tripRepository;
        this.roleRepository = roleRepository;
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
