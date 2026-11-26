package com.tripmate.repository.role;

import com.tripmate.entity.role.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    // 보드가 역할마다 할 일을 읽는다. 따로 부르면 역할 수만큼 쿼리가 나간다
    @EntityGraph(attributePaths = "tasks")
    List<Role> findByTripIdOrderById(Long tripId);

    Optional<Role> findByIdAndTripId(Long id, Long tripId);

    long countByTripId(Long tripId);
}
