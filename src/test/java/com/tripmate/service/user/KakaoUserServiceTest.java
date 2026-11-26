package com.tripmate.service.user;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoUserServiceTest {

    private static Map<String, Object> kakao(Map<String, Object> profile) {
        return Map.of("id", 12345L, "kakao_account", Map.of("profile", profile));
    }

    @Test
    void 닉네임과_사진을_꺼낸다() {
        var profile = KakaoUserService.profileOf(kakao(Map.of(
                "nickname", "재현", "thumbnail_image_url", "https://k.kakaocdn.net/t.jpg", "is_default_image", false)));
        assertThat(profile).isEqualTo(new KakaoUserService.Profile("재현", "https://k.kakaocdn.net/t.jpg"));
    }

    @Test
    void 사진에_동의하지_않았거나_기본_이미지면_사진은_없다() {
        assertThat(KakaoUserService.profileOf(kakao(Map.of("nickname", "민지"))).imageUrl()).isNull();
        assertThat(KakaoUserService.profileOf(kakao(Map.of(
                "nickname", "민지", "thumbnail_image_url", "https://k.kakaocdn.net/d.jpg", "is_default_image", true)))
                .imageUrl()).isNull();
    }

    @Test
    void https_가_아닌_사진_주소는_버린다() {
        assertThat(KakaoUserService.profileOf(kakao(Map.of(
                "nickname", "도윤", "thumbnail_image_url", "http://k.kakaocdn.net/t.jpg"))).imageUrl()).isNull();
    }

    @Test
    void 닉네임이_없으면_기본_이름() {
        assertThat(KakaoUserService.profileOf(Map.of("id", 1L)).nickname()).isEqualTo("카카오 사용자");
    }
}
