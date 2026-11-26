package com.tripmate.service.trip;

// 방 안의 무언가가 바뀌었다는 알림. what 은 "roles", "members" 처럼 화면이 무엇을 다시 읽을지 고르는 데 쓴다
public record TripChanged(Long tripId, String what) {
}
