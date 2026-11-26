package com.tripmate.entity.settle;

import com.tripmate.entity.trip.Member;
import com.tripmate.entity.trip.Trip;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

// 여행에서 한 사람이 먼저 낸 돈 한 건(숙소 잔금, 기름값·톨비, 식비…). 장보기에서 "내가 살게"로 적은 돈은 장보기 표에 있어 여기 다시 적지 않는다
@Entity
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member payer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseKind kind;

    private String memo;
    private int amount;

    // 이 돈을 나눌 사람(참여자 id). 비어 있으면 기본(모두, 규칙이 켜지면 기름값은 운전자 빼고·술은 마신 사람끼리).
    // 커플 동반, 일부만 간 저녁, 개인 간식처럼 "이건 이 사람들만"일 때만 고른다
    @ElementCollection
    @CollectionTable(name = "expense_sharer")
    @Column(name = "member_id")
    private Set<Long> sharerIds = new HashSet<>();

    protected Expense() {
    }

    public Expense(Trip trip, Member payer, ExpenseKind kind, String memo, int amount, Set<Long> sharerIds) {
        this.trip = trip;
        this.payer = payer;
        this.kind = kind;
        this.memo = memo;
        this.amount = amount;
        if (sharerIds != null) this.sharerIds.addAll(sharerIds);
    }

    public Long getId() { return id; }
    public Member getPayer() { return payer; }
    public ExpenseKind getKind() { return kind; }
    public String getMemo() { return memo; }
    public int getAmount() { return amount; }
    public Set<Long> getSharerIds() { return sharerIds; }
}
