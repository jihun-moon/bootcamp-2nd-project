package com.tripmate.survey;

import com.tripmate.trip.TripAccess;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/members/{memberId}/survey")
public class SurveyController {

    private final TripAccess tripAccess;
    private final SurveyService surveyService;

    public SurveyController(TripAccess tripAccess, SurveyService surveyService) {
        this.tripAccess = tripAccess;
        this.surveyService = surveyService;
    }

    // PUT: /api/trips/1/members/2/survey  body = SurveyDto.Request 위 주석의 JSON
    // 다시 하면 고쳐 쓰니 POST 대신 PUT. 처음이든 다시든 같은 요청이다. 본인 설문만 쓸 수 있다
    @PutMapping
    public ResponseEntity<SurveyDto.Response> saveSurvey(@PathVariable Long tripId, @PathVariable Long memberId,
                                                         @Valid @RequestBody SurveyDto.Request req) {
        var me = tripAccess.self(tripId, memberId);
        return ResponseEntity.ok(surveyService.save(tripId, me, req));
    }

    // GET: /api/trips/1/members/2/survey   보는 사람은 로그인한 본인
    @GetMapping
    public ResponseEntity<SurveyDto.Response> getSurvey(@PathVariable Long tripId, @PathVariable Long memberId) {
        var viewer = tripAccess.me(tripId);
        return ResponseEntity.ok(surveyService.find(tripId, memberId, viewer));
    }
}
