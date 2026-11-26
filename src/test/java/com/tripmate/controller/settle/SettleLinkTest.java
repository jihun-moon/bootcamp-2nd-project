package com.tripmate.controller.settle;

import com.tripmate.TestLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 정산 받는 링크(/s/{code}). 예시 방(id 1) 8명, 재현(방장)이 숙소 480,000 을 냈다 → 나머지 7명이 재현에게 60,000 씩
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettleLinkTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    private String code(String who) throws Exception {
        var body = mvc.perform(put("/api/trips/1/settlement/link").session(TestLogin.as(mvc, who)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("code").asString();
    }

    private void stay() throws Exception {
        mvc.perform(post("/api/trips/1/expenses").session(TestLogin.as(mvc, "재현")).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"kind\": \"STAY\", \"memo\": \"숲속 독채\", \"amount\": 480000 }")).andExpect(status().isCreated());
    }

    @Test
    void 방_사람_누가_불러도_같은_코드이고_추측할_수_없는_길이다() throws Exception {
        var first = code("민지");
        assertThat(code("재현")).isEqualTo(first);
        assertThat(first).hasSize(22).matches("[A-Za-z0-9_-]+");
        mvc.perform(put("/api/trips/1/settlement/link").session(TestLogin.as(mvc, "남"))).andExpect(status().isNotFound());
    }

    @Test
    void 로그인_없이_누가_누구에게_얼마만_보인다() throws Exception {
        stay();
        var code = code("민지");
        mvc.perform(get("/s/" + code))   // 세션 없음
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("60,000원")))
                .andExpect(content().string(containsString("숲속 독채")))
                .andExpect(content().string(containsString("noindex")))
                // 방에 들어오는 초대 코드는 이 화면에 없다(정산 링크로 방에 들어올 수 없게)
                .andExpect(content().string(not(containsString("/join/"))));
    }

    @Test
    void 틀린_코드와_지운_방은_404() throws Exception {
        mvc.perform(get("/s/AAAAAAAAAAAAAAAAAAAAAA")).andExpect(status().isNotFound());
        var code = code("민지");
        mvc.perform(delete("/api/trips/1").session(TestLogin.as(mvc, "재현"))).andExpect(status().isNoContent());
        mvc.perform(get("/s/" + code)).andExpect(status().isNotFound());
    }
}
