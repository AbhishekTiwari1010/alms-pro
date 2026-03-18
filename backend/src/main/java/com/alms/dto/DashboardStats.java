package com.alms.dto;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardStats {
    public long totalTripsToday;
    public long totalTripsAllTime;
    public long activeLifts;
    public long idleLifts;
    public long maintenanceLifts;
    public long queuedRequests;
    public Double avgWaitSeconds;
    public List<FloorStat> topFloors;
    public List<TripDto> recentTrips;
    public List<LiftStatusDto> liftStatuses;
}
