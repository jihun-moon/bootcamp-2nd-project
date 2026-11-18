package com.tripmate.user;

import jakarta.persistence.*;

// 로그인한 사람. 방마다의 참여자(Member)와 따로 둔다. user 는 Postgres 예약어라 app_user
@Entity
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "kakao:12345" 처럼 어디로 로그인했는지까지 붙인다. 개발용 로그인("dev:민지")과 섞이지 않게
    @Column(unique = true, nullable = false, updatable = false)
    private String loginId;

    private String nickname;

    protected AppUser() {
    }

    public AppUser(String loginId, String nickname) {
        this.loginId = loginId;
        this.nickname = nickname;
    }

    public void rename(String nickname) {
        this.nickname = nickname;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getNickname() { return nickname; }
}
