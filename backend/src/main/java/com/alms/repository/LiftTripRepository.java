package com.alms.repository;
import com.alms.model.LiftTrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LiftTripRepository extends JpaRepository<LiftTrip, Long> {
    List<LiftTrip> findByUserIdOrderByRequestedAtDesc(Long userId);
    List<LiftTrip> findByBuildingIdOrderByRequestedAtDesc(Long buildingId);
    List<LiftTrip> findByEmployeeIdOrderByRequestedAtDesc(String employeeId);
    List<LiftTrip> findByLiftNumberAndTripStatus(Integer liftNumber, LiftTrip.TripStatus status);

    @Query("SELECT t FROM LiftTrip t WHERE t.building.id = :bid " +
           "AND t.requestedAt BETWEEN :start AND :end ORDER BY t.requestedAt DESC")
    List<LiftTrip> findByBuildingAndDateRange(@Param("bid") Long buildingId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    @Query("SELECT t.fromFloor AS floor, COUNT(t) AS cnt FROM LiftTrip t " +
           "WHERE t.building.id = :bid GROUP BY t.fromFloor ORDER BY cnt DESC")
    List<Object[]> findTopFloorsByBuilding(@Param("bid") Long buildingId);

    @Query("SELECT COUNT(t) FROM LiftTrip t WHERE t.building.id = :bid AND t.requestedAt >= :since")
    long countRecentTrips(@Param("bid") Long buildingId, @Param("since") LocalDateTime since);

    @Query("SELECT AVG(t.waitSeconds) FROM LiftTrip t WHERE t.building.id = :bid AND t.waitSeconds IS NOT NULL")
    Double avgWaitSeconds(@Param("bid") Long buildingId);
}
