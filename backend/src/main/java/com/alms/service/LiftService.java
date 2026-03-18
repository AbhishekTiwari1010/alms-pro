package com.alms.service;

import com.alms.algorithm.LiftAssignmentAlgorithm;
import com.alms.dto.*;
import com.alms.model.*;
import com.alms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LiftService {

    private final LiftAssignmentAlgorithm  algo;
    private final LiftRepository           liftRepo;
    private final LiftTripRepository       tripRepo;
    private final LiftQueueRepository      queueRepo;
    private final SavedRouteRepository     routeRepo;
    private final UserRepository           userRepo;
    private final MaintenanceLogRepository maintRepo;
    private final LiftSimulationService    simulation;

    // ── Request a lift ─────────────────────────────────────────
    public AssignmentResponse requestLift(String email, LiftRequestDto req) {
        User u = userRepo.findByEmail(email).orElseThrow();
        LiftAssignmentAlgorithm.AssignmentResult r = algo.assign(
                u.getId(), u.getBuilding().getId(),
                req.fromFloor, req.toFloor, req.tripType,
                LiftTrip.Priority.NORMAL,
                req.routeName);

        String msg = r.status() == LiftAssignmentAlgorithm.Status.ASSIGNED
                ? String.format("Lift L-%d assigned — waiting window open", r.liftNumber())
                : String.format("All lifts busy. Queue position: #%d", r.queuePosition());

        return AssignmentResponse.builder()
                .liftNumber(r.liftNumber()).liftName(r.liftName())
                .status(r.status().name()).tripId(r.tripId()).queueId(r.queueId())
                .blockStart(r.blockStart()).blockEnd(r.blockEnd())
                .queuePosition(r.queuePosition()).message(msg).build();
    }

    // ── Lift statuses (with simulation phase) ─────────────────
    public List<LiftStatusDto> getLiftStatuses(String email) {
        User u = userRepo.findByEmail(email).orElseThrow();
        return liftRepo.findByBuildingIdOrderByLiftNumber(u.getBuilding().getId())
                .stream().map(l -> {
                    LiftStatusDto dto = toLiftDto(l);
                    dto.phase         = simulation.getPhase(l.getId());
                    dto.waitRemaining = simulation.getWaitRemaining(l.getId());
                    return dto;
                }).collect(Collectors.toList());
    }

    // ── My trips ───────────────────────────────────────────────
    public List<TripDto> getMyTrips(String email) {
        User u = userRepo.findByEmail(email).orElseThrow();
        return tripRepo.findByUserIdOrderByRequestedAtDesc(u.getId())
                .stream().map(this::toTripDto).collect(Collectors.toList());
    }

    // ── Saved routes ───────────────────────────────────────────
    public RouteDto createRoute(String email, CreateRouteRequest req) {
        User u = userRepo.findByEmail(email).orElseThrow();
        SavedRoute r = SavedRoute.builder().user(u)
                .routeName(req.routeName).fromFloor(req.fromFloor).toFloor(req.toFloor)
                .icon(req.icon).color(req.color).build();
        return toRouteDto(routeRepo.save(r));
    }

    public List<RouteDto> getMyRoutes(String email) {
        User u = userRepo.findByEmail(email).orElseThrow();
        return routeRepo.findByUserIdOrderByUseCountDesc(u.getId())
                .stream().map(this::toRouteDto).collect(Collectors.toList());
    }

    @Transactional
    public void deleteRoute(String email, Long routeId) {
        User u = userRepo.findByEmail(email).orElseThrow();
        routeRepo.deleteByIdAndUserId(routeId, u.getId());
    }

    // ── Report maintenance ─────────────────────────────────────
    public void reportMaintenance(String email, Long liftId, MaintenanceRequest req) {
        User u    = userRepo.findByEmail(email).orElseThrow();
        Lift lift = liftRepo.findById(liftId).orElseThrow();
        maintRepo.save(MaintenanceLog.builder().lift(lift).reportedBy(u)
                .issue(req.issue).notes(req.notes).build());
    }

    // ── Admin: all trips ───────────────────────────────────────
    public List<TripDto> getAllTrips(String email) {
        User u = userRepo.findByEmail(email).orElseThrow();
        return tripRepo.findByBuildingIdOrderByRequestedAtDesc(u.getBuilding().getId())
                .stream().map(this::toTripDto).collect(Collectors.toList());
    }

    public List<TripDto> getTripsByEmployee(String email, String employeeId) {
        return tripRepo.findByEmployeeIdOrderByRequestedAtDesc(employeeId)
                .stream().map(this::toTripDto).collect(Collectors.toList());
    }

    // ── Admin: dashboard stats ─────────────────────────────────
    public DashboardStats getDashboardStats(String email) {
        User u   = userRepo.findByEmail(email).orElseThrow();
        Long bid = u.getBuilding().getId();
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

        List<LiftStatusDto> lifts = liftRepo.findByBuildingIdOrderByLiftNumber(bid)
                .stream().map(l -> {
                    LiftStatusDto dto = toLiftDto(l);
                    dto.phase         = simulation.getPhase(l.getId());
                    dto.waitRemaining = simulation.getWaitRemaining(l.getId());
                    return dto;
                }).collect(Collectors.toList());

        List<Object[]> topFloorRaw = tripRepo.findTopFloorsByBuilding(bid);
        List<FloorStat> topFloors = topFloorRaw.stream().limit(5)
                .map(r -> new FloorStat(((Number) r[0]).intValue(), ((Number) r[1]).longValue()))
                .collect(Collectors.toList());

        List<TripDto> recent = tripRepo.findByBuildingIdOrderByRequestedAtDesc(bid)
                .stream().limit(10).map(this::toTripDto).collect(Collectors.toList());

        return DashboardStats.builder()
                .totalTripsToday(tripRepo.countRecentTrips(bid, todayStart))
                .totalTripsAllTime(tripRepo.findByBuildingIdOrderByRequestedAtDesc(bid).size())
                .activeLifts(lifts.stream().filter(l -> "BUSY".equals(l.liftStatus)).count())
                .idleLifts(lifts.stream().filter(l -> "IDLE".equals(l.liftStatus)).count())
                .maintenanceLifts(lifts.stream().filter(l -> "MAINTENANCE".equals(l.liftStatus) || "OFFLINE".equals(l.liftStatus)).count())
                .queuedRequests(queueRepo.countByBuildingIdAndQueueStatus(bid, LiftQueue.QueueStatus.WAITING))
                .avgWaitSeconds(tripRepo.avgWaitSeconds(bid))
                .topFloors(topFloors).recentTrips(recent).liftStatuses(lifts).build();
    }

    // ── Admin: maintenance toggle ──────────────────────────────
    @Transactional
    public void setLiftMaintenance(String email, Long buildingId, int liftNumber, boolean maintenance) {
        algo.setMaintenance(buildingId, liftNumber, maintenance);
    }

    // ── Mappers ────────────────────────────────────────────────
    public LiftStatusDto toLiftDto(Lift l) {
        LiftStatusDto dto = new LiftStatusDto();
        dto.id = l.getId(); dto.liftNumber = l.getLiftNumber(); dto.liftName = l.getLiftName();
        dto.currentFloor = l.getCurrentFloor(); dto.liftStatus = l.getLiftStatus().name();
        dto.capacity = l.getCapacity(); dto.currentLoad = l.getCurrentLoad();
        dto.assignedBlockStart = l.getAssignedBlockStart(); dto.assignedBlockEnd = l.getAssignedBlockEnd();
        dto.activeTripCount = l.getActiveTripCount(); dto.totalTrips = l.getTotalTrips();
        return dto;
    }

    public TripDto toTripDto(LiftTrip t) {
        return TripDto.builder()
                .id(t.getId()).employeeId(t.getEmployeeId())
                .fullName(t.getUser() != null ? t.getUser().getFullName() : "")
                .liftNumber(t.getLiftNumber()).fromFloor(t.getFromFloor()).toFloor(t.getToFloor())
                .tripType(t.getTripType().name()).tripStatus(t.getTripStatus().name())
                .routeName(t.getRouteName())
                .waitSeconds(t.getWaitSeconds()).travelSeconds(t.getTravelSeconds())
                .requestedAt(t.getRequestedAt()).completedAt(t.getCompletedAt()).build();
    }

    private RouteDto toRouteDto(SavedRoute r) {
        return RouteDto.builder().id(r.getId()).routeName(r.getRouteName())
                .fromFloor(r.getFromFloor()).toFloor(r.getToFloor())
                .icon(r.getIcon()).color(r.getColor()).useCount(r.getUseCount()).build();
    }
}
