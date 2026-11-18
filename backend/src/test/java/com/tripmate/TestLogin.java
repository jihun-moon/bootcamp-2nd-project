package com.tripmate;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예시 방(id 1) 참여자: 1 재현(방장), 2 민지, 3 도윤, 4 서연, 5 하준, 6 지우, 7 예린, 8 시우.
// 같은 이름으로 개발용 로그인하면 그 참여자가 되고, 돌려준 세션을 요청마다 붙인다
public final class TestLogin {

    private TestLogin() {
    }

    public static MockHttpSession as(MockMvc mvc, String name) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/dev/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"" + name + "\" }"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession();
    }
}
