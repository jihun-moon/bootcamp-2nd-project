package com.tripmate.user;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    // "로그인 기억하기": 손님은 다음 날 같은 휴대폰으로 다시 오면 나로 들어와야 하고(docs/19), 카카오도 세션이 끝나도 이어진다.
    // 토큰은 persistent_logins 표에(해시가 아니라 무작위 값을 쿠키·표에 두고 쓸 때마다 바꾼다, 옛 값이 다시 오면 도둑맞은 것으로 보고 지움)
    @Bean
    PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        var repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    // 기억 쿠키로 다시 들어올 때 loginId 로 사람을 찾는다. 손님은 ROLE_GUEST
    @Bean
    UserDetailsService userDetailsService(AppUserRepository appUserRepository) {
        return loginId -> appUserRepository.findByLoginId(loginId)
                .map(u -> User.withUsername(loginId).password("").authorities(u.isGuest() ? "ROLE_GUEST" : "ROLE_USER").build())
                .orElseThrow(() -> new UsernameNotFoundException(loginId));
    }

    @Bean
    RememberMeServices rememberMeServices(UserDetailsService userDetailsService, PersistentTokenRepository tokens,
                                          @Value("${tripmate.remember-key:}") String key,
                                          @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        // 키가 없으면 켤 때마다 새로 만든다. 기억 토큰은 표에서 확인하므로 키가 바뀌어도 이미 받은 쿠키는 그대로 쓴다
        var services = new PersistentTokenBasedRememberMeServices(key.isBlank() ? UUID.randomUUID().toString() : key,
                userDetailsService, tokens);
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds((int) Duration.ofDays(60).toSeconds());
        services.setCookieName("tm_remember");
        services.setUseSecureCookie(secure);
        return services;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KakaoUserService kakaoUserService, RememberMeServices rememberMeServices,
                                            PersistentTokenRepository tokens, ApplicationEventPublisher events) throws Exception {
        http
                // 세션 쿠키가 SameSite=Lax 라 다른 사이트의 POST 에는 쿠키가 안 붙고, API 는 JSON 만 받아
                // 다른 사이트 스크립트는 CORS 사전 요청에서 막힌다. 그래서 CSRF 토큰을 따로 두지 않는다
                .csrf(AbstractHttpConfigurer::disable)
                // 화면(Thymeleaf)은 누구나 열 수 있고, 데이터는 화면의 jQuery 가 /api 로 읽는다. 로그인 검사는 /api 에서 한다
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/options").permitAll()
                        // 손님 참여는 로그인 전에 부른다. JSON 만 받아(@RequestBody) 다른 사이트의 form POST 는 415 라 로그인 CSRF 가 안 된다
                        .requestMatchers(HttpMethod.POST, "/api/invites/*/guest").permitAll()
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
                        // 손님이던 브라우저가 카카오로 로그인하면 손님으로 한 것을 옮기고 손님 기억 토큰은 지운다
                        .successHandler((req, res, a) -> {
                            var session = req.getSession(false);
                            var guest = session == null ? null : (String) session.getAttribute(GuestService.GUEST_SESSION_KEY);
                            if (guest != null) {
                                session.removeAttribute(GuestService.GUEST_SESSION_KEY);
                                events.publishEvent(new GuestUpgraded(guest, a.getName()));
                                tokens.removeUserTokens(guest);
                            }
                            res.sendRedirect("/login/done");
                        })
                        // 실패 까닭을 남긴다(키 값은 안 찍힌다). 예: invalid_client = 콘솔 Client Secret 이 켜져 있는데 서버에 없음,
                        // authorization_request_not_found = 로그인 도중 서버가 다시 켜져 세션이 사라짐
                        .failureHandler((req, res, ex) -> {
                            log.warn("카카오 로그인 실패: {}", ex.getMessage());
                            res.sendRedirect("/login?error");
                        }))
                // CSP: 스크립트는 우리 서버와 jQuery CDN 만. 화면에 끼어든 <script> 는 돌지 않는다(XSS 를 한 겹 더 막음).
                // 글꼴·스타일은 cdnjs(Pretendard), 숙소 사진은 예약 사이트 https 주소라 img 는 https 전체.
                // style 'unsafe-inline' 은 jQuery .css() 와 막대 너비 같은 style 속성 때문. 스크립트에는 주지 않는다
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' https://ajax.googleapis.com; "
                                        + "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; font-src https://cdnjs.cloudflare.com; "
                                        + "img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .rememberMe(r -> r.rememberMeServices(rememberMeServices))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/logout"))
                        .logoutSuccessHandler((req, res, a) -> res.setStatus(HttpStatus.NO_CONTENT.value())));
        return http.build();
    }
}
