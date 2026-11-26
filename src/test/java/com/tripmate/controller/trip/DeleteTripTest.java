package com.tripmate.controller.trip;

import com.tripmate.TestLogin;
import com.tripmate.component.LinkPreview;
import jakarta.persistence.EntityManager;
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

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 방 지우기(REQ-05). 예시 방(id 1)에 기능마다 자료를 채운 뒤 지워도 외래 키에 걸리지 않고 다 사라지는지
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeleteTripTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager em;

    @MockitoBean
    LinkPreview linkPreview;

    private ResultActions send(MockHttpSession s, String method, String url, String body) throws Exception {
        var req = switch (method) {
            case "POST" -> post(url);
            default -> put(url);
        };
        return mvc.perform(req.session(s).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 방장이_지우면_모든_기능의_자료가_같이_사라진다() throws Exception {
        when(linkPreview.fetch(anyString())).thenReturn(new LinkPreview.Result("숲속 독채", null, "개별 바베큐", 300_000));
        var host = TestLogin.as(mvc, "재현");
        var minji = TestLogin.as(mvc, "민지");
        var stay = send(minji, "POST", "/api/trips/1/stays", "{ \"url\": \"https://stay.example.com/1\" }")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var stayId = JsonMapper.builder().build().readTree(stay).get("id").asLong();
        send(minji, "PUT", "/api/trips/1/votes/2", "{ \"stayId\": " + stayId + " }").andExpect(status().is2xxSuccessful());
        send(minji, "PUT", "/api/trips/1/members/2/survey", """
                { "activity": "BOTH", "planning": "OUTLINE", "hasCar": false, "separateDeparture": false,
                  "departure": null, "mobilityLimited": false, "avoid": [] }""").andExpect(status().isOk());
        mvc.perform(post("/api/trips/1/roles/recommend").session(host)).andExpect(status().isOk());
        send(minji, "PUT", "/api/trips/1/shopping/items/SOJU", "{ \"amount\": 12000 }").andExpect(status().isOk());
        send(host, "PUT", "/api/trips/1/shopping/plan", "{ \"drinkers\": 6, \"meatGrams\": 300 }").andExpect(status().isOk());
        send(host, "POST", "/api/trips/1/expenses", "{ \"kind\": \"STAY\", \"amount\": 80000 }").andExpect(status().isCreated());
        send(host, "POST", "/api/trips/1/payments", "{ \"fromId\": 2, \"toId\": 1, \"amount\": 10000 }").andExpect(status().isCreated());
        send(host, "POST", "/api/trips/1/room-draw", "{ \"rooms\": 2 }").andExpect(status().isOk());
        send(host, "PUT", "/api/trips/1/members/1/dates", "{ \"dates\": [\"" + LocalDate.now().plusDays(10) + "\"] }");

        mvc.perform(delete("/api/trips/1").session(minji)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/trips/1").session(host)).andExpect(status().isNoContent());
        em.flush();   // 실제 지우기 쿼리가 외래 키에 걸리지 않는지까지(시험은 끝에 되돌리므로 여기서 내보낸다)

        mvc.perform(get("/api/trips/1").session(host)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/trips").session(minji)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
