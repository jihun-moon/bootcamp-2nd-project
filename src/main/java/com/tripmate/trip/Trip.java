package com.tripmate.trip;

import com.tripmate.user.AppUser;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    public Member addMember(AppUser user, String name, boolean host) {
        var member = new Member(this, user, name, host);
        members.add(member);
        return member;
    }

    public void updateRules(Boolean driverPaysFuel, Boolean refundNoShow, Boolean drinksSeparate) {
        this.driverPaysFuel = driverPaysFuel;
        this.refundNoShow = refundNoShow;
        this.drinksSeparate = drinksSeparate;
    }

    public Long getId() { return id; }
    public String getInviteCode() { return inviteCode; }
    public String getTitle() { return title; }
    public String getDestination() { return destination; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public List<Member> getMembers() { return members; }
    public Boolean getDriverPaysFuel() { return driverPaysFuel; }
    public Boolean getRefundNoShow() { return refundNoShow; }
    public Boolean getDrinksSeparate() { return drinksSeparate; }
}
