package com.tripmate.entity.shopping;

import com.tripmate.entity.trip.Trip;
import jakarta.persistence.*;

// 방마다 장보기 기준 하나. 없으면 술 마시는 사람 = 전원, 1인 고기 300g
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "trip_id"))
public class ShoppingPlan {

    public static final int DEFAULT_MEAT_GRAMS = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    private Integer drinkers;   // null = 전원
    private int meatGrams = DEFAULT_MEAT_GRAMS;

    protected ShoppingPlan() {
    }

    public ShoppingPlan(Trip trip) {
        this.trip = trip;
    }

    public void change(Integer drinkers, int meatGrams) {
        this.drinkers = drinkers;
        this.meatGrams = meatGrams;
    }

    public Integer getDrinkers() { return drinkers; }
    public int getMeatGrams() { return meatGrams; }
}
