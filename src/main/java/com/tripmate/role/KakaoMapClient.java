package com.tripmate.role;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

// 카카오 로컬(동네 이름 → 좌표)과 카카오모빌리티 길찾기(좌표 → 거리·시간·통행료).
// 둘 다 로그인에 쓰는 같은 REST API 키로 부른다. 콘솔에서 카카오맵·길찾기를 켜 둬야 한다.
// 이동 근거는 있으면 좋은 것이라 키가 없거나 실패하면 빈 값을 주고 화면은 그대로 둔다
@Component
public class KakaoMapClient {

    private static final String UNSET = "unset";
    private static final String ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json?query={query}&size=1";
    private static final String KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json?query={query}&size=1";
    private static final String DIRECTIONS_URL =
            "https://apis-navi.kakaomobility.com/v1/directions?origin={origin}&destination={destination}&summary=true";

    public record Point(double x, double y) {
        String param() { return x + "," + y; }
    }

    public record Drive(int meters, int seconds, int toll) {
    }

    // { "documents": [ { "address_name": "경기 가평군", "x": "127.5096", "y": "37.8315" } ] }
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Places(List<Place> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String x, String y) {
    }

    // { "routes": [ { "result_code": 0, "summary": { "distance": 62310, "duration": 4210, "fare": { "toll": 3200 } } } ] }
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Directions(List<Route> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(@JsonProperty("result_code") int resultCode, Summary summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summary(int distance, int duration, Fare fare) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fare(int toll) {
    }

    private final RestTemplate restTemplate;
    private final String restKey;

    public KakaoMapClient(RestTemplate restTemplate, @Value("${tripmate.kakao.rest-key}") String restKey) {
        this.restTemplate = restTemplate;
        this.restKey = restKey;
    }

    public boolean ready() {
        return !UNSET.equals(restKey);
    }

    // 방 지역("가평")·출발 동네("서울 마포구")는 주소로 먼저 찾고, "강남역"처럼 주소가 아니면 장소 이름으로 찾는다
    public Optional<Point> find(String query) {
        for (var url : List.of(ADDRESS_URL, KEYWORD_URL)) {
            var places = get(url, Places.class, query);
            if (places.isPresent() && places.get().documents() != null && !places.get().documents().isEmpty()) {
                var first = places.get().documents().get(0);
                return Optional.of(new Point(Double.parseDouble(first.x()), Double.parseDouble(first.y())));
            }
        }
        return Optional.empty();
    }

    // result_code 가 0 이 아니면(출발·도착이 너무 가깝거나 길이 없음) 길을 못 찾은 것이다
    public Optional<Drive> drive(Point from, Point to) {
        return get(DIRECTIONS_URL, Directions.class, from.param(), to.param())
                .filter(d -> d.routes() != null && !d.routes().isEmpty() && d.routes().get(0).resultCode() == 0)
                .map(d -> d.routes().get(0).summary())
                .map(s -> new Drive(s.distance(), s.duration(), s.fare() == null ? 0 : s.fare().toll()));
    }

    private <T> Optional<T> get(String url, Class<T> type, Object... params) {
        if (!ready()) return Optional.empty();
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + restKey);
        try {
            return Optional.ofNullable(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), type, params).getBody());
        } catch (RestClientException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
