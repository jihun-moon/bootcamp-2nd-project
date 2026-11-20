package com.tripmate.trip;

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
            model.addAttribute("ogDescription", "설문 1분이면 운전·총무 같은 역할과 숙소를 근거 있게 같이 정해요. 앱 설치 없이 바로 열려요.");
        });
        return "trip/join";
    }

    // GET: /trips/1
    @GetMapping("/trips/{tripId}")
    public String overview(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        model.addAttribute("tab", "overview");
        return "trip/overview";
    }
}
