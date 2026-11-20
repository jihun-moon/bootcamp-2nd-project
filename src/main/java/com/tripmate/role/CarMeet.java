package com.tripmate.role;

import com.tripmate.trip.Member;
import jakarta.persistence.*;

import java.time.LocalTime;

// 차마다 태우는 곳·시간. 사는 동네가 다르면 차마다 가까운 곳에서 모인다(방 전체 모이는 곳과 따로)
@Entity
public class CarMeet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true)
    private Member driver;

    private LocalTime meetTime;

    @Column(length = 40)
    private String meetPlace;

    protected CarMeet() {
    }

    CarMeet(Member driver) {
        this.driver = driver;
    }

    void change(LocalTime time, String place) {
        this.meetTime = time;
        this.meetPlace = place;
    }

    Member getDriver() { return driver; }
    LocalTime getMeetTime() { return meetTime; }
    String getMeetPlace() { return meetPlace; }
}
