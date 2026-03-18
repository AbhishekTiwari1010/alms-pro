package com.alms.repository;
import com.alms.model.SavedRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface SavedRouteRepository extends JpaRepository<SavedRoute, Long> {
    List<SavedRoute> findByUserIdOrderByUseCountDesc(Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}
