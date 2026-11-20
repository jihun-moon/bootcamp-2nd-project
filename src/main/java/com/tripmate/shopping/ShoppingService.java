package com.tripmate.shopping;

import com.tripmate.trip.TripDeleting;
import org.springframework.context.event.EventListener;
import com.tripmate.trip.MemberLeaving;
import com.tripmate.trip.Member;
import com.tripmate.trip.TripChanged;
import com.tripmate.trip.TripRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShoppingService {

    private final TripRepository tripRepository;
    private final ShoppingPlanRepository planRepository;
    private final ShoppingClaimRepository claimRepository;
    private final ApplicationEventPublisher events;

    public ShoppingService(TripRepository tripRepository, ShoppingPlanRepository planRepository,
                           ShoppingClaimRepository claimRepository, ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.planRepository = planRepository;
        this.claimRepository = claimRepository;
        this.events = events;
    }

    // 장보기 판: 방 인원·날짜와 기준(술 마시는 사람, 1인 고기)으로 목록을 세고, 줄마다 누가 사는지와 쓴 돈을 붙인다
    @Transactional(readOnly = true)
    public ShoppingDto.Board board(Long tripId) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var people = trip.getMembers().size();
        // 날짜가 아직이면 1박으로 센다. 당일치기는 0박(전에는 1박으로 세서 아침 햇반·라면까지 나왔다)
        var nights = (trip.getStartDate() == null || trip.getEndDate() == null) ? 1
                : (int) Math.max(0, ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()));
        var plan = planRepository.findByTripId(tripId);
        // 방장이 따로 정하지 않았으면 각자 고른 "술 마셔요"로 센다(전에는 모두 마신다고 보고 방장이 손으로 줄였다)
        var drinking = (int) trip.getMembers().stream().filter(Member::drinks).count();
        var drinkers = Math.min(people, plan.map(ShoppingPlan::getDrinkers).filter(d -> d != null).orElse(drinking));
        var meatGrams = plan.map(ShoppingPlan::getMeatGrams).orElse(ShoppingPlan.DEFAULT_MEAT_GRAMS);

        var claims = claimRepository.findByTripId(tripId).stream()
                .collect(Collectors.toMap(ShoppingClaim::getItem, Function.identity()));
        var items = Arrays.stream(ShoppingItem.values()).filter(i -> i.needed(drinkers, nights)).map(i -> {
            var c = claims.get(i);
            return new ShoppingDto.Item(i, i.getLabel(), i.getGroup(), i.quantity(people, drinkers, nights, meatGrams),
                    c == null ? null : c.getMember().getId(), c == null ? null : c.getMember().getName(),
                    c == null ? null : c.getAmount(), i.isBring());
        }).toList();
        var spent = claims.values().stream().mapToInt(c -> c.getAmount() == null ? 0 : c.getAmount()).sum();
        // 1인 몫은 100원 단위로 올린다(회비 걷을 때 그대로 쓰게)
        var perPerson = people == 0 ? 0 : (int) (Math.ceil(spent / (double) people / 100) * 100);
        return new ShoppingDto.Board(people, nights, drinkers, meatGrams, items, spent, perPerson);
    }

    @Transactional
    public ShoppingDto.Board savePlan(Long tripId, ShoppingDto.PlanRequest req) {
        var plan = planRepository.findByTripId(tripId)
                .orElseGet(() -> planRepository.save(new ShoppingPlan(tripRepository.getReferenceById(tripId))));
        plan.change(req.drinkers(), req.meatGrams());
        events.publishEvent(new TripChanged(tripId, "shopping"));
        return board(tripId);
    }

    // "내가 살게". 처음 누르면 맡고, 내가 맡은 줄이면 쓴 돈만 고친다. 남이 맡은 줄이면 409(누가 사는지는 화면이 보여 준다)
    @Transactional
    public ShoppingDto.Board claim(Long tripId, ShoppingItem item, Member me, ShoppingDto.ClaimRequest req) {
        var existing = claimRepository.findByTripIdAndItem(tripId, item);
        if (existing.isPresent()) {
            var claim = existing.get();
            if (!claim.getMember().getId().equals(me.getId())) throw new ResponseStatusException(HttpStatus.CONFLICT);
            claim.changeAmount(req.amount());
        } else {
            claimRepository.save(new ShoppingClaim(tripRepository.getReferenceById(tripId), item, me, req.amount()));
        }
        events.publishEvent(new TripChanged(tripId, "shopping"));
        return board(tripId);
    }

    // 맡은 걸 내려놓는다. 맡은 사람 본인이나 방장만
    @Transactional
    public ShoppingDto.Board release(Long tripId, ShoppingItem item, Member me) {
        var claim = claimRepository.findByTripIdAndItem(tripId, item)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!claim.getMember().getId().equals(me.getId()) && !me.isHost()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        claimRepository.delete(claim);
        events.publishEvent(new TripChanged(tripId, "shopping"));
        return board(tripId);
    }

    // 방을 지우면 장보기 맡은 줄과 기준도 지운다
    @EventListener
    public void onTripDeleting(TripDeleting e) {
        claimRepository.deleteAll(claimRepository.findByTripId(e.tripId()));
        planRepository.findByTripId(e.tripId()).ifPresent(planRepository::delete);
    }

    // 방을 나간 사람이 "내가 살게"만 하고 아직 안 산 줄은 다시 빈 줄로(남아 있으면 아무도 안 산다).
    // 이미 사서 금액을 적은 줄은 남긴다. 그 돈은 정산에서 나간 사람이 돌려받아야 한다
    @EventListener
    public void onMemberLeaving(MemberLeaving e) {
        claimRepository.findByMemberId(e.memberId()).stream()
                .filter(c -> c.getAmount() == null || c.getAmount() == 0)
                .forEach(claimRepository::delete);
    }
}
