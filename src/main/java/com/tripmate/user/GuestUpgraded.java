package com.tripmate.user;

// 손님이던 브라우저가 카카오로 로그인했다. 방 쪽(TripService)이 손님 참여자를 카카오 계정으로 옮긴다
public record GuestUpgraded(String guestLoginId, String userLoginId) {
}
