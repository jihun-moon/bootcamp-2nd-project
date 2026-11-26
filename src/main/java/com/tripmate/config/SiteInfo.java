package com.tripmate.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// 모든 화면에 서버 주소를 넣어 둔다. 카톡·문자 미리보기의 og:image 는 "https://…/img/og.png" 처럼 전체 주소여야 해서
// 상대 주소(@{/img/og.png})로는 안 뜬다. 배포에서 앞단 프록시를 거치면 FORWARD_HEADERS=framework 로 https 가 맞게 나온다
@ControllerAdvice
public class SiteInfo {

    @ModelAttribute("baseUrl")
    public String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
