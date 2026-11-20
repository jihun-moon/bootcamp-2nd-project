package com.tripmate.trip;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 첫 화면 "내 여행방"과 초대 링크 미리보기(og)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyTripsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 참여한_방이_인원과_방장_여부와_같이_나온다() throws Exception {
        mvc.perform(get("/api/me/trips").session(TestLogin.as(mvc, "재현")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("겨울 펜션 2박 3일"))
                .andExpect(jsonPath("$[0].memberCount").value(8))
                .andExpect(jsonPath("$[0].host").value(true));
        mvc.perform(get("/api/me/trips").session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$[0].host").value(false));
    }

    @Test
    void 로그인_안_했으면_401() throws Exception {
        mvc.perform(get("/api/me/trips")).andExpect(status().isUnauthorized());
    }

    @Test
    void 초대_링크_미리보기에_방_이름이_들어간다() throws Exception {
        var trips = mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "재현"))).andReturn().getResponse().getContentAsString();
        var code = tools.jackson.databind.json.JsonMapper.builder().build().readTree(trips).get("inviteCode").asText();

        mvc.perform(get("/join/" + code))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("og:title\" content=\"&#39;겨울 펜션 2박 3일&#39; 여행에 초대받았어요")));
    }
}
