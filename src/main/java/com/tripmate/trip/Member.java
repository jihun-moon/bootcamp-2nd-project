package com.tripmate.trip;

import com.tripmate.user.AppUser;
import jakarta.persistence.*;

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

    protected Member() {
    }

    Member(Trip trip, AppUser user, String name, boolean host) {
        this.trip = trip;
        this.user = user;
        this.name = name;
        this.host = host;
    }

    public void finishSurvey() {
        surveyDone = true;
    }

    public boolean belongsTo(AppUser other) {
        return user != null && user.getId().equals(other.getId());
    }

    Trip getTrip() { return trip; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isHost() { return host; }
    public boolean isSurveyDone() { return surveyDone; }
    public String getImageUrl() { return user == null ? null : user.getProfileImageUrl(); }
}
