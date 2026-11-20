package com.tripmate.shopping;

// 펜션 단체 여행 장보기 기본 목록. 양은 사람·술 마시는 사람·박 수·1인 고기 양으로 센다.
// 기준: 커뮤니티에서 가장 많이 쓰는 "1인 고기 300g"(에펨 24명 MT 글, 82cook), 소주는 술 마시는 사람 1명이 하룻밤 0.7병,
// 맥주 2캔, 숯 3kg 1봉이 4명 한 끼. 끼니는 바베큐 저녁 = 박 수, 아침(햇반·라면) = 박 수. docs/14
public enum ShoppingItem {
    PORK_BELLY("삼겹살", "고기"),
    PORK_NECK("목살", "고기"),
    LETTUCE("쌈채소", "곁들임"),
    SSAMJANG("쌈장", "곁들임"),
    SOJU("소주", "술"),
    BEER("맥주 500ml", "술"),
    CHARCOAL("숯 3kg", "바베큐"),
    RICE("햇반", "아침"),
    RAMEN("라면", "아침"),
    WATER("생수 2L", "물"),
    DISPOSABLES("일회용 접시·컵·수저", "기타");

    private final String label;
    private final String group;

    ShoppingItem(String label, String group) {
        this.label = label;
        this.group = group;
    }

    public String getLabel() { return label; }
    public String getGroup() { return group; }

    // 방 규칙 "술값은 마신 사람만"이면 이 줄들은 술 마시는 사람끼리 나눈다(정산)
    public boolean isDrink() { return "술".equals(group); }

    // 사는 양을 사람이 읽는 글로. 고기는 100g 단위로 올려 kg, 나머지는 개수
    String quantity(int people, int drinkers, int nights, int meatGrams) {
        var bbq = nights;
        return switch (this) {
            case PORK_BELLY -> kg(people * meatGrams * 0.7 * bbq);
            case PORK_NECK -> kg(people * meatGrams * 0.3 * bbq);
            case LETTUCE -> ceil(people / 4.0) * bbq + "봉";
            case SSAMJANG -> ceil(people / 8.0) + "통";
            case SOJU -> ceil(drinkers * 0.7 * nights) + "병";
            case BEER -> drinkers * 2 * nights + "캔";
            case CHARCOAL -> ceil(people / 4.0) * bbq + "봉";
            case RICE, RAMEN -> people * nights + "개";
            case WATER -> ceil(people / 2.0) * (nights + 1) + "병";
            case DISPOSABLES -> ceil(people / 10.0) + "세트";
        };
    }

    // 술 마시는 사람이 없으면 술 줄은 뺀다
    boolean needed(int drinkers) {
        return !(this == SOJU || this == BEER) || drinkers > 0;
    }

    private static int ceil(double v) {
        return (int) Math.ceil(v - 1e-9);
    }

    private static String kg(double grams) {
        var rounded = Math.ceil(grams / 100) * 100;
        return rounded >= 1000 ? String.format("%.1fkg", rounded / 1000).replace(".0kg", "kg") : (int) rounded + "g";
    }
}
