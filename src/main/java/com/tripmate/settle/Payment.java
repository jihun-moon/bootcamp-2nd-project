package com.tripmate.settle;

import com.tripmate.trip.Member;
import com.tripmate.trip.Trip;
import jakarta.persistence.*;

// 정산 줄 하나를 실제로 보냈다(받았다)는 표시. 보낸 사람이나 받은 사람(또는 방장)이 한 번 누르면 남는다.
// 정산은 이 돈을 "보낸 사람이 받은 사람 몫으로 낸 돈"으로 셈해서, 남은 보낼 줄에서 저절로 빠진다
@Entity
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member receiver;

    // 누가 눌렀나(화면에 "재현이 받았어요"로 보인다)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member markedBy;

    private int amount;

    // 미리 걷은 회비(낸 사람 → 총무). 셈은 보냈어요와 같고, 화면에 "회비"로 따로 보인다. 옛 행은 null = 아님
    private Boolean dues;

    protected Payment() {
    }

    static Payment dues(Trip trip, Member payer, Member collector, Member markedBy, int amount) {
        var p = new Payment(trip, payer, collector, markedBy, amount);
        p.dues = true;
        return p;
    }

    public Payment(Trip trip, Member sender, Member receiver, Member markedBy, int amount) {
        this.trip = trip;
        this.sender = sender;
        this.receiver = receiver;
        this.markedBy = markedBy;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public Member getSender() { return sender; }
    public Member getReceiver() { return receiver; }
    public Member getMarkedBy() { return markedBy; }
    public int getAmount() { return amount; }
    public boolean isDues() { return Boolean.TRUE.equals(dues); }
}
