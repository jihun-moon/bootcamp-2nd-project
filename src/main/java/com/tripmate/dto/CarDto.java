package com.tripmate.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

public final class CarDto {

    private CarDto() {
    }

    // GET /api/trips/1/cars
    // { "cars": [ { "driverId": 1, "driverName": "재현", "meetTime": "08:30", "meetPlace": "합정역 2번 출구", "riderIds": [3, 4] } ],
    //   "separateIds": [8] }   riderIds 는 운전자를 뺀 탄 사람, separateIds 는 따로 가는 사람
    public record Cars(List<Car> cars, List<Long> separateIds) {
    }

    public record Car(Long driverId, String driverName, LocalTime meetTime, String meetPlace, List<Long> riderIds) {
    }

    // PUT /api/trips/1/cars/rides/3  { "driverId": 1 }   null 이면 따로 가요
    public record RideRequest(Long driverId) {
    }

    // PUT /api/trips/1/cars/1/meet  { "time": "08:30", "place": "수원역 4번 출구" }
    public record MeetRequest(LocalTime time, @Size(max = 40) String place) {
    }
}
