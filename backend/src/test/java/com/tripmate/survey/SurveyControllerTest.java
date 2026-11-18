package com.tripmate.survey;

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

// 예시 방(id 1)을 쓴다. 참여자 1 이 방장, 2·3 은 일반 참여자
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SurveyControllerTest {

    private static final String BODY = """
            { "activity": "INDOOR", "planning": "PLANNER", "hasCar": false, "separateDeparture": true,
              "mobilityLimited": true, "avoid": ["DRIVING"], "preferences": { "DETACHED": 5, "KARAOKE": 1 } }
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void 저장하면_설문완료로_바뀐다() throws Exception {
        mvc.perform(put("/api/trips/1/members/2/survey").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.DETACHED").value(5));

        mvc.perform(get("/api/trips/1"))
                .andExpect(jsonPath("$.members[1].surveyDone").value(true));
    }

    @Test
    void 이동제약은_본인과_방장만_본다() throws Exception {
        mvc.perform(put("/api/trips/1/members/2/survey").contentType(MediaType.APPLICATION_JSON).content(BODY));

        mvc.perform(get("/api/trips/1/members/2/survey").header("X-Member-Id", 2))
                .andExpect(jsonPath("$.mobilityLimited").value(true));
        mvc.perform(get("/api/trips/1/members/2/survey").header("X-Member-Id", 1))
                .andExpect(jsonPath("$.mobilityLimited").value(true));
        mvc.perform(get("/api/trips/1/members/2/survey").header("X-Member-Id", 3))
                .andExpect(jsonPath("$.mobilityLimited").doesNotExist());
    }

    @Test
    void 점수가_1에서_5를_벗어나면_400() throws Exception {
        var body = BODY.replace("\"DETACHED\": 5", "\"DETACHED\": 6");
        mvc.perform(put("/api/trips/1/members/2/survey").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 방에_없는_참여자면_404() throws Exception {
        mvc.perform(put("/api/trips/1/members/999/survey").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/1/members/2/survey").header("X-Member-Id", 999))
                .andExpect(status().isNotFound());
    }
}
