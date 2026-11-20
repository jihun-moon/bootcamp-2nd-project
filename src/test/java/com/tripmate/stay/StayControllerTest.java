package com.tripmate.stay;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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

// 예시 방(id 1). 2 민지, 3 도윤. 링크 미리보기는 바깥 사이트에 나가지 않게 가짜로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StayControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LinkPreview linkPreview;

    MockHttpSession minji;
    MockHttpSession doyun;

    @BeforeEach
    void setUp() throws Exception {
        when(linkPreview.fetch(anyString())).thenReturn(new LinkPreview.Result("미리보기 제목", "https://img.example.com/a.jpg", "개별 바베큐가 있는 독채", 238640));
        minji = TestLogin.as(mvc, "민지");
        doyun = TestLogin.as(mvc, "도윤");
    }

    private ResultActions postStay(String body) throws Exception {
        return mvc.perform(post("/api/trips/1/stays").session(minji)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long addStay(String body) throws Exception {
        var json = postStay(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(json).get("id").asLong();
    }

    private ResultActions vote(MockHttpSession session, int memberId, Long stayId) throws Exception {
        return mvc.perform(put("/api/trips/1/votes/" + memberId).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"stayId\": " + stayId + " }"));
    }

    @Test
    void 제목을_비우면_링크_제목과_사진을_쓴다() throws Exception {
        postStay("{ \"url\": \"https://stay.example.com/1\", \"amenities\": [\"DETACHED\", \"PRIVATE_BBQ\"] }")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("미리보기 제목"))
                .andExpect(jsonPath("$.imageUrl").value("https://img.example.com/a.jpg"))
                .andExpect(jsonPath("$.amenities", containsInAnyOrder("DETACHED", "PRIVATE_BBQ")));
    }

    @Test
    void 링크를_붙이면_소개_글에서_조건을_찾아_준다() throws Exception {
        mvc.perform(post("/api/trips/1/stays/preview").session(minji).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"url\": \"https://stay.example.com/1\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("미리보기 제목"))
                .andExpect(jsonPath("$.amenities", containsInAnyOrder("DETACHED", "PRIVATE_BBQ")));
    }

    @Test
    void 방_사람이_아니면_미리보기도_못_한다() throws Exception {
        mvc.perform(post("/api/trips/1/stays/preview").session(TestLogin.as(mvc, "남"))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"url\": \"https://stay.example.com/1\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 제목을_적으면_그_제목을_쓴다() throws Exception {
        addStay("{ \"url\": \"https://stay.example.com/2\", \"title\": \"우리가 부르는 이름\" }");
        mvc.perform(get("/api/trips/1/stays").session(doyun))
                .andExpect(jsonPath("$.stays[0].title").value("우리가 부르는 이름"));
    }

    @Test
    void http_가_아닌_링크는_막는다() throws Exception {
        postStay("{ \"url\": \"javascript:alert(1)\" }").andExpect(status().isBadRequest());
    }

    @Test
    void 한_사람_한_표이고_다시_던지면_옮겨진다() throws Exception {
        var a = addStay("{ \"url\": \"https://stay.example.com/a\" }");
        var b = addStay("{ \"url\": \"https://stay.example.com/b\" }");

        vote(minji, 2, a).andExpect(status().isNoContent());
        vote(doyun, 3, a).andExpect(status().isNoContent());
        vote(minji, 2, b).andExpect(status().isNoContent());

        mvc.perform(get("/api/trips/1/stays").session(minji))
                .andExpect(jsonPath("$.stays[0].voterIds", contains(3)))
                .andExpect(jsonPath("$.stays[1].voterIds", contains(2)));

        vote(doyun, 3, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/1/stays").session(minji))
                .andExpect(jsonPath("$.stays[0].voterIds", empty()));
    }

    @Test
    void 남의_표는_던질_수_없다() throws Exception {
        var a = addStay("{ \"url\": \"https://stay.example.com/a\" }");
        vote(minji, 3, a).andExpect(status().isForbidden());
    }

    @Test
    void 다른_방_숙소에는_투표할_수_없다() throws Exception {
        vote(minji, 2, 999L).andExpect(status().isNotFound());
    }

    @Test
    void 그룹_취향은_설문의_평균과_최솟값이다() throws Exception {
        for (var row : new Object[][]{{minji, 2, 5}, {doyun, 3, 1}}) {
            mvc.perform(put("/api/trips/1/members/" + row[1] + "/survey").session((MockHttpSession) row[0])
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { "activity": "BOTH", "planning": "OUTLINE", "hasCar": false, "separateDeparture": false,
                              "mobilityLimited": false, "avoid": [], "preferences": { "HEATED_POOL": %d } }
                            """.formatted((int) row[2])))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/trips/1/stays").session(minji))
                .andExpect(jsonPath("$.preferences.HEATED_POOL.avg").value(3.0))
                .andExpect(jsonPath("$.preferences.HEATED_POOL.min").value(1))
                .andExpect(jsonPath("$.amenities[2].label").value("온수풀"));
    }
}
