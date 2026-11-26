package com.tripmate.service.trip;

// 나감 표시가 끝난 뒤 알린다. 남은 사람으로 다시 셀 일(예: 마지막 미완료자가 나가 설문이 다 모였으면 역할 추천)이 듣는다
public record MemberLeft(Long tripId) {
}
