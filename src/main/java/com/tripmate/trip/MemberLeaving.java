package com.tripmate.trip;

// 사람이 방을 나가기 바로 전에 알린다. 설문·역할·숙소 표·장보기는 각 기능이 자기 표에서 그 사람 것을 지운다.
// 방 쪽이 남의 표를 직접 만지지 않게(기능은 사람마다 나뉘어 있다, docs/05)
public record MemberLeaving(Long tripId, Long memberId) {
}

