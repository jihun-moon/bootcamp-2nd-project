package com.tripmate.trip;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void 로그인하면_방마다_내가_안_한_일이_보인다() throws Exception {
        // 예시 방은 날짜가 있어서 설문만 남는다
        mvc.perform(get("/api/me/trips").session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$[0].todo[0]").value("survey"));
        mvc.perform(put("/api/trips/1/members/2/survey").session(TestLogin.as(mvc, "민지"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "activity": "BOTH", "planning": "OUTLINE", "hasCar": false, "separateDeparture": false,
                          "departure": null, "mobilityLimited": false, "avoid": [] }"""));
        mvc.perform(get("/api/me/trips").session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$[0].todo.length()").value(0));

        // 날짜 없는 방을 혼자 만들면 초대 → 설문, 설문을 하고 되는 날을 안 눌렀으면 날짜
        var solo = TestLogin.as(mvc, "혼자");
        var trip = mvc.perform(post("/api/trips").session(solo).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"속초 여행\" }")).andReturn().getResponse().getContentAsString();
        var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(trip);
        mvc.perform(get("/api/me/trips").session(solo))
                .andExpect(jsonPath("$[0].todo[0]").value("invite"))
                .andExpect(jsonPath("$[0].todo[1]").value("survey"));
        mvc.perform(put("/api/trips/" + node.get("id").asLong() + "/members/" + node.get("meId").asLong() + "/survey").session(solo)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "activity": "BOTH", "planning": "OUTLINE", "hasCar": false, "separateDeparture": false,
                          "departure": null, "mobilityLimited": false, "avoid": [] }"""));
        mvc.perform(get("/api/me/trips").session(solo))
                .andExpect(jsonPath("$[0].todo[0]").value("invite"))
                .andExpect(jsonPath("$[0].todo[1]").value("dates"));
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
