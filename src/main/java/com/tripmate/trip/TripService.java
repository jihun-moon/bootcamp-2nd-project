package com.tripmate.trip;

import com.tripmate.role.Role;
import com.tripmate.role.RoleRepository;
import com.tripmate.user.AppUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher events;

    public TripService(TripRepository tripRepository, RoleRepository roleRepository, ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.roleRepository = roleRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public TripDto.Response find(Long tripId, Member me) {
        var trip = tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return TripDto.Response.of(trip, me);
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
