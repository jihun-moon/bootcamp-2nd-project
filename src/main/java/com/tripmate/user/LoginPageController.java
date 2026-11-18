package com.tripmate.user;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 화면만 돌려준다. 어떤 로그인 버튼을 보일지는 login.js 가 /api/auth/options 로 묻는다
@Controller
public class LoginPageController {

    // GET: /login?next=/trips/1   (카카오 로그인이 실패하면 /login?error)
    @GetMapping("/login")
    public String login() {
        return "user/login";
    }

    // GET: /login/done   카카오 로그인이 끝나면 SecurityConfig 가 여기로 보낸다. 로그인 전에 맡겨 둔 곳으로 돌아간다
    @GetMapping("/login/done")
    public String loginDone() {
        return "user/login-done";
    }
}
