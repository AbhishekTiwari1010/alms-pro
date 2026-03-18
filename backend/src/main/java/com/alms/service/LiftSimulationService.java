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

/**
 * ════════════════════════════════════════════════════════════════
 *  LIFT SIMULATION ENGINE
 * ════════════════════════════════════════════════════════════════
 *
 *  State machine per lift:
 *
 *  IDLE ──(request arrives)──► WAITING
 *       15s accumulation window — collects requests
 *       each new request resets timer (+15s) up to capacity
 *       ──(timer expires OR full)──► SERVING
 *
 *  SERVING:
 *    Sort stops by floor order
 *    Travel: 3 seconds per floor
 *    Halt at each stop: [2*(requests for that floor) + 3] seconds
 *    ──(all stops done)──► IDLE at last floor
 *
 *  currentFloor updates every tick during travel for live UI
 * ════════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiftSimulationService {

    private final LiftRepository      liftRepo;
    private final LiftTripRepository  tripRepo;
    private final LiftQueueRepository queueRepo;
    private final BuildingRepository  buildingRepo;

    // Per-lift runtime state (liftId -> state)
    private final Map<Long, LiftRuntime> runtimes = new ConcurrentHashMap<>();

    private static final long DEFAULT_BUILDING_ID = 1L;
    private static final int  WAIT_SECONDS        = 15;   // accumulation window
    private static final int  SECONDS_PER_FLOOR   = 1;    // travel speed
    private static final int  TICK_MS             = 500;  // simulation tick

    // ── Called by scheduler every 500ms ───────────────────────
    @Scheduled(fixedDelay = 500)
    @Transactional
    public void tick() {
        List<Lift> lifts = liftRepo.findByBuildingIdOrderByLiftNumber(DEFAULT_BUILDING_ID);
        for (Lift lift : lifts) {
            LiftRuntime rt = runtimes.computeIfAbsent(lift.getId(), id -> new LiftRuntime());
            tickLift(lift, rt);
        }
    }

    // ── Main per-lift tick ─────────────────────────────────────
    private void tickLift(Lift lift, LiftRuntime rt) {
        switch (rt.phase) {

            case IDLE -> {
                // Check if any queued trips assigned to this lift
                List<LiftTrip> pending = tripRepo.findByLiftNumberAndTripStatus(
                        lift.getLiftNumber(), LiftTrip.TripStatus.ASSIGNED);
                if (!pending.isEmpty()) {
                    rt.phase = Phase.WAITING;
                    rt.waitDeadline = System.currentTimeMillis() + (WAIT_SECONDS * 1000L);
                    rt.pendingTrips.clear();
                    rt.pendingTrips.addAll(pending);
                    lift.setLiftStatus(Lift.LiftStatus.BUSY);
                    liftRepo.save(lift);
                    log.info("[LIFT-{}] IDLE→WAITING deadline={}s trips={}",
                            lift.getLiftNumber(), WAIT_SECONDS, pending.size());
                }
            }

            case WAITING -> {
                // Collect any new trips assigned during window
                List<LiftTrip> newTrips = tripRepo.findByLiftNumberAndTripStatus(
                        lift.getLiftNumber(), LiftTrip.TripStatus.ASSIGNED);
                int prevSize = rt.pendingTrips.size();
                // Add trips not already tracked
                for (LiftTrip t : newTrips) {
                    boolean exists = rt.pendingTrips.stream().anyMatch(p -> p.getId().equals(t.getId()));
                    if (!exists) {
                        rt.pendingTrips.add(t);
                        // New request → extend deadline by 15s
                        rt.waitDeadline = Math.max(rt.waitDeadline,
                                System.currentTimeMillis() + (WAIT_SECONDS * 1000L));
                        log.info("[LIFT-{}] New request during window, extended deadline. trips={}",
                                lift.getLiftNumber(), rt.pendingTrips.size());
                    }
                }

                // Check capacity full or deadline passed
                boolean full    = rt.pendingTrips.size() >= lift.getCapacity();
                boolean expired = System.currentTimeMillis() >= rt.waitDeadline;

                if (full || expired) {
                    log.info("[LIFT-{}] WAITING→SERVING trips={} reason={}",
                            lift.getLiftNumber(), rt.pendingTrips.size(), full ? "FULL" : "TIMEOUT");
                    startServing(lift, rt);
                }
            }

            case TRAVELING -> {
                long now = System.currentTimeMillis();
                if (now >= rt.arrivalTime) {
                    // Arrived at next stop
                    lift.setCurrentFloor(rt.targetFloor);
                    liftRepo.updateCurrentFloor(DEFAULT_BUILDING_ID, lift.getLiftNumber(), rt.targetFloor);

                    // Calculate halt time for this floor
                    int requestsHere = (int) rt.pendingTrips.stream()
                            .filter(t -> t.getFromFloor() == rt.targetFloor || t.getToFloor() == rt.targetFloor)
                            .count();
                    int haltSeconds = (2 * requestsHere) + 3;

                    rt.phase = Phase.HALTING;
                    rt.haltDeadline = now + (haltSeconds * 1000L);
                    log.info("[LIFT-{}] Arrived at {}F, halting {}s for {} requests",
                            lift.getLiftNumber(), rt.targetFloor, haltSeconds, requestsHere);
                } else {
                    // Interpolate current floor for live UI
                    double elapsed   = now - rt.departTime;
                    double total     = rt.arrivalTime - rt.departTime;
                    double progress  = Math.min(1.0, elapsed / total);
                    int interpolated = (int) Math.round(rt.previousFloor + progress * (rt.targetFloor - rt.previousFloor));
                    if (interpolated != lift.getCurrentFloor()) {
                        lift.setCurrentFloor(interpolated);
                        liftRepo.updateCurrentFloor(DEFAULT_BUILDING_ID, lift.getLiftNumber(), interpolated);
                    }
                }
            }

            case HALTING -> {
                if (System.currentTimeMillis() >= rt.haltDeadline) {
                    // Complete trips whose toFloor is this floor
                    completeTripsAtFloor(lift, rt, rt.targetFloor);
                    // Mark boarding trips (fromFloor == targetFloor) as IN_PROGRESS
                    boardTripsAtFloor(lift, rt, rt.targetFloor);
                    // Remove this stop
                    rt.stops.remove(Integer.valueOf(rt.targetFloor));

                    if (rt.stops.isEmpty()) {
                        // All done
                        log.info("[LIFT-{}] All stops served, going IDLE at {}F", lift.getLiftNumber(), rt.targetFloor);
                        lift.setLiftStatus(Lift.LiftStatus.IDLE);
                        lift.setActiveTripCount(0);
                        lift.setCurrentLoad(0);
                        lift.setAssignedBlockStart(null);
                        lift.setAssignedBlockEnd(null);
                        lift.setLastUpdated(LocalDateTime.now());
                        liftRepo.save(lift);
                        rt.phase = Phase.IDLE;
                        rt.pendingTrips.clear();
                        // Drain queue
                        drainQueue(lift);
                    } else {
                        // Move to next stop
                        moveToNextStop(lift, rt);
                    }
                }
            }
        }
    }

    // ── Start serving: build stop list ────────────────────────
    private void startServing(Lift lift, LiftRuntime rt) {
        // Build sorted stop list (from current floor direction-optimized)
        Set<Integer> stopSet = new TreeSet<>();
        for (LiftTrip t : rt.pendingTrips) {
            stopSet.add(t.getFromFloor());
            stopSet.add(t.getToFloor());
        }
        // Remove current floor (already here)
        stopSet.remove(lift.getCurrentFloor());

        // Direction: go up first if more stops above, else down
        List<Integer> above = stopSet.stream().filter(f -> f > lift.getCurrentFloor()).sorted().toList();
        List<Integer> below = stopSet.stream().filter(f -> f < lift.getCurrentFloor()).sorted(Comparator.reverseOrder()).toList();

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
            t.setTripStatus(LiftTrip.TripStatus.IN_PROGRESS);
            tripRepo.save(t);
        }

        lift.setActiveTripCount(rt.pendingTrips.size());
        lift.setCurrentLoad(rt.pendingTrips.size());
        liftRepo.save(lift);

        rt.phase = Phase.TRAVELING;
        moveToNextStop(lift, rt);
    }

    // ── Move lift to next stop ─────────────────────────────────
    private void moveToNextStop(Lift lift, LiftRuntime rt) {
        if (rt.stops.isEmpty()) return;
        int nextFloor    = rt.stops.iterator().next();
        int distance     = Math.abs(lift.getCurrentFloor() - nextFloor);
        long travelMs    = distance * SECONDS_PER_FLOOR * 1000L;

        rt.previousFloor = lift.getCurrentFloor();
        rt.targetFloor   = nextFloor;
        rt.departTime    = System.currentTimeMillis();
        rt.arrivalTime   = rt.departTime + travelMs;
        rt.phase         = Phase.TRAVELING;

        log.info("[LIFT-{}] Traveling {}F→{}F dist={} time={}s",
                lift.getLiftNumber(), lift.getCurrentFloor(), nextFloor, distance, distance * SECONDS_PER_FLOOR);
    }

    // ── Complete trips whose destination is this floor ────────
    private void completeTripsAtFloor(Lift lift, LiftRuntime rt, int floor) {
        LocalDateTime now = LocalDateTime.now();
        List<LiftTrip> toComplete = rt.pendingTrips.stream()
                .filter(t -> t.getToFloor() == floor && t.getTripStatus() == LiftTrip.TripStatus.IN_PROGRESS)
                .toList();
        for (LiftTrip t : toComplete) {
            t.setTripStatus(LiftTrip.TripStatus.COMPLETED);
            t.setCompletedAt(now);
            tripRepo.save(t);
            rt.pendingTrips.remove(t);
            liftRepo.decrementTripCount(DEFAULT_BUILDING_ID, lift.getLiftNumber());
            liftRepo.incrementTotalTrips(DEFAULT_BUILDING_ID, lift.getLiftNumber());
            log.info("[LIFT-{}] Trip #{} COMPLETED at {}F", lift.getLiftNumber(), t.getId(), floor);
        }
    }

    // ── Board trips whose origin is this floor ─────────────────
    private void boardTripsAtFloor(Lift lift, LiftRuntime rt, int floor) {
        rt.pendingTrips.stream()
                .filter(t -> t.getFromFloor() == floor && t.getTripStatus() == LiftTrip.TripStatus.ASSIGNED)
                .forEach(t -> {
                    t.setTripStatus(LiftTrip.TripStatus.IN_PROGRESS);
                    t.setAssignedAt(LocalDateTime.now());
                    tripRepo.save(t);
                });
    }

    // ── Drain queue after lift goes idle ──────────────────────
    private void drainQueue(Lift lift) {
        List<LiftQueue> waiting = queueRepo
                .findByBuildingIdAndQueueStatusOrderByQueuedAtAsc(DEFAULT_BUILDING_ID, LiftQueue.QueueStatus.WAITING);
        if (waiting.isEmpty()) return;

        LiftRuntime rt = runtimes.get(lift.getId());
        if (rt == null) return;

        for (LiftQueue q : waiting) {
            if (rt.pendingTrips.size() >= lift.getCapacity()) break;
            // Create a trip from queue entry
            Building b = buildingRepo.findById(DEFAULT_BUILDING_ID).orElse(null);
            if (b == null) continue;
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
            log.info("[LIFT-{}] Drained queue #{} from queue", lift.getLiftNumber(), q.getId());
        }

        if (!rt.pendingTrips.isEmpty()) {
            lift.setLiftStatus(Lift.LiftStatus.BUSY);
            liftRepo.save(lift);
            rt.phase = Phase.WAITING;
            rt.waitDeadline = System.currentTimeMillis() + (WAIT_SECONDS * 1000L);
        }
    }

    // ── Public: get live lift state for UI ─────────────────────
    public Map<Long, LiftRuntime> getRuntimes() { return Collections.unmodifiableMap(runtimes); }

    public String getPhase(Long liftId) {
        LiftRuntime rt = runtimes.get(liftId);
        return rt != null ? rt.phase.name() : "IDLE";
    }

    public long getWaitRemaining(Long liftId) {
        LiftRuntime rt = runtimes.get(liftId);
        if (rt == null || rt.phase != Phase.WAITING) return 0;
        return Math.max(0, (rt.waitDeadline - System.currentTimeMillis()) / 1000);
    }

    // ── Runtime state per lift ────────────────────────────────
    public enum Phase { IDLE, WAITING, TRAVELING, HALTING }

    public static class LiftRuntime {
        public Phase        phase        = Phase.IDLE;
        public long         waitDeadline = 0;
        public long         haltDeadline = 0;
        public long         departTime   = 0;
        public long         arrivalTime  = 0;
        public int          targetFloor  = 1;
        public int          previousFloor= 1;
        public List<LiftTrip> pendingTrips = new ArrayList<>();
        public Set<Integer>   stops       = new LinkedHashSet<>();
    }
}
