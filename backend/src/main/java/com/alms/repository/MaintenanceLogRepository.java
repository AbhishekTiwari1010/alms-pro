package com.alms.repository;
import com.alms.model.MaintenanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, Long> {
    List<MaintenanceLog> findByLiftIdOrderByCreatedAtDesc(Long liftId);
    List<MaintenanceLog> findByLogStatusOrderByCreatedAtDesc(MaintenanceLog.LogStatus status);
}
