package com.tripmate.entity.stay;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

// 숙소 조건이자 설문 선호 축. 둘이 같은 목록이어야 숙소 점수를 그룹 취향으로 셀 수 있다
// words 는 숙소 페이지 제목·소개 글에서 이 조건을 알아보는 낱말이다. 링크를 붙이면 조건을 미리 칠해 두고, 사람은 맞는지만 본다
public enum Amenity {
    DETACHED("독채", "독채|단독\\s*(채|펜션|건물|주택)|단독형|private\\s*house"),
    PRIVATE_BBQ("개별 바베큐", "바베큐|바비큐|bbq|그릴"),
    // "풀빌라"만으로는 겨울에 물이 데워지는지 모른다. 틀리게 칠하면 빠진 것보다 나빠서 "온수"가 있을 때만
    HEATED_POOL("온수풀", "온수\\s*(풀|수영장)|heated\\s*pool"),
    KARAOKE("노래방", "노래방|가라오케|karaoke"),
    ROOMS_3_PLUS("방 3개 이상", "(방|침실|객실)\\s*([3-9]|1\\d)\\s*개|쓰리룸|포룸|[3-9]\\s*룸|[3-9]\\s*bedrooms?"),
    BATHS_2_PLUS("화장실 2개", "(화장실|욕실)\\s*[2-9]|[2-9]\\s*(bath|baths|bathrooms)"),
    BARRIER_FREE("1층·엘리베이터", "엘리베이터|1층|무장애|휠체어|barrier[\\s-]*free");

    private final String label;
    private final Pattern words;

    Amenity(String label, String words) {
        this.label = label;
        this.words = Pattern.compile(words, Pattern.CASE_INSENSITIVE);
    }

    public String getLabel() { return label; }

    // "가평 독채 풀빌라 · 개별 바베큐, 방 3개" -> [DETACHED, HEATED_POOL, PRIVATE_BBQ, ROOMS_3_PLUS]
    public static Set<Amenity> guess(String text) {
        var found = EnumSet.noneOf(Amenity.class);
        if (text == null || text.isBlank()) return found;
        for (var a : values()) {
            if (a.words.matcher(text).find()) found.add(a);
        }
        return found;
    }
}
