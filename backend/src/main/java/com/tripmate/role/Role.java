package com.tripmate.role;

import com.tripmate.trip.Trip;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    private String title;
    private boolean required;

    // 운전처럼 한 역할에 여러 명이 필요할 수 있다. 배정 알고리즘은 역할을 capacity 칸만큼 늘려 푼다
    private int capacity;

    @Enumerated(EnumType.STRING)
    private Chore chore;

    @ElementCollection
    @CollectionTable(name = "role_task")
    @OrderColumn(name = "position")
    @Column(name = "task")
    private List<String> tasks = new ArrayList<>();

    protected Role() {
    }

    public Role(Trip trip, String title, boolean required, int capacity, Chore chore, List<String> tasks) {
        this.trip = trip;
        this.title = title;
        this.required = required;
        this.capacity = capacity;
        this.chore = chore;
        this.tasks.addAll(tasks);
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getTitle() { return title; }
    public boolean isRequired() { return required; }
    public int getCapacity() { return capacity; }
    public Chore getChore() { return chore; }
    public List<String> getTasks() { return tasks; }
}
