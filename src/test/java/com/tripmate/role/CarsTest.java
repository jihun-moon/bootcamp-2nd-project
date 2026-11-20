package com.tripmate.role;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 사는 동네가 달라 차 두 대로 가는 8명. 1 재현(서울 마포, 차), 2 민지(수원, 차), 3 도윤 … 8 시우(기차로 따로).
// 아무도 안 고르면 차마다 고르게 나뉘고, 다른 차가 편한 사람만 옮기고, 기름값은 그 차에 탄 사람끼리 나눈다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CarsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String[] NAMES = {"재현", "민지", "도윤", "서연", "하준", "지우", "예린", "시우"};

    @Autowired
    MockMvc mvc;

    private ResultActions ride(String who, long memberId, Long driverId) throws Exception {
        return mvc.perform(put("/api/trips/1/cars/rides/" + memberId).session(TestLogin.as(mvc, who))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"driverId\": " + driverId + " }"));
    }

    private JsonNode get(String url) throws Exception {
        return JSON.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .session(TestLogin.as(mvc, "시우"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    // 설문이 다 모이면 차 있는 둘(재현·민지)이 운전 칸에 저절로 들어간다
    @BeforeEach
    void 설문() throws Exception {
        for (var i = 0; i < NAMES.length; i++) {
            var car = i < 2;
            mvc.perform(put("/api/trips/1/members/" + (i + 1) + "/survey").session(TestLogin.as(mvc, NAMES[i]))
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            { "activity": "BOTH", "planning": "OUTLINE", "hasCar": %s, "separateDeparture": false,
                              "departure": %s, "mobilityLimited": false, "avoid": [] }""".formatted(car, car ? "\"서울\"" : "null")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void 안_고르면_차마다_고르게_나뉜다() throws Exception {
        var cars = get("/api/trips/1/cars").get("cars");
        assertThat(cars.size()).isEqualTo(2);
        assertThat(cars.get(0).get("riderIds").size()).isEqualTo(3);
        assertThat(cars.get(1).get("riderIds").size()).isEqualTo(3);
    }

    @Test
    void 옮기고_따로_가고_차마다_태우는_곳() throws Exception {
        ride("시우", 8, null).andExpect(status().isOk()).andExpect(jsonPath("$.separateIds[0]").value(8));
        ride("하준", 5, 2L).andExpect(status().isOk());
        ride("하준", 3, 2L).andExpect(status().isForbidden());        // 남의 자리는 방장만
        ride("재현", 1, 2L).andExpect(status().isBadRequest());       // 운전자는 자기 차
        mvc.perform(put("/api/trips/1/cars/2/meet").session(TestLogin.as(mvc, "서연")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"time\": \"08:30\", \"place\": \"수원역 4번 출구\" }")).andExpect(status().isForbidden());
        mvc.perform(put("/api/trips/1/cars/2/meet").session(TestLogin.as(mvc, "민지")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"time\": \"08:30\", \"place\": \"수원역 4번 출구\" }")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cars[1].meetPlace").value("수원역 4번 출구"));

        var cars = get("/api/trips/1/cars").get("cars");
        assertThat(cars.get(0).get("riderIds").toString()).isEqualTo("[3,7]");
        assertThat(cars.get(1).get("riderIds").toString()).isEqualTo("[4,5,6]");
    }

    @Test
    void 기름값은_그_차에_탄_사람끼리_따로_간_사람은_안_낸다() throws Exception {
        ride("시우", 8, null);
        ride("하준", 5, 2L);
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"driverPaysFuel\": false, \"refundNoShow\": null, \"drinksSeparate\": null }"));
        mvc.perform(post("/api/trips/1/expenses").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"kind\": \"FUEL\", \"memo\": \"마포에서\", \"amount\": 60000 }")).andExpect(status().isCreated());
        mvc.perform(post("/api/trips/1/expenses").session(TestLogin.as(mvc, "민지")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"kind\": \"FUEL\", \"memo\": \"수원에서\", \"amount\": 90000 }")).andExpect(status().isCreated());

        // 재현 차(도윤·예린)는 60,000 ÷ 2, 민지 차(서연·하준·지우)는 90,000 ÷ 3 이라 다섯 모두 30,000 씩.
        // 누가 누구에게 보낼지는 보내는 횟수를 줄이려 짝을 지어서, 재현 차 사람이 민지에게 보낼 수도 있다(합은 같다)
        var paid = new HashMap<String, Integer>();
        var got = new HashMap<String, Integer>();
        for (var t : get("/api/trips/1/settlement").get("transfers")) {
            paid.merge(t.get("fromName").asString(), t.get("amount").asInt(), Integer::sum);
            got.merge(t.get("toName").asString(), t.get("amount").asInt(), Integer::sum);
        }
        for (var name : new String[]{"도윤", "서연", "하준", "지우", "예린"}) assertThat(paid.get(name)).isEqualTo(30_000);
        assertThat(paid).doesNotContainKey("시우");
        assertThat(got).containsEntry("재현", 60_000).containsEntry("민지", 90_000);
    }
}
