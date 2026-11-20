package com.tripmate.settle;

import com.tripmate.trip.Member;
import com.tripmate.trip.Trip;
import jakarta.persistence.*;

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

    protected Expense() {
    }

    public Expense(Trip trip, Member payer, ExpenseKind kind, String memo, int amount) {
        this.trip = trip;
        this.payer = payer;
        this.kind = kind;
        this.memo = memo;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public Member getPayer() { return payer; }
    public ExpenseKind getKind() { return kind; }
    public String getMemo() { return memo; }
    public int getAmount() { return amount; }
}
