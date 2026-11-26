package com.tripmate.controller.role;

import com.tripmate.component.TripAccess;
import com.tripmate.dto.CarDto;
import com.tripmate.service.role.CarService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/cars")
public class CarController {

    private final TripAccess tripAccess;
    private final CarService carService;

    public CarController(TripAccess tripAccess, CarService carService) {
        this.tripAccess = tripAccess;
        this.carService = carService;
    }

    // GET: /api/trips/1/cars   방 사람 누구나
    @GetMapping
    public ResponseEntity<CarDto.Cars> cars(@PathVariable Long tripId) {
        tripAccess.me(tripId);
        return ResponseEntity.ok(carService.cars(tripId));
    }

    // PUT: /api/trips/1/cars/rides/3  body = { "driverId": 1 } (null = 따로 가요)   본인이나 방장
    @PutMapping("/rides/{memberId}")
    public ResponseEntity<CarDto.Cars> ride(@PathVariable Long tripId, @PathVariable Long memberId, @RequestBody CarDto.RideRequest req) {
        var me = tripAccess.me(tripId);
        return ResponseEntity.ok(carService.ride(tripId, me, memberId, req.driverId()));
    }

    // PUT: /api/trips/1/cars/1/meet  body = { "time": "08:30", "place": "수원역 4번 출구" }   그 차 운전자나 방장
    @PutMapping("/{driverId}/meet")
    public ResponseEntity<CarDto.Cars> meet(@PathVariable Long tripId, @PathVariable Long driverId, @Valid @RequestBody CarDto.MeetRequest req) {
        var me = tripAccess.me(tripId);
        return ResponseEntity.ok(carService.meet(tripId, me, driverId, req));
    }
}
