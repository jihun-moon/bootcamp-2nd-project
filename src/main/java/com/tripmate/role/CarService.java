package com.tripmate.role;

import com.tripmate.trip.Member;
import com.tripmate.trip.MemberLeaving;
import com.tripmate.trip.TripChanged;
import com.tripmate.trip.TripDeleting;
import com.tripmate.trip.TripRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 차 나누기. 차 = 운전 칸에 들어간 사람. 사는 동네가 달라 차 두 대가 각자 가까운 곳에서 태워 가는 경우를 위한 것.
// 손 안 가게: 아무도 안 고르면 차마다 사람 수가 고르게 저절로 나뉜다. 다른 차가 편한 사람만 한 번 눌러 옮기고,
// 기차·버스로 따로 가는 사람은 "따로 가요". 정산은 기름값을 그 차에 탄 사람끼리 나눈다(SettleService)
@Service
public class CarService {

    // 승용차 한 대 5명(운전자 포함). 큰 차일 수도 있어 막지는 않고, 안 고른 사람을 넣을 때만 꽉 찬 차를 건너뛴다
    static final int SEATS = 5;

    // 정산이 쓰는 꼴: 운전자 id -> 그 차에 탄 사람(운전자 포함), 따로 가는 사람
    public record Seating(Map<Long, Set<Long>> cars, Set<Long> separate) {
    }

    private final TripRepository tripRepository;
    private final AssignmentRepository assignmentRepository;
    private final RideRepository rideRepository;
    private final CarMeetRepository meetRepository;
    private final ApplicationEventPublisher events;

    public CarService(TripRepository tripRepository, AssignmentRepository assignmentRepository, RideRepository rideRepository,
                      CarMeetRepository meetRepository, ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.assignmentRepository = assignmentRepository;
        this.rideRepository = rideRepository;
        this.meetRepository = meetRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public CarDto.Cars cars(Long tripId) {
        var members = members(tripId);
        var seating = seat(tripId, members);
        var names = members.stream().collect(Collectors.toMap(Member::getId, Member::getName));
        var meets = meetRepository.findByDriverTripId(tripId).stream()
                .collect(Collectors.toMap(m -> m.getDriver().getId(), m -> m));
        var cars = seating.cars().entrySet().stream().map(e -> {
            var meet = meets.get(e.getKey());
            var riders = e.getValue().stream().filter(id -> !id.equals(e.getKey())).sorted().toList();
            return new CarDto.Car(e.getKey(), names.get(e.getKey()), meet == null ? null : meet.getMeetTime(),
                    meet == null ? null : meet.getMeetPlace(), riders);
        }).toList();
        return new CarDto.Cars(cars, seating.separate().stream().sorted().toList());
    }

    @Transactional(readOnly = true)
    public Seating seating(Long tripId) {
        return seat(tripId, members(tripId));
    }

    // 어느 차 탈래요(driverId), 따로 가요(null). 본인이나 방장
    @Transactional
    public CarDto.Cars ride(Long tripId, Member me, Long memberId, Long driverId) {
        if (!me.isHost() && !me.getId().equals(memberId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var members = members(tripId);
        var byId = members.stream().collect(Collectors.toMap(Member::getId, m -> m));
        var member = byId.get(memberId);
        if (member == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var drivers = driverIds(tripId, members);
        if (drivers.contains(memberId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "운전하는 사람은 자기 차예요");
        if (driverId != null && !drivers.contains(driverId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "운전하는 사람의 차만 고를 수 있어요");
        var driver = driverId == null ? null : byId.get(driverId);
        rideRepository.findByMemberTripId(tripId).stream().filter(r -> r.getMember().getId().equals(memberId)).findFirst()
                .ifPresentOrElse(r -> r.changeTo(driver), () -> rideRepository.save(new Ride(member, driver)));
        events.publishEvent(new TripChanged(tripId, "cars"));
        return cars(tripId);
    }

    // 차마다 태우는 곳·시간. 그 차 운전자나 방장
    @Transactional
    public CarDto.Cars meet(Long tripId, Member me, Long driverId, CarDto.MeetRequest req) {
        if (!me.isHost() && !me.getId().equals(driverId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var members = members(tripId);
        if (!driverIds(tripId, members).contains(driverId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var driver = members.stream().filter(m -> m.getId().equals(driverId)).findFirst().orElseThrow();
        var place = req.place() == null || req.place().isBlank() ? null : req.place().strip();
        var meet = meetRepository.findByDriverTripId(tripId).stream().filter(m -> m.getDriver().getId().equals(driverId)).findFirst()
                .orElseGet(() -> meetRepository.save(new CarMeet(driver)));
        meet.change(req.time(), place);
        events.publishEvent(new TripChanged(tripId, "cars"));
        return cars(tripId);
    }

    // 나간 사람이 고른 차, 그 사람 차를 고른 사람(다시 저절로 나뉜다), 그 사람 차의 태우는 곳을 지운다
    @EventListener
    public void onMemberLeaving(MemberLeaving e) {
        rideRepository.findByMemberTripId(e.tripId()).stream()
                .filter(r -> r.getMember().getId().equals(e.memberId()) || (r.getDriver() != null && r.getDriver().getId().equals(e.memberId())))
                .forEach(rideRepository::delete);
        meetRepository.findByDriverTripId(e.tripId()).stream()
                .filter(m -> m.getDriver().getId().equals(e.memberId()))
                .forEach(meetRepository::delete);
    }

    @EventListener
    public void onTripDeleting(TripDeleting e) {
        rideRepository.deleteAll(rideRepository.findByMemberTripId(e.tripId()));
        meetRepository.deleteAll(meetRepository.findByDriverTripId(e.tripId()));
    }

    private List<Member> members(Long tripId) {
        return tripRepository.findWithMembersById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).getMembers();
    }

    private Set<Long> driverIds(Long tripId, List<Member> members) {
        var active = members.stream().map(Member::getId).collect(Collectors.toSet());
        return assignmentRepository.findByRoleTripIdOrderById(tripId).stream()
                .filter(a -> a.getRole().getKind() == RoleKind.DRIVER)
                .map(a -> a.getMember().getId())
                .filter(active::contains)
                .collect(Collectors.toSet());
    }

    // 차례인 차가 꽉 찼으면 자리 남은 다음 차로. 다 찼으면 차례 그대로(화면이 "5인승이면 넘어요"로 알린다)
    private static Set<Long> seatDefault(Map<Long, Set<Long>> cars, Long turn) {
        if (cars.get(turn).size() < SEATS) return cars.get(turn);
        return cars.values().stream().filter(c -> c.size() < SEATS).findFirst().orElse(cars.get(turn));
    }

    // 안 고른 사람은 들어온 순서대로 차를 번갈아(1번 차, 2번 차, 1번 차…), 고른 사람만 고른 차로.
    // 처음엔 "지금 가장 적게 탄 차"로 넣었는데, 한 사람이 옮기면 안 고른 다른 사람 자리까지 바뀌었다(CarsTest 에서 찾음).
    // 운전자가 바뀌어 고른 차가 없어졌으면 안 고른 것으로 본다
    private Seating seat(Long tripId, List<Member> members) {
        var drivers = driverIds(tripId, members).stream().sorted().toList();
        var cars = new LinkedHashMap<Long, Set<Long>>();
        drivers.forEach(d -> cars.put(d, new HashSet<>(Set.of(d))));
        var separate = new HashSet<Long>();
        if (drivers.isEmpty()) return new Seating(cars, separate);
        var picked = new HashMap<Long, Ride>();
        rideRepository.findByMemberTripId(tripId).forEach(r -> picked.put(r.getMember().getId(), r));
        var order = members.stream().map(Member::getId).filter(id -> !cars.containsKey(id)).sorted().toList();
        for (var i = 0; i < order.size(); i++) {
            var m = order.get(i);
            var r = picked.get(m);
            if (r != null && r.getDriver() == null) separate.add(m);
            else if (r != null && cars.containsKey(r.getDriver().getId())) cars.get(r.getDriver().getId()).add(m);
            else seatDefault(cars, drivers.get(i % drivers.size())).add(m);
        }
        return new Seating(cars, separate);
    }
}
