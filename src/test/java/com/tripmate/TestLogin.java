package com.tripmate;

import com.tripmate.user.AppUser;
import com.tripmate.user.AppUserRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.WebApplicationContextUtils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// 예시 방(id 1, TestTripData) 참여자: 1 재현(방장), 2 민지, 3 도윤, 4 서연, 5 하준, 6 지우, 7 예린, 8 시우.
// 카카오 로그인을 거친 것과 같은 세션(로그인 정보 = loginId)을 바로 만든다. 처음 보는 이름이면 계정만 새로 만든다(방에는 없음)
public final class TestLogin {

    private TestLogin() {
    }

    public static MockHttpSession as(MockMvc mvc, String name) throws Exception {
        var servletContext = mvc.perform(get("/api/auth/options")).andReturn().getRequest().getServletContext();
        var users = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext).getBean(AppUserRepository.class);
        var loginId = "test:" + name;
        users.findByLoginId(loginId).orElseGet(() -> users.save(new AppUser(loginId, name, null)));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(loginId, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
