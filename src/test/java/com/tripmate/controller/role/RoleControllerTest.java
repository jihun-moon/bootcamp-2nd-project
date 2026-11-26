package com.tripmate.controller.role;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예시 방(id 1). 참여자 id 는 TestLogin 주석 순서, 역할 id 는 RoleKind 순서대로 1 총무, 2 운전, 3 장보기, 4 바베큐, 5 DJ, 6 기록
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleControllerTest {

    private static final List<String> NAMES = List.of("재현", "민지", "도윤", "서연", "하준", "지우", "예린", "시우");

    @Autowired
    MockMvc mvc;

    MockHttpSession host;
    MockHttpSession minji;

    @BeforeEach
    void login() throws Exception {
        host = TestLogin.as(mvc, "재현");
        minji = TestLogin.as(mvc, "민지");
    }

    private void survey(int memberId, String activity, String planning, boolean hasCar, boolean mobilityLimited) throws Exception {
        var body = """
                { "activity": "%s", "planning": "%s", "hasCar": %s, "separateDeparture": false,
                  "mobilityLimited": %s, "avoid": [] }
                """.formatted(activity, planning, hasCar, mobilityLimited);
        mvc.perform(put("/api/trips/1/members/" + memberId + "/survey").session(TestLogin.as(mvc, NAMES.get(memberId - 1)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private ResultActions recommend(MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/trips/1/roles/recommend").session(session));
    }

    @Test
    void 설문대로_역할을_나눈다() throws Exception {
        survey(1, "BOTH", "PLANNER", false, false);
        survey(3, "BOTH", "SPONTANEOUS", true, false);
        survey(5, "BOTH", "SPONTANEOUS", true, false);
        survey(7, "INDOOR", "SPONTANEOUS", false, true);

        recommend(host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(8)))
                .andExpect(jsonPath("$.assignments[?(@.roleId == 1)].memberId").value(hasItem(1)))
                .andExpect(jsonPath("$.assignments[?(@.roleId == 2)].memberId", containsInAnyOrder(3, 5)))
                .andExpect(jsonPath("$.assignments[?(@.memberId == 7)].roleId").value(hasItem(4)))
                .andExpect(jsonPath("$.assignments[?(@.memberId == 7)].reasons[*]", hasItem("이동 적은 역할 우선 +2")));
    }

    @Test
    void 마지막_사람이_설문을_끝내면_역할이_저절로_정해진다() throws Exception {
        for (var id = 1; id <= 7; id++) survey(id, "BOTH", "OUTLINE", id <= 2, false);
        mvc.perform(get("/api/trips/1/roles").session(minji)).andExpect(jsonPath("$.assignments", hasSize(0)));

        survey(8, "BOTH", "OUTLINE", false, false);
        mvc.perform(get("/api/trips/1/roles").session(minji))
                .andExpect(jsonPath("$.assignments", hasSize(8)))
                .andExpect(jsonPath("$.assignments[?(@.roleId == 2)].memberId", containsInAnyOrder(1, 2)));
    }

    @Test
    void 이미_배정이_있으면_설문이_끝나도_그대로_둔다() throws Exception {
        move(2, 4, null).andExpect(status().isOk());
        for (var id = 1; id <= 8; id++) survey(id, "BOTH", "OUTLINE", false, false);
        mvc.perform(get("/api/trips/1/roles").session(minji)).andExpect(jsonPath("$.assignments", hasSize(1)));
    }

    @Test
    void 추천은_방장만_한다() throws Exception {
        recommend(minji).andExpect(status().isForbidden());
    }

    @Test
    void 차_있는_사람이_없으면_운전은_비워_둔다() throws Exception {
        recommend(host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[?(@.roleId == 2)]", hasSize(0)));
    }

    @Test
    void 다시_추천하면_이전_배정을_갈아끼운다() throws Exception {
        recommend(host);
        recommend(host).andExpect(status().isOk());

        mvc.perform(get("/api/trips/1/roles").session(minji))
                .andExpect(jsonPath("$.roles", hasSize(6)))
                .andExpect(jsonPath("$.assignments", hasSize(6)));
    }

    private ResultActions move(int memberId, Integer roleId, Integer version) throws Exception {
        var body = "{ \"roleId\": %s, \"version\": %s }".formatted(roleId, version);
        return mvc.perform(patch("/api/trips/1/roles/assignments/" + memberId).session(minji)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 카드를_옮기면_version_이_오른다() throws Exception {
        move(2, 4, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[?(@.memberId == 2)].version").value(hasItem(0)));
        move(2, 5, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[?(@.memberId == 2)].roleId").value(hasItem(5)))
                .andExpect(jsonPath("$.assignments[?(@.memberId == 2)].version").value(hasItem(1)));
    }

    @Test
    void 보드는_같이_쓰니_남의_카드도_옮긴다() throws Exception {
        move(3, 4, null).andExpect(status().isOk());
    }

    @Test
    void 그사이_누가_옮겼으면_409() throws Exception {
        move(2, 4, null).andExpect(status().isOk());
        move(2, 5, 0).andExpect(status().isOk());
        // 첫 화면의 version 0 을 그대로 들고 온 두 번째 사람
        move(2, 6, 0).andExpect(status().isConflict());
        // 미배정인 줄 알고 끌어온 사람
        move(2, 6, null).andExpect(status().isConflict());
    }

    @Test
    void 미배정으로_옮기면_배정이_사라진다() throws Exception {
        move(2, 4, null).andExpect(status().isOk());
        move(2, null, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[?(@.memberId == 2)]", hasSize(0)));
    }

    @Test
    void 다른_방_역할이나_참여자면_404() throws Exception {
        move(2, 999, null).andExpect(status().isNotFound());
        move(999, 4, null).andExpect(status().isNotFound());
    }

    @Test
    void 없는_방이면_404() throws Exception {
        mvc.perform(get("/api/trips/999/roles").session(minji)).andExpect(status().isNotFound());
        recommend(host).andExpect(status().isOk());
        mvc.perform(post("/api/trips/999/roles/recommend").session(host)).andExpect(status().isNotFound());
    }
}
