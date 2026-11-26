package com.tripmate.controller.trip;

import com.tripmate.TestLogin;
import com.tripmate.component.TripEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 커밋 뒤에 알림이 나가는지 보려고 일부러 @Transactional 을 걸지 않는다.
// 배정은 끝에 미배정으로 되돌리고, 참여자 8 의 설문(차 없음)은 남는다. 다른 테스트 결과를 바꾸지 않는 값이다
@SpringBootTest
@AutoConfigureMockMvc
class TripEventsTest {

    @Autowired
    MockMvc mvc;

    // 8 시우
    MockHttpSession siwoo;

    @BeforeEach
    void login() throws Exception {
        siwoo = TestLogin.as(mvc, "시우");
    }

    @Test
    void 저장이_커밋되면_구독자에게_알린다() throws Exception {
        var stream = mvc.perform(get("/api/trips/1/events").session(siwoo))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();

        mvc.perform(put("/api/trips/1/members/8/survey").session(siwoo).contentType(MediaType.APPLICATION_JSON).content("""
                { "activity": "BOTH", "planning": "OUTLINE", "hasCar": false, "separateDeparture": false,
                  "mobilityLimited": false, "avoid": [] }
                """)).andExpect(status().isOk());
        mvc.perform(patch("/api/trips/1/roles/assignments/8").session(siwoo).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"roleId\": 5, \"version\": null }")).andExpect(status().isOk());
        mvc.perform(patch("/api/trips/1/roles/assignments/8").session(siwoo).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"roleId\": null, \"version\": 0 }")).andExpect(status().isOk());

        assertThat(stream.getContentAsString())
                .contains(":connected")
                .contains("event:change\ndata:members")
                .contains("event:change\ndata:roles");
    }

    @Test
    void 막힌_이동은_알리지_않는다() throws Exception {
        var stream = mvc.perform(get("/api/trips/1/events").session(siwoo)).andReturn().getResponse();

        mvc.perform(patch("/api/trips/1/roles/assignments/8").session(siwoo).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"roleId\": 5, \"version\": 7 }")).andExpect(status().isConflict());

        assertThat(stream.getContentAsString()).doesNotContain("event:change");
    }

    @Autowired
    TripEvents tripEvents;

    @Test
    void 조용할_때도_ping_을_보낸다() throws Exception {
        var stream = mvc.perform(get("/api/trips/1/events").session(siwoo)).andReturn().getResponse();

        tripEvents.ping();

        assertThat(stream.getContentAsString()).contains("event:ping");
    }

    @Test
    void 없는_방은_404() throws Exception {
        mvc.perform(get("/api/trips/999/events").session(siwoo)).andExpect(status().isNotFound());
    }
}
