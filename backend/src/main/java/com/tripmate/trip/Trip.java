package com.tripmate.trip;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    public String getTitle() { return title; }
    public String getDestination() { return destination; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public List<Member> getMembers() { return members; }
}
