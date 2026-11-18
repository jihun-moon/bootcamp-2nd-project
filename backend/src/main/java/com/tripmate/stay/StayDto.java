package com.tripmate.stay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StayDto {

    private StayDto() {
    }

    // { "url": "https://example.com/stay/1", "title": "숲속 독채", "amenities": ["DETACHED", "PRIVATE_BBQ"], "addedBy": 2 }
    // title 을 비우면 링크의 og:title 을 쓴다
    public record CreateRequest(@NotBlank @Size(max = 2000) @Pattern(regexp = "https?://\\S+") String url,
                                @Size(max = 200) String title,
                                Set<Amenity> amenities,
                                @NotNull Long addedBy) {
    }

    // { "stayId": 3 }   stayId null = 표 취소
    public record VoteRequest(Long stayId) {
    }

    // amenities 는 칸 순서와 한글 이름. 프론트가 이름을 따로 들고 있지 않게 여기서 준다
    public record Board(List<AmenityView> amenities, Map<Amenity, Preference> preferences, int surveyed,
                        List<StayView> stays) {
    }

    public record AmenityView(Amenity key, String label) {
    }

    // 평균만 보면 4:4 로 갈린 조건이 무난해 보여서 가장 낮게 준 점수를 같이 준다
    public record Preference(double avg, int min) {
    }

    public record StayView(Long id, String title, String url, String imageUrl, Set<Amenity> amenities,
                           List<Long> voterIds) {
    }
}
