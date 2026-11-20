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
    private final ApplicationEventPublisher events;

    public SettleService(TripRepository tripRepository, ExpenseRepository expenseRepository,
                         ShoppingClaimRepository claimRepository, ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.expenseRepository = expenseRepository;
        this.claimRepository = claimRepository;
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
            add(paid, owed, e.getPayer(), e.getAmount(), sharers);
            var label = e.getMemo() == null || e.getMemo().isBlank() ? e.getKind().getLabel() : e.getKind().getLabel() + " · " + e.getMemo();
            lines.add(new SettleDto.Line(e.getId(), e.getPayer().getId(), e.getPayer().getName(), e.getKind().name(), label, e.getAmount(), false));
        }
        for (var c : claims) {
            add(paid, owed, c.getMember(), c.getAmount(), c.getItem().isDrink() ? drinkers : members);
            lines.add(new SettleDto.Line(null, c.getMember().getId(), c.getMember().getName(), "SHOPPING",
                    "장보기 · " + c.getItem().getLabel(), c.getAmount(), true));
        }

        var balances = members.stream().map(m -> {
            var p = paid.getOrDefault(m.getId(), 0.0);
            var o = owed.getOrDefault(m.getId(), 0.0);
            return new SettleDto.Balance(m.getId(), m.getName(), (int) Math.round(p), (int) Math.round(o), (int) Math.round(p - o));
        }).toList();
        var total = lines.stream().mapToInt(SettleDto.Line::amount).sum();
        return new SettleDto.Board(total, lines, balances, transfers(members, paid, owed), trip.getDriverPaysFuel(),
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
            if (diff >= 1) creditors.add(new Side(m, new double[]{diff}));
            else if (diff <= -1) debtors.add(new Side(m, new double[]{-diff}));
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
            if (to.left()[0] < 1) c++;
            if (from.left()[0] < 1) d++;
        }
        return out;
    }

    @Transactional
    public SettleDto.Board add(Long tripId, Member me, SettleDto.ExpenseRequest req) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var memo = req.memo() == null ? null : req.memo().strip();
        expenseRepository.save(new Expense(trip, me, req.kind(), memo, req.amount()));
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

    // 방을 나간 사람이 적은 돈도 같이 빠진다(남은 사람끼리 다시 나눈다)
    @EventListener
    public void onMemberLeaving(MemberLeaving e) {
        expenseRepository.deleteByPayerId(e.memberId());
    }
}
