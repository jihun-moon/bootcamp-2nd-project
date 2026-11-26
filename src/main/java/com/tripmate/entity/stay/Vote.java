package com.tripmate.entity.stay;

import com.tripmate.entity.trip.Member;
import jakarta.persistence.*;

// Member 는 방 하나에만 속하니 member 유일이면 "한 방에 한 표"가 된다. 표를 바꾸면 stay 만 고친다
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "member_id"))
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Stay stay;

    protected Vote() {
    }

    public Vote(Member member, Stay stay) {
        this.member = member;
        this.stay = stay;
    }

    public void changeTo(Stay stay) {
        this.stay = stay;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public Stay getStay() { return stay; }
}
