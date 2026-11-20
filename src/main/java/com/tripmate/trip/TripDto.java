package com.tripmate.trip;

import com.tripmate.user.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class TripDto {

    private TripDto() {
    }

    // { "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15", "endDate": "2027-01-17" }
    public record CreateRequest(@NotBlank @Size(max = 100) String title, @Size(max = 100) String destination,
                                LocalDate startDate, LocalDate endDate) {
    }

    // meId 는 요청한 사람의 이 방 참여자 id. 화면이 "나"를 고르는 단계 없이 바로 쓴다
    public record Response(Long id, String title, String destination, LocalDate startDate, LocalDate endDate,
                           String inviteCode, Long meId, List<MemberResponse> members) {

        static Response of(Trip trip, Member me) {
            var members = trip.getMembers().stream().map(MemberResponse::from).toList();
            return new Response(trip.getId(), trip.getTitle(), trip.getDestination(),
                    trip.getStartDate(), trip.getEndDate(), trip.getInviteCode(), me.getId(), members);
        }
    }

    // GET /api/me/trips 의 한 줄. 첫 화면에서 들어갈 방을 고르는 데 필요한 것만
    public record Summary(Long id, String title, String destination, LocalDate startDate, LocalDate endDate,
                          int memberCount, long surveyDone, boolean host) {

        static Summary of(Trip trip, AppUser user) {
            var members = trip.getMembers();
            var host = members.stream().anyMatch(m -> m.isHost() && m.belongsTo(user));
            return new Summary(trip.getId(), trip.getTitle(), trip.getDestination(), trip.getStartDate(), trip.getEndDate(),
                    members.size(), members.stream().filter(Member::isSurveyDone).count(), host);
        }
    }

    // POST /api/invites/{inviteCode} 의 응답. 화면이 이 방으로 옮겨 간다
    public record Joined(Long tripId) {
    }

    public record MemberResponse(Long id, String name, boolean host, boolean surveyDone, String imageUrl) {

        static MemberResponse from(Member m) {
            return new MemberResponse(m.getId(), m.getName(), m.isHost(), m.isSurveyDone(), m.getImageUrl());
        }
    }
}
