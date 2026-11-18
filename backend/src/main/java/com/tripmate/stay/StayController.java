package com.tripmate.stay;

import com.tripmate.survey.SurveyAnswerRepository;
import com.tripmate.trip.MemberRepository;
import com.tripmate.trip.TripAccess;
import com.tripmate.trip.TripChanged;
import com.tripmate.trip.TripRepository;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class StayController {

    private final TripRepository tripRepository;
    private final MemberRepository memberRepository;
    private final StayRepository stayRepository;
    private final VoteRepository voteRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final LinkPreview linkPreview;
    private final ApplicationEventPublisher events;
    private final TripAccess tripAccess;

    public StayController(TripRepository tripRepository, MemberRepository memberRepository, StayRepository stayRepository,
                          VoteRepository voteRepository, SurveyAnswerRepository surveyAnswerRepository,
                          LinkPreview linkPreview, ApplicationEventPublisher events, TripAccess tripAccess) {
        this.tripRepository = tripRepository;
        this.memberRepository = memberRepository;
        this.stayRepository = stayRepository;
        this.voteRepository = voteRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.linkPreview = linkPreview;
        this.events = events;
        this.tripAccess = tripAccess;
    }

    // GET: /api/trips/1/stays
    @GetMapping("/stays")
    @Transactional(readOnly = true)
    public ResponseEntity<StayDto.Board> getBoard(@PathVariable Long tripId) {
        tripAccess.me(tripId);

        var surveys = surveyAnswerRepository.findByMemberTripId(tripId);
        var preferences = new EnumMap<Amenity, StayDto.Preference>(Amenity.class);
        for (var amenity : Amenity.values()) {
            IntSummaryStatistics stats = surveys.stream()
                    .map(s -> s.getPreferences().get(amenity)).filter(v -> v != null)
                    .collect(Collectors.summarizingInt(Integer::intValue));
            if (stats.getCount() > 0) {
                preferences.put(amenity, new StayDto.Preference(Math.round(stats.getAverage() * 10) / 10.0, stats.getMin()));
            }
        }

        var voters = voteRepository.findByStayTripId(tripId).stream().collect(Collectors.groupingBy(
                v -> v.getStay().getId(), Collectors.mapping(v -> v.getMember().getId(), Collectors.toList())));
        var stays = stayRepository.findByTripIdOrderById(tripId).stream()
                .map(s -> new StayDto.StayView(s.getId(), s.getTitle(), s.getUrl(), s.getImageUrl(),
                        Set.copyOf(s.getAmenities()), voters.getOrDefault(s.getId(), List.of())))
                .toList();
        var amenities = Arrays.stream(Amenity.values()).map(a -> new StayDto.AmenityView(a, a.getLabel())).toList();
        return ResponseEntity.ok(new StayDto.Board(amenities, preferences, surveys.size(), stays));
    }

    // POST: /api/trips/1/stays  body = StayDto.CreateRequest 위 주석의 JSON
    // 링크 미리보기(최대 5초)를 DB 트랜잭션 밖에서 먼저 한다. 안에서 하면 그동안 커넥션을 붙잡는다
    @PostMapping("/stays")
    public ResponseEntity<StayDto.StayView> addStay(@PathVariable Long tripId, @Valid @RequestBody StayDto.CreateRequest req) {
        var member = tripAccess.me(tripId);
        var preview = linkPreview.fetch(req.url());
        var title = firstNonBlank(req.title(), preview.title(), URI.create(req.url()).getHost());
        var amenities = (req.amenities() == null) ? EnumSet.noneOf(Amenity.class) : req.amenities();

        var trip = tripRepository.getReferenceById(tripId);
        var stay = stayRepository.save(new Stay(trip, member, title, req.url(), preview.imageUrl(), amenities));
        events.publishEvent(new TripChanged(tripId, "stays"));
        return ResponseEntity.status(HttpStatus.CREATED).body(new StayDto.StayView(stay.getId(), stay.getTitle(),
                stay.getUrl(), stay.getImageUrl(), Set.copyOf(stay.getAmenities()), List.of()));
    }

    // PUT: /api/trips/1/votes/2  body = { "stayId": 3 }  (한 사람 한 표라 다시 보내면 표를 옮긴다. 본인 표만)
    @PutMapping("/votes/{memberId}")
    @Transactional
    public ResponseEntity<Void> vote(@PathVariable Long tripId, @PathVariable Long memberId,
                                     @RequestBody StayDto.VoteRequest req) {
        var member = tripAccess.self(tripId, memberId);
        var stay = (req.stayId() == null) ? null : stayRepository.findByIdAndTripId(req.stayId(), tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var current = voteRepository.findByMemberId(memberId);

        if (stay == null) {
            current.ifPresent(voteRepository::delete);
        } else if (current.isPresent()) {
            current.get().changeTo(stay);
        } else {
            voteRepository.save(new Vote(member, stay));
        }
        events.publishEvent(new TripChanged(tripId, "stays"));
        return ResponseEntity.noContent().build();
    }

    // 같은 사람이 두 화면에서 동시에 처음 투표하면 member 유일 제약에 걸린다. 한쪽만 들어갔으니 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    private static String firstNonBlank(String... values) {
        for (var v : values) if (v != null && !v.isBlank()) return v.strip();
        return "숙소";
    }
}
