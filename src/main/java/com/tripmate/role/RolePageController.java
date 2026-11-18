package com.tripmate.role;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class RolePageController {

    // GET: /trips/1/roles
    @GetMapping("/trips/{tripId}/roles")
    public String roles(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        model.addAttribute("tab", "roles");
        return "role/roles";
    }
}
