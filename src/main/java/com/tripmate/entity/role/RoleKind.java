package com.tripmate.entity.role;

import java.util.List;

// 방을 만들면 이 목록대로 역할이 생긴다. 점수 규칙도 제목 문자열이 아니라 이 값으로 고른다
public enum RoleKind {
    TREASURER("총무·타임라인", true, 1, Chore.MONEY, List.of("회비 걷기", "일정표 공유")),
    DRIVER("운전", true, 2, Chore.DRIVING, List.of("차량 픽업", "주유")),
    GROCERY("장보기", true, 1, null, List.of("고기 사기", "술·음료")),
    BBQ("바베큐 세팅", false, 2, Chore.COOKING, List.of("그릴 준비", "뒷정리")),
    DJ("여행 DJ", false, 1, null, List.of("플레이리스트")),
    RECORDER("기록 담당", false, 1, null, List.of("사진·영상", "후기 정리"));

    final String title;
    final boolean required;
    final int capacity;
    final Chore chore;
    final List<String> tasks;

    RoleKind(String title, boolean required, int capacity, Chore chore, List<String> tasks) {
        this.title = title;
        this.required = required;
        this.capacity = capacity;
        this.chore = chore;
        this.tasks = tasks;
    }

    public Chore getChore() { return chore; }
}
