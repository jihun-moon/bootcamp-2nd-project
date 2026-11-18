package com.tripmate.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final CurrentUser currentUser;
    private final boolean kakaoReady;
    private final boolean devLogin;

    public AuthController(CurrentUser currentUser,
                          @Value("${spring.security.oauth2.client.registration.kakao.client-id}") String kakaoClientId,
                          @Value("${tripmate.dev-login}") boolean devLogin) {
        this.currentUser = currentUser;
        this.kakaoReady = !"unset".equals(kakaoClientId);
        this.devLogin = devLogin;
    }

    public record Me(Long id, String nickname) {
    }

    public record Options(boolean kakao, boolean dev) {
    }

    // GET: /api/me   로그인 안 했으면 401
    @GetMapping("/me")
    public ResponseEntity<Me> me() {
        var user = currentUser.get();
        return ResponseEntity.ok(new Me(user.getId(), user.getNickname()));
    }

    // GET: /api/auth/options   로그인 화면이 어떤 버튼을 보여 줄지 고른다
    @GetMapping("/auth/options")
    public ResponseEntity<Options> options() {
        return ResponseEntity.ok(new Options(kakaoReady, devLogin));
    }
}
