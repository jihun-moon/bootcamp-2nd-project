package com.tripmate;

import com.tripmate.entity.role.Role;
import com.tripmate.entity.trip.Trip;
import com.tripmate.entity.user.AppUser;
import com.tripmate.repository.role.RoleRepository;
import com.tripmate.repository.trip.TripRepository;
import com.tripmate.repository.user.AppUserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;

// 시험에서만 쓰는 예시 방(id 1). 참여자마다 계정("test:이름")을 붙여 두고, TestLogin 이 그 계정으로 세션을 만든다.
// 서비스(src/main)에는 예시 방도 개발용 로그인도 없다
@Configuration
public class TestTripData {

    @Bean
    ApplicationRunner loadTestTrip(TripRepository tripRepository, RoleRepository roleRepository, AppUserRepository appUserRepository) {
        return args -> {
            if (tripRepository.count() > 0) return;

            var trip = new Trip("겨울 펜션 2박 3일", "가평", LocalDate.of(2027, 1, 15), LocalDate.of(2027, 1, 17));
            var names = List.of("재현", "민지", "도윤", "서연", "하준", "지우", "예린", "시우");
            for (var name : names) {
                var user = appUserRepository.save(new AppUser("test:" + name, name, null));
                trip.addMember(user, name, name.equals("재현"));
            }
            tripRepository.save(trip);
            roleRepository.saveAll(Role.defaultsFor(trip));
        };
    }
}
