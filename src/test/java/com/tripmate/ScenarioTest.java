package com.tripmate;

import com.tripmate.component.LinkPreview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 사람이 누르는 순서 그대로 방 하나를 끝까지 돌려 본다: 방장이 만들고 → 친구들이 초대로 들어와 → 되는 날·설문 →
// 마지막 사람이 끝내면 역할이 저절로 → 방장이 날짜를 고르고 → 숙소·투표·규칙. 인원은 3·8·15명으로 바꿔 본다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScenarioTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LocalDate D1 = LocalDate.now().plusDays(40);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LinkPreview linkPreview;

    @BeforeEach
    void stubPreview() {
        when(linkPreview.fetch(anyString())).thenReturn(new LinkPreview.Result("숲속 독채", null, "독채 개별 바베큐", 300_000));
    }

    // 사람 한 명: 세션과 이 방에서의 참여자 id
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

    // 방장이 방을 만들고 friends 명이 초대 링크로 들어온다
    private List<Person> room(int size, Map<String, Object> holder) throws Exception {
        var host = TestLogin.as(mvc, "방장" + size);
        var trip = call(host, "POST", "/api/trips", "{ \"title\": \"강릉 여행\", \"destination\": \"강릉\" }", 201);
        var tripId = trip.get("id").asLong();
        holder.put("tripId", tripId);
        var people = new ArrayList<Person>();
        people.add(new Person("방장" + size, host, trip.get("meId").asLong()));
        for (var i = 1; i < size; i++) {
            var name = "친구" + size + "-" + i;
            var s = TestLogin.as(mvc, name);
            call(s, "POST", "/api/invites/" + trip.get("inviteCode").asString(), null, 200);
            people.add(new Person(name, s, call(s, "GET", "/api/trips/" + tripId, null, 200).get("meId").asLong()));
        }
        return people;
    }

    private void survey(long tripId, Person p, boolean hasCar, String planning) throws Exception {
        var body = """
                { "activity": "BOTH", "planning": "%s", "hasCar": %s, "separateDeparture": false,
                  "departure": %s, "mobilityLimited": false, "avoid": [] }
                """.formatted(planning, hasCar, hasCar ? "\"서울 마포구\"" : "null");
        call(p.session(), "PUT", "/api/trips/" + tripId + "/members/" + p.memberId() + "/survey", body, 200);
    }

    private JsonNode roles(long tripId, Person p) throws Exception {
        return call(p.session(), "GET", "/api/trips/" + tripId + "/roles", null, 200);
    }

    private long roleId(JsonNode board, String title) {
        for (var r : board.get("roles")) if (r.get("title").asString().equals(title)) return r.get("id").asLong();
        throw new IllegalStateException(title);
    }

    @Test
    void 여덟_명_방을_처음부터_끝까지() throws Exception {
        var h = new LinkedHashMap<String, Object>();
        var people = room(8, h);
        var tripId = (long) h.get("tripId");

        // 되는 날: 모두 D1~D3, 한 명만 D1~D2. 겹치는 2박 3일은 7명, 1박 2일은 8명
        for (var i = 0; i < people.size(); i++) {
            var days = i == 7 ? List.of(D1, D1.plusDays(1)) : List.of(D1, D1.plusDays(1), D1.plusDays(2));
            var body = "{ \"dates\": [" + String.join(",", days.stream().map(d -> "\"" + d + "\"").toList()) + "] }";
            call(people.get(i).session(), "PUT", "/api/trips/" + tripId + "/members/" + people.get(i).memberId() + "/dates", body, 200);
        }
        var board = call(people.get(0).session(), "GET", "/api/trips/" + tripId + "/dates", null, 200);
        assertThat(board.get("responded").asInt()).isEqualTo(8);
        assertThat(board.get("available").get(D1.plusDays(2).toString()).size()).isEqualTo(7);

        // 설문: 차 있는 사람 둘(방장, 친구1), 계획형 한 명(친구2). 일곱 명까지는 역할이 비어 있다
        for (var i = 0; i < 7; i++) survey(tripId, people.get(i), i <= 1, i == 2 ? "PLANNER" : "OUTLINE");
        assertThat(roles(tripId, people.get(0)).get("assignments").size()).isZero();

        // 마지막 사람이 끝내면 방장이 누르지 않아도 8명 모두 역할이 생긴다. 운전은 차 있는 둘, 총무는 계획형
        survey(tripId, people.get(7), false, "SPONTANEOUS");
        var after = roles(tripId, people.get(3));
        assertThat(after.get("assignments").size()).isEqualTo(8);
        var drive = roleId(after, "운전");
        var money = roleId(after, "총무·타임라인");
        var drivers = new ArrayList<Long>();
        long treasurer = -1;
        for (var a : after.get("assignments")) {
            if (a.get("roleId").asLong() == drive) drivers.add(a.get("memberId").asLong());
            if (a.get("roleId").asLong() == money) treasurer = a.get("memberId").asLong();
        }
        assertThat(drivers).containsExactlyInAnyOrder(people.get(0).memberId(), people.get(1).memberId());
        assertThat(treasurer).isEqualTo(people.get(2).memberId());

        // 방장이 1박 2일(8명 모두) 후보로 정한다. 참여자는 못 정한다
        var period = "{ \"startDate\": \"" + D1 + "\", \"endDate\": \"" + D1.plusDays(1) + "\" }";
        call(people.get(5).session(), "PUT", "/api/trips/" + tripId + "/period", period, 403);
        var trip = call(people.get(0).session(), "PUT", "/api/trips/" + tripId + "/period", period, 200);
        assertThat(trip.get("startDate").asString()).isEqualTo(D1.toString());

        // 숙소: 링크만 붙이면 가격이 채워지고(가짜 미리보기 30만 원), 다섯 명이 투표
        var stay = call(people.get(4).session(), "POST", "/api/trips/" + tripId + "/stays",
                "{ \"url\": \"https://stay.example.com/1\", \"amenities\": [] }", 201);
        assertThat(stay.get("price").asInt()).isEqualTo(300_000);
        for (var i = 0; i < 5; i++) {
            call(people.get(i).session(), "PUT", "/api/trips/" + tripId + "/votes/" + people.get(i).memberId(),
                    "{ \"stayId\": " + stay.get("id").asLong() + " }", 204);
        }
        var stays = call(people.get(6).session(), "GET", "/api/trips/" + tripId + "/stays", null, 200);
        assertThat(stays.get("stays").get(0).get("voterIds").size()).isEqualTo(5);

        // 규칙: 방장만
        var rules = "{ \"driverPaysFuel\": false, \"refundNoShow\": false, \"drinksSeparate\": true }";
        call(people.get(3).session(), "PUT", "/api/trips/" + tripId + "/rules", rules, 403);
        assertThat(call(people.get(0).session(), "PUT", "/api/trips/" + tripId + "/rules", rules, 200)
                .get("rules").get("driverPaysFuel").asBoolean()).isFalse();
    }

    @Test
    void 세_명이면_필수_역할부터_차고_차_없으면_운전은_비운다() throws Exception {
        var h = new LinkedHashMap<String, Object>();
        var people = room(3, h);
        var tripId = (long) h.get("tripId");
        for (var p : people) survey(tripId, p, false, "OUTLINE");

        var board = roles(tripId, people.get(0));
        assertThat(board.get("assignments").size()).isEqualTo(3);
        var drive = roleId(board, "운전");
        for (var a : board.get("assignments")) assertThat(a.get("roleId").asLong()).isNotEqualTo(drive);
    }

    @Test
    void 열다섯_명이면_칸이_모자란_만큼_미배정이고_역할을_더하면_다시_나눈다() throws Exception {
        var h = new LinkedHashMap<String, Object>();
        var people = room(15, h);
        var tripId = (long) h.get("tripId");
        IntStream.range(0, 15).forEach(i -> {
            try { survey(tripId, people.get(i), i < 2, "OUTLINE"); } catch (Exception e) { throw new RuntimeException(e); }
        });
        var board = roles(tripId, people.get(0));
        var slots = 0;
        for (var r : board.get("roles")) slots += r.get("capacity").asInt();
        assertThat(board.get("assignments").size()).isEqualTo(slots).isLessThan(15);

        // 방장이 '설거지'(정원 4)와 '방 정리'(정원 3)를 더하고 다시 추천받으면 15명 모두 역할이 생긴다
        call(people.get(0).session(), "POST", "/api/trips/" + tripId + "/roles",
                "{ \"title\": \"설거지\", \"required\": false, \"capacity\": 4, \"tasks\": [] }", 201);
        call(people.get(0).session(), "POST", "/api/trips/" + tripId + "/roles",
                "{ \"title\": \"방 정리\", \"required\": false, \"capacity\": 3, \"tasks\": [] }", 201);
        var again = call(people.get(0).session(), "POST", "/api/trips/" + tripId + "/roles/recommend", null, 200);
        assertThat(again.get("assignments").size()).isEqualTo(15);
    }
}
