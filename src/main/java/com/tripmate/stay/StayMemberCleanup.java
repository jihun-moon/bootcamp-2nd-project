package com.tripmate.stay;

import com.tripmate.trip.MemberLeaving;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 방을 나간 사람의 숙소 표는 지우고, 올린 숙소는 남긴다(올린 사람 칸만 비움)
@Component
public class StayMemberCleanup {

    private final VoteRepository voteRepository;
    private final StayRepository stayRepository;

    public StayMemberCleanup(VoteRepository voteRepository, StayRepository stayRepository) {
        this.voteRepository = voteRepository;
        this.stayRepository = stayRepository;
    }

    @EventListener
    public void onMemberLeaving(MemberLeaving e) {
        voteRepository.findByMemberId(e.memberId()).ifPresent(voteRepository::delete);
        stayRepository.findByAddedById(e.memberId()).forEach(Stay::forgetAddedBy);
    }
}
