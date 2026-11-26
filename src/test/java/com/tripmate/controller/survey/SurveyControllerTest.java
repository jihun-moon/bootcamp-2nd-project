package com.tripmate.controller.survey;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예시 방(id 1). 1 재현(방장), 2 민지, 3 도윤
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

    private ResultActions save(MockHttpSession session, int memberId, String body) throws Exception {
        return mvc.perform(put("/api/trips/1/members/" + memberId + "/survey").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 저장하면_설문완료로_바뀐다() throws Exception {
        var minji = TestLogin.as(mvc, "민지");
        save(minji, 2, BODY).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.DETACHED").value(5));

        mvc.perform(get("/api/trips/1").session(minji))
                .andExpect(jsonPath("$.members[1].surveyDone").value(true));
    }

    @Test
    void 이동제약은_본인과_방장만_본다() throws Exception {
        save(TestLogin.as(mvc, "민지"), 2, BODY);

        mvc.perform(get("/api/trips/1/members/2/survey").session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$.mobilityLimited").value(true));
        mvc.perform(get("/api/trips/1/members/2/survey").session(TestLogin.as(mvc, "재현")))
                .andExpect(jsonPath("$.mobilityLimited").value(true));
        mvc.perform(get("/api/trips/1/members/2/survey").session(TestLogin.as(mvc, "도윤")))
                .andExpect(jsonPath("$.mobilityLimited").doesNotExist());
    }

    @Test
    void 남의_설문은_쓸_수_없다() throws Exception {
        save(TestLogin.as(mvc, "민지"), 3, BODY).andExpect(status().isForbidden());
    }

    @Test
    void 점수가_1에서_5를_벗어나면_400() throws Exception {
        save(TestLogin.as(mvc, "민지"), 2, BODY.replace("\"DETACHED\": 5", "\"DETACHED\": 6"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 방에_없는_참여자_설문은_404() throws Exception {
        save(TestLogin.as(mvc, "민지"), 2, BODY);
        mvc.perform(get("/api/trips/1/members/999/survey").session(TestLogin.as(mvc, "민지")))
                .andExpect(status().isNotFound());
    }
}
