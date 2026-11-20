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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 미리 걷는 회비. 예시 방(id 1) 8명: 1 재현(방장), 2 민지, 3 도윤(총무로 정함), 4 서연, 5 하준, 6 지우, 7 예린, 8 시우.
// 1인 100,000 을 도윤에게 걷고, 도윤이 회비로 숙소 560,000 을 냈다 → 1인 몫 70,000, 낸 7명은 30,000 씩 돌려받는다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DuesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String[] OTHERS = {"재현", "민지", "서연", "하준", "지우", "예린", "시우"};

    @Autowired
    MockMvc mvc;

    private ResultActions setDues(String who, int amount, long collectorId) throws Exception {
        return mvc.perform(put("/api/trips/1/dues").session(TestLogin.as(mvc, who)).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"amount\": " + amount + ", \"collectorId\": " + collectorId + " }"));
    }

    private ResultActions paid(String who, long memberId) throws Exception {
        return mvc.perform(put("/api/trips/1/dues/paid/" + memberId).session(TestLogin.as(mvc, who)));
    }

    private JsonNode board() throws Exception {
        return JSON.readTree(mvc.perform(get("/api/trips/1/settlement").session(TestLogin.as(mvc, "시우")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void 방장이_정하고_본인이나_총무가_냈어요를_누른다() throws Exception {
        setDues("민지", 100_000, 3).andExpect(status().isForbidden());
        setDues("재현", 100_000, 3).andExpect(status().isOk()).andExpect(jsonPath("$.dues.collectorName").value("도윤"));

        paid("민지", 2).andExpect(status().isOk());
        paid("민지", 2).andExpect(status().isOk());                 // 두 번 눌러도 한 번
        paid("서연", 2).andExpect(status().isForbidden());          // 남의 것은 총무·방장만
        paid("도윤", 5).andExpect(status().isOk());                 // 총무가 받음 표시
        paid("도윤", 3).andExpect(status().isBadRequest());         // 총무 몫은 이미 총무에게

        var dues = board().get("dues");
        assertThat(dues.get("collected").asInt()).isEqualTo(200_000);
        assertThat(dues.get("paidIds").size()).isEqualTo(2);
        // 여행 전이라 걷는 중. 화면은 "돌려줄 줄" 대신 안내를 보인다
        assertThat(dues.get("pending").asBoolean()).isTrue();

        mvc.perform(delete("/api/trips/1/dues/paid/2").session(TestLogin.as(mvc, "민지"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.dues.paidIds.length()").value(1));
        // 총무를 바꾸는 건 방장이나 지금 총무
        setDues("도윤", 120_000, 3).andExpect(status().isOk()).andExpect(jsonPath("$.dues.amount").value(120_000));
    }

    @Test
    void 다녀오면_남은_회비를_총무가_나눠_돌려준다() throws Exception {
        mvc.perform(put("/api/trips/1/period").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"startDate\": \"" + LocalDate.now().minusDays(3) + "\", \"endDate\": \"" + LocalDate.now().minusDays(1) + "\" }"))
                .andExpect(status().isOk());
        setDues("재현", 100_000, 3).andExpect(status().isOk());
        long[] ids = {1, 2, 4, 5, 6, 7, 8};
        for (var id : ids) paid("도윤", id).andExpect(status().isOk());
        mvc.perform(post("/api/trips/1/expenses").session(TestLogin.as(mvc, "도윤")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"kind\": \"STAY\", \"memo\": \"회비로 숙소\", \"amount\": 560000 }")).andExpect(status().isCreated());

        var b = board();
        assertThat(b.get("dues").get("pending").asBoolean()).isFalse();
        assertThat(b.get("total").asInt()).isEqualTo(560_000);   // 회비는 쓴 돈이 아니다
        var transfers = b.get("transfers");
        assertThat(transfers.size()).isEqualTo(7);
        for (var t : transfers) {
            assertThat(t.get("fromName").asString()).isEqualTo("도윤");
            assertThat(t.get("amount").asInt()).isEqualTo(30_000);
        }
        assertThat(transfers.findValues("toName").stream().map(JsonNode::asString).toList()).containsExactlyInAnyOrder(OTHERS);
    }

    @Test
    void 총무가_나가면_회비_설정만_지우고_낸_돈은_정산에_남는다() throws Exception {
        setDues("재현", 100_000, 3).andExpect(status().isOk());
        paid("민지", 2).andExpect(status().isOk());
        mvc.perform(delete("/api/trips/1/members/3").session(TestLogin.as(mvc, "도윤"))).andExpect(status().isNoContent());
        var b = board();
        assertThat(b.get("dues").isNull()).isTrue();
        // 민지가 낸 100,000 은 나간 도윤이 돌려줄 돈으로 남는다
        var fromDoyun = 0;
        for (var t : b.get("transfers")) if (t.get("fromName").asString().startsWith("도윤")) fromDoyun += t.get("amount").asInt();
        assertThat(fromDoyun).isEqualTo(100_000);
    }
}
