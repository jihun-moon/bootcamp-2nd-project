package com.tripmate.settle;

import com.tripmate.TestLogin;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 다녀와서 누가 누구에게 얼마. 예시 방(id 1) 8명: 1 재현(방장), 2 민지, 3 도윤, 4 서연 … 8 시우.
// 재현이 숙소 480,000, 민지가 기름값·톨비 64,000(운전), 도윤이 장보기 소주 16,000 을 냈다.
// 규칙 "운전자는 기름값 안 냄"이면: 숙소 60,000 + 장보기 2,000 은 모두, 기름값 9,142.9 는 민지 빼고 7명
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettleTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    private ResultActions pay(String who, String body) throws Exception {
        return mvc.perform(post("/api/trips/1/expenses").session(TestLogin.as(mvc, who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode board(String who) throws Exception {
        return JSON.readTree(mvc.perform(get("/api/trips/1/settlement").session(TestLogin.as(mvc, who)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void story(Boolean driverPaysFuel) throws Exception {
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"driverPaysFuel\": " + driverPaysFuel + ", \"refundNoShow\": null, \"drinksSeparate\": null }"));
        pay("재현", "{ \"kind\": \"STAY\", \"memo\": \"숲속 독채\", \"amount\": 480000 }").andExpect(status().isCreated());
        pay("민지", "{ \"kind\": \"FUEL\", \"amount\": 64000 }").andExpect(status().isCreated());
        mvc.perform(put("/api/trips/1/shopping/items/SOJU").session(TestLogin.as(mvc, "도윤"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"amount\": 16000 }"));
    }

    @Test
    void 운전자를_빼고_나눈_기름값까지_보낼_줄이_나온다() throws Exception {
        story(false);
        var b = board("시우");
        assertThat(b.get("total").asInt()).isEqualTo(560_000);

        var sent = new HashMap<String, Integer>();
        for (var t : b.get("transfers")) sent.merge(t.get("fromName").asString() + "→" + t.get("toName").asString(), t.get("amount").asInt(), Integer::sum);
        // 아무것도 안 낸 다섯은 재현에게 60,000 + 9,142.9 + 2,000 = 71,142.9 → 100원 올림 71,200
        for (var name : new String[]{"서연", "하준", "지우", "예린", "시우"}) assertThat(sent.get(name + "→재현")).isEqualTo(71_200);
        // 도윤은 몫 71,142.9 - 낸 16,000 = 55,142.9 를 재현(53,142.9 남음)과 민지(2,000)에게 나눠 보낸다
        assertThat(sent.get("도윤→재현")).isEqualTo(53_200);
        assertThat(sent.get("도윤→민지")).isEqualTo(2_000);
        assertThat(b.get("transfers").size()).isEqualTo(7);
    }

    @Test
    void 운전자도_기름값을_내는_규칙이면_8명이_똑같이() throws Exception {
        story(true);
        var b = board("시우");
        // 60,000 + 8,000 + 2,000 = 70,000
        for (var t : b.get("transfers")) if (t.get("fromName").asString().equals("시우")) assertThat(t.get("amount").asInt()).isEqualTo(70_000);
        for (var bal : b.get("balances")) if (bal.get("name").asString().equals("민지")) assertThat(bal.get("diff").asInt()).isEqualTo(-6_000);
    }

    @Test
    void 남이_적은_돈은_못_지우고_낸_사람이나_방장은_지운다() throws Exception {
        var id = JSON.readTree(pay("민지", "{ \"kind\": \"OTHER\", \"memo\": \"휴게소\", \"amount\": 9000 }")
                .andReturn().getResponse().getContentAsString()).get("lines").get(0).get("id").asLong();
        mvc.perform(delete("/api/trips/1/expenses/" + id).session(TestLogin.as(mvc, "도윤"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/trips/1/expenses/" + id).session(TestLogin.as(mvc, "재현")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void 금액이_0이거나_종류가_없으면_받지_않는다() throws Exception {
        pay("민지", "{ \"kind\": \"FOOD\", \"amount\": 0 }").andExpect(status().isBadRequest());
        pay("민지", "{ \"amount\": 5000 }").andExpect(status().isBadRequest());
        mvc.perform(get("/api/trips/1/settlement").session(TestLogin.as(mvc, "모르는사람"))).andExpect(status().isNotFound());
    }

    @Test
    void 방을_나간_사람이_적은_돈은_빠지고_남은_사람끼리_나눈다() throws Exception {
        pay("민지", "{ \"kind\": \"FOOD\", \"amount\": 70000 }");
        pay("재현", "{ \"kind\": \"FOOD\", \"amount\": 14000 }");
        mvc.perform(delete("/api/trips/1/members/2").session(TestLogin.as(mvc, "민지"))).andExpect(status().isNoContent());
        var b = board("시우");
        assertThat(b.get("total").asInt()).isEqualTo(14_000);
        assertThat(b.get("balances").size()).isEqualTo(7);
        for (var t : b.get("transfers")) assertThat(t.get("amount").asInt()).isEqualTo(2_000);
    }
}
