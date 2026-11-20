package com.tripmate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// 화면(Thymeleaf)이 템플릿 오류 없이 그려지는지만 본다. 데이터는 화면의 jQuery 가 API 로 읽으니 API 테스트가 맡는다
@SpringBootTest
@AutoConfigureMockMvc
class PageTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 로그인_전_화면() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("user/login"));
        mvc.perform(get("/login").param("error", "")).andExpect(content().string(containsString("카카오 로그인에 실패")));
        mvc.perform(get("/login/done")).andExpect(status().isOk());
        mvc.perform(get("/trips/new")).andExpect(status().isOk()).andExpect(view().name("trip/new"));
        // 체험 방은 로그인 없이 열린다
        mvc.perform(get("/demo")).andExpect(status().isOk()).andExpect(view().name("trip/demo"))
                .andExpect(content().string(containsString("체험 방이에요")));
        mvc.perform(get("/join/abc123")).andExpect(content().string(containsString("data-invite-code=\"abc123\"")));
    }

    @Test
    void 방_화면은_탭과_방_번호가_들어간다() throws Exception {
        mvc.perform(get("/trips/1")).andExpect(view().name("trip/overview"))
                .andExpect(content().string(containsString("data-trip-id=\"1\"")))
                .andExpect(content().string(containsString("href=\"/trips/1/roles\"")));
        mvc.perform(get("/trips/1/survey")).andExpect(view().name("survey/survey"));
        mvc.perform(get("/trips/1/roles")).andExpect(view().name("role/roles"));
        mvc.perform(get("/trips/1/stays")).andExpect(view().name("stay/stays"));
        mvc.perform(get("/trips/1/receipt")).andExpect(view().name("trip/receipt"));
        mvc.perform(get("/trips/1/shopping")).andExpect(view().name("shopping/shopping"));
    }

    @Test
    void 정적_파일() throws Exception {
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mvc.perform(get("/js/common.js")).andExpect(status().isOk());
    }

    @Test
    void 첫_화면_이렇게_굴러가요는_실제_화면_아홉_장() throws Exception {
        var html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        for (var shot : new String[]{"join", "dates", "roles", "stays", "shopping", "dues", "receipt", "settle", "next"}) {
            assertThat(html).contains("/img/tour/" + shot + "-"); // 파일 이름 뒤에 내용 해시가 붙는다(캐시 깨기)
            mvc.perform(get("/img/tour/" + shot + ".webp")).andExpect(status().isOk());
        }
    }
}
