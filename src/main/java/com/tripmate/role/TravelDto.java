package com.tripmate.role;

import java.util.List;

public final class TravelDto {

    private TravelDto() {
    }

    // RoleService 가 DB 에서 꺼낸 값. 길찾기는 트랜잭션 밖에서 하려고 엔티티 대신 값만 담아 넘긴다
    public record Plan(String destination, int memberCount, List<Car> cars) {
    }

    // 차를 가져가는 사람, 또는 운전 칸에 들어간 사람. departure 는 서버 안에서만 쓰고 응답에 넣지 않는다
    public record Car(Long memberId, String name, String departure, boolean driver) {
    }

    // GET /api/trips/1/roles/travel
    // { "message": null, "legs": [ { "name": "재현", "driver": true, "km": 62, "minutes": 70, "toll": 3200, "fuel": 8800 } ],
    //   "perPerson": 6000, "perPassenger": 8000, "fuelPrice": 1700, "fuelSource": "어림값", "kmPerLiter": 12, "tips": [] }
    // perPassenger 는 운전한 사람을 빼고 나눈 값. 커뮤니티에서 가장 자주 싸우는 게 "운전자도 기름값 내나"라서 두 값을 나란히 준다
    public record Summary(String message, List<Leg> legs, Integer perPerson, Integer perPassenger,
                          int fuelPrice, String fuelSource, int kmPerLiter, List<String> tips) {

        static Summary unavailable(String message) {
            return new Summary(message, List.of(), null, null, 0, null, 0, List.of());
        }
    }

    // 편도 값. 길을 못 찾았으면 숫자 칸은 null 이고 note 에 까닭을 적는다
    public record Leg(Long memberId, String name, boolean driver,
                      Integer km, Integer minutes, Integer toll, Integer fuel, String note) {

        static Leg missing(Car car, String note) {
            return new Leg(car.memberId(), car.name(), car.driver(), null, null, null, null, note);
        }
    }
}
