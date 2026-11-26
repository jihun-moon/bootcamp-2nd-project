package com.tripmate.entity.user;

import jakarta.persistence.*;

import java.time.Instant;

// Spring Security "로그인 기억하기"(JdbcTokenRepositoryImpl)가 읽고 쓰는 표. 코드에서 직접 쓰지 않는다.
// 표 꼴을 JPA 로 두는 까닭: 저장소가 표를 만들게 하면(setCreateTableOnStartup) 두 번째 켤 때 "이미 있음"으로 멈춘다.
// 토큰은 쓸 때마다 바뀌고(series 는 그대로), 옛 토큰이 다시 오면 도둑맞은 것으로 보고 그 사람 토큰을 다 지운다
@Entity
@Table(name = "persistent_logins")
public class PersistentLogin {

    @Id
    @Column(length = 64)
    private String series;

    @Column(length = 64, nullable = false)
    private String username;

    @Column(length = 64, nullable = false)
    private String token;

    @Column(name = "last_used", nullable = false)
    private Instant lastUsed;

    protected PersistentLogin() {
    }
}
