package com.tripmate.role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예시 방(id 1, 참여자 1~8). 역할 id 는 RoleKind 순서대로 1 총무, 2 운전, 3 장보기, 4 바베큐, 5 DJ, 6 기록
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleControllerTest {

    @Autowired
    MockMvc mvc;

    private void survey(int memberId, String activity, String planning, boolean hasCar, boolean mobilityLimited) throws Exception {
        var body = """
                { "activity": "%s", "planning": "%s", "hasCar": %s, "separateDeparture": false,
                  "mobilityLimited": %s, "avoid": [] }
                """.formatted(activity, planning, hasCar, mobilityLimited);
        mvc.perform(put("/api/trips/1/members/" + memberId + "/survey")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void 설문대로_역할을_나눈다() throws Exception {
        survey(1, "BOTH", "PLANNER", false, false);
        survey(3, "BOTH", "SPONTANEOUS", true, false);
        survey(5, "BOTH", "SPONTANEOUS", true, false);
        survey(7, "INDOOR", "SPONTANEOUS", false, true);

        mvc.perform(post("/api/trips/1/roles/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(8)))
                .andExpect(jsonPath("$.assignments[?(@.roleId == 1)].memberId").value(hasItem(1)))
                .andExpect(jsonPath("$.assignments[?(@.roleId == 2)].memberId", containsInAnyOrder(3, 5)))
                .andExpect(jsonPath("$.assignments[?(@.memberId == 7)].roleId").value(hasItem(4)))
                .andExpect(jsonPath("$.assignments[?(@.memberId == 7)].reasons[*]", hasItem("이동 적은 역할 우선 +2")));
    }

    @Test
    void 차_있는_사람이_없으면_운전은_비워_둔다() throws Exception {
        mvc.perform(post("/api/trips/1/roles/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[?(@.roleId == 2)]", hasSize(0)));
    }

    @Test
    void 다시_추천하면_이전_배정을_갈아끼운다() throws Exception {
        mvc.perform(post("/api/trips/1/roles/recommend"));
        mvc.perform(post("/api/trips/1/roles/recommend")).andExpect(status().isOk());

        mvc.perform(get("/api/trips/1/roles"))
                .andExpect(jsonPath("$.roles", hasSize(6)))
                .andExpect(jsonPath("$.assignments", hasSize(6)));
    }

    @Test
    void 없는_방이면_404() throws Exception {
        mvc.perform(get("/api/trips/999/roles")).andExpect(status().isNotFound());
        mvc.perform(post("/api/trips/999/roles/recommend")).andExpect(status().isNotFound());
    }
}
