package com.tripmate.settle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SettleDto {

    private SettleDto() {
    }

    // POST /api/trips/1/expenses  { "kind": "STAY", "memo": "숲속 독채 잔금", "amount": 476000 }   낸 사람은 요청한 본인
    public record ExpenseRequest(@NotNull ExpenseKind kind, @Size(max = 40) String memo,
                                 @Positive @Max(100_000_000) int amount) {
    }

    // GET /api/trips/1/settlement
    // { "total": 612000, "lines": [...], "balances": [...], "transfers": [ { "fromName": "민지", "toName": "준호", "amount": 32000 } ] }
    public record Board(int total, List<Line> lines, List<Balance> balances, List<Transfer> transfers,
                        Boolean driverPaysFuel, Boolean drinksSeparate) {
    }

    // 낸 돈 한 줄. shopping 이 true 면 장보기 표에서 온 줄이라 여기서는 못 지운다(장보기 화면에서 고친다)
    public record Line(Long id, Long payerId, String payerName, String kind, String label, int amount, boolean shopping) {
    }

    // 사람마다: 낸 돈, 몫, 차이(+ 받을 돈 / - 보낼 돈)
    public record Balance(Long memberId, String name, int paid, int share, int diff) {
    }

    public record Transfer(Long fromId, String fromName, Long toId, String toName, int amount) {
    }
}
