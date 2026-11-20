package com.tripmate.settle;

import com.tripmate.trip.Member;
import com.tripmate.trip.Trip;
import jakarta.persistence.*;

// 미리 걷는 회비. 방마다 하나: 1인 얼마를 누구(총무)에게.
// "냈어요"는 따로 표를 두지 않고 Payment(dues=true, 낸 사람 → 총무)로 남긴다. 그러면 정산 셈이 그대로 맞물려
// 다녀와서 남은 회비는 총무가 돌려주는 줄, 모자라면 더 보내는 줄이 저절로 나온다
@Entity
public class Dues {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member collector;

    private int amount;

    protected Dues() {
    }

    public Dues(Trip trip, Member collector, int amount) {
        this.trip = trip;
        this.collector = collector;
        this.amount = amount;
    }

    void change(Member collector, int amount) {
        this.collector = collector;
        this.amount = amount;
    }

    public Member getCollector() { return collector; }
    public int getAmount() { return amount; }
}
