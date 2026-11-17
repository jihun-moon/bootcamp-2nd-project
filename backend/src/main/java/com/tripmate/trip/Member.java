package com.tripmate.trip;

import jakarta.persistence.*;

@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    private String name;
    private boolean host;
    private boolean surveyDone;

    protected Member() {
    }

    Member(Trip trip, String name, boolean host) {
        this.trip = trip;
        this.name = name;
        this.host = host;
    }

    public void finishSurvey() {
        surveyDone = true;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isHost() { return host; }
    public boolean isSurveyDone() { return surveyDone; }
}
