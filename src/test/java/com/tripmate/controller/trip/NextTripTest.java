package com.tripmate.controller.trip;

import com.tripmate.TestLogin;
import com.tripmate.entity.trip.Trip;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 같은 멤버로 다음 여행. 예시 방(id 1) 8명, 방장 재현
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NextTripTest {

    @Autowired
    MockMvc mvc;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private long nextOf(String who, int status) throws Exception {
        var body = mvc.perform(post("/api/trips/1/next").session(TestLogin.as(mvc, who)))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).path("tripId").asLong();
    }

    @Test
    void 누른_사람이_방장이고_8명이_그대로_들어간다() throws Exception {
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "재현"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"driverPaysFuel\": false, \"refundNoShow\": true, \"drinksSeparate\": true }"));

        var id = nextOf("민지", 201);

        mvc.perform(get("/api/trips/" + id).session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$.members.length()").value(8))
                .andExpect(jsonPath("$.members[0].name").value("민지"))
                .andExpect(jsonPath("$.members[0].host").value(true))
                .andExpect(jsonPath("$.members[1].host").value(false))
                .andExpect(jsonPath("$.members[0].surveyDone").value(false))
                .andExpect(jsonPath("$.title").value("겨울 펜션 2박 3일 2탄"))
                .andExpect(jsonPath("$.startDate").doesNotExist())
                .andExpect(jsonPath("$.rules.driverPaysFuel").value(false))
                .andExpect(jsonPath("$.rules.refundNoShow").value(true));

        // 다른 사람 첫 화면 "내 여행방"에도 바로 보인다
        mvc.perform(get("/api/me/trips").session(TestLogin.as(mvc, "시우")))
                .andExpect(jsonPath("$[0].id").value(id));
        // 역할 칸도 같이 생긴다
        mvc.perform(get("/api/trips/" + id + "/roles").session(TestLogin.as(mvc, "시우")))
                .andExpect(status().isOk());
    }

    @Test
    void 두_번째로_누르면_새로_안_만들고_그_방으로_간다() throws Exception {
        var first = nextOf("재현", 201);
        var second = nextOf("도윤", 200);
        assertThat(second).isEqualTo(first);

        mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "예린")))
                .andExpect(jsonPath("$.nextTripId").value(first));
    }

    @Test
    void 새_방에서_빠진_사람은_지난_방_단추로_다시_못_들어온다() throws Exception {
        var next = nextOf("재현", 201);
        var minjiInNext = JSON.readTree(mvc.perform(get("/api/trips/" + next).session(TestLogin.as(mvc, "민지")))
                .andReturn().getResponse().getContentAsString()).get("meId").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/trips/" + next + "/members/" + minjiInNext)
                .session(TestLogin.as(mvc, "재현"))).andExpect(status().isNoContent());
        nextOf("민지", 403);
        mvc.perform(get("/api/trips/" + next).session(TestLogin.as(mvc, "민지"))).andExpect(status().isNotFound());
    }

    @Test
    void 방에_없는_사람은_못_만든다() throws Exception {
        mvc.perform(post("/api/trips/1/next").session(TestLogin.as(mvc, "모르는사람")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 이름은_2탄_3탄으로_이어진다() {
        assertThat(Trip.sequelOf("가평 2박 3일")).isEqualTo("가평 2박 3일 2탄");
        assertThat(Trip.sequelOf("가평 2박 3일 2탄")).isEqualTo("가평 2박 3일 3탄");
        assertThat(Trip.sequelOf("MT 9탄")).isEqualTo("MT 10탄");
        assertThat(Trip.sequelOf("가".repeat(100))).hasSize(100);
    }
}
