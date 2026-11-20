package com.tripmate.role;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

// 오피넷 전국 평균 휘발유 값. 키가 없거나 못 받으면 설정의 어림값을 쓰고, 어느 쪽인지 화면에 같이 적는다
@Component
public class FuelPriceClient {

    private static final String UNSET = "unset";
    private static final String GASOLINE = "B027";
    private static final String URL = "https://www.opinet.co.kr/api/avgAllPrice.do?out=json&code={key}";

    public record Price(int wonPerLiter, String source) {
    }

    // { "RESULT": { "OIL": [ { "PRODCD": "B027", "PRODNM": "휘발유", "PRICE": "1690.52" } ] } }
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
    private final String key;
    private final int defaultPrice;

    public FuelPriceClient(RestTemplate restTemplate, @Value("${tripmate.opinet.key}") String key,
                           @Value("${tripmate.fuel.default-price}") int defaultPrice) {
        this.restTemplate = restTemplate;
        this.key = key;
        this.defaultPrice = defaultPrice;
    }

    public Price gasoline() {
        var fallback = new Price(defaultPrice, "어림값");
        if (UNSET.equals(key)) return fallback;
        try {
            var body = restTemplate.getForObject(URL, Body.class, key);
            if (body == null || body.result() == null || body.result().oil() == null) return fallback;
            return body.result().oil().stream()
                    .filter(o -> GASOLINE.equals(o.code()))
                    .findFirst()
                    .map(o -> new Price((int) Math.round(Double.parseDouble(o.price())), "오피넷 전국 평균"))
                    .orElse(fallback);
        } catch (RestClientException | NumberFormatException e) {
            return fallback;
        }
    }
}
