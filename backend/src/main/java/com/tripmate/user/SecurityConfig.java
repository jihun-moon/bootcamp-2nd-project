package com.tripmate.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KakaoUserService kakaoUserService,
                                            @Value("${tripmate.frontend-url}") String frontendUrl) throws Exception {
        http
                // 세션 쿠키가 SameSite=Lax 라 다른 사이트의 POST 에는 쿠키가 안 붙고, API 는 JSON 만 받아
                // 다른 사이트 스크립트는 CORS 사전 요청에서 막힌다. 그래서 CSRF 토큰을 따로 두지 않는다
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/options", "/api/dev/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // API 는 로그인 화면으로 넘기지 않고 401 만 준다. 화면(Next)이 받아서 /login 으로 보낸다
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(kakaoUserService))
                        .successHandler((req, res, a) -> res.sendRedirect(frontendUrl + "/login/done"))
                        .failureHandler((req, res, ex) -> res.sendRedirect(frontendUrl + "/login?error")))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.POST, "/api/logout"))
                        .logoutSuccessHandler((req, res, a) -> res.setStatus(HttpStatus.NO_CONTENT.value())));
        return http.build();
    }
}
