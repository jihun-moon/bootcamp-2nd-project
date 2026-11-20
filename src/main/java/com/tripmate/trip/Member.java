package com.tripmate.trip;

import com.tripmate.user.AppUser;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

// 그 방의 참여자. 계정(AppUser)과 따로 두어 방마다 이름이 달라도 되고, 예시 방처럼 계정 없는 참여자도 있을 수 있다
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"}))
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser user;

    private String name;
    private boolean host;
    private boolean surveyDone;

    // 술 마시나. null 은 아직 안 고름 = 마신다고 본다(대부분 마신다, 안 마시는 사람만 끄게).
    // 장보기 술 양과, 규칙 "술값은 마신 사람만"일 때 정산에 쓴다
    private Boolean drinks;

    // 날짜 맞추기: 이 사람이 되는 날. 방에 날짜가 아직 없을 때 모아서 겹치는 날을 찾는다
    @ElementCollection
    @CollectionTable(name = "member_available_date")
    @Column(name = "available_date")
    private Set<LocalDate> availableDates = new HashSet<>();

    protected Member() {
    }

    Member(Trip trip, AppUser user, String name, boolean host) {
        this.trip = trip;
        this.user = user;
        this.name = name;
        this.host = host;
    }

    public void changeAvailableDates(Set<LocalDate> dates) {
        availableDates.clear();
        availableDates.addAll(dates);
    }

    public Set<LocalDate> getAvailableDates() { return availableDates; }

    void makeHost() {
        host = true;
    }

    public void changeDrinks(boolean drinks) {
        this.drinks = drinks;
    }

    public boolean drinks() { return !Boolean.FALSE.equals(drinks); }

    public void finishSurvey() {
        surveyDone = true;
    }

    public boolean belongsTo(AppUser other) {
        return user != null && user.getId().equals(other.getId());
    }

    Trip getTrip() { return trip; }
    AppUser getUser() { return user; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isHost() { return host; }
    public boolean isSurveyDone() { return surveyDone; }
    public String getImageUrl() { return user == null ? null : user.getProfileImageUrl(); }
}
