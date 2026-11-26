package com.tripmate.dto;

import com.tripmate.entity.shopping.ShoppingItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public final class ShoppingDto {

    private ShoppingDto() {
    }

    // GET /api/trips/1/shopping
    // { "people": 8, "nights": 2, "drinkers": 6, "meatGrams": 300,
    //   "items": [{ "key": "PORK_BELLY", "label": "삼겹살", "group": "고기", "quantity": "3.4kg", "buyerId": 2, "buyerName": "민지", "amount": 52000 }],
    //   "spent": 52000, "perPerson": 6500 }
    public record Board(int people, int nights, int drinkers, int meatGrams, List<Item> items, int spent, int perPerson) {
    }

    // bring 이 true 면 사는 게 아니라 챙겨 가는 줄("내가 챙길게", 쓴 돈 없음)
    public record Item(ShoppingItem key, String label, String group, String quantity, Long buyerId, String buyerName, Integer amount,
                       boolean bring) {
    }

    // PUT /api/trips/1/shopping/plan  { "drinkers": 6, "meatGrams": 300 }   drinkers null = 전원
    public record PlanRequest(@Min(0) @Max(100) Integer drinkers, @Min(100) @Max(600) int meatGrams) {
    }

    // PUT /api/trips/1/shopping/items/PORK_BELLY  { "amount": 52000 }   amount 는 산 뒤에 적는다(처음엔 null)
    public record ClaimRequest(@PositiveOrZero @Max(10_000_000) Integer amount) {
    }
}
