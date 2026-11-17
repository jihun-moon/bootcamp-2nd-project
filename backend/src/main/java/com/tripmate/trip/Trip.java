package com.tripmate.trip;

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

    public void addMember(String name, boolean host) {
        members.add(new Member(this, name, host));
    }

    public Long getId() { return id; }
    public String getInviteCode() { return inviteCode; }
    public String getTitle() { return title; }
    public String getDestination() { return destination; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public List<Member> getMembers() { return members; }
}
