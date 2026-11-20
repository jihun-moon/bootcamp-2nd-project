package com.tripmate.role;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    // 보드에 사람 이름과 역할 id 가 같이 필요하다. 따로 부르면 카드 수만큼 쿼리가 나간다
    @EntityGraph(attributePaths = {"member", "role"})
    List<Assignment> findByRoleTripIdOrderById(Long tripId);

    Optional<Assignment> findByMemberId(Long memberId);

    // 역할을 지우기 전에 그 칸의 배정을 먼저 지운다(외래 키)
    void deleteByRoleId(Long roleId);
}
