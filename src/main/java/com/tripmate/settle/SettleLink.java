package com.tripmate.settle;

import com.tripmate.trip.Trip;
import jakarta.persistence.*;

import java.security.SecureRandom;
import java.util.Base64;

// 정산 받는 링크(/s/{code}). 로그인 없이 "누가 누구에게 얼마"만 보는 주소라 코드가 곧 열쇠다.
// 초대 코드(12자)와 따로 둔다. 정산 링크로 방에 들어오면 안 되고, 방 링크로 정산만 보는 일도 없다.
// 128비트 난수라 하나씩 넣어 찾을 수 없다. 방마다 하나, 처음 정산을 보낼 때 만든다
@Entity
public class SettleLink {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true)
    private Trip trip;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    protected SettleLink() {
    }

    public SettleLink(Trip trip) {
        this.trip = trip;
        var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        this.code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Trip getTrip() { return trip; }
    public String getCode() { return code; }
}
