package com.tripmate.repository.role;

import com.tripmate.entity.role.CarMeet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarMeetRepository extends JpaRepository<CarMeet, Long> {

    @EntityGraph(attributePaths = "driver")
    List<CarMeet> findByDriverTripId(Long tripId);
}
