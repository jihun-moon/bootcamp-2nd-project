package com.tripmate.settle;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// 정산 받는 링크. 로그인 없이 "누가 누구에게 얼마"만 본다(카뱅 빵나누기처럼 받는 쪽은 보고 보내기만).
// 카톡 안 브라우저는 크롬과 로그인이 따로라 방 화면 링크를 누르면 로그인부터 나왔다. 계좌는 받지도 저장하지도 않는다.
// 서버에서 그린다: 카톡 미리보기(og)가 자바스크립트 없이 읽고, 따로 여는 API 가 없어 바깥에 드러나는 칸도 이 화면뿐이다
@Controller
public class SettlePageController {

    private final SettleService settleService;

    public SettlePageController(SettleService settleService) {
        this.settleService = settleService;
    }

    // GET: /s/Qm9vdGNhbXAtMi10cmlw   틀린 코드면 404 화면
    @GetMapping("/s/{code}")
    public String shared(@PathVariable String code, Model model, HttpServletResponse response) {
        var shared = settleService.shared(code);
        model.addAttribute("s", shared);
        model.addAttribute("ogTitle", "'" + shared.title() + "' 정산 나왔어요");
        model.addAttribute("ogDescription", shared.transfers().isEmpty()
                ? "보낼 돈이 다 정리됐어요"
                : "보낼 돈 " + shared.transfers().size() + "줄 · 로그인 없이 누가 누구에게 얼마인지 봐요");
        model.addAttribute("noindex", true);
        // 이름과 금액이 든 화면이라 중간 저장(프록시·공용 PC 뒤로 가기)에 남기지 않는다
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        return "settle/shared";
    }
}
