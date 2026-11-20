package com.tripmate.trip;

import com.tripmate.role.Role;
import com.tripmate.role.RoleRepository;
import com.tripmate.user.AppUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

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
        return TripDto.Response.of(trip, me);
    }

    // 참여한 방을 최근 것부터. 방 id 를 먼저 모은 뒤 참여자와 같이 한 번에 읽는다(방마다 따로 읽지 않게)
    @Transactional(readOnly = true)
    public List<TripDto.Summary> myTrips(AppUser user) {
        var ids = memberRepository.findTop50ByUserIdOrderByIdDesc(user.getId()).stream()
                .map(m -> m.getTrip().getId()).toList();
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

    // 방장이 방 규칙을 바꾸면 모두의 화면(개요·역할 보드의 교통비·결과 글)이 다시 읽는다
    @Transactional
    public TripDto.Response updateRules(Long tripId, Member host, TripDto.Rules rules) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        trip.updateRules(rules.driverPaysFuel(), rules.refundNoShow(), rules.drinksSeparate());
        events.publishEvent(new TripChanged(tripId, "trip"));
        return TripDto.Response.of(trip, host);
    }

    // 초대 링크를 두 번 눌러도 참여자는 한 번만 들어간다
    @Transactional
    public TripDto.Joined join(AppUser user, String inviteCode) {
        var trip = tripRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var already = trip.getMembers().stream().anyMatch(m -> m.belongsTo(user));
        if (!already) {
            trip.addMember(user, user.getNickname(), false);
            events.publishEvent(new TripChanged(trip.getId(), "members"));
        }
        return new TripDto.Joined(trip.getId());
    }
}
