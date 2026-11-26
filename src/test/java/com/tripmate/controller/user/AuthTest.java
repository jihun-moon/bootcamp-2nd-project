package com.tripmate.controller.user;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 로그인_안_하면_API_는_401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/trips/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON).content("{ \"title\": \"x\" }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인하면_내_정보와_내_방이_보인다() throws Exception {
        var minji = TestLogin.as(mvc, "민지");
        mvc.perform(get("/api/me").session(minji)).andExpect(jsonPath("$.nickname").value("민지"));
        mvc.perform(get("/api/trips/1").session(minji))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meId").value(2))
                .andExpect(jsonPath("$.inviteCode").isString());
    }

    @Test
    void 참여자가_아니면_방이_있는지도_모른다() throws Exception {
        var stranger = TestLogin.as(mvc, "낯선사람");
        mvc.perform(get("/api/trips/1").session(stranger)).andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/1/roles").session(stranger)).andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/1/stays").session(stranger)).andExpect(status().isNotFound());
    }

    @Test
    void 방을_만들면_방장이고_초대_링크로_들어온_사람은_참여자가_된다() throws Exception {
        var host = TestLogin.as(mvc, "새방장");
        var created = mvc.perform(post("/api/trips").session(host).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"봄 캠핑\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members[0].name").value("새방장"))
                .andExpect(jsonPath("$.members[0].host").value(true))
                .andReturn().getResponse().getContentAsString();
        var json = JsonMapper.builder().build().readTree(created);
        var tripId = json.get("id").asLong();
        var code = json.get("inviteCode").asString();

        var friend = TestLogin.as(mvc, "새친구");
        mvc.perform(get("/api/trips/" + tripId).session(friend)).andExpect(status().isNotFound());
        mvc.perform(post("/api/invites/" + code).session(friend))
                .andExpect(jsonPath("$.tripId").value(tripId));
        // 두 번 눌러도 한 번만 들어간다
        mvc.perform(post("/api/invites/" + code).session(friend)).andExpect(status().isOk());
        mvc.perform(get("/api/trips/" + tripId).session(friend))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[1].host").value(false));

        mvc.perform(post("/api/invites/없는코드").session(friend)).andExpect(status().isNotFound());
    }

    @Test
    void 로그아웃하면_다시_401() throws Exception {
        var minji = TestLogin.as(mvc, "민지");
        mvc.perform(post("/api/logout").session(minji)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(minji)).andExpect(status().isUnauthorized());
    }
}
