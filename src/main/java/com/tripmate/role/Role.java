package com.tripmate.role;

import com.tripmate.trip.Trip;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    private RoleKind kind;

    // 기본값은 kind 에서 오지만 방마다 고칠 수 있게 칸으로 따로 둔다
    private String title;
    private boolean required;

    // 운전처럼 한 역할에 여러 명이 필요할 수 있다. 배정 알고리즘은 역할을 capacity 칸만큼 늘려 푼다
    private int capacity;

    @ElementCollection
    @CollectionTable(name = "role_task")
    @OrderColumn(name = "position")
    @Column(name = "task")
    private List<String> tasks = new ArrayList<>();

    protected Role() {
    }

    public Role(Trip trip, RoleKind kind) {
        this.trip = trip;
        this.kind = kind;
        this.title = kind.title;
        this.required = kind.required;
        this.capacity = kind.capacity;
        this.tasks.addAll(kind.tasks);
    }

    // 방장이 더한 역할. 점수 규칙이 없어(kind null) 추천에서는 0점으로 들어간다
    public Role(Trip trip, String title, boolean required, int capacity, List<String> tasks) {
        this.trip = trip;
        update(title, required, capacity, tasks);
    }

    public void update(String title, boolean required, int capacity, List<String> tasks) {
        this.title = title;
        this.required = required;
        this.capacity = capacity;
        this.tasks.clear();
        this.tasks.addAll(tasks);
    }

    public static List<Role> defaultsFor(Trip trip) {
        return Arrays.stream(RoleKind.values()).map(k -> new Role(trip, k)).toList();
    }

    public Long getId() { return id; }
    public RoleKind getKind() { return kind; }
    public String getTitle() { return title; }
    public boolean isRequired() { return required; }
    public int getCapacity() { return capacity; }
    public List<String> getTasks() { return tasks; }
}
