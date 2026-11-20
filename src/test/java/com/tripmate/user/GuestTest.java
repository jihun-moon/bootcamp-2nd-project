package com.tripmate.user;

import com.tripmate.TestLogin;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인 없이 이름만 쓰고 참여(docs/19). 예시 방(id 1) 8명, 방장 재현
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    // 같은 곳(IP)에서 한 시간 40번 제한이 시험끼리 섞이지 않게 시험마다 다른 주소
    private static final AtomicInteger IP = new AtomicInteger(10);

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    AppUserRepository appUserRepository;

    private String inviteCode() throws Exception {
        var body = mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "재현"))).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("inviteCode").asString();
    }

    private MvcResult guest(String code, String name, String ip) throws Exception {
        return mvc.perform(post("/api/invites/" + code + "/guest").with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content("{ \"name\": \"" + name + "\" }")).andReturn();
    }

    private String freshIp() {
        return "10.0.0." + IP.incrementAndGet();
    }

    @Test
    void 이름만_쓰면_방에_들어가_설문까지_하고_남의_방은_404() throws Exception {
        var result = guest(inviteCode(), "민재", freshIp());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var session = (MockHttpSession) result.getRequest().getSession();
        assertThat(result.getResponse().getCookie("tm_remember")).isNotNull();

        var trip = JSON.readTree(mvc.perform(get("/api/trips/1").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(trip.get("members").size()).isEqualTo(9);
        var meId = trip.get("meId").asLong();
        mvc.perform(put("/api/trips/1/members/" + meId + "/survey").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "activity": "BOTH", "planning": "OUTLINE", "hasCar": false, "separateDeparture": false,
                          "departure": null, "mobilityLimited": false, "avoid": [] }""")).andExpect(status().isOk());
        mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.guest").value(true));

        // 다른 방(재현이 새로 만든 방)은 모른다
        var other = JSON.readTree(mvc.perform(post("/api/trips").session(TestLogin.as(mvc, "재현"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"title\": \"딴 방\" }")).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/trips/" + other.get("id").asLong()).session(session)).andExpect(status().isNotFound());
    }

    @Test
    void 손님은_방을_만들거나_방장이_될_수_없다() throws Exception {
        var result = guest(inviteCode(), "민재", freshIp());
        var session = (MockHttpSession) result.getRequest().getSession();
        mvc.perform(post("/api/trips").session(session).contentType(MediaType.APPLICATION_JSON).content("{ \"title\": \"내 방\" }"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/1/next").session(session)).andExpect(status().isForbidden());
        var guestId = JSON.readTree(mvc.perform(get("/api/trips/1").session(session)).andReturn().getResponse().getContentAsString())
                .get("meId").asLong();
        mvc.perform(put("/api/trips/1/host").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"memberId\": " + guestId + " }")).andExpect(status().isBadRequest());
    }

    @Test
    void 세션이_끝나도_기억_쿠키로_다시_나로_들어온다() throws Exception {
        var result = guest(inviteCode(), "예진", freshIp());
        Cookie remember = result.getResponse().getCookie("tm_remember");
        mvc.perform(get("/api/me").cookie(remember))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("예진"));
    }

    @Test
    void 같은_이름은_구분해_붙이고_카카오로_로그인하면_손님_자리가_옮겨진다() throws Exception {
        var result = guest(inviteCode(), "민지", freshIp());   // 예시 방에 민지가 이미 있다
        var session = (MockHttpSession) result.getRequest().getSession();
        mvc.perform(get("/api/trips/1").session(session)).andExpect(jsonPath("$.members[8].name").value("민지(2)"));
        var guestLoginId = (String) session.getAttribute(GuestService.GUEST_SESSION_KEY);

        TestLogin.as(mvc, "카카오민지");   // 카카오 계정(test:카카오민지)을 만든다
        events.publishEvent(new GuestUpgraded(guestLoginId, "test:카카오민지"));
        mvc.perform(get("/api/me/trips").session(TestLogin.as(mvc, "카카오민지"))).andExpect(jsonPath("$[0].id").value(1));
        assertThat(appUserRepository.findByLoginId(guestLoginId)).isPresent();
    }

    @Test
    void 잘못된_이름_틀린_코드_form_전송은_막는다() throws Exception {
        var code = inviteCode();
        assertThat(guest(code, " ", freshIp()).getResponse().getStatus()).isEqualTo(400);
        assertThat(guest(code, "가".repeat(21), freshIp()).getResponse().getStatus()).isEqualTo(400);
        assertThat(guest("없는코드", "민재", freshIp()).getResponse().getStatus()).isEqualTo(404);
        mvc.perform(post("/api/invites/" + code + "/guest").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("name", "민재"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void 같은_곳에서_한_시간_41번째는_429() throws Exception {
        // 통신사 데이터는 여럿이 한 IP 를 나눠 쓴다. 한 방(30명 상한)을 꽉 채우고 다른 방까지 40번은 들어오고 41번째에 막힌다
        var ip = freshIp();
        var first = inviteCode();
        for (var i = 0; i < 30; i++) assertThat(guest(first, "손님" + i, ip).getResponse().getStatus()).isEqualTo(200);
        var body = mvc.perform(post("/api/trips").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"둘째 방\" }")).andReturn().getResponse().getContentAsString();
        var second = JSON.readTree(body).get("inviteCode").asString();
        for (var i = 30; i < 40; i++) assertThat(guest(second, "손님" + i, ip).getResponse().getStatus()).isEqualTo(200);
        assertThat(guest(second, "손님40", ip).getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void 한_방_손님은_30명까지() throws Exception {
        var code = inviteCode();
        for (var i = 0; i < 30; i++) assertThat(guest(code, "손님" + i, freshIp()).getResponse().getStatus()).isEqualTo(200);
        assertThat(guest(code, "서른한번째", freshIp()).getResponse().getStatus()).isEqualTo(429);
        mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "재현"))).andExpect(jsonPath("$.members", hasSize(38)));
    }
}
