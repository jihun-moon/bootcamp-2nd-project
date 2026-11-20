package com.tripmate.role;

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
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// REQ-12. 예시 방(id 1)의 기본 역할 6개: 1 총무, 2 운전, 3 장보기, 4 바베큐, 5 DJ, 6 기록
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleEditTest {

    private static final String DISHES = """
            { "title": " 설거지 ", "required": true, "capacity": 2, "tasks": ["저녁 설거지", " 분리수거 "] }
            """;

    @Autowired
    MockMvc mvc;

    MockHttpSession host;
    MockHttpSession minji;

    @BeforeEach
    void login() throws Exception {
        host = TestLogin.as(mvc, "재현");
        minji = TestLogin.as(mvc, "민지");
    }

    private ResultActions add(MockHttpSession session, String body) throws Exception {
        return mvc.perform(post("/api/trips/1/roles").session(session).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 방장이_역할을_더하면_앞뒤_공백을_지우고_보드에_나온다() throws Exception {
        add(host, DISHES)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", hasSize(7)))
                .andExpect(jsonPath("$.roles[6].title").value("설거지"))
                .andExpect(jsonPath("$.roles[6].capacity").value(2))
                .andExpect(jsonPath("$.roles[6].tasks[1]").value("분리수거"));
    }

    @Test
    void 더한_역할도_추천에_들어간다() throws Exception {
        var created = add(host, DISHES).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // 테스트는 되돌려도 DB 번호는 계속 오르니 새 역할 id 는 응답에서 읽는다
        var newRoleId = JsonMapper.builder().build().readTree(created).get("roles").get(6).get("id").asInt();

        // 설문 전 8명: 운전은 금지, 필수 역할 첫 칸부터 찬다. 새 필수 역할도 한 명은 받는다
        mvc.perform(post("/api/trips/1/roles/recommend").session(host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[*].roleId", hasItem(newRoleId)));
    }

    @Test
    void 고치면_이름_정원_할_일이_바뀐다() throws Exception {
        mvc.perform(patch("/api/trips/1/roles/5").session(host).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"노래 담당\", \"required\": false, \"capacity\": 1, \"tasks\": [] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[4].title").value("노래 담당"))
                .andExpect(jsonPath("$.roles[4].tasks", hasSize(0)));
    }

    @Test
    void 지우면_그_역할에_있던_사람은_미배정이_된다() throws Exception {
        mvc.perform(patch("/api/trips/1/roles/assignments/2").session(minji).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"roleId\": 5, \"version\": null }")).andExpect(status().isOk());

        mvc.perform(delete("/api/trips/1/roles/5").session(host)).andExpect(status().isNoContent());

        mvc.perform(get("/api/trips/1/roles").session(minji))
                .andExpect(jsonPath("$.roles", hasSize(5)))
                .andExpect(jsonPath("$.assignments", hasSize(0)));
    }

    @Test
    void 참여자는_역할을_못_바꾼다() throws Exception {
        add(minji, DISHES).andExpect(status().isForbidden());
        mvc.perform(patch("/api/trips/1/roles/5").session(minji).contentType(MediaType.APPLICATION_JSON).content(DISHES))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/trips/1/roles/5").session(minji)).andExpect(status().isForbidden());
    }

    @Test
    void 이름이_비거나_정원이_범위를_넘으면_400() throws Exception {
        add(host, "{ \"title\": \"  \", \"required\": false, \"capacity\": 1, \"tasks\": [] }").andExpect(status().isBadRequest());
        add(host, "{ \"title\": \"설거지\", \"required\": false, \"capacity\": 0, \"tasks\": [] }").andExpect(status().isBadRequest());
        add(host, "{ \"title\": \"설거지\", \"required\": false, \"capacity\": 9, \"tasks\": [] }").andExpect(status().isBadRequest());
        add(host, "{ \"title\": \"설거지\", \"required\": false, \"capacity\": 1, \"tasks\": [\"\"] }").andExpect(status().isBadRequest());
    }

    @Test
    void 역할은_12개까지() throws Exception {
        for (var i = 0; i < 6; i++) add(host, DISHES).andExpect(status().isCreated());
        add(host, DISHES).andExpect(status().isBadRequest());
    }

    @Test
    void 다른_방_역할이면_404() throws Exception {
        mvc.perform(patch("/api/trips/1/roles/999").session(host).contentType(MediaType.APPLICATION_JSON).content(DISHES))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/trips/1/roles/999").session(host)).andExpect(status().isNotFound());
    }
}
