package com.tripmate.trip;

import com.tripmate.TestLogin;
import com.tripmate.stay.LinkPreview;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 방 나가기·빼기. 예시 방(id 1) 8명: 1 재현(방장), 2 민지, 3 도윤 … 8 시우.
// 7명 시뮬레이션에서 전날 빠진 다인을 뺄 길이 없어 1인 금액이 다 틀렸다(docs/17)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaveTripTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LinkPreview linkPreview;

    @BeforeEach
    void stubPreview() {
        when(linkPreview.fetch(anyString())).thenReturn(new LinkPreview.Result("숲속 독채", null, "개별 바베큐", 300_000));
    }

    private ResultActions leave(String who, long memberId) throws Exception {
        return mvc.perform(delete("/api/trips/1/members/" + memberId).session(TestLogin.as(mvc, who)));
    }

    private ResultActions send(MockHttpSession s, String method, String url, String body) throws Exception {
        var req = switch (method) {
            case "POST" -> post(url);
            default -> put(url);
        };
        return mvc.perform(req.session(s).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 나가면_설문_표_장보기_역할이_같이_빠지고_1인_금액이_남은_사람으로_나뉜다() throws Exception {
        var minji = TestLogin.as(mvc, "민지");
        var stay = send(minji, "POST", "/api/trips/1/stays", "{ \"url\": \"https://stay.example.com/1\" }")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var stayId = JSON.readTree(stay).get("id").asLong();
        send(minji, "PUT", "/api/trips/1/votes/2", "{ \"stayId\": " + stayId + " }").andExpect(status().is2xxSuccessful());
        send(minji, "PUT", "/api/trips/1/shopping/items/SOJU", "{ \"amount\": 12000 }").andExpect(status().is2xxSuccessful());
        send(minji, "PUT", "/api/trips/1/members/2/survey", """
                { "activity": "BOTH", "planning": "OUTLINE", "hasCar": false, "separateDeparture": false,
                  "departure": null, "mobilityLimited": false, "avoid": [] }""").andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/trips/1/roles/recommend").session(TestLogin.as(mvc, "재현"))).andExpect(status().is2xxSuccessful());

        leave("민지", 2).andExpect(status().isNoContent());

        var siwoo = TestLogin.as(mvc, "시우");
        mvc.perform(get("/api/trips/1").session(siwoo)).andExpect(jsonPath("$.members", hasSize(7)));
        mvc.perform(get("/api/trips/1/roles").session(siwoo))
                .andExpect(jsonPath("$.assignments[?(@.memberId == 2)]", hasSize(0)));
        mvc.perform(get("/api/trips/1/stays").session(siwoo))
                .andExpect(jsonPath("$.stays", hasSize(1)))          // 올린 숙소는 남는다
                .andExpect(jsonPath("$.stays[0].voterIds", hasSize(0)));
        mvc.perform(get("/api/trips/1/shopping").session(siwoo))
                .andExpect(jsonPath("$.people").value(7))
                .andExpect(jsonPath("$.items[?(@.key == 'SOJU')].buyerId").value(org.hamcrest.Matchers.contains((Object) null)));
        // 나간 사람은 방을 못 본다
        mvc.perform(get("/api/trips/1").session(minji)).andExpect(status().isNotFound());
    }

    @Test
    void 방장은_남을_뺄_수_있고_참여자는_못_뺀다() throws Exception {
        leave("민지", 3).andExpect(status().isForbidden());
        leave("재현", 3).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "민지"))).andExpect(jsonPath("$.members", hasSize(7)));
    }

    @Test
    void 방장이_나가면_다음_사람이_방장() throws Exception {
        leave("재현", 1).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/1").session(TestLogin.as(mvc, "민지")))
                .andExpect(jsonPath("$.members[0].name").value("민지"))
                .andExpect(jsonPath("$.members[0].host").value(true));
    }

    @Test
    void 혼자_남은_방은_못_나간다() throws Exception {
        var alone = TestLogin.as(mvc, "혼자");
        var trip = send(alone, "POST", "/api/trips", "{ \"title\": \"혼자 여행\" }").andReturn().getResponse().getContentAsString();
        var node = JSON.readTree(trip);
        mvc.perform(delete("/api/trips/" + node.get("id").asLong() + "/members/" + node.get("meId").asLong()).session(alone))
                .andExpect(status().isBadRequest());
    }
}
