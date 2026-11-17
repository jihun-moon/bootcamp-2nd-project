package com.tripmate.role;

import com.tripmate.trip.Member;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

// 한 사람은 역할 하나. 카드를 옮기면 새 행을 만들지 않고 role 만 바꿔서 version 이 이어지게 한다
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "member_id"))
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Role role;

    // 화면에 "왜 이 역할인지"를 보여 주려고 매칭 때 근거를 같이 남긴다. 손으로 옮기면 비운다
    @ElementCollection
    @CollectionTable(name = "assignment_reason")
    @OrderColumn(name = "position")
    @Column(name = "reason")
    private List<String> reasons = new ArrayList<>();

    // 두 사람이 같은 카드를 동시에 옮기면 늦게 온 쪽이 OptimisticLockException 으로 막힌다
    @Version
    private Long version;

    protected Assignment() {
    }

    public Assignment(Member member, Role role, List<String> reasons) {
        this.member = member;
        this.role = role;
        this.reasons.addAll(reasons);
    }

    public void moveTo(Role role) {
        this.role = role;
        this.reasons.clear();
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public Role getRole() { return role; }
    public List<String> getReasons() { return reasons; }
    public Long getVersion() { return version; }
}
