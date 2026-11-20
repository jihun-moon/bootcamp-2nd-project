package com.tripmate.trip;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 방 규칙: 운전자 기름값, 전날 불참 예약금, 술값. 예시 방(id 1) 방장은 재현
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomRulesTest {

    private static final String RULES = "{ \"driverPaysFuel\": false, \"refundNoShow\": false, \"drinksSeparate\": true }";

    @Autowired
    MockMvc mvc;

    @Test
    void 처음에는_아무것도_안_정해져_있다() throws Exception {
        mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$.rules.driverPaysFuel").doesNotExist())
                .andExpect(jsonPath("$.rules.drinksSeparate").doesNotExist());
    }

    @Test
    void 방장이_정하면_참여자_화면에도_보인다() throws Exception {
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "재현"))
                        .contentType(MediaType.APPLICATION_JSON).content(RULES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.driverPaysFuel").value(false));

        mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$.rules.driverPaysFuel").value(false))
                .andExpect(jsonPath("$.rules.refundNoShow").value(false))
                .andExpect(jsonPath("$.rules.drinksSeparate").value(true));
    }

    @Test
    void 참여자는_규칙을_못_바꾼다() throws Exception {
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "민지"))
                        .contentType(MediaType.APPLICATION_JSON).content(RULES))
                .andExpect(status().isForbidden());
    }

    @Test
    void 모일_시간과_장소는_방장이_적고_모두에게_보인다() throws Exception {
        var meet = "{ \"time\": \"08:00\", \"place\": \" 합정역 2번 출구 \" }";
        mvc.perform(put("/api/trips/1/meet").session(TestLogin.as(mvc, "민지"))
                .contentType(MediaType.APPLICATION_JSON).content(meet)).andExpect(status().isForbidden());
        mvc.perform(put("/api/trips/1/meet").session(TestLogin.as(mvc, "재현"))
                .contentType(MediaType.APPLICATION_JSON).content(meet)).andExpect(status().isOk());
        mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$.meet.time").value(org.hamcrest.Matchers.startsWith("08:00")))
                .andExpect(jsonPath("$.meet.place").value("합정역 2번 출구"));
        mvc.perform(put("/api/trips/1/meet").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"time\": null, \"place\": \"" + "가".repeat(41) + "\" }")).andExpect(status().isBadRequest());
    }

    @Test
    void 방장은_방_이름과_지역을_고치고_방장을_넘길_수_있다() throws Exception {
        mvc.perform(put("/api/trips/1").session(TestLogin.as(mvc, "민지")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"강릉 우정 여행\", \"destination\": \"강릉\" }")).andExpect(status().isForbidden());
        mvc.perform(put("/api/trips/1").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \" 강릉 우정 여행 \", \"destination\": \"강릉\" }"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("강릉 우정 여행"))
                .andExpect(jsonPath("$.destination").value("강릉"));
        mvc.perform(put("/api/trips/1").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"\" }")).andExpect(status().isBadRequest());

        // 재현이 민지(2)에게 방장을 넘기면 재현은 규칙을 못 바꾸고 민지는 바꾼다
        mvc.perform(put("/api/trips/1/host").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"memberId\": 2 }")).andExpect(status().isOk());
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content(RULES)).andExpect(status().isForbidden());
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "민지")).contentType(MediaType.APPLICATION_JSON)
                .content(RULES)).andExpect(status().isOk());
        mvc.perform(put("/api/trips/1/host").session(TestLogin.as(mvc, "민지")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"memberId\": 2 }")).andExpect(status().isBadRequest());
    }
}
