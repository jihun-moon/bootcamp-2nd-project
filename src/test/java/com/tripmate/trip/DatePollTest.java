package com.tripmate.trip;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 날짜 맞추기. 예시 방(id 1): 1 재현(방장), 2 민지, 3 도윤
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DatePollTest {

    private static final LocalDate D1 = LocalDate.now().plusDays(30);
    private static final LocalDate D2 = D1.plusDays(1);

    @Autowired
    MockMvc mvc;

    private ResultActions saveDates(String name, int memberId, LocalDate... dates) throws Exception {
        var list = String.join(",", java.util.Arrays.stream(dates).map(d -> "\"" + d + "\"").toList());
        return mvc.perform(put("/api/trips/1/members/" + memberId + "/dates").session(TestLogin.as(mvc, name))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"dates\": [" + list + "] }"));
    }

    @Test
    void 되는_날을_모으면_날짜마다_되는_사람이_나온다() throws Exception {
        saveDates("민지", 2, D1, D2).andExpect(status().isOk());
        saveDates("도윤", 3, D1).andExpect(status().isOk());

        mvc.perform(get("/api/trips/1/dates").session(TestLogin.as(mvc, "재현")))
                .andExpect(jsonPath("$.memberCount").value(8))
                .andExpect(jsonPath("$.responded").value(2))
                .andExpect(jsonPath("$.available['" + D1 + "']", containsInAnyOrder(2, 3)))
                .andExpect(jsonPath("$.available['" + D2 + "']", containsInAnyOrder(2)));
    }

    @Test
    void 다시_보내면_통째로_바뀐다() throws Exception {
        saveDates("민지", 2, D1, D2);
        saveDates("민지", 2, D2).andExpect(jsonPath("$.available['" + D1 + "']").doesNotExist());
    }

    @Test
    void 남의_날짜는_못_고치고_지난_날은_못_고른다() throws Exception {
        saveDates("민지", 3, D1).andExpect(status().isForbidden());
        saveDates("민지", 2, LocalDate.now().minusDays(1)).andExpect(status().isBadRequest());
    }

    @Test
    void 방장이_후보를_고르면_방_날짜가_된다() throws Exception {
        var body = "{ \"startDate\": \"" + D1 + "\", \"endDate\": \"" + D2 + "\" }";
        mvc.perform(put("/api/trips/1/period").session(TestLogin.as(mvc, "민지"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/trips/1/period").session(TestLogin.as(mvc, "재현"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(D1.toString()))
                .andExpect(jsonPath("$.endDate").value(D2.toString()));
        mvc.perform(put("/api/trips/1/period").session(TestLogin.as(mvc, "재현"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"startDate\": \"" + D2 + "\", \"endDate\": \"" + D1 + "\" }")).andExpect(status().isBadRequest());
    }
}
