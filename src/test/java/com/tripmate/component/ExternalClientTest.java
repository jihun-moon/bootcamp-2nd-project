package com.tripmate.component;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

// 카카오·오피넷 응답 JSON 을 우리 그릇이 제대로 읽는지. 실제 서버 대신 MockRestServiceServer 가 답한다
class ExternalClientTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();

    @Test
    void 주소로_못_찾으면_장소_이름으로_찾는다() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/address.json")))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess("{ \"documents\": [], \"meta\": { \"total_count\": 0 } }", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
                .andRespond(withSuccess("""
                        { "documents": [ { "place_name": "강남역 2호선", "x": "127.0276", "y": "37.4979" } ] }
                        """, MediaType.APPLICATION_JSON));

        var point = new KakaoMapClient(restTemplate, "test-key").find("강남역");

        assertThat(point).contains(new KakaoMapClient.Point(127.0276, 37.4979));
    }

    @Test
    void 길찾기_요약에서_거리_시간_통행료만_꺼낸다() {
        server.expect(requestTo(startsWith("https://apis-navi.kakaomobility.com/v1/directions")))
                .andRespond(withSuccess("""
                        { "trans_id": "x", "routes": [ { "result_code": 0, "result_msg": "길찾기 성공",
                          "summary": { "distance": 62310, "duration": 4210, "fare": { "taxi": 70000, "toll": 3200 } } } ] }
                        """, MediaType.APPLICATION_JSON));

        var drive = new KakaoMapClient(restTemplate, "test-key")
                .drive(new KakaoMapClient.Point(126.9, 37.5), new KakaoMapClient.Point(127.5, 37.8));

        assertThat(drive).contains(new KakaoMapClient.Drive(62310, 4210, 3200));
    }

    @Test
    void 길을_못_찾거나_서버가_실패하면_빈_값() {
        server.expect(requestTo(startsWith("https://apis-navi.kakaomobility.com/v1/directions")))
                .andRespond(withSuccess("{ \"routes\": [ { \"result_code\": 104, \"result_msg\": \"너무 가까움\" } ] }",
                        MediaType.APPLICATION_JSON));
        var client = new KakaoMapClient(restTemplate, "test-key");
        var a = new KakaoMapClient.Point(127.0, 37.5);

        assertThat(client.drive(a, a)).isEmpty();

        // 키가 틀리면(401) 주소·장소 두 길 다 막힌다. 둘 다 빈 값으로 넘기고 예외를 밖으로 내지 않는다
        server.reset();
        server.expect(ExpectedCount.times(2), requestTo(startsWith("https://dapi.kakao.com")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThat(client.find("가평")).isEqualTo(Optional.empty());
        server.verify();
    }

    @Test
    void 키가_없으면_부르지_않는다() {
        var client = new KakaoMapClient(restTemplate, "unset");

        assertThat(client.ready()).isFalse();
        assertThat(client.find("가평")).isEmpty();
        server.verify();
    }

    @Test
    void 오피넷은_text_html_로_와도_휘발유_값만_꺼내고_실패하면_어림값() {
        server.expect(requestTo(startsWith("https://www.opinet.co.kr/api/avgAllPrice.do")))
                .andRespond(withSuccess("""
                        { "RESULT": { "OIL": [
                          { "TRADE_DT": "20261120", "PRODCD": "B034", "PRODNM": "고급휘발유", "PRICE": "1950.10" },
                          { "TRADE_DT": "20261120", "PRODCD": "B027", "PRODNM": "휘발유", "PRICE": "1854.46" } ] } }
                        """, MediaType.TEXT_HTML));
        var mapper = JsonMapper.builder().build();
        var client = new FuelPriceClient(restTemplate, mapper, "test-key", 1700);

        assertThat(client.gasoline()).isEqualTo(new FuelPriceClient.Price(1854, "오피넷 전국 평균"));
        assertThat(new FuelPriceClient(restTemplate, mapper, "unset", 1700).gasoline())
                .isEqualTo(new FuelPriceClient.Price(1700, "어림값"));
    }
}
