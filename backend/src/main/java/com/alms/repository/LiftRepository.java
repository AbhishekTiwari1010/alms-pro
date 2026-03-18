package com.alms.repository;

import com.alms.model.Lift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiftRepository extends JpaRepository<Lift, Long> {

    List<Lift> findByBuildingIdOrderByLiftNumber(Long buildingId);
    Optional<Lift> findByBuildingIdAndLiftNumber(Long buildingId, Integer liftNumber);
    List<Lift> findByBuildingIdAndLiftStatus(Long buildingId, Lift.LiftStatus status);

    // Dynamic: all BUSY lifts already dispatched to this block, least loaded first
    @Query("SELECT l FROM Lift l WHERE l.building.id = :bid AND l.liftStatus = 'BUSY' " +
           "AND l.assignedBlockStart <= :floor AND l.assignedBlockEnd >= :floor " +
           "AND l.currentLoad < l.capacity ORDER BY l.activeTripCount ASC")
    List<Lift> findBusyLiftsForBlock(@Param("bid") Long buildingId, @Param("floor") int floor);

    // Dynamic: all IDLE lifts sorted by distance to requested floor
    @Query("SELECT l FROM Lift l WHERE l.building.id = :bid AND l.liftStatus = 'IDLE' " +
           "ORDER BY ABS(l.currentFloor - :floor) ASC")
    List<Lift> findIdleLiftsNearestTo(@Param("bid") Long buildingId, @Param("floor") int floor);

    // Emergency: IDLE lifts of type EXPRESS or VIP first
    @Query("SELECT l FROM Lift l WHERE l.building.id = :bid AND l.liftStatus = 'IDLE' " +
           "ORDER BY CASE l.liftType WHEN 'VIP' THEN 0 WHEN 'EXPRESS' THEN 1 ELSE 2 END, " +
           "ABS(l.currentFloor - :floor) ASC")
    List<Lift> findIdlePriorityLiftsNearestTo(@Param("bid") Long buildingId, @Param("floor") int floor);

    @Modifying
    @Query("UPDATE Lift l SET l.currentFloor = :floor, l.lastUpdated = CURRENT_TIMESTAMP " +
           "WHERE l.building.id = :bid AND l.liftNumber = :num")
    void updateCurrentFloor(@Param("bid") Long buildingId, @Param("num") int liftNumber, @Param("floor") int floor);

    @Modifying
    @Query("UPDATE Lift l SET l.activeTripCount = GREATEST(0, l.activeTripCount - 1), " +
           "l.currentLoad = GREATEST(0, l.currentLoad - 1), l.lastUpdated = CURRENT_TIMESTAMP " +
           "WHERE l.building.id = :bid AND l.liftNumber = :num")
    void decrementTripCount(@Param("bid") Long buildingId, @Param("num") int liftNumber);

    @Modifying
    @Query("UPDATE Lift l SET l.totalTrips = l.totalTrips + 1 WHERE l.building.id = :bid AND l.liftNumber = :num")
    void incrementTotalTrips(@Param("bid") Long buildingId, @Param("num") int liftNumber);
}
