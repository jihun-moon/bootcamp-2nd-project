package com.tripmate.stay;

// 숙소 조건이자 설문 선호 축. 둘이 같은 목록이어야 숙소 점수를 그룹 취향으로 셀 수 있다
public enum Amenity {
    DETACHED("독채"),
    PRIVATE_BBQ("개별 바베큐"),
    HEATED_POOL("온수풀"),
    KARAOKE("노래방"),
    ROOMS_3_PLUS("방 3개 이상"),
    BATHS_2_PLUS("화장실 2개"),
    BARRIER_FREE("1층·엘리베이터");

    private final String label;

    Amenity(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
