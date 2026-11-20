package com.tripmate.role;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

// 운전·회비 근거. 차마다 출발 동네 → 방 지역 길을 찾아 거리·시간·통행료·기름값을 내고,
// 운전 칸에 들어간 차만 모아 왕복으로 1인 교통비를 낸다. 총무가 회비를 걷을 때 근거로 쓴다
@Service
public class TravelService {

    // 이보다 오래 혼자 운전하면 교대할 사람을 정해 두라고 알린다
    private static final int LONG_DRIVE_MINUTES = 120;
    private static final int ROUND_TRIP = 2;
    private static final int WON_UNIT = 100;
    private static final int METERS_PER_KM = 1000;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MINUTES_PER_HOUR = 60;

    private final KakaoMapClient kakaoMapClient;
    private final FuelPriceClient fuelPriceClient;
    private final int kmPerLiter;

    public TravelService(KakaoMapClient kakaoMapClient, FuelPriceClient fuelPriceClient,
                         @Value("${tripmate.fuel.km-per-liter}") int kmPerLiter) {
        this.kakaoMapClient = kakaoMapClient;
        this.fuelPriceClient = fuelPriceClient;
        this.kmPerLiter = kmPerLiter;
    }

    public TravelDto.Summary estimate(TravelDto.Plan plan) {
        if (!kakaoMapClient.ready()) {
            return TravelDto.Summary.unavailable("카카오 REST 키가 없어 길을 찾지 못해요. secrets.properties 에 KAKAO_CLIENT_ID 를 넣어 주세요.");
        }
        if (plan.destination() == null || plan.destination().isBlank()) {
            return TravelDto.Summary.unavailable("방 지역이 비어 있어요. 방장이 지역을 넣으면 셀 수 있어요.");
        }
        if (plan.cars().isEmpty()) {
            return TravelDto.Summary.unavailable("차를 가져가는 사람이 아직 없어요. 설문에서 '차 가능'과 출발 동네를 적어 주세요.");
        }
        var to = kakaoMapClient.find(plan.destination());
        if (to.isEmpty()) {
            return TravelDto.Summary.unavailable("방 지역 '" + plan.destination() + "' 을 지도에서 찾지 못했어요.");
        }

        var price = fuelPriceClient.gasoline();
        var legs = plan.cars().stream().map(car -> leg(car, to.get(), price.wonPerLiter())).toList();
        var driving = legs.stream().filter(l -> l.driver() && l.km() != null).toList();

        var tips = new ArrayList<String>();
        Integer perPerson = null;
        if (driving.isEmpty()) {
            tips.add("운전 칸에 사람이 들어가면 1인 교통비를 셀게요.");
        } else {
            var total = driving.stream().mapToInt(l -> (l.fuel() + l.toll()) * ROUND_TRIP).sum();
            perPerson = roundUp((double) total / plan.memberCount());
        }
        driving.stream()
                .filter(l -> l.minutes() > LONG_DRIVE_MINUTES)
                .forEach(l -> tips.add(l.name() + " 차는 편도 " + duration(l.minutes()) + "이에요. 교대할 사람을 정해 두세요."));

        return new TravelDto.Summary(null, legs, perPerson, price.wonPerLiter(), price.source(), kmPerLiter, tips);
    }

    private TravelDto.Leg leg(TravelDto.Car car, KakaoMapClient.Point to, int wonPerLiter) {
        if (car.departure() == null) return TravelDto.Leg.missing(car, "설문에 출발 동네가 없어요");
        var from = kakaoMapClient.find(car.departure());
        if (from.isEmpty()) return TravelDto.Leg.missing(car, "출발 동네를 지도에서 찾지 못했어요");
        var drive = kakaoMapClient.drive(from.get(), to);
        if (drive.isEmpty()) return TravelDto.Leg.missing(car, "길을 찾지 못했어요");

        var meters = drive.get().meters();
        var fuel = roundUp((double) meters / METERS_PER_KM / kmPerLiter * wonPerLiter);
        return new TravelDto.Leg(car.memberId(), car.name(), car.driver(),
                Math.round((float) meters / METERS_PER_KM), Math.round((float) drive.get().seconds() / SECONDS_PER_MINUTE),
                drive.get().toll(), fuel, null);
    }

    // 회비는 100원 단위로 걷으니 올림한다
    private static int roundUp(double won) {
        return (int) Math.ceil(won / WON_UNIT) * WON_UNIT;
    }

    private static String duration(int minutes) {
        var rest = minutes % MINUTES_PER_HOUR;
        return (minutes / MINUTES_PER_HOUR) + "시간" + (rest == 0 ? "" : " " + rest + "분");
    }
}
