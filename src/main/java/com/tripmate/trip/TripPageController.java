package com.tripmate.trip;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// 화면 틀만 돌려준다. 방 정보는 화면의 jQuery 가 /api/trips/{tripId} 로 읽는다(로그인·참여자 검사는 API 에서)
@Controller
public class TripPageController {

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
    @GetMapping("/join/{inviteCode}")
    public String join(@PathVariable String inviteCode, Model model) {
        model.addAttribute("inviteCode", inviteCode);
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
