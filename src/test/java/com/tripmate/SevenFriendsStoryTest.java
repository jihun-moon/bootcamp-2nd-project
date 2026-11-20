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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 대학 동기 7명 강릉 2박 3일(docs/17). 실제 단톡방처럼 사람들이 들쭉날쭉 들어오고 답한다.
// 방장 하늘이 방을 만들고 바로 자기 설문을 하고, 제일 빠른 준호가 들어와 설문까지 끝낸 뒤에야 나머지 다섯이 들어온다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SevenFriendsStoryTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    record Person(String name, MockHttpSession session, long memberId) {
    }

    private JsonNode call(MockHttpSession s, String method, String url, String body, int expected) throws Exception {
        var req = switch (method) {
            case "GET" -> get(url);
            case "POST" -> post(url);
            default -> put(url);
        };
        req.session(s);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        var text = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return text.isBlank() ? null : JSON.readTree(text);
    }

    private Person join(String name, JsonNode trip) throws Exception {
        var s = TestLogin.as(mvc, name);
        call(s, "POST", "/api/invites/" + trip.get("inviteCode").asString(), null, 200);
        return new Person(name, s, call(s, "GET", "/api/trips/" + trip.get("id").asLong(), null, 200).get("meId").asLong());
    }

    private void survey(long tripId, Person p, boolean hasCar, String planning) throws Exception {
        var body = """
                { "activity": "BOTH", "planning": "%s", "hasCar": %s, "separateDeparture": false,
                  "departure": %s, "mobilityLimited": false, "avoid": [] }
                """.formatted(planning, hasCar, hasCar ? "\"서울 관악구\"" : "null");
        call(p.session(), "PUT", "/api/trips/" + tripId + "/members/" + p.memberId() + "/survey", body, 200);
    }

    @Test
    void 먼저_끝낸_둘만_역할을_받고_나중에_온_다섯은_빈손이면_안_된다() throws Exception {
        var haneul = TestLogin.as(mvc, "하늘");
        var trip = call(haneul, "POST", "/api/trips", "{ \"title\": \"강릉 2박 3일\", \"destination\": \"강릉\" }", 201);
        var tripId = trip.get("id").asLong();
        var host = new Person("하늘", haneul, trip.get("meId").asLong());

        // 하늘은 만들자마자 자기 설문, 준호는 1분 만에 들어와 설문
        survey(tripId, host, false, "PLANNER");
        var junho = join("준호", trip);
        survey(tripId, junho, true, "OUTLINE");

        // 나머지 다섯은 저녁에 들어와 설문
        var late = new ArrayList<Person>();
        for (var name : List.of("소연", "민재", "예진", "태오", "다인")) late.add(join(name, trip));
        for (var i = 0; i < late.size(); i++) survey(tripId, late.get(i), i == 0, "SPONTANEOUS");

        var board = call(haneul, "GET", "/api/trips/" + tripId + "/roles", null, 200);
        assertThat(board.get("assignments").size()).as("7명 모두 설문을 끝냈으면 7명 모두 역할이 있어야 한다").isEqualTo(7);
    }
}
