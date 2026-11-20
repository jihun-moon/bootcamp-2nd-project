package com.tripmate.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KakaoUserService kakaoUserService) throws Exception {
        http
                // 세션 쿠키가 SameSite=Lax 라 다른 사이트의 POST 에는 쿠키가 안 붙고, API 는 JSON 만 받아
                // 다른 사이트 스크립트는 CORS 사전 요청에서 막힌다. 그래서 CSRF 토큰을 따로 두지 않는다
                .csrf(AbstractHttpConfigurer::disable)
                // 화면(Thymeleaf)은 누구나 열 수 있고, 데이터는 화면의 jQuery 가 /api 로 읽는다. 로그인 검사는 /api 에서 한다
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/options").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // API 는 로그인 화면으로 넘기지 않고 401 만 준다. 화면의 common.js 가 받아서 /login 으로 보낸다
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                // loginPage 를 주지 않으면 Spring Security 가 기본 로그인 화면을 /login 에 띄운다. 우리 화면(LoginPageController)을 쓴다
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(u -> u.userService(kakaoUserService))
                        .successHandler((req, res, a) -> res.sendRedirect("/login/done"))
                        .failureHandler((req, res, ex) -> res.sendRedirect("/login?error")))
                // CSP: 스크립트는 우리 서버와 jQuery CDN 만. 화면에 끼어든 <script> 는 돌지 않는다(XSS 를 한 겹 더 막음).
                // 글꼴·스타일은 cdnjs(Pretendard), 숙소 사진은 예약 사이트 https 주소라 img 는 https 전체.
                // style 'unsafe-inline' 은 jQuery .css() 와 막대 너비 같은 style 속성 때문. 스크립트에는 주지 않는다
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' https://ajax.googleapis.com; "
                                        + "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; font-src https://cdnjs.cloudflare.com; "
                                        + "img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/logout"))
                        .logoutSuccessHandler((req, res, a) -> res.setStatus(HttpStatus.NO_CONTENT.value())));
        return http.build();
    }
}
