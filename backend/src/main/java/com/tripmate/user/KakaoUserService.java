package com.tripmate.user;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

// 카카오가 준 사용자 정보에서 id 와 닉네임만 꺼내 AppUser 로 저장한다. 나머지 칸은 들고 다니지 않는다
@Service
public class KakaoUserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;

    public KakaoUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    // 카카오 /v2/user/me 응답 예:
    // { "id": 12345, "properties": { "nickname": "재현" }, "kakao_account": { "profile": { "nickname": "재현" } } }
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) {
        var kakao = super.loadUser(request);
        var loginId = "kakao:" + kakao.getAttributes().get("id");
        var nickname = nicknameOf(kakao.getAttributes());

        appUserRepository.findByLoginId(loginId).ifPresentOrElse(
                user -> user.rename(nickname),
                () -> appUserRepository.save(new AppUser(loginId, nickname)));

        // 이후 어디서든 authentication.getName() 이 loginId 가 되게 이름 칸을 바꿔 끼운다(CurrentUser)
        return new DefaultOAuth2User(kakao.getAuthorities(), Map.of("loginId", loginId, "nickname", nickname), "loginId");
    }

    @SuppressWarnings("unchecked")
    private static String nicknameOf(Map<String, Object> attributes) {
        var properties = (Map<String, Object>) attributes.getOrDefault("properties", new HashMap<>());
        var nickname = properties.get("nickname");
        return nickname == null ? "카카오 사용자" : nickname.toString();
    }
}
