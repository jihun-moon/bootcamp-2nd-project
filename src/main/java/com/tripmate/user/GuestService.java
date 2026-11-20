package com.tripmate.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 이름만 쓰고 들어오는 손님(docs/19). 손님도 AppUser 로 만들어 세션에 로그인시켜서 CurrentUser·TripAccess 가 그대로 돈다.
// 다음 날 같은 휴대폰은 "로그인 기억하기" 쿠키로 다시 들어온다. 나중에 카카오로 로그인하면 손님으로 한 것이 옮겨진다(GuestUpgraded)
@Service
public class GuestService {

    // 카카오 로그인이 끝났을 때 "이 브라우저는 손님이었다"를 알려고 세션에 손님 loginId 를 남긴다
    public static final String GUEST_SESSION_KEY = "tripmate.guest";
    // 링크만 있으면 누구나 손님이 될 수 있어 같은 곳(IP)에서 한 시간에 40번까지. 방마다 30명 상한은 TripService.
    // 10번이었는데, 통신사 데이터는 여러 사람이 한 IP 를 나눠 써서(CGNAT) 같은 카페·같은 통신사 친구 열한 명째가 막힐 수 있었다
    private static final int PER_IP_PER_HOUR = 40;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final AppUserRepository appUserRepository;
    private final RememberMeServices rememberMeServices;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    // shortcut: 횟수 셈이 서버 메모리라 서버 한 대 기준. 여러 대로 늘리면 Redis 같은 곳으로
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public GuestService(AppUserRepository appUserRepository, RememberMeServices rememberMeServices) {
        this.appUserRepository = appUserRepository;
        this.rememberMeServices = rememberMeServices;
    }

    @Transactional
    public AppUser signIn(String name, HttpServletRequest request, HttpServletResponse response) {
        limit(request.getRemoteAddr());
        var user = appUserRepository.save(AppUser.guest(name.strip()));
        var auth = UsernamePasswordAuthenticationToken.authenticated(user.getLoginId(), null,
                AuthorityUtils.createAuthorityList("ROLE_GUEST"));
        // 세션 고정 막기: 로그인 상태를 넣기 전에 세션 id 를 바꾼다
        request.getSession(true);
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        rememberMeServices.loginSuccess(request, response, auth);
        request.getSession().setAttribute(GUEST_SESSION_KEY, user.getLoginId());
        return user;
    }

    private void limit(String ip) {
        var now = Instant.now();
        var times = recent.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) times.pollFirst();
            if (times.size() >= PER_IP_PER_HOUR) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 뒤 다시 해 주세요");
            }
            times.addLast(now);
        }
    }
}
