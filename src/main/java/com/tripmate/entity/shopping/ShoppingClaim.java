package com.tripmate.entity.shopping;

import com.tripmate.entity.trip.Member;
import com.tripmate.entity.trip.Trip;
import jakarta.persistence.*;

// "내가 살게": 방마다 한 줄에 한 사람. 산 뒤 쓴 돈(amount)을 적으면 장보기 합계와 영수증으로 간다
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "item"}))
public class ShoppingClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private ShoppingItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    private Integer amount;

    protected ShoppingClaim() {
    }

    public ShoppingClaim(Trip trip, ShoppingItem item, Member member, Integer amount) {
        this.trip = trip;
        this.item = item;
        this.member = member;
        this.amount = amount;
    }

    public void changeAmount(Integer amount) {
        this.amount = amount;
    }

    public ShoppingItem getItem() { return item; }
    public Member getMember() { return member; }
    public Integer getAmount() { return amount; }
}
