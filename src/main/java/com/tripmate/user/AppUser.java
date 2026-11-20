package com.tripmate.user;

import jakarta.persistence.*;

import java.util.UUID;

// 로그인한 사람. 방마다의 참여자(Member)와 따로 둔다. user 는 Postgres 예약어라 app_user
@Entity
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "kakao:12345" 처럼 어디로 로그인했는지까지 붙인다. 손님은 "guest:무작위"(docs/19)
    @Column(unique = true, nullable = false, updatable = false)
    private String loginId;

    private String nickname;

    // 카카오 프로필 사진(선택 동의). 동의하지 않았거나 손님이면 null
    private String profileImageUrl;

    // 이름만 쓰고 들어온 손님. 방 만들기·방장·2탄은 못 한다. null = 카카오(기존 행에 NOT NULL 칸을 더하지 않게)
    private Boolean guest;

    protected AppUser() {
    }

    public AppUser(String loginId, String nickname, String profileImageUrl) {
        this.loginId = loginId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    static AppUser guest(String name) {
        var user = new AppUser("guest:" + UUID.randomUUID(), name, null);
        user.guest = true;
        return user;
    }

    // 카카오에서 이름이나 사진을 바꿨을 수 있어 로그인할 때마다 맞춘다
    public void refresh(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public boolean isGuest() { return Boolean.TRUE.equals(guest); }
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
}
