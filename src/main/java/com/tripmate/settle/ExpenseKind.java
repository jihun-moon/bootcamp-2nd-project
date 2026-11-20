package com.tripmate.settle;

// 기름값·톨비(운전자도 내나)와 술(마신 사람만 내나)은 방 규칙에 따라 나누는 사람이 달라진다. 나머지는 모두가 똑같이
public enum ExpenseKind {
    STAY("숙소"),
    FUEL("기름값·톨비"),
    FOOD("식비"),
    DRINK("술"),
    OTHER("기타");

    private final String label;

    ExpenseKind(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
