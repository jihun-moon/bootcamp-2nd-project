package com.tripmate.service.trip;

// 방을 지우기 바로 전에 알린다. 설문·역할·숙소·장보기·정산은 각 기능이 그 방의 자기 표를 지운다(REQ-05).
// 방 쪽이 남의 표를 직접 만지지 않게(나가기의 MemberLeaving 과 같은 꼴)
public record TripDeleting(Long tripId) {
}
