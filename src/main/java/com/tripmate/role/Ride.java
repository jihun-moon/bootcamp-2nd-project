package com.tripmate.role;

import com.tripmate.trip.Member;
import jakarta.persistence.*;

// 누가 어느 차를 타나. 직접 고른 사람만 남긴다(안 고른 사람은 CarService 가 차마다 고르게 나눈다).
// driver 가 null 이면 "따로 가요"(기차·버스·따로 운전). 따로 가는 사람은 기름값을 나누지 않는다
@Entity
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member driver;

    protected Ride() {
    }

    Ride(Member member, Member driver) {
        this.member = member;
        this.driver = driver;
    }

    void changeTo(Member driver) {
        this.driver = driver;
    }

    Member getMember() { return member; }
    Member getDriver() { return driver; }
}
