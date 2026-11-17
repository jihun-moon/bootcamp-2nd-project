package com.tripmate.trip;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public final class TripDto {

    private TripDto() {
    }

    // { "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15", "endDate": "2027-01-17" }
    public record CreateRequest(@NotBlank String title, String destination, LocalDate startDate, LocalDate endDate) {
    }

    public record Response(Long id, String title, String destination, LocalDate startDate, LocalDate endDate,
                           List<MemberResponse> members) {

        static Response from(Trip trip) {
            var members = trip.getMembers().stream().map(MemberResponse::from).toList();
            return new Response(trip.getId(), trip.getTitle(), trip.getDestination(),
                    trip.getStartDate(), trip.getEndDate(), members);
        }
    }

    public record MemberResponse(Long id, String name, boolean host, boolean surveyDone) {

        static MemberResponse from(Member m) {
            return new MemberResponse(m.getId(), m.getName(), m.isHost(), m.isSurveyDone());
        }
    }
}
