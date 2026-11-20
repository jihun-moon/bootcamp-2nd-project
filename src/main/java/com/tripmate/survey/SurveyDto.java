package com.tripmate.survey;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tripmate.role.Chore;
import com.tripmate.stay.Amenity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

public final class SurveyDto {

    private SurveyDto() {
    }

    // {
    //   "activity": "INDOOR", "planning": "PLANNER", "hasCar": true, "separateDeparture": false,
    //   "departure": "서울 마포구", "mobilityLimited": false, "avoid": ["DRIVING"],
    //   "preferences": { "DETACHED": 5, "PRIVATE_BBQ": 4, "HEATED_POOL": 2 }
    // }
    public record Request(@NotNull ActivityStyle activity, @NotNull PlanningStyle planning,
                          boolean hasCar, boolean separateDeparture, @Size(max = 30) String departure,
                          boolean mobilityLimited, Set<Chore> avoid,
                          Map<Amenity, @NotNull @Min(1) @Max(5) Integer> preferences) {
    }

    // mobilityLimited·departure 가 null 이면 칸째로 빠진다. 본인·방장이 아닌 사람에게는 있는지조차 안 보이게
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(Long memberId, ActivityStyle activity, PlanningStyle planning,
                           boolean hasCar, boolean separateDeparture, String departure, Boolean mobilityLimited,
                           Set<Chore> avoid, Map<Amenity, Integer> preferences) {

        static Response of(SurveyAnswer s, boolean showPrivate) {
            return new Response(s.getMember().getId(), s.getActivity(), s.getPlanning(),
                    s.isHasCar(), s.isSeparateDeparture(), showPrivate ? s.getDeparture() : null,
                    showPrivate ? s.isMobilityLimited() : null,
                    Set.copyOf(s.getAvoid()), Map.copyOf(s.getPreferences()));
        }
    }
}
