package com.tripmate.stay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예시 방(id 1, 참여자 1~8). 링크 미리보기는 바깥 사이트에 나가지 않게 가짜로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StayControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LinkPreview linkPreview;

    @BeforeEach
    void preview() {
        when(linkPreview.fetch(anyString())).thenReturn(new LinkPreview.Result("미리보기 제목", "https://img.example.com/a.jpg"));
    }

    private long addStay(String body) throws Exception {
        var json = mvc.perform(post("/api/trips/1/stays").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(json).get("id").asLong();
    }

    private ResultActions vote(int memberId, Long stayId) throws Exception {
        return mvc.perform(put("/api/trips/1/votes/" + memberId).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"stayId\": " + stayId + " }"));
    }

    @Test
    void 제목을_비우면_링크_제목과_사진을_쓴다() throws Exception {
        mvc.perform(post("/api/trips/1/stays").contentType(MediaType.APPLICATION_JSON).content("""
                        { "url": "https://stay.example.com/1", "amenities": ["DETACHED", "PRIVATE_BBQ"], "addedBy": 2 }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("미리보기 제목"))
                .andExpect(jsonPath("$.imageUrl").value("https://img.example.com/a.jpg"))
                .andExpect(jsonPath("$.amenities", containsInAnyOrder("DETACHED", "PRIVATE_BBQ")));
    }

    @Test
    void 제목을_적으면_그_제목을_쓴다() throws Exception {
        addStay("{ \"url\": \"https://stay.example.com/2\", \"title\": \"우리가 부르는 이름\", \"addedBy\": 2 }");
        mvc.perform(get("/api/trips/1/stays"))
                .andExpect(jsonPath("$.stays[0].title").value("우리가 부르는 이름"));
    }

    @Test
    void http_가_아닌_링크나_없는_참여자는_막는다() throws Exception {
        mvc.perform(post("/api/trips/1/stays").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"url\": \"javascript:alert(1)\", \"addedBy\": 2 }"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/1/stays").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"url\": \"https://stay.example.com/3\", \"addedBy\": 999 }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 한_사람_한_표이고_다시_던지면_옮겨진다() throws Exception {
        var a = addStay("{ \"url\": \"https://stay.example.com/a\", \"addedBy\": 2 }");
        var b = addStay("{ \"url\": \"https://stay.example.com/b\", \"addedBy\": 2 }");

        vote(2, a).andExpect(status().isNoContent());
        vote(3, a).andExpect(status().isNoContent());
        vote(2, b).andExpect(status().isNoContent());

        mvc.perform(get("/api/trips/1/stays"))
                .andExpect(jsonPath("$.stays[0].voterIds", contains(3)))
                .andExpect(jsonPath("$.stays[1].voterIds", contains(2)));

        vote(3, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/1/stays"))
                .andExpect(jsonPath("$.stays[0].voterIds", empty()));
    }

    @Test
    void 다른_방_숙소에는_투표할_수_없다() throws Exception {
        vote(2, 999L).andExpect(status().isNotFound());
        vote(999, null).andExpect(status().isNotFound());
    }

    @Test
    void 그룹_취향은_설문의_평균과_최솟값이다() throws Exception {
        for (var row : new int[][]{{2, 5}, {3, 1}}) {
            mvc.perform(put("/api/trips/1/members/" + row[0] + "/survey").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { "activity": "BOTH", "planning": "OUTLINE", "hasCar": false, "separateDeparture": false,
                              "mobilityLimited": false, "avoid": [], "preferences": { "HEATED_POOL": %d } }
                            """.formatted(row[1])));
        }
        mvc.perform(get("/api/trips/1/stays"))
                .andExpect(jsonPath("$.preferences.HEATED_POOL.avg").value(3.0))
                .andExpect(jsonPath("$.preferences.HEATED_POOL.min").value(1))
                .andExpect(jsonPath("$.amenities[2].label").value("온수풀"));
    }
}
