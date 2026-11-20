package com.tripmate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 여러 무리에 맞게 굴러가나: 둘이 당일치기, 15명 4박, 차 없음, 아무도 술을 안 마심
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FlexibilityTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LocalDate D = LocalDate.now().plusDays(30);

    @Autowired
    MockMvc mvc;

    record Person(MockHttpSession session, long memberId) {
    }

    private List<Person> room(String prefix, int size, LocalDate start, LocalDate end, long[] tripId) throws Exception {
        var host = TestLogin.as(mvc, prefix + "방장");
        var body = "{ \"title\": \"" + prefix + "\", \"destination\": \"강릉\", \"startDate\": \"" + start + "\", \"endDate\": \"" + end + "\" }";
        var trip = JSON.readTree(mvc.perform(post("/api/trips").session(host).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        tripId[0] = trip.get("id").asLong();
        var people = new ArrayList<Person>();
        people.add(new Person(host, trip.get("meId").asLong()));
        for (var i = 1; i < size; i++) {
            var s = TestLogin.as(mvc, prefix + i);
            mvc.perform(post("/api/invites/" + trip.get("inviteCode").asString()).session(s)).andExpect(status().isOk());
            people.add(new Person(s, read(s, "/api/trips/" + tripId[0]).get("meId").asLong()));
        }
        return people;
    }

    private JsonNode read(MockHttpSession s, String url) throws Exception {
        return JSON.readTree(mvc.perform(get(url).session(s)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void survey(long tripId, Person p, boolean hasCar) throws Exception {
        mvc.perform(put("/api/trips/" + tripId + "/members/" + p.memberId() + "/survey").session(p.session())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "activity": "BOTH", "planning": "OUTLINE", "hasCar": %s, "separateDeparture": false,
                          "departure": %s, "mobilityLimited": false, "avoid": [] }""".formatted(hasCar, hasCar ? "\"서울\"" : "null")))
                .andExpect(status().isOk());
    }

    @Test
    void 둘이_당일치기면_아침_장보기는_빠지고_역할은_둘에게() throws Exception {
        var id = new long[1];
        var two = room("당일", 2, D, D, id);
        for (var p : two) survey(id[0], p, false);
        assertThat(read(two.get(0).session(), "/api/trips/" + id[0] + "/roles").get("assignments").size()).isEqualTo(2);
        mvc.perform(get("/api/trips/" + id[0] + "/shopping").session(two.get(1).session()))
                .andExpect(jsonPath("$.nights").value(0))
                .andExpect(jsonPath("$.items[*].key", not(hasItem("RICE"))))
                .andExpect(jsonPath("$.items[?(@.key == 'PORK_BELLY')].quantity").value(hasItem("500g")));   // 2 × 300 × 0.7 = 420 → 500g
        mvc.perform(post("/api/trips/" + id[0] + "/room-draw").session(two.get(1).session())
                .contentType(MediaType.APPLICATION_JSON).content("{ \"rooms\": 2 }")).andExpect(status().isOk());
    }

    @Test
    void 열다섯_명_4박이면_양이_늘고_방은_고르게() throws Exception {
        var id = new long[1];
        var many = room("긴여행", 15, D, D.plusDays(4), id);
        var board = read(many.get(3).session(), "/api/trips/" + id[0] + "/shopping");
        assertThat(board.get("people").asInt()).isEqualTo(15);
        assertThat(board.get("nights").asInt()).isEqualTo(4);
        var draw = JSON.readTree(mvc.perform(post("/api/trips/" + id[0] + "/room-draw").session(many.get(5).session())
                .contentType(MediaType.APPLICATION_JSON).content("{ \"rooms\": 4 }")).andReturn().getResponse().getContentAsString());
        var sizes = new ArrayList<Integer>();
        draw.get("roomDraw").get("rooms").forEach(r -> sizes.add(r.size()));
        assertThat(sizes).containsExactlyInAnyOrder(4, 4, 4, 3);
    }

    @Test
    void 차가_없고_아무도_술을_안_마셔도_막히지_않는다() throws Exception {
        var id = new long[1];
        var group = room("기차여행", 4, D, D.plusDays(1), id);
        for (var p : group) {
            survey(id[0], p, false);
            mvc.perform(put("/api/trips/" + id[0] + "/members/" + p.memberId() + "/drinks").session(p.session())
                    .contentType(MediaType.APPLICATION_JSON).content("{ \"drinks\": false }")).andExpect(status().isOk());
        }
        // 교통비는 "차를 가져가는 사람이 없어요" 안내(200), 장보기에는 술 줄이 없다
        mvc.perform(get("/api/trips/" + id[0] + "/roles/travel").session(group.get(1).session())).andExpect(status().isOk());
        mvc.perform(get("/api/trips/" + id[0] + "/shopping").session(group.get(1).session()))
                .andExpect(jsonPath("$.drinkers").value(0))
                .andExpect(jsonPath("$.items[*].key", not(hasItem("SOJU"))));
        // 기차표는 정산 "기타"로 적으면 모두가 나눈다
        mvc.perform(post("/api/trips/" + id[0] + "/expenses").session(group.get(0).session()).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"kind\": \"OTHER\", \"memo\": \"KTX 4장\", \"amount\": 200000 }")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.transfers.length()").value(3));
    }
}
