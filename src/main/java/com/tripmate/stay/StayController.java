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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class StayController {

    private static final int MAX_STAYS = 30;
    private static final int MAX_URL = 2000;

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
                .map(s -> new StayDto.StayView(s.getId(), s.getTitle(), s.getUrl(), s.getImageUrl(), s.getPrice(),
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
        // 숙소마다 서버가 바깥 페이지를 한 번 읽고, 보드는 숙소를 전부 읽는다. 끝없이 늘지 않게 막는다
        if (stayRepository.countByTripId(tripId) >= MAX_STAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "숙소는 " + MAX_STAYS + "개까지 올릴 수 있어요");
        }
        var shared = ShareText.of(req.url());
        var preview = linkPreview.fetch(shared.url());
        var title = firstNonBlank(req.title(), preview.title(), shared.name(), hostOf(shared.url()));
        var imageUrl = (preview.imageUrl() != null && preview.imageUrl().length() <= MAX_URL) ? preview.imageUrl() : null;
        var amenities = (req.amenities() == null) ? EnumSet.noneOf(Amenity.class) : req.amenities();

        var trip = tripRepository.getReferenceById(tripId);
        // 가격 칸을 비우고 올렸으면(미리보기 전에 눌렀으면) 링크가 알려 준 값을 쓴다
        var price = (req.price() != null) ? req.price() : preview.price();
        var stay = stayRepository.save(new Stay(trip, member, title, shared.url(), imageUrl, price, amenities));
        events.publishEvent(new TripChanged(tripId, "stays"));
        return ResponseEntity.status(HttpStatus.CREATED).body(new StayDto.StayView(stay.getId(), stay.getTitle(),
                stay.getUrl(), stay.getImageUrl(), stay.getPrice(), Set.copyOf(stay.getAmenities()), List.of()));
    }

    // POST: /api/trips/1/stays/preview  body = { "url": "https://..." } 또는 { "url": "[카카오맵] 가평펜션 ... https://kko.to/x" }(공유 글 통째)
    // 링크를 붙여 넣자마자 이름·사진과 소개 글에서 찾은 조건을 돌려준다. 사람은 칠해진 조건이 맞는지만 보고 올린다
    @PostMapping("/stays/preview")
    public ResponseEntity<StayDto.Preview> preview(@PathVariable Long tripId, @Valid @RequestBody StayDto.PreviewRequest req) {
        tripAccess.me(tripId);
        var shared = ShareText.of(req.url());
        var preview = linkPreview.fetch(shared.url());
        var imageUrl = (preview.imageUrl() != null && preview.imageUrl().length() <= MAX_URL) ? preview.imageUrl() : null;
        var title = (preview.title() != null) ? preview.title() : shared.name();
        var text = Objects.toString(title, "") + " " + Objects.toString(preview.description(), "");
        return ResponseEntity.ok(new StayDto.Preview(title, imageUrl, preview.price(), Amenity.guess(text)));
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

    // "https://a.com/|x" 처럼 \S+ 는 통과해도 URI 로는 안 읽히는 링크가 있다. 그때는 이름을 비워 "숙소"로 둔다
    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (var v : values) if (v != null && !v.isBlank()) return v.strip();
        return "숙소";
    }
}
