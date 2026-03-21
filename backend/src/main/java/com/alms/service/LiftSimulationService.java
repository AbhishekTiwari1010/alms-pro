package com.alms.service;

import com.alms.model.*;
import com.alms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiftSimulationService {

    private final LiftRepository      liftRepo;
    private final LiftTripRepository  tripRepo;
    private final LiftQueueRepository queueRepo;
    private final BuildingRepository  buildingRepo;

    private final Map<Long, LiftRuntime> runtimes = new ConcurrentHashMap<>();

    private static final long DEFAULT_BUILDING_ID = 1L;
    private static final int  WAIT_SECONDS        = 15;
    private static final int  SECONDS_PER_FLOOR   = 3;

    // ── Tick every 500ms ──────────────────────────────────────
    @Scheduled(fixedDelay = 500)
    @Transactional
    public void tick() {
        List<Lift> lifts = liftRepo.findByBuildingIdOrderByLiftNumber(DEFAULT_BUILDING_ID);
        for (Lift lift : lifts) {
            // BUG FIX 1: Skip lifts under MAINTENANCE/OFFLINE — don't simulate them
            if (lift.getLiftStatus() == Lift.LiftStatus.MAINTENANCE ||
                lift.getLiftStatus() == Lift.LiftStatus.OFFLINE) {
                // Clear runtime if it exists so it doesn't hold stale state
                runtimes.remove(lift.getId());
                continue;
            }

            LiftRuntime rt = runtimes.computeIfAbsent(lift.getId(), id -> {
                LiftRuntime r = new LiftRuntime();
                // BUG FIX 2: Initialize from actual DB floor, not hardcoded 1
                r.previousFloor = lift.getCurrentFloor();
                r.targetFloor   = lift.getCurrentFloor();
                return r;
            });
            tickLift(lift, rt);
        }
    }

    private void tickLift(Lift lift, LiftRuntime rt) {
        switch (rt.phase) {

            case IDLE -> {
                // BUG FIX 3: Only pick ASSIGNED trips — not IN_PROGRESS or COMPLETED ones
                // that may linger from previous trips
                List<LiftTrip> pending = tripRepo.findByLiftNumberAndTripStatus(
                        lift.getLiftNumber(), LiftTrip.TripStatus.ASSIGNED);

                if (!pending.isEmpty()) {
                    rt.phase = Phase.WAITING;
                    rt.waitDeadline = System.currentTimeMillis() + (WAIT_SECONDS * 1000L);
                    rt.pendingTrips.clear();
                    rt.pendingTrips.addAll(pending);
                    rt.previousFloor = lift.getCurrentFloor();
                    lift.setLiftStatus(Lift.LiftStatus.BUSY);
                    liftRepo.save(lift);
                    log.info("[LIFT-{}] IDLE→WAITING at {}F, trips={}",
                            lift.getLiftNumber(), lift.getCurrentFloor(), pending.size());
                }
            }

            case WAITING -> {
                // BUG FIX 4: During WAITING, also check if lift was set to MAINTENANCE
                // by admin — if so, abort waiting and go back to IDLE
                if (lift.getLiftStatus() == Lift.LiftStatus.MAINTENANCE) {
                    rt.phase = Phase.IDLE;
                    rt.pendingTrips.clear();
                    rt.stops.clear();
                    return;
                }

                // Collect new ASSIGNED trips for this lift during the window
                List<LiftTrip> newTrips = tripRepo.findByLiftNumberAndTripStatus(
                        lift.getLiftNumber(), LiftTrip.TripStatus.ASSIGNED);

                for (LiftTrip t : newTrips) {
                    boolean exists = rt.pendingTrips.stream().anyMatch(p -> p.getId().equals(t.getId()));
                    if (!exists) {
                        rt.pendingTrips.add(t);
                        // Extend window on each new request
                        rt.waitDeadline = Math.max(rt.waitDeadline,
                                System.currentTimeMillis() + (WAIT_SECONDS * 1000L));
                        log.info("[LIFT-{}] New request in window, extended. trips={}",
                                lift.getLiftNumber(), rt.pendingTrips.size());
                    }
                }

                boolean full    = rt.pendingTrips.size() >= lift.getCapacity();
                boolean expired = System.currentTimeMillis() >= rt.waitDeadline;

                if (full || expired) {
                    log.info("[LIFT-{}] WAITING→SERVING trips={} reason={}",
                            lift.getLiftNumber(), rt.pendingTrips.size(), full ? "FULL" : "TIMEOUT");
                    startServing(lift, rt);
                }
            }

            case TRAVELING -> {
                // BUG FIX 5: If distance is 0 (lift already at target), don't divide by zero
                long now = System.currentTimeMillis();
                if (now >= rt.arrivalTime || rt.arrivalTime == rt.departTime) {
                    // Arrived at stop
                    lift.setCurrentFloor(rt.targetFloor);
                    liftRepo.updateCurrentFloor(DEFAULT_BUILDING_ID, lift.getLiftNumber(), rt.targetFloor);
                    rt.previousFloor = rt.targetFloor;

                    // Calculate halt time — minimum 3s even if no requests exactly here
                    int requestsHere = (int) rt.pendingTrips.stream()
                            .filter(t -> t.getFromFloor() == rt.targetFloor
                                      || t.getToFloor()   == rt.targetFloor)
                            .count();
                    int haltSeconds = (2 * requestsHere) + 3;

                    rt.phase = Phase.HALTING;
                    rt.haltDeadline = now + (haltSeconds * 1000L);
                    log.info("[LIFT-{}] Arrived {}F, halting {}s for {} pax",
                            lift.getLiftNumber(), rt.targetFloor, haltSeconds, requestsHere);
                } else {
                    // Smooth interpolation for live UI
                    double elapsed   = now - rt.departTime;
                    double total     = rt.arrivalTime - rt.departTime;
                    double progress  = Math.min(1.0, elapsed / total);
                    int interpolated = (int) Math.round(
                            rt.previousFloor + progress * (rt.targetFloor - rt.previousFloor));
                    if (interpolated != lift.getCurrentFloor()) {
                        lift.setCurrentFloor(interpolated);
                        liftRepo.updateCurrentFloor(DEFAULT_BUILDING_ID, lift.getLiftNumber(), interpolated);
                    }
                }
            }

            case HALTING -> {
                if (System.currentTimeMillis() >= rt.haltDeadline) {
                    // BUG FIX 6: Complete trips before boarding to avoid double-counting
                    completeTripsAtFloor(lift, rt, rt.targetFloor);
                    boardTripsAtFloor(lift, rt, rt.targetFloor);
                    rt.stops.remove(Integer.valueOf(rt.targetFloor));

                    if (rt.stops.isEmpty()) {
                        // BUG FIX 7: Stay at last floor — don't reset to floor 1
                        int lastFloor = rt.targetFloor;
                        log.info("[LIFT-{}] All stops done, IDLE at {}F",
                                lift.getLiftNumber(), lastFloor);

                        lift.setLiftStatus(Lift.LiftStatus.IDLE);
                        lift.setCurrentFloor(lastFloor);
                        lift.setActiveTripCount(0);
                        lift.setCurrentLoad(0);
                        lift.setAssignedBlockStart(null);
                        lift.setAssignedBlockEnd(null);
                        lift.setLastUpdated(LocalDateTime.now());
                        liftRepo.save(lift);
                        liftRepo.updateCurrentFloor(DEFAULT_BUILDING_ID, lift.getLiftNumber(), lastFloor);

                        rt.phase         = Phase.IDLE;
                        rt.previousFloor = lastFloor;
                        rt.targetFloor   = lastFloor;
                        rt.pendingTrips.clear();
                        rt.stops.clear();

                        drainQueue(lift);
                    } else {
                        moveToNextStop(lift, rt);
                    }
                }
            }
        }
    }

    // ── Build stop list and start serving ─────────────────────
    private void startServing(Lift lift, LiftRuntime rt) {
        Set<Integer> stopSet = new TreeSet<>();
        for (LiftTrip t : rt.pendingTrips) {
            // BUG FIX 8: Only add stops that are not the current floor
            if (t.getFromFloor() != lift.getCurrentFloor()) stopSet.add(t.getFromFloor());
            if (t.getToFloor()   != lift.getCurrentFloor()) stopSet.add(t.getToFloor());
        }

        // BUG FIX 9: Handle passengers already on current floor
        // Board them immediately before traveling
        boardTripsAtFloor(lift, rt, lift.getCurrentFloor());

        // Direction optimization: serve majority direction first
        List<Integer> above = stopSet.stream()
                .filter(f -> f > lift.getCurrentFloor()).sorted().toList();
        List<Integer> below = stopSet.stream()
                .filter(f -> f < lift.getCurrentFloor())
                .sorted(Comparator.reverseOrder()).toList();

        rt.stops = new LinkedHashSet<>();
        if (above.size() >= below.size()) {
            rt.stops.addAll(above);
            rt.stops.addAll(below);
        } else {
            rt.stops.addAll(below);
            rt.stops.addAll(above);
        }

        // Mark all trips IN_PROGRESS
        for (LiftTrip t : rt.pendingTrips) {
            if (t.getTripStatus() == LiftTrip.TripStatus.ASSIGNED) {
                t.setTripStatus(LiftTrip.TripStatus.IN_PROGRESS);
                tripRepo.save(t);
            }
        }

        lift.setActiveTripCount(rt.pendingTrips.size());
        lift.setCurrentLoad(rt.pendingTrips.size());
        liftRepo.save(lift);

        // BUG FIX 10: If all stops are empty after removing current floor
        // (everyone was already on this floor), go IDLE immediately
        if (rt.stops.isEmpty()) {
            log.info("[LIFT-{}] No stops needed, going IDLE at {}F", lift.getLiftNumber(), lift.getCurrentFloor());
            completeTripsAtFloor(lift, rt, lift.getCurrentFloor());
            lift.setLiftStatus(Lift.LiftStatus.IDLE);
            lift.setActiveTripCount(0);
            lift.setCurrentLoad(0);
            lift.setAssignedBlockStart(null);
            lift.setAssignedBlockEnd(null);
            liftRepo.save(lift);
            rt.phase = Phase.IDLE;
            rt.pendingTrips.clear();
            return;
        }

        rt.phase = Phase.TRAVELING;
        moveToNextStop(lift, rt);
    }

    // ── Move to next stop ──────────────────────────────────────
    private void moveToNextStop(Lift lift, LiftRuntime rt) {
        if (rt.stops.isEmpty()) return;
        int nextFloor = rt.stops.iterator().next();
        int distance  = Math.abs(lift.getCurrentFloor() - nextFloor);

        // BUG FIX 5: If distance is 0, arrive immediately
        long travelMs = distance * SECONDS_PER_FLOOR * 1000L;

        rt.previousFloor = lift.getCurrentFloor();
        rt.targetFloor   = nextFloor;
        rt.departTime    = System.currentTimeMillis();
        rt.arrivalTime   = rt.departTime + travelMs;
        rt.phase         = Phase.TRAVELING;

        log.info("[LIFT-{}] {}F→{}F dist={} time={}s",
                lift.getLiftNumber(), lift.getCurrentFloor(), nextFloor,
                distance, distance * SECONDS_PER_FLOOR);
    }

    // ── Complete trips whose toFloor == floor ─────────────────
    private void completeTripsAtFloor(Lift lift, LiftRuntime rt, int floor) {
        LocalDateTime now = LocalDateTime.now();
        // BUG FIX 11: Use new ArrayList to avoid ConcurrentModificationException
        List<LiftTrip> toComplete = new ArrayList<>(rt.pendingTrips.stream()
                .filter(t -> t.getToFloor() == floor
                          && t.getTripStatus() == LiftTrip.TripStatus.IN_PROGRESS)
                .toList());

        for (LiftTrip t : toComplete) {
            t.setTripStatus(LiftTrip.TripStatus.COMPLETED);
            t.setCompletedAt(now);
            // BUG FIX 12: Calculate travel seconds properly
            if (t.getAssignedAt() != null) {
                t.setTravelSeconds((int) java.time.Duration.between(t.getAssignedAt(), now).getSeconds());
            }
            tripRepo.save(t);
            rt.pendingTrips.remove(t);
            liftRepo.decrementTripCount(DEFAULT_BUILDING_ID, lift.getLiftNumber());
            liftRepo.incrementTotalTrips(DEFAULT_BUILDING_ID, lift.getLiftNumber());
            log.info("[LIFT-{}] Trip #{} COMPLETED at {}F", lift.getLiftNumber(), t.getId(), floor);
        }
    }

    // ── Board passengers whose fromFloor == floor ─────────────
    private void boardTripsAtFloor(Lift lift, LiftRuntime rt, int floor) {
        // BUG FIX 13: Only board ASSIGNED trips, not already IN_PROGRESS ones
        rt.pendingTrips.stream()
                .filter(t -> t.getFromFloor() == floor
                          && t.getTripStatus() == LiftTrip.TripStatus.ASSIGNED)
                .forEach(t -> {
                    t.setTripStatus(LiftTrip.TripStatus.IN_PROGRESS);
                    t.setAssignedAt(LocalDateTime.now());
                    tripRepo.save(t);
                    log.info("[LIFT-{}] Boarded trip #{} at {}F→{}F",
                            lift.getLiftNumber(), t.getId(), floor, t.getToFloor());
                });
    }

    // ── Drain queue when lift goes idle ───────────────────────
    private void drainQueue(Lift lift) {
        List<LiftQueue> waiting = queueRepo
                .findByBuildingIdAndQueueStatusOrderByQueuedAtAsc(
                        DEFAULT_BUILDING_ID, LiftQueue.QueueStatus.WAITING);
        if (waiting.isEmpty()) return;

        LiftRuntime rt = runtimes.get(lift.getId());
        if (rt == null) return;

        Building b = buildingRepo.findById(DEFAULT_BUILDING_ID).orElse(null);
        if (b == null) return;

        for (LiftQueue q : waiting) {
            if (rt.pendingTrips.size() >= lift.getCapacity()) break;
            // BUG FIX 14: Check queue entry not expired before draining
            if (q.getExpiresAt() != null && LocalDateTime.now().isAfter(q.getExpiresAt())) {
                q.setQueueStatus(LiftQueue.QueueStatus.EXPIRED);
                queueRepo.save(q);
                continue;
            }
            LiftTrip t = LiftTrip.builder()
                    .user(q.getUser()).building(b)
                    .employeeId(q.getEmployeeId())
                    .liftNumber(lift.getLiftNumber())
                    .fromFloor(q.getFromFloor()).toFloor(q.getToFloor())
                    .tripType(q.getTripType())
                    .tripStatus(LiftTrip.TripStatus.ASSIGNED)
                    .requestedAt(q.getQueuedAt())
                    .build();
            tripRepo.save(t);
            q.setQueueStatus(LiftQueue.QueueStatus.PROCESSING);
            queueRepo.save(q);
            rt.pendingTrips.add(t);
            log.info("[LIFT-{}] Drained queue entry #{}", lift.getLiftNumber(), q.getId());
        }

        if (!rt.pendingTrips.isEmpty()) {
            lift.setLiftStatus(Lift.LiftStatus.BUSY);
            liftRepo.save(lift);
            rt.phase = Phase.WAITING;
            rt.waitDeadline = System.currentTimeMillis() + (WAIT_SECONDS * 1000L);
            log.info("[LIFT-{}] Back to WAITING with {} queued trips",
                    lift.getLiftNumber(), rt.pendingTrips.size());
        }
    }

    // ── Public API for LiftService ─────────────────────────────
    public String getPhase(Long liftId) {
        LiftRuntime rt = runtimes.get(liftId);
        return rt != null ? rt.phase.name() : "IDLE";
    }

    public long getWaitRemaining(Long liftId) {
        LiftRuntime rt = runtimes.get(liftId);
        if (rt == null || rt.phase != Phase.WAITING) return 0;
        return Math.max(0, (rt.waitDeadline - System.currentTimeMillis()) / 1000);
    }

    public Map<Long, LiftRuntime> getRuntimes() {
        return Collections.unmodifiableMap(runtimes);
    }

    public enum Phase { IDLE, WAITING, TRAVELING, HALTING }

    public static class LiftRuntime {
        public Phase          phase        = Phase.IDLE;
        public long           waitDeadline = 0;
        public long           haltDeadline = 0;
        public long           departTime   = 0;
        public long           arrivalTime  = 0;
        public int            targetFloor  = 1;
        public int            previousFloor = 1;
        public List<LiftTrip> pendingTrips  = new ArrayList<>();
        public Set<Integer>   stops         = new LinkedHashSet<>();
    }
}
