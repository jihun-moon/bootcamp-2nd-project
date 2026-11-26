package com.tripmate.controller.stay;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class StayPageController {

    // GET: /trips/1/stays
    @GetMapping("/trips/{tripId}/stays")
    public String stays(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        model.addAttribute("tab", "stays");
        return "stay/stays";
    }
}
