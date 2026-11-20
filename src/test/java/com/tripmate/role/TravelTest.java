package com.tripmate.role;

import com.tripmate.TestLogin;
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

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예시 방(id 1, 8명). 카카오·오피넷은 가짜로 바꿔 인터넷 없이 돈다.
// 가짜 길: 편도 100km, 2시간 10분, 통행료 5,000원. 휘발유 1,700원/L, 연비 12km/L(application.properties)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TravelTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KakaoMapClient kakaoMapClient;

    @MockitoBean
    FuelPriceClient fuelPriceClient;

    MockHttpSession host;
    MockHttpSession minji;

    @BeforeEach
    void setUp() throws Exception {
        host = TestLogin.as(mvc, "재현");
        minji = TestLogin.as(mvc, "민지");
        when(kakaoMapClient.ready()).thenReturn(true);
        when(kakaoMapClient.find(anyString())).thenReturn(Optional.of(new KakaoMapClient.Point(127.0, 37.5)));
        when(kakaoMapClient.drive(any(), any())).thenReturn(Optional.of(new KakaoMapClient.Drive(100_000, 7_800, 5_000)));
        when(fuelPriceClient.gasoline()).thenReturn(new FuelPriceClient.Price(1_700, "어림값"));
    }

    private void survey(int memberId, MockHttpSession session, boolean hasCar, String departure) throws Exception {
        var body = """
                { "activity": "BOTH", "planning": "OUTLINE", "hasCar": %s, "separateDeparture": false,
                  "departure": %s, "mobilityLimited": false, "avoid": [] }
                """.formatted(hasCar, departure == null ? "null" : "\"" + departure + "\"");
        mvc.perform(put("/api/trips/1/members/" + memberId + "/survey").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void 운전하는_차의_왕복_기름값과_통행료를_사람_수로_나눈다() throws Exception {
        survey(1, host, true, "서울 마포구");
        survey(2, minji, true, "성남시 분당구");
        mvc.perform(post("/api/trips/1/roles/recommend").session(host)).andExpect(status().isOk());

        // 기름 100km / 12 * 1700 = 14,166 → 14,200(100원 올림). 차 한 대 왕복 (14,200 + 5,000) * 2 = 38,400
        // 두 대 76,800 / 8명 = 9,600
        mvc.perform(get("/api/trips/1/roles/travel").session(minji))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs", hasSize(2)))
                .andExpect(jsonPath("$.legs[0].driver").value(true))
                .andExpect(jsonPath("$.legs[0].km").value(100))
                .andExpect(jsonPath("$.legs[0].minutes").value(130))
                .andExpect(jsonPath("$.legs[0].fuel").value(14_200))
                .andExpect(jsonPath("$.perPerson").value(9_600))
                .andExpect(jsonPath("$.tips", hasSize(2)))
                .andExpect(jsonPath("$.tips[0]", containsString("편도 2시간 10분")))
                .andExpect(jsonPath("$.legs[0].departure").doesNotExist());
    }

    @Test
    void 운전이_안_정해졌으면_차마다_길만_보이고_1인_교통비는_비운다() throws Exception {
        survey(1, host, true, "서울 마포구");

        mvc.perform(get("/api/trips/1/roles/travel").session(host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs", hasSize(1)))
                .andExpect(jsonPath("$.legs[0].driver").value(false))
                .andExpect(jsonPath("$.perPerson").doesNotExist())
                .andExpect(jsonPath("$.tips[0]", containsString("운전 칸")));
    }

    @Test
    void 출발_동네가_없으면_그_차만_까닭을_적는다() throws Exception {
        survey(1, host, true, null);

        mvc.perform(get("/api/trips/1/roles/travel").session(host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].km").doesNotExist())
                .andExpect(jsonPath("$.legs[0].note", containsString("출발 동네")));
    }

    @Test
    void 차를_가져가는_사람이_없으면_안내만_준다() throws Exception {
        mvc.perform(get("/api/trips/1/roles/travel").session(host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("차를 가져가는 사람")))
                .andExpect(jsonPath("$.legs", hasSize(0)));
    }

    @Test
    void 키가_없으면_안내만_준다() throws Exception {
        when(kakaoMapClient.ready()).thenReturn(false);
        survey(1, host, true, "서울 마포구");

        mvc.perform(get("/api/trips/1/roles/travel").session(host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("길찾기를 쓸 수 없어")));
    }

    @Test
    void 출발_동네는_본인과_방장만_보고_차가_없으면_남기지_않는다() throws Exception {
        survey(2, minji, true, "성남시 분당구");
        survey(3, TestLogin.as(mvc, "도윤"), false, "서울 강서구");

        mvc.perform(get("/api/trips/1/members/2/survey").session(minji))
                .andExpect(jsonPath("$.departure").value("성남시 분당구"));
        mvc.perform(get("/api/trips/1/members/2/survey").session(host))
                .andExpect(jsonPath("$.departure").value("성남시 분당구"));
        mvc.perform(get("/api/trips/1/members/2/survey").session(TestLogin.as(mvc, "도윤")))
                .andExpect(jsonPath("$.departure").doesNotExist());
        mvc.perform(get("/api/trips/1/members/3/survey").session(host))
                .andExpect(jsonPath("$.departure").doesNotExist());
    }

    @Test
    void 방_참여자가_아니면_못_본다() throws Exception {
        mvc.perform(get("/api/trips/999/roles/travel").session(host))
                .andExpect(status().isNotFound());
    }
}
