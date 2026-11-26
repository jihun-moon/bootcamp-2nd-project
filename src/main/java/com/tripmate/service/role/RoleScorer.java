package com.tripmate.service.role;

import com.tripmate.entity.role.RoleKind;
import com.tripmate.entity.survey.ActivityStyle;
import com.tripmate.entity.survey.PlanningStyle;
import com.tripmate.entity.survey.SurveyAnswer;
import java.util.ArrayList;
import java.util.List;

// 설문 한 장과 역할 하나로 점수와 근거를 낸다. 근거는 보드 카드에 그대로 보여 준다
final class RoleScorer {

    // 차 없는 사람을 운전에 넣지 않으려고 아주 큰 감점을 준다. 0 칸(빈자리)보다 늘 나쁘다
    static final int FORBIDDEN = -1000;

    // 이동 제약은 다른 사람에게도 보이는 근거라 사정을 드러내지 않는 말로만 쓴다
    private static final String LOW_MOVEMENT = "이동 적은 역할 우선";

    record Score(int value, List<String> reasons) {
    }

    private RoleScorer() {
    }

    static Score score(SurveyAnswer s, RoleKind kind) {
        // 방장이 더한 역할은 규칙이 없다. 누구나 0점이라 남는 사람이 들어간다
        if (kind == null) return new Score(0, s == null ? List.of("설문 전") : List.of());
        // 설문 전이면 차가 있는지 모르니 운전에는 넣지 않는다
        if (s == null) return new Score(kind == RoleKind.DRIVER ? FORBIDDEN : 0, List.of("설문 전"));

        var reasons = new ArrayList<String>();
        var total = 0;

        switch (kind) {
            case TREASURER -> {
                if (s.getPlanning() == PlanningStyle.PLANNER) total += add(reasons, "계획형", 3);
                if (s.getPlanning() == PlanningStyle.OUTLINE) total += add(reasons, "큰 틀 계획", 1);
            }
            case DRIVER -> {
                if (!s.isHasCar()) return new Score(FORBIDDEN, List.of("차 없음"));
                total += add(reasons, "차 있음", 4);
                if (s.isSeparateDeparture()) total += add(reasons, "따로 출발", -2);
                if (s.isMobilityLimited()) total += add(reasons, LOW_MOVEMENT, -3);
            }
            case GROCERY -> {
                if (s.isHasCar()) total += add(reasons, "차 있음", 1);
                if (s.getActivity() == ActivityStyle.OUTDOOR) total += add(reasons, "밖 활동 선호", 1);
                if (s.isMobilityLimited()) total += add(reasons, LOW_MOVEMENT, -3);
            }
            case BBQ -> {
                if (s.getActivity() == ActivityStyle.INDOOR) total += add(reasons, "숙소 휴식 선호", 2);
                if (s.isMobilityLimited()) total += add(reasons, LOW_MOVEMENT, 2);
            }
            case DJ -> {
                if (s.getActivity() == ActivityStyle.INDOOR) total += add(reasons, "숙소 휴식 선호", 1);
                if (s.isMobilityLimited()) total += add(reasons, LOW_MOVEMENT, 2);
            }
            case RECORDER -> {
                if (s.getActivity() == ActivityStyle.OUTDOOR) total += add(reasons, "밖 활동 선호", 2);
            }
        }
        if (kind.getChore() != null && s.getAvoid().contains(kind.getChore())) total += add(reasons, "맡기 싫은 일", -5);
        return new Score(total, reasons);
    }

    private static int add(List<String> reasons, String why, int points) {
        reasons.add(why + (points > 0 ? " +" : " ") + points);
        return points;
    }
}
