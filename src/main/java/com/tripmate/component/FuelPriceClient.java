package com.tripmate.component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

// 오피넷 전국 평균 휘발유 값. 키가 없거나 못 받으면 설정의 어림값을 쓰고, 어느 쪽인지 화면에 같이 적는다
@Component
public class FuelPriceClient {

    private static final String UNSET = "unset";
    private static final String GASOLINE = "B027";
    private static final String URL = "https://www.opinet.co.kr/api/avgAllPrice.do?out=json&code={key}";

    public record Price(int wonPerLiter, String source) {
    }

    // { "RESULT": { "OIL": [ { "PRODCD": "B027", "PRODNM": "휘발유", "PRICE": "1854.46" } ] } }
    // 오피넷은 JSON 을 보내면서 Content-Type 을 text/html 로 붙인다. 그래서 RestTemplate 이 그릇으로 못 바꿔
    // 문자열로 받고 JsonMapper 로 직접 읽는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(@JsonProperty("RESULT") Result result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(@JsonProperty("OIL") List<Oil> oil) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Oil(@JsonProperty("PRODCD") String code, @JsonProperty("PRICE") String price) {
    }

    private final RestTemplate restTemplate;
    private final JsonMapper jsonMapper;
    private final String key;
    private final int defaultPrice;

    public FuelPriceClient(RestTemplate restTemplate, JsonMapper jsonMapper, @Value("${tripmate.opinet.key}") String key,
                           @Value("${tripmate.fuel.default-price}") int defaultPrice) {
        this.restTemplate = restTemplate;
        this.jsonMapper = jsonMapper;
        this.key = key;
        this.defaultPrice = defaultPrice;
    }

    public Price gasoline() {
        var fallback = new Price(defaultPrice, "어림값");
        if (UNSET.equals(key)) return fallback;
        try {
            var text = restTemplate.getForObject(URL, String.class, key);
            if (text == null) return fallback;
            var body = jsonMapper.readValue(text, Body.class);
            if (body == null || body.result() == null || body.result().oil() == null) return fallback;
            return body.result().oil().stream()
                    .filter(o -> GASOLINE.equals(o.code()))
                    .findFirst()
                    .map(o -> new Price((int) Math.round(Double.parseDouble(o.price())), "오피넷 전국 평균"))
                    .orElse(fallback);
        } catch (RestClientException | JacksonException | NumberFormatException e) {
            return fallback;
        }
    }
}
