package com.tripmate.repository.role;

import com.tripmate.entity.role.Ride;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {

    @EntityGraph(attributePaths = {"member", "driver"})
    List<Ride> findByMemberTripId(Long tripId);
}
