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

    // 카카오 프로필 사진(선택 동의). 동의하지 않았거나 개발용 로그인이면 null
    private String profileImageUrl;

    protected AppUser() {
    }

    public AppUser(String loginId, String nickname, String profileImageUrl) {
        this.loginId = loginId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    // 카카오에서 이름이나 사진을 바꿨을 수 있어 로그인할 때마다 맞춘다
    public void refresh(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
}
