package com.tripmate.settle;

import com.tripmate.shopping.ShoppingClaimRepository;
import com.tripmate.trip.Member;
import com.tripmate.trip.MemberLeaving;
import com.tripmate.trip.TripChanged;
import com.tripmate.trip.TripRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 다녀와서 "누가 누구에게 얼마". 낸 돈(직접 적은 것 + 장보기 "내가 살게"에 적은 돈)을 모두 똑같이 나누되,
// 기름값·톨비는 방 규칙이 "운전자는 안 냄"이면 기름값을 낸 사람(운전자)을 빼고,
// 술(직접 적은 술, 장보기 소주·맥주)은 규칙이 "술값은 마신 사람만"이면 "술 마셔요"인 사람끼리 나눈다.
// 보낼 돈은 많이 받을 사람부터 맞춰 보내는 횟수를 줄이고, 100원 단위로 올린다(회비처럼)
@Service
public class SettleService {

    private static final int UNIT = 100;

    private final TripRepository tripRepository;
    private final ExpenseRepository expenseRepository;
    private final ShoppingClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher events;

    public SettleService(TripRepository tripRepository, ExpenseRepository expenseRepository,
                         ShoppingClaimRepository claimRepository, PaymentRepository paymentRepository,
                         ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.expenseRepository = expenseRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public SettleDto.Board board(Long tripId) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var members = trip.getMembers();
        var expenses = expenseRepository.findByTripIdOrderById(tripId);
        var claims = claimRepository.findByTripId(tripId).stream()
                .filter(c -> c.getAmount() != null && c.getAmount() > 0).toList();

        // 기름값을 낸 사람 = 운전한 사람. 규칙이 "운전자는 안 냄"이면 기름값 몫에서 뺀다(다 빠지면 모두가 낸다)
        var driverIds = expenses.stream().filter(e -> e.getKind() == ExpenseKind.FUEL)
                .map(e -> e.getPayer().getId()).collect(Collectors.toSet());
        var driversExempt = Boolean.FALSE.equals(trip.getDriverPaysFuel());
        var fuelSharers = members.stream().filter(m -> !driversExempt || !driverIds.contains(m.getId())).toList();
        if (fuelSharers.isEmpty()) fuelSharers = members;
        // 술: 규칙을 켰을 때만 마시는 사람끼리(아무도 안 마시면 모두). 전에는 규칙을 켜도 결과가 같아 오해를 샀다(docs/17 8절)
        var drinksOnly = Boolean.TRUE.equals(trip.getDrinksSeparate());
        var drinkers = members.stream().filter(m -> !drinksOnly || m.drinks()).toList();
        if (drinkers.isEmpty()) drinkers = members;

        var paid = new HashMap<Long, Double>();
        var owed = new HashMap<Long, Double>();
        var lines = new ArrayList<SettleDto.Line>();
        for (var e : expenses) {
            var sharers = e.getKind() == ExpenseKind.FUEL ? fuelSharers : e.getKind() == ExpenseKind.DRINK ? drinkers : members;
            // 직접 고른 사람이 있으면 그 사람들끼리(나간 사람은 빠진다). 다 빠졌으면 기본대로
            var picked = members.stream().filter(m -> e.getSharerIds().contains(m.getId())).toList();
            if (!picked.isEmpty()) sharers = picked;
            add(paid, owed, e.getPayer(), e.getAmount(), sharers);
            var label = e.getMemo() == null || e.getMemo().isBlank() ? e.getKind().getLabel() : e.getKind().getLabel() + " · " + e.getMemo();
            lines.add(new SettleDto.Line(e.getId(), e.getPayer().getId(), e.getPayer().getName(), e.getKind().name(), label,
                    e.getAmount(), false, picked.isEmpty() ? null : picked.size()));
        }
        for (var c : claims) {
            add(paid, owed, c.getMember(), c.getAmount(), c.getItem().isDrink() ? drinkers : members);
            lines.add(new SettleDto.Line(null, c.getMember().getId(), c.getMember().getName(), "SHOPPING",
                    "장보기 · " + c.getItem().getLabel(), c.getAmount(), true, null));
        }

        // 보냈어요/받았어요: 보낸 사람이 받은 사람 몫을 낸 것으로 셈한다. 그러면 남은 보낼 줄에서 저절로 빠진다
        var paidRows = new ArrayList<SettleDto.Paid>();
        for (var p : paymentRepository.findByTripIdOrderById(tripId)) {
            paid.merge(p.getSender().getId(), (double) p.getAmount(), Double::sum);
            owed.merge(p.getReceiver().getId(), (double) p.getAmount(), Double::sum);
            paidRows.add(new SettleDto.Paid(p.getId(), p.getSender().getId(), p.getSender().getName(), p.getReceiver().getId(),
                    p.getReceiver().getName(), p.getAmount(), p.getMarkedBy().getId(), p.getMarkedBy().getName()));
        }

        var balances = members.stream().map(m -> {
            var p = paid.getOrDefault(m.getId(), 0.0);
            var o = owed.getOrDefault(m.getId(), 0.0);
            return new SettleDto.Balance(m.getId(), m.getName(), (int) Math.round(p), (int) Math.round(o), (int) Math.round(p - o));
        }).toList();
        var total = lines.stream().mapToInt(SettleDto.Line::amount).sum();
        return new SettleDto.Board(total, lines, balances, transfers(members, paid, owed), paidRows, trip.getDriverPaysFuel(),
                trip.getDrinksSeparate());
    }

    private static void add(Map<Long, Double> paid, Map<Long, Double> owed, Member payer, int amount, List<Member> sharers) {
        paid.merge(payer.getId(), (double) amount, Double::sum);
        var each = (double) amount / sharers.size();
        sharers.forEach(m -> owed.merge(m.getId(), each, Double::sum));
    }

    // 받을 사람과 보낼 사람을 큰 금액부터 짝짓는다. 8명이면 보내는 줄이 많아야 7줄
    private static List<SettleDto.Transfer> transfers(List<Member> members, Map<Long, Double> paid, Map<Long, Double> owed) {
        record Side(Member member, double[] left) {
        }
        var creditors = new ArrayList<Side>();
        var debtors = new ArrayList<Side>();
        for (var m : members) {
            var diff = paid.getOrDefault(m.getId(), 0.0) - owed.getOrDefault(m.getId(), 0.0);
            // 보낼 줄을 100원 단위로 올려 보내면 몇십 원씩 남는다. 100원 아래는 끝난 것으로 본다
            if (diff >= UNIT) creditors.add(new Side(m, new double[]{diff}));
            else if (diff <= -UNIT) debtors.add(new Side(m, new double[]{-diff}));
        }
        Comparator<Side> big = Comparator.comparingDouble((Side s) -> s.left()[0]).reversed();
        creditors.sort(big);
        debtors.sort(big);
        var out = new ArrayList<SettleDto.Transfer>();
        int c = 0, d = 0;
        while (c < creditors.size() && d < debtors.size()) {
            var to = creditors.get(c);
            var from = debtors.get(d);
            var x = Math.min(to.left()[0], from.left()[0]);
            var won = (int) (Math.ceil(x / UNIT - 1e-9) * UNIT);
            if (won > 0) out.add(new SettleDto.Transfer(from.member().getId(), from.member().getName(),
                    to.member().getId(), to.member().getName(), won));
            to.left()[0] -= x;
            from.left()[0] -= x;
            if (to.left()[0] < UNIT) c++;
            if (from.left()[0] < UNIT) d++;
        }
        return out;
    }

    @Transactional
    public SettleDto.Board add(Long tripId, Member me, SettleDto.ExpenseRequest req) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var memo = req.memo() == null ? null : req.memo().strip();
        // 나눌 사람은 이 방 사람만. 남의 방 사람 id 를 넣으면 400
        var memberIds = trip.getMembers().stream().map(Member::getId).collect(Collectors.toSet());
        if (req.sharerIds() != null && !memberIds.containsAll(req.sharerIds())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 방 사람만 고를 수 있어요");
        }
        expenseRepository.save(new Expense(trip, me, req.kind(), memo, req.amount(), req.sharerIds()));
        events.publishEvent(new TripChanged(tripId, "settle"));
        return board(tripId);
    }

    // 낸 사람 본인이나 방장만 지운다(잘못 적은 것 고치기)
    @Transactional
    public SettleDto.Board remove(Long tripId, Long expenseId, Member me) {
        var expense = expenseRepository.findByIdAndTripId(expenseId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!expense.getPayer().getId().equals(me.getId()) && !me.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        expenseRepository.delete(expense);
        events.publishEvent(new TripChanged(tripId, "settle"));
        return board(tripId);
    }

    // 보냈어요/받았어요. 보낸 사람·받은 사람이 한 번 누르면 된다(방장은 대신 눌러 줄 수 있다)
    @Transactional
    public SettleDto.Board markPaid(Long tripId, Member me, SettleDto.PaymentRequest req) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!me.isHost() && !me.getId().equals(req.fromId()) && !me.getId().equals(req.toId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var byId = trip.getMembers().stream().collect(Collectors.toMap(Member::getId, m -> m));
        var from = byId.get(req.fromId());
        var to = byId.get(req.toId());
        if (from == null || to == null || from == to) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 방 두 사람 사이만 돼요");
        paymentRepository.save(new Payment(trip, from, to, byId.get(me.getId()), req.amount()));
        events.publishEvent(new TripChanged(tripId, "settle"));
        return board(tripId);
    }

    // 잘못 누른 것 되돌리기: 누른 사람, 보낸·받은 사람, 방장
    @Transactional
    public SettleDto.Board unmarkPaid(Long tripId, Long paymentId, Member me) {
        var p = paymentRepository.findByIdAndTripId(paymentId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var mine = Stream.of(p.getSender(), p.getReceiver(), p.getMarkedBy()).anyMatch(m -> m.getId().equals(me.getId()));
        if (!mine && !me.isHost()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        paymentRepository.delete(p);
        events.publishEvent(new TripChanged(tripId, "settle"));
        return board(tripId);
    }

    // 방을 나간 사람이 적은 돈과 보냈어요 표시도 같이 빠진다(남은 사람끼리 다시 나눈다)
    @EventListener
    public void onMemberLeaving(MemberLeaving e) {
        expenseRepository.deleteByPayerId(e.memberId());
        paymentRepository.deleteBySenderIdOrReceiverIdOrMarkedById(e.memberId(), e.memberId(), e.memberId());
    }
}
