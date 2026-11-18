package com.tripmate.user;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// 카카오가 준 사용자 정보에서 id, 닉네임, 프로필 사진만 꺼내 AppUser 로 저장한다. 나머지 칸은 들고 다니지 않는다
@Service
public class KakaoUserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;

    public KakaoUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    record Profile(String nickname, String imageUrl) {
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) {
        var kakao = super.loadUser(request);
        var loginId = "kakao:" + kakao.getAttributes().get("id");
        var profile = profileOf(kakao.getAttributes());

        appUserRepository.findByLoginId(loginId).ifPresentOrElse(
                user -> user.refresh(profile.nickname(), profile.imageUrl()),
                () -> appUserRepository.save(new AppUser(loginId, profile.nickname(), profile.imageUrl())));

        // 이후 어디서든 authentication.getName() 이 loginId 가 되게 이름 칸을 바꿔 끼운다(CurrentUser)
        return new DefaultOAuth2User(kakao.getAuthorities(), Map.of("loginId", loginId), "loginId");
    }

    // 카카오 /v2/user/me 응답 예:
    // { "id": 12345,
    //   "kakao_account": { "profile": { "nickname": "재현", "thumbnail_image_url": "https://k.kakaocdn.net/.../t.jpg",
    //                                   "is_default_image": false } } }
    // 사진은 선택 동의라 없을 수 있고, 기본 이미지면 사진이 없는 것으로 본다
    @SuppressWarnings("unchecked")
    static Profile profileOf(Map<String, Object> attributes) {
        var account = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        var profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());

        var nickname = profile.get("nickname") instanceof String n && !n.isBlank() ? n : "카카오 사용자";
        var image = profile.get("thumbnail_image_url") instanceof String url ? url : null;
        var isDefault = Boolean.TRUE.equals(profile.get("is_default_image"));
        // 화면에 <img src> 로 넣으니 https 만 받는다
        var imageUrl = (!isDefault && image != null && image.startsWith("https://")) ? image : null;
        return new Profile(nickname, imageUrl);
    }
}
