package com.tripmate.survey;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class SurveyPageController {

    // GET: /trips/1/survey
    @GetMapping("/trips/{tripId}/survey")
    public String survey(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        model.addAttribute("tab", "survey");
        return "survey/survey";
    }
}
