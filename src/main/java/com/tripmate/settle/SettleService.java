package com.tripmate.settle;

import com.tripmate.role.CarService;
import com.tripmate.shopping.ShoppingClaimRepository;
import com.tripmate.trip.Member;
import com.tripmate.trip.MemberLeaving;
import com.tripmate.trip.Trip;
import com.tripmate.trip.TripChanged;
import com.tripmate.trip.TripDeleting;
import com.tripmate.trip.TripRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 다녀와서 "누가 누구에게 얼마". 낸 돈(직접 적은 것 + 장보기 "내가 살게"에 적은 돈)을 모두 똑같이 나누되,
// 기름값·톨비는 방 규칙이 "운전자는 안 냄"이면 기름값을 낸 사람(운전자)을 빼고,
// 술(직접 적은 술, 장보기 소주·맥주)은 규칙이 "술값은 마신 사람만"이면 "술 마셔요"인 사람끼리 나눈다.
// 보낼 돈은 많이 받을 사람부터 맞춰 보내는 횟수를 줄이고, 100원 단위로 올린다(회비처럼)
@Service
public class SettleService {

    private static final int UNIT = 100;
    // 소수 셈 오차(34.000000001)로 100원이 한 번 더 붙지 않게 올리기 전에 아주 작은 값을 뺀다
    private static final double ROUNDING_SLACK = 1e-9;

    private final TripRepository tripRepository;
    private final ExpenseRepository expenseRepository;
    private final ShoppingClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final SettleLinkRepository linkRepository;
    private final DuesRepository duesRepository;
    private final CarService carService;
    private final ApplicationEventPublisher events;

    public SettleService(TripRepository tripRepository, ExpenseRepository expenseRepository,
                         ShoppingClaimRepository claimRepository, PaymentRepository paymentRepository,
                         SettleLinkRepository linkRepository, DuesRepository duesRepository, CarService carService,
                         ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.expenseRepository = expenseRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.linkRepository = linkRepository;
        this.duesRepository = duesRepository;
        this.carService = carService;
        this.events = events;
    }

    // 정산 받는 링크 코드. 있으면 그대로, 없으면 만든다(몇 번 불러도 같은 코드)
    @Transactional
    public String linkCode(Long tripId) {
        return linkRepository.findByTripId(tripId)
                .orElseGet(() -> linkRepository.save(new SettleLink(tripRepository.getReferenceById(tripId))))
                .getCode();
    }

    // /s/{code} 화면. 틀린 코드는 404(있는지 없는지 말고는 아무것도 알려 주지 않는다)
    @Transactional(readOnly = true)
    public SettleDto.Shared shared(String code) {
        var trip = linkRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).getTrip();
        var b = board(trip.getId());
        return new SettleDto.Shared(trip.getId(), trip.getTitle(), trip.getDestination(), trip.getStartDate(), trip.getEndDate(),
                b.total(), b.lines(), b.transfers(), b.payments(), b.dues() != null && b.dues().pending());
    }

    @Transactional(readOnly = true)
    public SettleDto.Board board(Long tripId) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var members = trip.getMembers();
        var departed = trip.getEveryone().stream().filter(Member::isDeparted).toList();
        var expenses = expenseRepository.findByTripIdOrderById(tripId);
        var claims = claimRepository.findByTripId(tripId).stream()
                .filter(c -> c.getAmount() != null && c.getAmount() > 0).toList();
        var seating = carService.seating(tripId);
        var driversExempt = Boolean.FALSE.equals(trip.getDriverPaysFuel());
        var fuelSharers = fuelSharers(members, expenses, seating, driversExempt);
        var drinkers = drinkers(trip);
        var staySharers = staySharers(trip, departed);

        var paid = new HashMap<Long, Double>();
        var owed = new HashMap<Long, Double>();
        var lines = new ArrayList<SettleDto.Line>();
        for (var e : expenses) {
            var car = seating.cars().get(e.getPayer().getId());
            var sharers = switch (e.getKind()) {
                case FUEL -> car == null ? fuelSharers : carSharers(members, car, e.getPayer().getId(), driversExempt);
                case DRINK -> drinkers;
                case STAY -> staySharers;
                default -> members;
            };
            // 직접 고른 사람이 있으면 그 사람들끼리(나간 사람은 빠진다). 다 빠졌으면 기본대로
            var picked = members.stream().filter(m -> e.getSharerIds().contains(m.getId())).toList();
            if (!picked.isEmpty()) sharers = picked;
            add(paid, owed, e.getPayer(), e.getAmount(), sharers);
            var label = e.getMemo() == null || e.getMemo().isBlank() ? e.getKind().getLabel() : e.getKind().getLabel() + " · " + e.getMemo();
            lines.add(new SettleDto.Line(e.getId(), e.getPayer().getId(), nameOf(e.getPayer()), e.getKind().name(), label,
                    e.getAmount(), false, picked.isEmpty() ? null : picked.size()));
        }
        for (var c : claims) {
            add(paid, owed, c.getMember(), c.getAmount(), c.getItem().isDrink() ? drinkers : members);
            lines.add(new SettleDto.Line(null, c.getMember().getId(), nameOf(c.getMember()), "SHOPPING",
                    "장보기 · " + c.getItem().getLabel(), c.getAmount(), true, null));
        }
        var payments = addPayments(tripId, paid, owed);

        // 정산에 나오는 사람: 지금 있는 사람 + 돈이 걸린 나간 사람
        var parties = new ArrayList<>(members);
        departed.stream().filter(m -> paid.containsKey(m.getId()) || owed.containsKey(m.getId())).forEach(parties::add);
        var balances = parties.stream().map(m -> {
            var p = paid.getOrDefault(m.getId(), 0.0);
            var o = owed.getOrDefault(m.getId(), 0.0);
            return new SettleDto.Balance(m.getId(), nameOf(m), (int) Math.round(p), (int) Math.round(o), (int) Math.round(p - o));
        }).toList();
        var total = lines.stream().mapToInt(SettleDto.Line::amount).sum();
        return new SettleDto.Board(total, lines, balances, transfers(parties, paid, owed, carOf(seating)), payments,
                trip.getDriverPaysFuel(), trip.getDrinksSeparate(), trip.getRefundNoShow(), dues(trip, payments));
    }

    // 기름값을 낸 사람 = 운전한 사람. 규칙이 "운전자는 안 냄"이면 기름값 몫에서 뺀다(다 빠지면 모두가 낸다).
    // 차 나누기(CarService)가 있으면 기름값은 그 차에 탄 사람끼리(carSharers) 나누고, 이 목록은 차가 없을 때 쓴다.
    // 기차로 따로 간 사람은 기름값을 내지 않는다
    private static List<Member> fuelSharers(List<Member> members, List<Expense> expenses, CarService.Seating seating,
                                            boolean driversExempt) {
        var driverIds = expenses.stream().filter(e -> e.getKind() == ExpenseKind.FUEL)
                .map(e -> e.getPayer().getId()).collect(Collectors.toSet());
        var sharers = members.stream().filter(m -> !seating.separate().contains(m.getId()))
                .filter(m -> !driversExempt || !driverIds.contains(m.getId())).toList();
        return sharers.isEmpty() ? members : sharers;
    }

    // 술: 규칙을 켰을 때만 마시는 사람끼리(아무도 안 마시면 모두). 전에는 규칙을 켜도 결과가 같아 오해를 샀다(docs/17 8절)
    private static List<Member> drinkers(Trip trip) {
        var members = trip.getMembers();
        var drinksOnly = Boolean.TRUE.equals(trip.getDrinksSeparate());
        var drinkers = members.stream().filter(m -> !drinksOnly || m.drinks()).toList();
        return drinkers.isEmpty() ? members : drinkers;
    }

    // 숙소: 나간 사람도 먼저 낸 돈·보낸 돈은 남는다. 규칙이 "전날 빠지면 예약금 안 돌려줘요"면 숙소 몫도 그대로 진다
    private static List<Member> staySharers(Trip trip, List<Member> departed) {
        var sharers = new ArrayList<>(trip.getMembers());
        if (Boolean.FALSE.equals(trip.getRefundNoShow())) sharers.addAll(departed);
        return sharers;
    }

    // 보냈어요/받았어요: 보낸 사람이 받은 사람 몫을 낸 것으로 셈한다. 그러면 남은 보낼 줄에서 저절로 빠진다
    private List<SettleDto.Paid> addPayments(Long tripId, Map<Long, Double> paid, Map<Long, Double> owed) {
        var rows = new ArrayList<SettleDto.Paid>();
        for (var p : paymentRepository.findByTripIdOrderById(tripId)) {
            paid.merge(p.getSender().getId(), (double) p.getAmount(), Double::sum);
            owed.merge(p.getReceiver().getId(), (double) p.getAmount(), Double::sum);
            rows.add(new SettleDto.Paid(p.getId(), p.getSender().getId(), nameOf(p.getSender()), p.getReceiver().getId(),
                    nameOf(p.getReceiver()), p.getAmount(), p.getMarkedBy().getId(), nameOf(p.getMarkedBy()), p.isDues()));
        }
        return rows;
    }

    // 회비 판. 여행이 끝나기 전(pending)에는 보낼 줄 대신 "다녀와서 남은 회비를 나눠요"로 보인다. 회비가 없으면 null
    private SettleDto.Dues dues(Trip trip, List<SettleDto.Paid> payments) {
        return duesRepository.findByTripId(trip.getId()).map(d -> {
            var duesPaid = payments.stream().filter(p -> p.dues() && p.toId().equals(d.getCollector().getId())).toList();
            var end = trip.getEndDate();
            return new SettleDto.Dues(d.getAmount(), d.getCollector().getId(), nameOf(d.getCollector()),
                    duesPaid.stream().map(SettleDto.Paid::fromId).toList(), duesPaid.stream().mapToInt(SettleDto.Paid::amount).sum(),
                    end == null || end.isAfter(LocalDate.now()));
        }).orElse(null);
    }

    // 그 차에 탄 사람(운전자 빼기 규칙이면 운전자 빼고). 혼자 탄 차면 운전자 혼자
    private static List<Member> carSharers(List<Member> members, Set<Long> car, Long driverId, boolean driversExempt) {
        var riders = members.stream().filter(m -> car.contains(m.getId()))
                .filter(m -> !driversExempt || !m.getId().equals(driverId)).toList();
        return riders.isEmpty() ? members.stream().filter(m -> m.getId().equals(driverId)).toList() : riders;
    }

    private static Map<Long, Long> carOf(CarService.Seating seating) {
        var map = new HashMap<Long, Long>();
        seating.cars().forEach((driver, riders) -> riders.stream().filter(r -> !r.equals(driver)).forEach(r -> map.put(r, driver)));
        return map;
    }

    private static String nameOf(Member m) {
        return m.isDeparted() ? m.getName() + "(나감)" : m.getName();
    }

    private static void add(Map<Long, Double> paid, Map<Long, Double> owed, Member payer, int amount, List<Member> sharers) {
        paid.merge(payer.getId(), (double) amount, Double::sum);
        var each = (double) amount / sharers.size();
        sharers.forEach(m -> owed.merge(m.getId(), each, Double::sum));
    }

    // 아직 받을(보낼) 돈이 남은 사람. 짝지어 보낼 때마다 left 가 준다
    private static class Remaining {
        final Member member;
        double left;

        Remaining(Member member, double left) {
            this.member = member;
            this.left = left;
        }
    }

    // 받을 사람과 보낼 사람을 큰 금액부터 짝짓는다. 8명이면 보내는 줄이 많아야 7줄.
    // 차를 나눴으면 먼저 탄 사람이 자기 차 운전자에게 보낸다. 큰 금액끼리만 짝지으면 재현 차에 탄 도윤이 민지에게
    // 보내는 줄이 나와 "왜 내가 민지한테?"가 됐다(합은 맞았다). carOf = 탄 사람 id -> 그 차 운전자 id
    // shortcut: 줄마다 100원 단위로 올려서 받는 사람이 몇십 원 더 받는다(10,000 ÷ 3 → 3,400 × 2 = 6,800). 회비를 100원 단위로
    //   걷는 관습을 따른 것이고, 남는 자투리는 100원 아래라 끝난 것으로 본다. 원 단위가 필요해지면 마지막 줄에서 차액을 빼서 맞춘다
    private static List<SettleDto.Transfer> transfers(List<Member> members, Map<Long, Double> paid, Map<Long, Double> owed,
                                                      Map<Long, Long> carOf) {
        var creditors = new ArrayList<Remaining>();
        var debtors = new ArrayList<Remaining>();
        for (var m : members) {
            var diff = paid.getOrDefault(m.getId(), 0.0) - owed.getOrDefault(m.getId(), 0.0);
            // 보낼 줄을 100원 단위로 올려 보내면 몇십 원씩 남는다. 100원 아래는 끝난 것으로 본다
            if (diff >= UNIT) creditors.add(new Remaining(m, diff));
            else if (diff <= -UNIT) debtors.add(new Remaining(m, -diff));
        }
        var out = new ArrayList<SettleDto.Transfer>();
        // 1. 같은 차: 탄 사람 -> 그 차 운전자(운전자가 받을 사람일 때만)
        for (var from : debtors) {
            var driverId = carOf.get(from.member.getId());
            if (driverId == null) continue;
            creditors.stream().filter(to -> to.member.getId().equals(driverId)).findFirst().ifPresent(to -> send(from, to, out));
        }
        // 2. 남은 것은 큰 금액끼리
        Comparator<Remaining> biggestFirst = Comparator.comparingDouble((Remaining r) -> r.left).reversed();
        var restTo = creditors.stream().filter(r -> r.left >= UNIT).sorted(biggestFirst).toList();
        var restFrom = debtors.stream().filter(r -> r.left >= UNIT).sorted(biggestFirst).toList();
        int c = 0, d = 0;
        while (c < restTo.size() && d < restFrom.size()) {
            send(restFrom.get(d), restTo.get(c), out);
            if (restTo.get(c).left < UNIT) c++;
            if (restFrom.get(d).left < UNIT) d++;
        }
        return out;
    }

    private static void send(Remaining from, Remaining to, List<SettleDto.Transfer> out) {
        var x = Math.min(to.left, from.left);
        var won = (int) (Math.ceil(x / UNIT - ROUNDING_SLACK) * UNIT);
        if (won > 0) out.add(new SettleDto.Transfer(from.member.getId(), nameOf(from.member),
                to.member.getId(), nameOf(to.member), won));
        to.left -= x;
        from.left -= x;
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

    // 방을 지우면 그 방의 보냈어요 표시와 먼저 낸 돈도 지운다
    @EventListener
    public void onTripDeleting(TripDeleting e) {
        duesRepository.findByTripId(e.tripId()).ifPresent(duesRepository::delete);
        linkRepository.findByTripId(e.tripId()).ifPresent(linkRepository::delete);
        paymentRepository.deleteAll(paymentRepository.findByTripIdOrderById(e.tripId()));
        expenseRepository.deleteAll(expenseRepository.findByTripIdOrderById(e.tripId()));
    }

    // 회비 정하기·고치기: 방장이나 지금 총무. 총무는 이 방에 있는 사람만.
    // shortcut: 낸 뒤 금액을 바꾸면 이미 낸 사람은 옛 금액으로 남는다(차액은 다녀와서 정산이 맞춘다). 차액 알림이 필요해지면 그때
    @Transactional
    public SettleDto.Board setDues(Long tripId, Member me, SettleDto.DuesRequest req) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var current = duesRepository.findByTripId(tripId);
        var isCollector = current.map(d -> d.getCollector().getId().equals(me.getId())).orElse(false);
        if (!me.isHost() && !isCollector) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var collector = trip.getMembers().stream().filter(m -> m.getId().equals(req.collectorId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 방 사람에게만 걷을 수 있어요"));
        current.ifPresentOrElse(d -> d.change(collector, req.amount()),
                () -> duesRepository.save(new Dues(trip, collector, req.amount())));
        events.publishEvent(new TripChanged(tripId, "settle"));
        return board(tripId);
    }

    // 회비 냈어요: 낸 사람 본인, 총무, 방장. 두 번 눌러도 한 번(이미 있으면 그대로)
    @Transactional
    public SettleDto.Board markDues(Long tripId, Member me, Long memberId) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var dues = duesRepository.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var collector = dues.getCollector();
        checkCanMarkDues(me, memberId, collector);
        var payer = trip.getMembers().stream().filter(m -> m.getId().equals(memberId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 방 사람만 돼요"));
        if (payer.getId().equals(collector.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "총무 몫은 이미 총무에게 있어요");
        var already = paymentRepository.findByTripIdOrderById(tripId).stream()
                .anyMatch(p -> p.isDues() && p.getSender().getId().equals(payer.getId())
                        && p.getReceiver().getId().equals(collector.getId()));
        if (!already) {
            var marker = trip.getMembers().stream().filter(m -> m.getId().equals(me.getId())).findFirst().orElseThrow();
            paymentRepository.save(Payment.dues(trip, payer, collector, marker, dues.getAmount()));
            events.publishEvent(new TripChanged(tripId, "settle"));
        }
        return board(tripId);
    }

    // 잘못 누른 회비 되돌리기: 같은 사람들(본인, 총무, 방장)
    @Transactional
    public SettleDto.Board unmarkDues(Long tripId, Member me, Long memberId) {
        var dues = duesRepository.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        checkCanMarkDues(me, memberId, dues.getCollector());
        paymentRepository.findByTripIdOrderById(tripId).stream()
                .filter(p -> p.isDues() && p.getSender().getId().equals(memberId)
                        && p.getReceiver().getId().equals(dues.getCollector().getId()))
                .forEach(paymentRepository::delete);
        events.publishEvent(new TripChanged(tripId, "settle"));
        return board(tripId);
    }

    private static void checkCanMarkDues(Member me, Long memberId, Member collector) {
        if (!me.isHost() && !me.getId().equals(memberId) && !me.getId().equals(collector.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    // 총무가 방을 나가면 회비 설정만 지운다(방장이 새 총무로 다시 정한다). 이미 낸 회비는 보낸 돈으로 남아
    // 정산에서 나간 총무가 돌려줄 줄로 나온다
    @EventListener
    public void onMemberLeaving(MemberLeaving e) {
        duesRepository.findByTripId(e.tripId())
                .filter(d -> d.getCollector().getId().equals(e.memberId()))
                .ifPresent(duesRepository::delete);
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
}
