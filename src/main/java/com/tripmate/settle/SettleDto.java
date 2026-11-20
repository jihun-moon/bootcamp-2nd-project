package com.tripmate.settle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
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
    // dues 는 미리 걷는 회비(없으면 null)
    public record Board(int total, List<Line> lines, List<Balance> balances, List<Transfer> transfers,
                        List<Paid> payments, Boolean driverPaysFuel, Boolean drinksSeparate, Boolean refundNoShow, Dues dues) {
    }

    // 회비: 1인 얼마를 누구에게, 낸 사람 id, 걷힌 돈(총무 몫은 빼고 센다).
    // pending 은 여행이 아직 안 끝남. 그동안은 보낼 줄 대신 "다녀와서 남은 회비를 나눠요"로 보인다(걷자마자 돌려주라는 줄이 뜨지 않게)
    public record Dues(int amount, Long collectorId, String collectorName, List<Long> paidIds, int collected, boolean pending) {
    }

    // PUT /api/trips/1/dues  { "amount": 100000, "collectorId": 3 }   방장이나 지금 총무
    public record DuesRequest(@Positive @Max(10_000_000) int amount, @NotNull Long collectorId) {
    }

    // POST /api/trips/1/payments  { "fromId": 2, "toId": 1, "amount": 20000 }   보낸 사람·받은 사람·방장만
    public record PaymentRequest(@NotNull Long fromId, @NotNull Long toId, @Positive @Max(100_000_000) int amount) {
    }

    public record Paid(Long id, Long fromId, String fromName, Long toId, String toName, int amount, Long markedById, String markedByName,
                       boolean dues) {
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

    // 로그인 없이 보는 정산(/s/{code}). 방 화면의 판에서 사람별 몫(balances)·규칙은 빼고 보낼 줄·낸 돈·끝난 줄만
    public record Shared(Long tripId, String title, String destination, LocalDate startDate, LocalDate endDate,
                         int total, List<Line> lines, List<Transfer> transfers, List<Paid> payments, boolean duesPending) {
    }

    // PUT /api/trips/1/settlement/link -> { "code": "Qm9vdGNhbXAtMi10cmlw" }  주소는 /s/{code}
    public record Link(String code) {
    }
}
