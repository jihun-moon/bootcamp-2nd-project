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
    void 먼저_낸_사람이_나가도_그_돈은_남아_돌려받는다() throws Exception {
        // 리뷰 #10: 전에는 나가면 그 사람이 낸 돈이 정산에서 사라져 못 돌려받았다
        pay("민지", "{ \"kind\": \"FOOD\", \"amount\": 70000 }");
        pay("재현", "{ \"kind\": \"FOOD\", \"amount\": 14000 }");
        mvc.perform(delete("/api/trips/1/members/2").session(TestLogin.as(mvc, "민지"))).andExpect(status().isNoContent());
        var b = board("시우");
        assertThat(b.get("total").asInt()).isEqualTo(84_000);
        // 식비는 남은 7명이 12,000씩. 민지(나감)는 몫 0, 낸 70,000 을 돌려받는다
        var toMinji = 0;
        for (var t : b.get("transfers")) if (t.get("toName").asString().equals("민지(나감)")) toMinji += t.get("amount").asInt();
        assertThat(toMinji).isEqualTo(70_000);
        assertThat(b.get("balances").size()).isEqualTo(8);

        // 나간 민지에게 보낼 줄도 "보냈어요"로 끝낼 수 있다(전에는 400)
        for (var t : b.get("transfers")) {
            if (t.get("toId").asInt() != 2) continue;
            markPaid("재현", t.get("fromId").asInt(), 2, t.get("amount").asInt()).andExpect(status().isCreated());
            break;
        }
        assertThat(board("시우").get("payments").size()).isEqualTo(1);
    }

    @Test
    void 예약금_안_돌려줘요_규칙이면_나간_사람도_숙소_몫을_낸다() throws Exception {
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"driverPaysFuel\": null, \"refundNoShow\": false, \"drinksSeparate\": null }"));
        pay("재현", "{ \"kind\": \"STAY\", \"amount\": 80000 }");
        mvc.perform(delete("/api/trips/1/members/8").session(TestLogin.as(mvc, "재현"))).andExpect(status().isNoContent());
        var b = board("민지");
        var fromSiwoo = 0;
        for (var t : b.get("transfers")) if (t.get("fromName").asString().equals("시우(나감)")) fromSiwoo += t.get("amount").asInt();
        assertThat(fromSiwoo).isEqualTo(10_000);   // 8명 몫 10,000 을 그대로 진다
        assertThat(b.get("refundNoShow").asBoolean()).isFalse();
    }

    @Test
    void 예약금_돌려줘요_규칙이면_나간_사람_몫은_남은_사람이_나눈다() throws Exception {
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"driverPaysFuel\": null, \"refundNoShow\": true, \"drinksSeparate\": null }"));
        pay("재현", "{ \"kind\": \"STAY\", \"amount\": 70000 }");
        mvc.perform(delete("/api/trips/1/members/8").session(TestLogin.as(mvc, "재현"))).andExpect(status().isNoContent());
        for (var t : board("민지").get("transfers")) {
            assertThat(t.get("fromName").asString()).doesNotContain("시우");
            assertThat(t.get("amount").asInt()).isEqualTo(10_000);   // 남은 7명이 10,000씩
        }
    }

    private void drinkStory(boolean drinksSeparate) throws Exception {
        mvc.perform(put("/api/trips/1/rules").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"driverPaysFuel\": null, \"refundNoShow\": null, \"drinksSeparate\": " + drinksSeparate + " }"));
        // 예린(7)은 본인이 끄고, 시우(8)는 방장이 대신 끈다
        mvc.perform(put("/api/trips/1/members/7/drinks").session(TestLogin.as(mvc, "예린"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"drinks\": false }")).andExpect(status().isOk());
        mvc.perform(put("/api/trips/1/members/8/drinks").session(TestLogin.as(mvc, "재현"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"drinks\": false }")).andExpect(status().isOk());
        pay("민지", "{ \"kind\": \"DRINK\", \"memo\": \"편의점 술\", \"amount\": 60000 }").andExpect(status().isCreated());
        mvc.perform(put("/api/trips/1/shopping/items/SOJU").session(TestLogin.as(mvc, "도윤"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"amount\": 12000 }"));
    }

    @Test
    void 술값은_마신_사람만_규칙이면_안_마신_사람은_안_낸다() throws Exception {
        drinkStory(true);
        var b = board("재현");
        assertThat(b.get("drinksSeparate").asBoolean()).isTrue();
        // 마신 6명이 60,000 + 12,000 을 나눈다 → 1인 12,000. 시우·예린은 0
        for (var bal : b.get("balances")) {
            var name = bal.get("name").asString();
            if (name.equals("시우") || name.equals("예린")) assertThat(bal.get("share").asInt()).isZero();
            if (name.equals("재현")) assertThat(bal.get("share").asInt()).isEqualTo(12_000);
        }
        for (var t : b.get("transfers")) assertThat(t.get("fromName").asString()).isNotIn("시우", "예린");
        // 장보기 술 양도 마시는 6명으로 센다(방장이 따로 정하지 않았으니)
        mvc.perform(get("/api/trips/1/shopping").session(TestLogin.as(mvc, "시우"))).andExpect(jsonPath("$.drinkers").value(6));
    }

    @Test
    void 규칙을_안_켰으면_술값도_모두_똑같이() throws Exception {
        drinkStory(false);
        for (var bal : board("재현").get("balances")) assertThat(bal.get("share").asInt()).isEqualTo(9_000);
    }

    @Test
    void 남의_술_표시는_방장만_바꾼다() throws Exception {
        mvc.perform(put("/api/trips/1/members/8/drinks").session(TestLogin.as(mvc, "민지"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"drinks\": false }")).andExpect(status().isForbidden());
    }

    @Test
    void 고른_사람끼리만_나누는_돈은_그_사람들만_낸다() throws Exception {
        // 재현·민지 커플 저녁 40,000 → 둘이 20,000씩. 나머지 여섯은 이 돈과 상관없다
        var b = JSON.readTree(pay("재현", "{ \"kind\": \"FOOD\", \"memo\": \"둘이 저녁\", \"amount\": 40000, \"sharerIds\": [1, 2] }")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(b.get("lines").get(0).get("sharedBy").asInt()).isEqualTo(2);
        assertThat(b.get("transfers").size()).isEqualTo(1);
        var t = b.get("transfers").get(0);
        assertThat(t.get("fromName").asString() + "→" + t.get("toName").asString() + " " + t.get("amount").asInt()).isEqualTo("민지→재현 20000");
        for (var bal : b.get("balances")) if (bal.get("name").asString().equals("시우")) assertThat(bal.get("share").asInt()).isZero();
    }

    @Test
    void 방에_없는_사람은_나눌_사람으로_못_고른다() throws Exception {
        pay("재현", "{ \"kind\": \"FOOD\", \"amount\": 10000, \"sharerIds\": [1, 9999] }").andExpect(status().isBadRequest());
    }

    private ResultActions markPaid(String who, int from, int to, int amount) throws Exception {
        return mvc.perform(post("/api/trips/1/payments").session(TestLogin.as(mvc, who)).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"fromId\": " + from + ", \"toId\": " + to + ", \"amount\": " + amount + " }"));
    }

    @Test
    void 보냈어요_받았어요를_누르면_남은_줄에서_빠지고_되돌릴_수_있다() throws Exception {
        pay("재현", "{ \"kind\": \"FOOD\", \"amount\": 80000 }");
        assertThat(board("시우").get("transfers").size()).isEqualTo(7);

        markPaid("민지", 2, 1, 10000).andExpect(status().isCreated());                 // 보낸 사람이 "보냈어요"
        var b = JSON.readTree(markPaid("재현", 3, 1, 10000).andExpect(status().isCreated())  // 받은 사람이 "받았어요"
                .andReturn().getResponse().getContentAsString());
        assertThat(b.get("transfers").size()).isEqualTo(5);
        assertThat(b.get("payments").size()).isEqualTo(2);
        for (var t : b.get("transfers")) assertThat(t.get("fromName").asString()).isNotIn("민지", "도윤");

        markPaid("시우", 4, 1, 10000).andExpect(status().isForbidden());                // 남의 줄은 못 누른다
        markPaid("재현", 1, 1, 10000).andExpect(status().isBadRequest());

        // 잘못 누른 것 되돌리기
        var paymentId = b.get("payments").get(0).get("id").asLong();
        mvc.perform(delete("/api/trips/1/payments/" + paymentId).session(TestLogin.as(mvc, "시우"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/trips/1/payments/" + paymentId).session(TestLogin.as(mvc, "민지")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transfers.length()").value(6));
    }

    @Test
    void 다_보내면_남은_줄이_없다_100원_올림_자투리는_끝난_것으로() throws Exception {
        pay("재현", "{ \"kind\": \"FOOD\", \"amount\": 70000 }");   // 8명 → 8,750, 보낼 줄은 8,800
        for (var t : board("재현").get("transfers")) {
            markPaid("재현", t.get("fromId").asInt(), t.get("toId").asInt(), t.get("amount").asInt()).andExpect(status().isCreated());
        }
        assertThat(board("시우").get("transfers").size()).isZero();
    }
}
