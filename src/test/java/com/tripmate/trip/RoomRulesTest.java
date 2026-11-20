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
}
