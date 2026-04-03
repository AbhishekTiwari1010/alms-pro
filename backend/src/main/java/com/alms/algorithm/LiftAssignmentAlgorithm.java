package com.alms.algorithm;

import com.alms.model.*;
import com.alms.repository.*;
import com.alms.service.LiftSimulationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class LiftAssignmentAlgorithm {

    private final LiftRepository        liftRepo;
    private final LiftTripRepository    tripRepo;
    private final LiftQueueRepository   queueRepo;
    private final UserRepository        userRepo;
    private final BuildingRepository    buildingRepo;
    private final LiftSimulationService simulation;

    @Value("${alms.block-size:10}")
    private int blockSize;

    @Autowired
    public LiftAssignmentAlgorithm(LiftRepository liftRepo, LiftTripRepository tripRepo,
                                   LiftQueueRepository queueRepo, UserRepository userRepo,
                                   BuildingRepository buildingRepo,
                                   @Lazy LiftSimulationService simulation) {
        this.liftRepo     = liftRepo;
        this.tripRepo     = tripRepo;
        this.queueRepo    = queueRepo;
        this.userRepo     = userRepo;
        this.buildingRepo = buildingRepo;
        this.simulation   = simulation;
    }

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

        log.info("[ASSIGN] emp={} {}F->{}F block={}-{}", user.getEmployeeId(), fromFloor, toFloor, bs, be);

        // ── STEP 1: SHARE a WAITING lift whose assigned block matches toFloor ──
        // We query by fromFloor for proximity, but the real match is:
        // toFloor must land inside the lift's assignedBlock.
        // fromFloor can be any floor — no restriction on pickup origin.
        List<Lift> busyOnBlock = liftRepo.findBusyLiftsForBlock(buildingId, fromFloor);
        if (!busyOnBlock.isEmpty()) {
            Lift lift = busyOnBlock.get(0);
            boolean toInLiftBlock = lift.getAssignedBlockStart() != null
                    && toFloor >= lift.getAssignedBlockStart()
                    && toFloor <= lift.getAssignedBlockEnd();
            String phase = simulation.getPhase(lift.getId());
            if (toInLiftBlock && "WAITING".equals(phase) && lift.hasCapacity()) {
                lift.setActiveTripCount(lift.getActiveTripCount() + 1);
                lift.setCurrentLoad(lift.getCurrentLoad() + 1);
                liftRepo.save(lift);
                LiftTrip trip = recordTrip(user, building, lift.getLiftNumber(),
                        fromFloor, toFloor, tripType, priority, routeName,
                        LiftTrip.TripStatus.ASSIGNED, now);
                log.info("[SHARE-WAITING] Lift {} block={}-{} trips={}",
                        lift.getLiftNumber(), bs, be, lift.getActiveTripCount());
                return new AssignmentResult(lift.getLiftNumber(), lift.getLiftName(),
                        Status.ASSIGNED, trip.getId(), null, bs, be, 0);
            }
        }

        // ── STEP 2: LOOK — TRAVELING lift, destination in SAME BLOCK ───────
        // A lift qualifies for LOOK-pickup ONLY when ALL of these are true:
        //   a) toFloor is in the lift's assigned block (bs-be)  ← KEY GUARD
        //      fromFloor can be ANY floor — we don't restrict pickup origin
        //   b) fromFloor is ahead of the lift in its current direction
        //      (lift going UP: fromFloor >= currentFloor)
        //      (lift going DN: fromFloor <= currentFloor)
        // Example: lift assigned block 4 (31-40) traveling UP from floor 1
        //   request floor 21 → floor 38: toFloor=38 in block 4 ✅ pickup
        //   request floor 5  → floor 36: toFloor=36 in block 4 ✅ pickup
        //   request floor 25 → floor 45: toFloor=45 in block 5 ❌ skip
        List<Lift> busyLifts = liftRepo.findByBuildingIdAndLiftStatus(buildingId, Lift.LiftStatus.BUSY);
        for (Lift lift : busyLifts) {
            // ── DESTINATION BLOCK GUARD — toFloor must land in lift's assigned block ──
            if (lift.getAssignedBlockStart() == null) continue;
            int liftBs = lift.getAssignedBlockStart();
            int liftBe = lift.getAssignedBlockEnd();
            boolean toInLiftBlock = toFloor >= liftBs && toFloor <= liftBe;
            if (!toInLiftBlock) continue;

            LiftSimulationService.Direction dir = simulation.getDirection(lift.getId());
            if (!lift.hasCapacity()) continue;
            if (dir == LiftSimulationService.Direction.NONE) continue;

            // fromFloor must be ahead of the lift in its travel direction
            boolean inPathUp   = dir == LiftSimulationService.Direction.UP
                    && fromFloor >= lift.getCurrentFloor();
            boolean inPathDown = dir == LiftSimulationService.Direction.DOWN
                    && fromFloor <= lift.getCurrentFloor();

            if (inPathUp || inPathDown) {
                lift.setActiveTripCount(lift.getActiveTripCount() + 1);
                lift.setCurrentLoad(lift.getCurrentLoad() + 1);
                liftRepo.save(lift);
                LiftTrip trip = recordTrip(user, building, lift.getLiftNumber(),
                        fromFloor, toFloor, tripType, priority, routeName,
                        LiftTrip.TripStatus.ASSIGNED, now);
                log.info("[LOOK-ASSIGN] Lift {} block={}-{} traveling {} pickup {}F->{}F",
                        lift.getLiftNumber(), liftBs, liftBe, dir, fromFloor, toFloor);
                return new AssignmentResult(lift.getLiftNumber(), lift.getLiftName(),
                        Status.ASSIGNED, trip.getId(), null, liftBs, liftBe, 0);
            }
        }

        // ── STEP 3: Dispatch nearest IDLE lift ────────────────────────────
        List<Lift> idle = liftRepo.findIdleLiftsNearestTo(buildingId, fromFloor);
        if (!idle.isEmpty()) {
            return dispatchLift(idle.get(0), user, building,
                    fromFloor, toFloor, tripType, priority, routeName, bs, be, now);
        }

        // ── STEP 4: Queue — all lifts busy ────────────────────────────────
        log.warn("[QUEUE] All lifts busy. emp={}", user.getEmployeeId());
        LiftQueue q = LiftQueue.builder()
                .user(user).building(building).employeeId(user.getEmployeeId())
                .fromFloor(fromFloor).toFloor(toFloor).tripType(tripType)
                .priority(priority).routeName(routeName)
                .queueStatus(LiftQueue.QueueStatus.WAITING)
                .expiresAt(now.plusMinutes(10))
                .build();
        LiftQueue saved = queueRepo.save(q);
        long pos = queueRepo.countByBuildingIdAndQueueStatus(buildingId, LiftQueue.QueueStatus.WAITING);
        LiftTrip trip = recordTrip(user, building, -1, fromFloor, toFloor,
                tripType, priority, routeName, LiftTrip.TripStatus.QUEUED, now);
        return new AssignmentResult(-1, null, Status.QUEUED,
                trip.getId(), saved.getId(), bs, be, (int) pos);
    }

    @Transactional
    public void setMaintenance(Long buildingId, int liftNumber, boolean inMaintenance) {
        liftRepo.findByBuildingIdAndLiftNumber(buildingId, liftNumber).ifPresent(lift -> {
            lift.setLiftStatus(inMaintenance ? Lift.LiftStatus.MAINTENANCE : Lift.LiftStatus.IDLE);
            if (inMaintenance) lift.setLastMaintenance(LocalDateTime.now());
            lift.setLastUpdated(LocalDateTime.now());
            liftRepo.save(lift);
        });
    }

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
        LiftTrip trip = recordTrip(user, building, lift.getLiftNumber(),
                fromFloor, toFloor, tripType, priority, routeName,
                LiftTrip.TripStatus.ASSIGNED, now);
        log.info("[DISPATCH] Lift {} dist={} block={}-{}",
                lift.getLiftNumber(), lift.distanceTo(fromFloor), bs, be);
        return new AssignmentResult(lift.getLiftNumber(), lift.getLiftName(),
                Status.ASSIGNED, trip.getId(), null, bs, be, 0);
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
        int idx = (floor - 1) / blockSize;
        int start = idx * blockSize + 1;
        return new int[]{ start, start + blockSize - 1 };
    }

    public record AssignmentResult(int liftNumber, String liftName, Status status,
                                   Long tripId, Long queueId, int blockStart, int blockEnd, int queuePosition) {}
    public enum Status { ASSIGNED, QUEUED }
}
