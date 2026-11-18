package com.tripmate.trip;

import com.tripmate.role.Role;
import com.tripmate.role.RoleRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;

@Configuration
public class DemoTripLoader {

    // 메모리 DB 라 켤 때마다 비어 있다. 화면을 바로 볼 수 있게 예시 방 하나(id 1)를 넣는다.
    // 참여자는 계정 없이 넣고, 개발용 로그인(같은 이름)이 붙잡아 쓴다. 그래서 개발용 로그인을 켰을 때만 만든다
    @Bean
    @ConditionalOnProperty(name = "tripmate.dev-login", havingValue = "true")
    ApplicationRunner loadDemoTrip(TripRepository tripRepository, RoleRepository roleRepository) {
        return args -> {
            if (tripRepository.count() > 0) return;

            var trip = new Trip("겨울 펜션 2박 3일", "가평", LocalDate.of(2027, 1, 15), LocalDate.of(2027, 1, 17));
            trip.addMember(null, "재현", true);
            for (var name : List.of("민지", "도윤", "서연", "하준", "지우", "예린", "시우")) {
                trip.addMember(null, name, false);
            }
            tripRepository.save(trip);
            roleRepository.saveAll(Role.defaultsFor(trip));
        };
    }
}
