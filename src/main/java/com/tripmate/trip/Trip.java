package com.tripmate.trip;

import com.tripmate.user.AppUser;
import jakarta.persistence.*;

import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 초대 링크에 id 를 그대로 쓰면 숫자만 바꿔 남의 방에 들어올 수 있어서 따로 둔다
    @Column(unique = true, nullable = false, updatable = false)
    private String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    private String title;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;

    // 방 규칙. 커뮤니티 글에서 가장 많이 싸운 세 가지를 출발 전에 정해 둔다(docs/11). null 은 아직 안 정함
    private Boolean driverPaysFuel;  // 운전한 사람도 기름값을 같이 내나
    private Boolean refundNoShow;    // 전날 빠지면 예약금을 돌려주나
    private Boolean drinksSeparate;  // 술값은 마신 사람만 내나

    // 모일 시간·장소(출발일 기준). 커뮤니티 불편 "지각·늦은 출발"(docs/17 8절). 방장이 한 번 적으면 D-day 줄에 뜬다
    private LocalTime meetTime;
    private String meetPlace;

    // 방·침대 자리 뽑기 결과. "1,2,3|4,5,6" 처럼 방마다 참여자 id. 누가 몇 번째로 뽑았는지도 남겨
    // 마음에 안 든다고 몰래 다시 뽑으면 다 보이게 한다(커뮤니티 불편 "침대 자리 다툼", docs/17 8절)
    private String roomDraw;
    private Integer roomDrawCount;
    private String roomDrawBy;

    // 같은 멤버로 다음 여행을 만들면 지난 방 id 를 남긴다. 단톡방에서 두 사람이 같이 눌러도 다음 방이 하나만 생기게(unique)
    @Column(unique = true)
    private Long previousTripId;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Member> members = new ArrayList<>();

    protected Trip() {
    }

    public Trip(String title, String destination, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // "가평 2박 3일" -> "가평 2박 3일 2탄", "… 2탄" -> "… 3탄". 다음 방 이름은 손으로 안 쓴다
    private static final Pattern SEQUEL = Pattern.compile("^(.*) (\\d+)탄$");

    static String sequelOf(String title) {
        var m = SEQUEL.matcher(title);
        var next = m.matches() ? m.group(1) + " " + (Integer.parseInt(m.group(2)) + 1) + "탄" : title + " 2탄";
        return next.length() <= 100 ? next : next.substring(next.length() - 100);
    }

    // 다음 여행 방: 이름·지역·규칙은 잇고, 날짜는 다시 맞춘다
    Trip nextTrip() {
        var next = new Trip(sequelOf(title), destination, null, null);
        next.previousTripId = id;
        next.updateRules(driverPaysFuel, refundNoShow, drinksSeparate);
        return next;
    }

    public Member addMember(AppUser user, String name, boolean host) {
        var member = new Member(this, user, name, host);
        members.add(member);
        return member;
    }

    // 나가기: 행은 남기고 나감 표시(Member.departed). 방장이 나가면 남은 사람 중 먼저 들어온 사람이 방장
    void removeMember(Member member) {
        var wasHost = member.isHost();
        member.depart();
        var rest = getMembers();
        // 방장은 카카오 계정인 사람 중 먼저 들어온 사람이 잇는다(손님은 다시 찾아올 수 없을 수 있다). 다 손님이면 먼저 들어온 손님
        if (wasHost && !rest.isEmpty()) rest.stream().filter(m -> !m.isGuest()).findFirst().orElse(rest.get(0)).makeHost();
    }

    // 방 이름·지역 고치기(방장). 이름이 비면 그대로 둔다
    public void rename(String title, String destination) {
        if (title != null && !title.isBlank()) this.title = title.strip();
        this.destination = destination == null || destination.isBlank() ? null : destination.strip();
    }

    // 방장 넘기기. 넘겨받는 사람은 지금 방에 있는 사람이어야 한다
    void handOver(Member from, Member to) {
        from.stepDown();
        to.makeHost();
    }

    void drawRooms(List<List<Long>> rooms, String by) {
        roomDraw = rooms.stream().map(r -> r.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .collect(Collectors.joining("|"));
        roomDrawCount = (roomDrawCount == null ? 0 : roomDrawCount) + 1;
        roomDrawBy = by;
    }

    public void updateMeet(LocalTime meetTime, String meetPlace) {
        this.meetTime = meetTime;
        this.meetPlace = meetPlace == null || meetPlace.isBlank() ? null : meetPlace.strip();
    }

    public void updatePeriod(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void updateRules(Boolean driverPaysFuel, Boolean refundNoShow, Boolean drinksSeparate) {
        this.driverPaysFuel = driverPaysFuel;
        this.refundNoShow = refundNoShow;
        this.drinksSeparate = drinksSeparate;
    }

    public Long getId() { return id; }
    public Long getPreviousTripId() { return previousTripId; }
    public LocalTime getMeetTime() { return meetTime; }
    public String getRoomDraw() { return roomDraw; }
    public Integer getRoomDrawCount() { return roomDrawCount; }
    public String getRoomDrawBy() { return roomDrawBy; }
    public String getMeetPlace() { return meetPlace; }
    public String getInviteCode() { return inviteCode; }
    public String getTitle() { return title; }
    public String getDestination() { return destination; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    // 지금 방에 있는 사람. 화면·역할·숙소·장보기·인원 셈은 모두 이것만 본다
    public List<Member> getMembers() { return members.stream().filter(m -> !m.isDeparted()).toList(); }

    // 나간 사람까지. 정산만 쓴다(먼저 낸 돈, 보낸 돈, 예약금 규칙)
    public List<Member> getEveryone() { return members; }
    public Boolean getDriverPaysFuel() { return driverPaysFuel; }
    public Boolean getRefundNoShow() { return refundNoShow; }
    public Boolean getDrinksSeparate() { return drinksSeparate; }
}
