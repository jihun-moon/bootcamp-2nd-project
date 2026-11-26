package com.tripmate.dto;

import com.tripmate.entity.stay.Amenity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StayDto {

    private StayDto() {
    }

    // url 에는 앱 공유 글을 통째로 넣어도 된다(ShareText 가 링크와 이름을 뽑는다). 그래서 링크가 들어 있기만 보면 된다
    private static final String SHARED = "(?s).*https?://\\S+.*";

    // { "url": "https://example.com/stay/1", "title": "숲속 독채", "amenities": ["DETACHED", "PRIVATE_BBQ"] }
    // title 을 비우면 링크의 og:title, 그것도 없으면 공유 글의 이름을 쓴다. 올린 사람은 로그인한 본인
    public record CreateRequest(@NotBlank @Size(max = 2000) @Pattern(regexp = SHARED) String url,
                                @Size(max = 200) String title,
                                @PositiveOrZero @Max(100_000_000) Integer price,
                                Set<Amenity> amenities) {
    }

    // POST /api/trips/1/stays/preview  { "url": "https://..." }
    public record PreviewRequest(@NotBlank @Size(max = 2000) @Pattern(regexp = SHARED) String url) {
    }

    // -> { "title": "가평 숲속 독채", "imageUrl": "https://...", "amenities": ["DETACHED", "PRIVATE_BBQ"] }
    // price 는 링크가 알려 준 1박 가격(원). 날짜마다 달라 "링크 기준"으로만 보인다
    public record Preview(String title, String imageUrl, Integer price, Set<Amenity> amenities) {
    }

    // { "stayId": 3 }   stayId null = 표 취소
    public record VoteRequest(Long stayId) {
    }

    // amenities 는 칸 순서와 한글 이름. 화면(stays.js)이 이름을 따로 들고 있지 않게 여기서 준다
    public record Board(List<AmenityView> amenities, Map<Amenity, Preference> preferences, int surveyed,
                        List<StayView> stays) {
    }

    public record AmenityView(Amenity key, String label) {
    }

    // 평균만 보면 4:4 로 갈린 조건이 무난해 보여서 가장 낮게 준 점수를 같이 준다
    public record Preference(double avg, int min) {
    }

    public record StayView(Long id, String title, String url, String imageUrl, Integer price, Set<Amenity> amenities,
                           List<Long> voterIds) {
    }
}
