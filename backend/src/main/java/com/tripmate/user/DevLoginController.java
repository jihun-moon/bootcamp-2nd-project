package com.tripmate.user;

import com.tripmate.trip.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// 카카오 키 없이 로컬에서 예시 방 참여자로 들어가 보는 길. tripmate.dev-login=true 일 때만 빈이 생긴다(기본 꺼짐)
@RestController
@ConditionalOnProperty(name = "tripmate.dev-login", havingValue = "true")
public class DevLoginController {

    private final AppUserRepository appUserRepository;
    private final MemberRepository memberRepository;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public DevLoginController(AppUserRepository appUserRepository, MemberRepository memberRepository) {
        this.appUserRepository = appUserRepository;
        this.memberRepository = memberRepository;
    }

    // { "name": "민지" }
    public record Request(@NotBlank @Size(max = 20) String name) {
    }

    // POST: /api/dev/login  body = { "name": "민지" }
    // 이름이 같은, 아직 아무도 아닌 예시 방 참여자를 이 사용자에게 붙인다
    @PostMapping("/api/dev/login")
    @Transactional
    public ResponseEntity<Void> login(@Valid @RequestBody Request req, HttpServletRequest request, HttpServletResponse response) {
        var loginId = "dev:" + req.name();
        var user = appUserRepository.findByLoginId(loginId)
                .orElseGet(() -> appUserRepository.save(new AppUser(loginId, req.name())));
        memberRepository.findByNameAndUserIsNull(req.name()).forEach(m -> m.linkTo(user));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(loginId, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }
}
