package com.tripmate.trip;

import com.tripmate.user.AppUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.stream.Collectors;
import java.util.Arrays;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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

    // meId 는 요청한 사람의 이 방 참여자 id. 화면이 "나"를 고르는 단계 없이 바로 쓴다.
    // nextTripId 는 이 방에서 이어 만든 다음 여행 방(없으면 null). 개요가 "2탄 방이 열렸어요"를 띄운다
    public record Response(Long id, String title, String destination, LocalDate startDate, LocalDate endDate,
                           String inviteCode, Long meId, List<MemberResponse> members, Rules rules, Long nextTripId,
                           Meet meet, RoomDraw roomDraw) {

        static Response of(Trip trip, Member me) {
            return of(trip, me, null);
        }

        static Response of(Trip trip, Member me, Long nextTripId) {
            var members = trip.getMembers().stream().map(MemberResponse::from).toList();
            return new Response(trip.getId(), trip.getTitle(), trip.getDestination(), trip.getStartDate(), trip.getEndDate(),
                    trip.getInviteCode(), me.getId(), members, Rules.of(trip), nextTripId, new Meet(trip.getMeetTime(), trip.getMeetPlace()),
                    RoomDraw.of(trip));
        }
    }

    // POST /api/trips/1/next -> { "tripId": 7, "created": true }  이미 누가 만들었으면 created 가 false 이고 그 방
    public record Next(Long tripId, boolean created, int memberCount) {
    }

    // GET /api/me/trips 의 한 줄. 첫 화면에서 들어갈 방을 고르는 데 필요한 것만.
    // todo 는 이 방에서 "내가" 아직 안 한 일(급한 순서): invite(혼자 있는 방장) → survey → dates(방 날짜가 없고 내 되는 날도 없음).
    // 알림을 보내는 대신 로그인해 첫 화면에 오면 바로 보이게 한다(지훈: "알림보다 들어오면 하라고 안내")
    public record Summary(Long id, String title, String destination, LocalDate startDate, LocalDate endDate,
                          int memberCount, long surveyDone, boolean host, List<String> todo) {

        static Summary of(Trip trip, AppUser user) {
            var members = trip.getMembers();
            var me = members.stream().filter(m -> m.belongsTo(user)).findFirst().orElse(null);
            var host = me != null && me.isHost();
            var todo = new ArrayList<String>();
            if (me != null) {
                if (host && members.size() == 1) todo.add("invite");
                if (!me.isSurveyDone()) todo.add("survey");
                else if (trip.getStartDate() == null && me.getAvailableDates().isEmpty()) todo.add("dates");
            }
            return new Summary(trip.getId(), trip.getTitle(), trip.getDestination(), trip.getStartDate(), trip.getEndDate(),
                    members.size(), members.stream().filter(Member::isSurveyDone).count(), host, todo);
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

    public record MemberResponse(Long id, String name, boolean host, boolean surveyDone, String imageUrl, boolean drinks) {

        static MemberResponse from(Member m) {
            return new MemberResponse(m.getId(), m.getName(), m.isHost(), m.isSurveyDone(), m.getImageUrl(), m.drinks());
        }
    }

    // PUT /api/trips/1  { "title": "강릉 우정 여행", "destination": "강릉" }   방장만
    public record RenameRequest(@NotBlank @Size(max = 100) String title, @Size(max = 100) String destination) {
    }

    // PUT /api/trips/1/host  { "memberId": 3 }   방장만. 넘기면 나는 참여자가 된다
    public record HostRequest(@NotNull Long memberId) {
    }

    // POST /api/trips/1/room-draw  { "rooms": 2 }   방 사람 누구나. 2~6개 방
    public record DrawRequest(@Min(2) @Max(6) int rooms) {
    }

    // 뽑기 결과: 방마다 이름. 나간 사람은 빠진다. 아직 안 뽑았으면 null
    public record RoomDraw(List<List<String>> rooms, int count, String by) {

        static RoomDraw of(Trip trip) {
            if (trip.getRoomDraw() == null) return null;
            var names = trip.getMembers().stream().collect(Collectors.toMap(Member::getId, Member::getName));
            var rooms = Arrays.stream(trip.getRoomDraw().split("\\|"))
                    .map(r -> Arrays.stream(r.split(",")).filter(s -> !s.isBlank()).map(Long::valueOf)
                            .filter(names::containsKey).map(names::get).toList())
                    .toList();
            return new RoomDraw(rooms, trip.getRoomDrawCount(), trip.getRoomDrawBy());
        }
    }

    // PUT /api/trips/1/meet  { "time": "08:00", "place": "합정역 2번 출구" }   둘 다 null 이면 지운다
    public record Meet(LocalTime time, @Size(max = 40) String place) {
    }

    // PUT /api/trips/1/members/2/drinks  { "drinks": false }
    public record DrinksRequest(boolean drinks) {
    }
}
