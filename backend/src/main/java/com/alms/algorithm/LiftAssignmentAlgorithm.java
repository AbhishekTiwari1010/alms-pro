package com.alms.algorithm;

import com.alms.model.*;
import com.alms.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ALMS PRO — ADVANCED LIFT ASSIGNMENT ALGORITHM v2.0
 *
 * FULLY DYNAMIC — no lift has a permanent floor zone.
 * Block size = 10 (configurable via alms.block-size).
 *
 * ASSIGNMENT RULES (in order):
 *   EMERGENCY: nearest idle VIP/EXPRESS → any idle → queue
 *   HIGH/NORMAL:
 *     1. SHARE  — BUSY lift on this block with capacity → least-loaded
 *     2. DISPATCH — nearest IDLE lift
 *     3. QUEUE  — all busy → lift_queue (auto-drained on completion)
 */
@Component
@Slf4j
public class LiftAssignmentAlgorithm {

    private final LiftRepository      liftRepo;
    private final LiftTripRepository  tripRepo;
    private final LiftQueueRepository queueRepo;
    private final UserRepository      userRepo;
    private final BuildingRepository  buildingRepo;

    @Value("${alms.block-size:10}")
    private int blockSize;

    @Autowired
    public LiftAssignmentAlgorithm(LiftRepository liftRepo,
                                   LiftTripRepository tripRepo,
                                   LiftQueueRepository queueRepo,
                                   UserRepository userRepo,
                                   BuildingRepository buildingRepo) {
        this.liftRepo    = liftRepo;
        this.tripRepo    = tripRepo;
        this.queueRepo   = queueRepo;
        this.userRepo    = userRepo;
        this.buildingRepo = buildingRepo;
    }

    // ── Assign ────────────────────────────────────────────────
    @Transactional
    public AssignmentResult assign(Long userId, Long buildingId,
                                   int fromFloor, int toFloor,
                                   LiftTrip.TripType tripType,
                                   LiftTrip.Priority priority,
                                   String routeName) {

        int[] block = blockFor(fromFloor);
        int bs = block[0], be = block[1];
        LocalDateTime now = LocalDateTime.now();

        User     user     = userRepo.findById(userId).orElseThrow();
        Building building = buildingRepo.findById(buildingId).orElseThrow();

        log.info("[ASSIGN] emp={} {}F->{}F block={}-{} type={} pri={}",
                user.getEmployeeId(), fromFloor, toFloor, bs, be, tripType, priority);

        // EMERGENCY — skip sharing, grab best lift immediately
        if (priority == LiftTrip.Priority.EMERGENCY) {
            List<Lift> best = liftRepo.findIdlePriorityLiftsNearestTo(buildingId, fromFloor);
            if (!best.isEmpty()) {
                return dispatchLift(best.get(0), user, building, fromFloor, toFloor, tripType, priority, routeName, bs, be, now);
            }
        }

        // STEP 1: SHARE
        List<Lift> busyOnBlock = liftRepo.findBusyLiftsForBlock(buildingId, fromFloor);
        if (!busyOnBlock.isEmpty()) {
            Lift lift = busyOnBlock.get(0);
            lift.setActiveTripCount(lift.getActiveTripCount() + 1);
            lift.setCurrentLoad(lift.getCurrentLoad() + 1);
            lift.setLastUpdated(now);
            liftRepo.save(lift);
            LiftTrip trip = recordTrip(user, building, lift.getLiftNumber(), fromFloor, toFloor, tripType, priority, routeName, LiftTrip.TripStatus.ASSIGNED, now);
            log.info("[SHARE] Lift {} block={}-{} trips={}", lift.getLiftNumber(), bs, be, lift.getActiveTripCount());
            return new AssignmentResult(lift.getLiftNumber(), lift.getLiftName(), Status.ASSIGNED, trip.getId(), null, bs, be, 0);
        }

        // STEP 2: DISPATCH
        List<Lift> idle = liftRepo.findIdleLiftsNearestTo(buildingId, fromFloor);
        if (!idle.isEmpty()) {
            return dispatchLift(idle.get(0), user, building, fromFloor, toFloor, tripType, priority, routeName, bs, be, now);
        }

        // STEP 3: QUEUE
        log.warn("[QUEUE] All lifts busy. emp={} floor={}F", user.getEmployeeId(), fromFloor);
        LiftQueue q = LiftQueue.builder()
                .user(user).building(building).employeeId(user.getEmployeeId())
                .fromFloor(fromFloor).toFloor(toFloor).tripType(tripType)
                .priority(priority).routeName(routeName)
                .queueStatus(LiftQueue.QueueStatus.WAITING)
                .expiresAt(now.plusMinutes(10))
                .build();
        LiftQueue saved = queueRepo.save(q);

        long pos = queueRepo.countByBuildingIdAndQueueStatus(buildingId, LiftQueue.QueueStatus.WAITING);
        LiftTrip trip = recordTrip(user, building, -1, fromFloor, toFloor, tripType, priority, routeName, LiftTrip.TripStatus.QUEUED, now);
        return new AssignmentResult(-1, null, Status.QUEUED, trip.getId(), saved.getId(), bs, be, (int) pos);
    }

    // ── Complete Trip ──────────────────────────────────────────
    @Transactional
    public void completeTrip(Long tripId, Long buildingId, int liftNumber) {
        LocalDateTime now = LocalDateTime.now();

        tripRepo.findById(tripId).ifPresent(trip -> {
            trip.setTripStatus(LiftTrip.TripStatus.COMPLETED);
            trip.setCompletedAt(now);
            if (trip.getAssignedAt() != null) {
                trip.setTravelSeconds((int) java.time.Duration.between(trip.getAssignedAt(), now).getSeconds());
            }
            tripRepo.save(trip);
        });

        liftRepo.decrementTripCount(buildingId, liftNumber);
        liftRepo.incrementTotalTrips(buildingId, liftNumber);

        liftRepo.findByBuildingIdAndLiftNumber(buildingId, liftNumber).ifPresent(lift -> {
            tripRepo.findById(tripId).ifPresent(t -> {
                lift.setCurrentFloor(t.getToFloor());
                liftRepo.updateCurrentFloor(buildingId, liftNumber, t.getToFloor());
            });

            if (lift.getActiveTripCount() <= 0) {
                lift.setLiftStatus(Lift.LiftStatus.IDLE);
                lift.setAssignedBlockStart(null);
                lift.setAssignedBlockEnd(null);
                lift.setActiveTripCount(0);
                lift.setCurrentLoad(0);
                lift.setLastUpdated(now);
                liftRepo.save(lift);
                log.info("[FREE] Lift {} now IDLE at {}F", liftNumber, lift.getCurrentFloor());
            }
        });

        processQueue(buildingId);
    }

    // ── Maintenance toggle ────────────────────────────────────
    @Transactional
    public void setMaintenance(Long buildingId, int liftNumber, boolean inMaintenance) {
        liftRepo.findByBuildingIdAndLiftNumber(buildingId, liftNumber).ifPresent(lift -> {
            lift.setLiftStatus(inMaintenance ? Lift.LiftStatus.MAINTENANCE : Lift.LiftStatus.IDLE);
            if (inMaintenance) lift.setLastMaintenance(LocalDateTime.now());
            lift.setLastUpdated(LocalDateTime.now());
            liftRepo.save(lift);
        });
    }

    // ── Process Queue ──────────────────────────────────────────
    @Transactional
    public void processQueue(Long buildingId) {
        List<LiftQueue> waiting = queueRepo.findByBuildingIdAndQueueStatusOrderByQueuedAtAsc(buildingId, LiftQueue.QueueStatus.WAITING);
        for (LiftQueue req : waiting) {
            if (req.getExpiresAt() != null && LocalDateTime.now().isAfter(req.getExpiresAt())) {
                req.setQueueStatus(LiftQueue.QueueStatus.EXPIRED);
                queueRepo.save(req);
                continue;
            }
            int[] block = blockFor(req.getFromFloor());

            List<Lift> busy = liftRepo.findBusyLiftsForBlock(buildingId, req.getFromFloor());
            if (!busy.isEmpty()) {
                Lift lift = busy.get(0);
                lift.setActiveTripCount(lift.getActiveTripCount() + 1);
                lift.setCurrentLoad(lift.getCurrentLoad() + 1);
                lift.setLastUpdated(LocalDateTime.now());
                liftRepo.save(lift);
                req.setQueueStatus(LiftQueue.QueueStatus.PROCESSING);
                queueRepo.save(req);
                recordTrip(req.getUser(), req.getBuilding(), lift.getLiftNumber(),
                        req.getFromFloor(), req.getToFloor(), req.getTripType(),
                        req.getPriority(), req.getRouteName(), LiftTrip.TripStatus.ASSIGNED, LocalDateTime.now());
                log.info("[QUEUE->SHARE] q#{} -> Lift {}", req.getId(), lift.getLiftNumber());
                continue;
            }

            List<Lift> idle = liftRepo.findIdleLiftsNearestTo(buildingId, req.getFromFloor());
            if (idle.isEmpty()) break;

            Lift lift = idle.get(0);
            lift.setLiftStatus(Lift.LiftStatus.BUSY);
            lift.setAssignedBlockStart(block[0]);
            lift.setAssignedBlockEnd(block[1]);
            lift.setActiveTripCount(1);
            lift.setCurrentLoad(1);
            lift.setLastUpdated(LocalDateTime.now());
            liftRepo.save(lift);
            req.setQueueStatus(LiftQueue.QueueStatus.PROCESSING);
            queueRepo.save(req);
            recordTrip(req.getUser(), req.getBuilding(), lift.getLiftNumber(),
                    req.getFromFloor(), req.getToFloor(), req.getTripType(),
                    req.getPriority(), req.getRouteName(), LiftTrip.TripStatus.ASSIGNED, LocalDateTime.now());
            log.info("[QUEUE->DISPATCH] q#{} -> Lift {} block={}-{}", req.getId(), lift.getLiftNumber(), block[0], block[1]);
        }
    }

    // ── Private helpers ────────────────────────────────────────
    private AssignmentResult dispatchLift(Lift lift, User user, Building building,
                                          int fromFloor, int toFloor,
                                          LiftTrip.TripType tripType, LiftTrip.Priority priority,
                                          String routeName, int bs, int be, LocalDateTime now) {
        lift.setLiftStatus(Lift.LiftStatus.BUSY);
        lift.setAssignedBlockStart(bs);
        lift.setAssignedBlockEnd(be);
        lift.setActiveTripCount(1);
        lift.setCurrentLoad(1);
        lift.setLastUpdated(now);
        liftRepo.save(lift);
        log.info("[DISPATCH] Lift {} ({}F->{}F) dist={} block={}-{}",
                lift.getLiftNumber(), lift.getCurrentFloor(), fromFloor, lift.distanceTo(fromFloor), bs, be);
        LiftTrip trip = recordTrip(user, building, lift.getLiftNumber(), fromFloor, toFloor, tripType, priority, routeName, LiftTrip.TripStatus.ASSIGNED, now);
        return new AssignmentResult(lift.getLiftNumber(), lift.getLiftName(), Status.ASSIGNED, trip.getId(), null, bs, be, 0);
    }

    private LiftTrip recordTrip(User user, Building building, int liftNumber,
                                 int from, int to, LiftTrip.TripType type,
                                 LiftTrip.Priority priority, String routeName,
                                 LiftTrip.TripStatus status, LocalDateTime now) {
        return tripRepo.save(LiftTrip.builder()
                .user(user).building(building).employeeId(user.getEmployeeId())
                .liftNumber(liftNumber).fromFloor(from).toFloor(to)
                .tripType(type).priority(priority).routeName(routeName)
                .tripStatus(status).requestedAt(now)
                .assignedAt(status == LiftTrip.TripStatus.ASSIGNED ? now : null)
                .build());
    }

    private int[] blockFor(int floor) {
        int idx   = (floor - 1) / blockSize;
        int start = idx * blockSize + 1;
        int end   = start + blockSize - 1;
        return new int[]{ start, end };
    }

    // Result record
    public record AssignmentResult(
            int liftNumber, String liftName, Status status,
            Long tripId, Long queueId,
            int blockStart, int blockEnd, int queuePosition
    ) {}

    public enum Status { ASSIGNED, QUEUED }
}
