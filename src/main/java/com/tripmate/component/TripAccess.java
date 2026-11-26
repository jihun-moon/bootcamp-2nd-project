package com.tripmate.component;

import com.tripmate.entity.trip.Member;
import com.tripmate.repository.trip.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

// 방 API 는 모두 여기를 먼저 지난다. 그 방 참여자가 아니면 방이 있는지도 알려 주지 않게 404
@Component
public class TripAccess {

    private final CurrentUser currentUser;
    private final MemberRepository memberRepository;

    public TripAccess(CurrentUser currentUser, MemberRepository memberRepository) {
        this.currentUser = currentUser;
        this.memberRepository = memberRepository;
    }

    // 요청한 사람의 이 방 참여자
    public Member me(Long tripId) {
        return memberRepository.findByTripIdAndUserId(tripId, currentUser.get().getId())
                .filter(m -> !m.isDeparted())   // 나간 사람은 방이 있는지도 모르게
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 주소의 memberId 가 본인일 때만. 남의 설문·표를 대신 고치지 못하게
    public Member self(Long tripId, Long memberId) {
        var me = me(tripId);
        if (!me.getId().equals(memberId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return me;
    }

    // 본인 것이거나 방장(나가기·빼기, 술 표시처럼 "내 것은 내가, 못 하는 사람은 방장이")
    public Member selfOrHost(Long tripId, Long memberId) {
        var me = me(tripId);
        if (!me.getId().equals(memberId) && !me.isHost()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return me;
    }

    public Member host(Long tripId) {
        var me = me(tripId);
        if (!me.isHost()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return me;
    }
}
