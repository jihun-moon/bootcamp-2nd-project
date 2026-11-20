package com.tripmate.shopping;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예시 방(id 1): 8명, 2027-01-15 ~ 17(2박). 방장 재현(1), 민지(2), 도윤(3)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShoppingTest {

    @Autowired
    MockMvc mvc;

    private ResultActions claim(MockHttpSession s, String item, Integer amount) throws Exception {
        return mvc.perform(put("/api/trips/1/shopping/items/" + item).session(s)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"amount\": " + amount + " }"));
    }

    @Test
    void 여덟_명_2박이면_고기_술_숯_아침까지_양이_나온다() throws Exception {
        // 삼겹 8 × 300g × 0.7 × 2끼 = 3,360g → 3.4kg, 목살 1,440g → 1.5kg, 소주 8 × 0.7 × 2 = 11.2 → 12병
        mvc.perform(get("/api/trips/1/shopping").session(TestLogin.as(mvc, "민지")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people").value(8))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.items[?(@.key == 'PORK_BELLY')].quantity").value(hasItem("3.4kg")))
                .andExpect(jsonPath("$.items[?(@.key == 'PORK_NECK')].quantity").value(hasItem("1.5kg")))
                .andExpect(jsonPath("$.items[?(@.key == 'SOJU')].quantity").value(hasItem("12병")))
                .andExpect(jsonPath("$.items[?(@.key == 'BEER')].quantity").value(hasItem("32캔")))
                .andExpect(jsonPath("$.items[?(@.key == 'CHARCOAL')].quantity").value(hasItem("4봉")))
                .andExpect(jsonPath("$.items[?(@.key == 'RICE')].quantity").value(hasItem("16개")));
    }

    @Test
    void 방장이_술_안_마시는_사람과_고기_양을_고치면_다시_센다() throws Exception {
        mvc.perform(put("/api/trips/1/shopping/plan").session(TestLogin.as(mvc, "민지"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"drinkers\": 0, \"meatGrams\": 250 }"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/trips/1/shopping/plan").session(TestLogin.as(mvc, "재현"))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"drinkers\": 0, \"meatGrams\": 250 }"))
                .andExpect(status().isOk())
                // 8 × 250 × 0.7 × 2 = 2,800g, 술 마시는 사람이 없으면 술 줄은 빠진다
                .andExpect(jsonPath("$.items[?(@.key == 'PORK_BELLY')].quantity").value(hasItem("2.8kg")))
                .andExpect(jsonPath("$.items[*].key").value(not(hasItem("SOJU"))));
        mvc.perform(put("/api/trips/1/shopping/plan").session(TestLogin.as(mvc, "재현"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"drinkers\": 3, \"meatGrams\": 900 }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 내가_살게를_누르면_맡고_남이_맡은_줄은_409_쓴_돈은_1인_몫으로() throws Exception {
        var minji = TestLogin.as(mvc, "민지");
        claim(minji, "PORK_BELLY", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.key == 'PORK_BELLY')].buyerName").value(hasItem("민지")));
        claim(TestLogin.as(mvc, "도윤"), "PORK_BELLY", null).andExpect(status().isConflict());

        // 산 뒤 52,000원을 적으면 합계 52,000, 1인 6,500
        claim(minji, "PORK_BELLY", 52000).andExpect(jsonPath("$.spent").value(52000)).andExpect(jsonPath("$.perPerson").value(6500));
        claim(minji, "PORK_BELLY", -1).andExpect(status().isBadRequest());
    }

    @Test
    void 내려놓기는_맡은_사람이나_방장만() throws Exception {
        claim(TestLogin.as(mvc, "민지"), "CHARCOAL", null).andExpect(status().isOk());
        mvc.perform(delete("/api/trips/1/shopping/items/CHARCOAL").session(TestLogin.as(mvc, "도윤"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/trips/1/shopping/items/CHARCOAL").session(TestLogin.as(mvc, "재현")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.key == 'CHARCOAL')].buyerName").value(hasItem((Object) null)));
        mvc.perform(get("/api/trips/1/shopping/items/NOPE").session(TestLogin.as(mvc, "민지"))).andExpect(status().is4xxClientError());
    }
}
