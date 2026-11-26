package com.tripmate.controller.shopping;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ShoppingPageController {

    // GET: /trips/1/shopping   화면 틀만. 목록은 shopping.js 가 /api/trips/1/shopping 으로 읽는다
    @GetMapping("/trips/{tripId}/shopping")
    public String shopping(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        model.addAttribute("tab", "shopping");
        return "shopping/shopping";
    }
}
