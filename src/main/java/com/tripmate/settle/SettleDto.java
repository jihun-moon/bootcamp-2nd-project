package com.tripmate.settle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public final class SettleDto {

    private SettleDto() {
    }

    // POST /api/trips/1/expenses  { "kind": "STAY", "memo": "숲속 독채 잔금", "amount": 476000, "sharerIds": null }
    // 낸 사람은 요청한 본인. sharerIds 가 null·빈 값이면 기본(모두·규칙대로), 고르면 그 사람들끼리만
    public record ExpenseRequest(@NotNull ExpenseKind kind, @Size(max = 40) String memo,
                                 @Positive @Max(100_000_000) int amount, @Size(max = 100) Set<Long> sharerIds) {
    }

    // GET /api/trips/1/settlement
    // { "total": 612000, "lines": [...], "balances": [...], "transfers": [ { "fromName": "민지", "toName": "준호", "amount": 32000 } ] }
    // transfers 는 아직 남은 보낼 줄, payments 는 "보냈어요/받았어요"로 끝난 줄
    public record Board(int total, List<Line> lines, List<Balance> balances, List<Transfer> transfers,
                        List<Paid> payments, Boolean driverPaysFuel, Boolean drinksSeparate) {
    }

    // POST /api/trips/1/payments  { "fromId": 2, "toId": 1, "amount": 20000 }   보낸 사람·받은 사람·방장만
    public record PaymentRequest(@NotNull Long fromId, @NotNull Long toId, @Positive @Max(100_000_000) int amount) {
    }

    public record Paid(Long id, Long fromId, String fromName, Long toId, String toName, int amount, Long markedById, String markedByName) {
    }

    // 낸 돈 한 줄. shopping 이 true 면 장보기 표에서 온 줄이라 여기서는 못 지운다(장보기 화면에서 고친다)
    // sharedBy 는 직접 고른 나눌 사람 수(기본이면 null). 화면에 "2명만"처럼 보인다
    public record Line(Long id, Long payerId, String payerName, String kind, String label, int amount, boolean shopping,
                       Integer sharedBy) {
    }

    // 사람마다: 낸 돈, 몫, 차이(+ 받을 돈 / - 보낼 돈)
    public record Balance(Long memberId, String name, int paid, int share, int diff) {
    }

    public record Transfer(Long fromId, String fromName, Long toId, String toName, int amount) {
    }
}
