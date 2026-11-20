package com.tripmate.trip;

import com.tripmate.role.Role;
import com.tripmate.role.RoleRepository;
import com.tripmate.user.AppUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher events;

    public TripService(TripRepository tripRepository, MemberRepository memberRepository, RoleRepository roleRepository,
                       ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public TripDto.Response find(Long tripId, Member me) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var next = tripRepository.findByPreviousTripId(tripId).map(Trip::getId).orElse(null);
        return TripDto.Response.of(trip, me, next);
    }

    // 같은 멤버로 다음 여행. 계정 있는 참여자를 그대로 옮기고 방 규칙도 잇는다. 누른 사람이 새 방 방장이고 날짜는 다시 맞춘다.
    // 이미 누가 만들었으면 새로 만들지 않고 그 방으로 보낸다(그 사이 들어온 사람이면 그 방에 넣어 준다)
    @Transactional
    public TripDto.Next next(Long tripId, AppUser user) {
        var existing = tripRepository.findByPreviousTripId(tripId);
        if (existing.isPresent()) {
            var next = existing.get();
            var mine = next.getEveryone().stream().filter(m -> m.belongsTo(user)).findFirst();
            // 새 방에서 방장이 뺀 사람은 지난 방 단추로 다시 못 들어온다(리뷰 #10). 처음 보는 사람만 넣는다
            if (mine.isPresent() && mine.get().isDeparted()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            if (mine.isEmpty()) {
                next.addMember(user, user.getNickname(), false);
                events.publishEvent(new TripChanged(next.getId(), "members"));
            }
            return new TripDto.Next(next.getId(), false, next.getMembers().size());
        }
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var next = trip.nextTrip();
        var me = trip.getMembers().stream().filter(m -> m.belongsTo(user)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        next.addMember(user, me.getName(), true);
        trip.getMembers().stream()
                .filter(m -> m.getUser() != null && m != me)
                .forEach(m -> next.addMember(m.getUser(), m.getName(), false));
        tripRepository.save(next);
        roleRepository.saveAll(Role.defaultsFor(next));
        events.publishEvent(new TripChanged(tripId, "trip"));
        return new TripDto.Next(next.getId(), true, next.getMembers().size());
    }

    // 참여한 방을 최근 것부터. 방 id 를 먼저 모은 뒤 참여자와 같이 한 번에 읽는다(방마다 따로 읽지 않게)
    @Transactional(readOnly = true)
    public List<TripDto.Summary> myTrips(AppUser user) {
        var ids = memberRepository.findTop50ByUserIdOrderByIdDesc(user.getId()).stream()
                .filter(m -> !m.isDeparted()).map(m -> m.getTrip().getId()).toList();
        return tripRepository.findByIdIn(ids).stream()
                .sorted(Comparator.comparingInt((Trip t) -> ids.indexOf(t.getId())))
                .map(t -> TripDto.Summary.of(t, user))
                .toList();
    }

    // 만든 사람이 방장이 되고, 기본 역할(총무·운전…)이 같이 생긴다
    @Transactional
    public TripDto.Response create(AppUser user, TripDto.CreateRequest req) {
        var trip = new Trip(req.title(), req.destination(), req.startDate(), req.endDate());
        var me = trip.addMember(user, user.getNickname(), true);
        tripRepository.save(trip);
        roleRepository.saveAll(Role.defaultsFor(trip));
        return TripDto.Response.of(trip, me);
    }

    // 날짜 맞추기 판. 날짜별로 되는 사람 id 를 모은다(8명 × 많아야 60일이라 한 번 읽어 메모리에서 센다)
    @Transactional(readOnly = true)
    public TripDto.DatesBoard dates(Long tripId) {
        var members = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).getMembers();
        var available = new TreeMap<LocalDate, List<Long>>();
        var responded = 0;
        for (var m : members) {
            if (!m.getAvailableDates().isEmpty()) responded++;
            for (var d : m.getAvailableDates()) available.computeIfAbsent(d, k -> new ArrayList<>()).add(m.getId());
        }
        return new TripDto.DatesBoard(members.size(), responded, available);
    }

    // 내 되는 날을 통째로 바꾼다. 지난 날과 1년 넘게 먼 날은 받지 않는다
    @Transactional
    public TripDto.DatesBoard saveDates(Long tripId, Long memberId, TripDto.DatesRequest req) {
        var today = LocalDate.now();
        if (req.dates().stream().anyMatch(d -> d.isBefore(today) || d.isAfter(today.plusYears(1)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "오늘부터 1년 안의 날만 고를 수 있어요");
        }
        var member = memberRepository.findByIdAndTripId(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        member.changeAvailableDates(req.dates());
        events.publishEvent(new TripChanged(tripId, "dates"));
        return dates(tripId);
    }

    // 방장이 후보 하나를 골라 방 날짜로 정한다. 모두의 방 머리(날짜)가 바뀐다
    @Transactional
    public TripDto.Response setPeriod(Long tripId, Member host, TripDto.PeriodRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "도착일이 출발일보다 앞이에요");
        }
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        trip.updatePeriod(req.startDate(), req.endDate());
        events.publishEvent(new TripChanged(tripId, "trip"));
        return TripDto.Response.of(trip, host);
    }

    // 모일 시간·장소. 방장이 적으면 모두의 방 머리(D-day 줄)에 뜬다
    @Transactional
    public TripDto.Response updateMeet(Long tripId, Member host, TripDto.Meet meet) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        trip.updateMeet(meet.time(), meet.place());
        events.publishEvent(new TripChanged(tripId, "trip"));
        return TripDto.Response.of(trip, host);
    }

    // 방장이 방 규칙을 바꾸면 모두의 화면(개요·역할 보드의 교통비·결과 글)이 다시 읽는다
    @Transactional
    public TripDto.Response updateRules(Long tripId, Member host, TripDto.Rules rules) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        trip.updateRules(rules.driverPaysFuel(), rules.refundNoShow(), rules.drinksSeparate());
        events.publishEvent(new TripChanged(tripId, "trip"));
        return TripDto.Response.of(trip, host);
    }

    // 나가기(본인)·빼기(방장). 그 사람의 설문·역할·숙소 표·장보기 맡은 것은 각 기능이 지우고(MemberLeaving), 올린 숙소는 남긴다.
    // 그래야 숙소·교통비·장보기 1인 금액이 남은 사람 수로 다시 나뉜다(전에는 한 명이 빠져도 그대로 7로 나눴다).
    // 혼자 남은 방은 나갈 수 없다
    @Transactional
    public void leave(Long tripId, Long memberId) {
        tripRepository.lockById(tripId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var member = trip.getMembers().stream().filter(m -> m.getId().equals(memberId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (trip.getMembers().size() == 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "혼자 남은 방은 나갈 수 없어요");
        }
        events.publishEvent(new MemberLeaving(tripId, memberId));
        trip.removeMember(member);
        events.publishEvent(new MemberLeft(tripId));
        events.publishEvent(new TripChanged(tripId, "members"));
    }

    // 술 마시나 바꾸기. 장보기 술 양과 정산(규칙 "술값은 마신 사람만")이 다시 센다
    @Transactional
    public TripDto.Response setDrinks(Long tripId, Long memberId, boolean drinks, Member me) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        trip.getMembers().stream().filter(m -> m.getId().equals(memberId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .changeDrinks(drinks);
        events.publishEvent(new TripChanged(tripId, "members"));
        return TripDto.Response.of(trip, me);
    }

    // 초대 링크를 두 번 눌러도 참여자는 한 번만 들어간다
    @Transactional
    public TripDto.Joined join(AppUser user, String inviteCode) {
        var trip = tripRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // 나갔던 사람이 초대 링크로 다시 오면 그 행을 되살린다(방마다 한 사람 한 행)
        var before = trip.getEveryone().stream().filter(m -> m.belongsTo(user)).findFirst();
        if (before.isEmpty()) {
            trip.addMember(user, user.getNickname(), false);
            events.publishEvent(new TripChanged(trip.getId(), "members"));
        } else if (before.get().isDeparted()) {
            before.get().rejoin();
            events.publishEvent(new TripChanged(trip.getId(), "members"));
        }
        return new TripDto.Joined(trip.getId());
    }
}
