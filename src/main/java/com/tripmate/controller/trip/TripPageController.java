package com.tripmate.controller.trip;

import com.tripmate.entity.trip.Member;
import com.tripmate.repository.trip.TripRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// 화면 틀만 돌려준다. 초대 링크 미리보기(og)만 방 이름을 서버에서 넣는다. 방 정보는 화면의 jQuery 가 /api/trips/{tripId} 로 읽는다(로그인·참여자 검사는 API 에서)
@Controller
public class TripPageController {

    private final TripRepository tripRepository;

    public TripPageController(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    // GET: /
    @GetMapping("/")
    public String home() {
        return "index";
    }

    // GET: /demo   로그인 없이 둘러보는 읽기 전용 체험 방(예시 데이터, DB 안 씀)
    @GetMapping("/demo")
    public String demo() {
        return "trip/demo";
    }

    // GET: /trips/new
    @GetMapping("/trips/new")
    public String newTrip() {
        return "trip/new";
    }

    // GET: /join/3f9a1c2b7d4e   친구가 보낸 초대 링크
    // 카톡은 링크를 받으면 이 HTML 의 og 태그로 미리보기를 만든다(자바스크립트는 안 돈다). 그래서 방 이름을 서버에서 넣는다.
    // 초대 코드를 아는 사람은 어차피 들어올 수 있으니 방 이름 정도는 보여도 된다
    @GetMapping("/join/{inviteCode}")
    public String join(@PathVariable String inviteCode, Model model) {
        model.addAttribute("inviteCode", inviteCode);
        tripRepository.findByInviteCode(inviteCode).ifPresent(trip -> {
            model.addAttribute("ogTitle", "'" + trip.getTitle() + "' 여행에 초대받았어요");
            // 미리보기에 "몇 명 중 몇 명 했는지"를 넣는다. 친구가 이미 했다는 걸 보면 누를 마음이 생긴다(조사: 다들 하는 걸 보면 따라 함).
            // shortcut: 카톡은 같은 주소의 미리보기를 한동안 들고 있어서 숫자가 늦게 바뀐다. 초대 글을 다시 보낼 때 새로 읽힌다
            var members = trip.getMembers();
            var done = members.stream().filter(Member::isSurveyDone).count();
            model.addAttribute("ogDescription", done > 0
                    ? members.size() + "명 중 " + done + "명 벌써 했어요 · 앱 설치 없이 1분이면 운전·총무까지 정해져요"
                    : "지금 " + members.size() + "명 들어와 있어요 · 앱 설치 없이 1분이면 운전·총무까지 정해져요");
        });
        return "trip/join";
    }

    // GET: /trips/1/receipt   우리 여행 영수증(역할·1인 숙소비·교통비·규칙을 한 장으로). 데이터는 화면이 API 로 읽는다
    @GetMapping("/trips/{tripId}/receipt")
    public String receipt(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        model.addAttribute("tab", "receipt");
        return "trip/receipt";
    }

    // GET: /trips/1
    @GetMapping("/trips/{tripId}")
    public String overview(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        model.addAttribute("tab", "overview");
        return "trip/overview";
    }
}
