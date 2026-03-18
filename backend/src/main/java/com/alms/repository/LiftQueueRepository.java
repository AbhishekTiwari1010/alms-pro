package com.alms.repository;
import com.alms.model.LiftQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface LiftQueueRepository extends JpaRepository<LiftQueue, Long> {
    List<LiftQueue> findByBuildingIdAndQueueStatusOrderByQueuedAtAsc(Long buildingId, LiftQueue.QueueStatus status);
    List<LiftQueue> findByUserId(Long userId);
    long countByBuildingIdAndQueueStatus(Long buildingId, LiftQueue.QueueStatus status);
}
