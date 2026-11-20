package com.tripmate.trip;

import com.tripmate.user.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TripDto {

    private TripDto() {
    }

    // { "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15", "endDate": "2027-01-17" }
    public record CreateRequest(@NotBlank @Size(max = 100) String title, @Size(max = 100) String destination,
                                LocalDate startDate, LocalDate endDate) {
    }

    // PUT /api/trips/1/rules  { "driverPaysFuel": false, "refundNoShow": false, "drinksSeparate": true }
    // 칸마다 null 이면 "아직 안 정함". 세 칸을 한 번에 보내 덮어쓴다
    public record Rules(Boolean driverPaysFuel, Boolean refundNoShow, Boolean drinksSeparate) {

        static Rules of(Trip trip) {
            return new Rules(trip.getDriverPaysFuel(), trip.getRefundNoShow(), trip.getDrinksSeparate());
        }
    }

    // meId 는 요청한 사람의 이 방 참여자 id. 화면이 "나"를 고르는 단계 없이 바로 쓴다
    public record Response(Long id, String title, String destination, LocalDate startDate, LocalDate endDate,
                           String inviteCode, Long meId, List<MemberResponse> members, Rules rules) {

        static Response of(Trip trip, Member me) {
            var members = trip.getMembers().stream().map(MemberResponse::from).toList();
            return new Response(trip.getId(), trip.getTitle(), trip.getDestination(),
                    trip.getStartDate(), trip.getEndDate(), trip.getInviteCode(), me.getId(), members, Rules.of(trip));
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

    // PUT /api/trips/1/members/2/dates  { "dates": ["2027-01-15", "2027-01-16", "2027-01-17"] }
    public record DatesRequest(@NotNull @Size(max = 62) Set<LocalDate> dates) {
    }

    // GET /api/trips/1/dates -> { "memberCount": 8, "responded": 5, "available": { "2027-01-15": [1, 2, 5] } }
    // 화면이 겹치는 날과 좋은 날짜 후보(2박·1박·당일)를 센다
    public record DatesBoard(int memberCount, int responded, Map<LocalDate, List<Long>> available) {
    }

    // PUT /api/trips/1/period  { "startDate": "2027-01-15", "endDate": "2027-01-17" }  방장이 후보 하나를 고른다
    public record PeriodRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {
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
