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

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void tick() {
        List<Lift> lifts = liftRepo.findByBuildingIdOrderByLiftNumber(DEFAULT_BUILDING_ID);
        for (Lift lift : lifts) {
            if (lift.getLiftStatus() == Lift.LiftStatus.MAINTENANCE ||
                    lift.getLiftStatus() == Lift.LiftStatus.OFFLINE) {
                runtimes.remove(lift.getId());
                continue;
            }
            LiftRuntime rt = runtimes.computeIfAbsent(lift.getId(), id -> {
                LiftRuntime r = new LiftRuntime();
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
                    log.info("[LIFT-{}] IDLE→WAITING at {}F trips={}", lift.getLiftNumber(), lift.getCurrentFloor(), pending.size());
                }
            }

            case WAITING -> {
                if (lift.getLiftStatus() == Lift.LiftStatus.MAINTENANCE) {
                    rt.phase = Phase.IDLE; rt.pendingTrips.clear(); rt.stops.clear(); return;
                }
                List<LiftTrip> newTrips = tripRepo.findByLiftNumberAndTripStatus(
                        lift.getLiftNumber(), LiftTrip.TripStatus.ASSIGNED);
                for (LiftTrip t : newTrips) {
                    boolean exists = rt.pendingTrips.stream().anyMatch(p -> p.getId().equals(t.getId()));
                    if (!exists) {
                        rt.pendingTrips.add(t);
                        rt.waitDeadline = Math.max(rt.waitDeadline, System.currentTimeMillis() + (WAIT_SECONDS * 1000L));
                        log.info("[LIFT-{}] New in window trips={}", lift.getLiftNumber(), rt.pendingTrips.size());
                    }
                }
                boolean full    = rt.pendingTrips.size() >= lift.getCapacity();
                boolean expired = System.currentTimeMillis() >= rt.waitDeadline;
                if (full || expired) {
                    log.info("[LIFT-{}] WAITING→SERVING trips={} reason={}", lift.getLiftNumber(), rt.pendingTrips.size(), full ? "FULL" : "TIMEOUT");
                    startServing(lift, rt);
                }
            }

            case TRAVELING -> {
                long now = System.currentTimeMillis();
                // LOOK: pick up new requests in travel direction
                pickupEnRoute(lift, rt);

                if (now >= rt.arrivalTime || rt.arrivalTime == rt.departTime) {
                    lift.setCurrentFloor(rt.targetFloor);
                    liftRepo.updateCurrentFloor(DEFAULT_BUILDING_ID, lift.getLiftNumber(), rt.targetFloor);
                    rt.previousFloor = rt.targetFloor;

                    int requestsHere = (int) rt.pendingTrips.stream()
                            .filter(t -> t.getFromFloor() == rt.targetFloor || t.getToFloor() == rt.targetFloor)
                            .count();
                    int haltSeconds = (2 * requestsHere) + 3;
                    rt.phase = Phase.HALTING;
                    rt.haltDeadline = now + (haltSeconds * 1000L);
                    log.info("[LIFT-{}] Arrived {}F halting {}s", lift.getLiftNumber(), rt.targetFloor, haltSeconds);
                } else {
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
                    completeTripsAtFloor(lift, rt, rt.targetFloor);
                    boardTripsAtFloor(lift, rt, rt.targetFloor);
                    rt.stops.remove(Integer.valueOf(rt.targetFloor));
                    // Check again for en-route pickups after halting
                    pickupEnRoute(lift, rt);

                    if (rt.stops.isEmpty()) {
                        int lastFloor = rt.targetFloor;
                        log.info("[LIFT-{}] All stops done IDLE at {}F", lift.getLiftNumber(), lastFloor);
                        lift.setLiftStatus(Lift.LiftStatus.IDLE);
                        lift.setCurrentFloor(lastFloor);
                        lift.setActiveTripCount(0);
                        lift.setCurrentLoad(0);
                        lift.setAssignedBlockStart(null);
                        lift.setAssignedBlockEnd(null);
                        lift.setLastUpdated(LocalDateTime.now());
                        liftRepo.save(lift);
                        liftRepo.updateCurrentFloor(DEFAULT_BUILDING_ID, lift.getLiftNumber(), lastFloor);
                        rt.phase = Phase.IDLE;
                        rt.previousFloor = lastFloor;
                        rt.targetFloor   = lastFloor;
                        rt.direction     = Direction.NONE;
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

    // ── LOOK Algorithm: pickup new trips in current direction ──
    private void pickupEnRoute(Lift lift, LiftRuntime rt) {
        if (rt.direction == Direction.NONE) return;
        if (rt.pendingTrips.size() >= lift.getCapacity()) return;

        List<LiftTrip> newTrips = tripRepo.findByLiftNumberAndTripStatus(
                lift.getLiftNumber(), LiftTrip.TripStatus.ASSIGNED);

        boolean added = false;
        for (LiftTrip t : newTrips) {
            if (rt.pendingTrips.size() >= lift.getCapacity()) break;
            boolean alreadyTracked = rt.pendingTrips.stream().anyMatch(p -> p.getId().equals(t.getId()));
            if (alreadyTracked) continue;

            // Only pick up if boarding floor is ahead in current direction
            boolean aheadUp   = rt.direction == Direction.UP   && t.getFromFloor() >= lift.getCurrentFloor();
            boolean aheadDown = rt.direction == Direction.DOWN  && t.getFromFloor() <= lift.getCurrentFloor();

            if (aheadUp || aheadDown) {
                rt.pendingTrips.add(t);
                t.setTripStatus(LiftTrip.TripStatus.IN_PROGRESS);
                t.setAssignedAt(LocalDateTime.now());
                tripRepo.save(t);

                if (t.getFromFloor() != lift.getCurrentFloor()) rt.stops.add(t.getFromFloor());
                if (t.getToFloor()   != lift.getCurrentFloor()) rt.stops.add(t.getToFloor());
                added = true;

                lift.setActiveTripCount(lift.getActiveTripCount() + 1);
                lift.setCurrentLoad(lift.getCurrentLoad() + 1);
                liftRepo.save(lift);
                log.info("[LIFT-{}] LOOK pickup trip #{} {}F→{}F direction={}",
                        lift.getLiftNumber(), t.getId(), t.getFromFloor(), t.getToFloor(), rt.direction);
            }
        }

        if (added) reorderStops(lift, rt);
    }

    private void reorderStops(Lift lift, LiftRuntime rt) {
        List<Integer> all = new ArrayList<>(rt.stops);
        rt.stops = new LinkedHashSet<>();
        if (rt.direction == Direction.UP) {
            all.stream().filter(f -> f >= lift.getCurrentFloor()).sorted().forEach(rt.stops::add);
            all.stream().filter(f -> f <  lift.getCurrentFloor()).sorted(Comparator.reverseOrder()).forEach(rt.stops::add);
        } else {
            all.stream().filter(f -> f <= lift.getCurrentFloor()).sorted(Comparator.reverseOrder()).forEach(rt.stops::add);
            all.stream().filter(f -> f >  lift.getCurrentFloor()).sorted().forEach(rt.stops::add);
        }
    }

    private void startServing(Lift lift, LiftRuntime rt) {
        Set<Integer> stopSet = new TreeSet<>();
        for (LiftTrip t : rt.pendingTrips) {
            if (t.getFromFloor() != lift.getCurrentFloor()) stopSet.add(t.getFromFloor());
            if (t.getToFloor()   != lift.getCurrentFloor()) stopSet.add(t.getToFloor());
        }

        boardTripsAtFloor(lift, rt, lift.getCurrentFloor());

        long above = stopSet.stream().filter(f -> f > lift.getCurrentFloor()).count();
        long below = stopSet.stream().filter(f -> f < lift.getCurrentFloor()).count();

        rt.stops = new LinkedHashSet<>();
        if (above >= below) {
            rt.direction = Direction.UP;
            stopSet.stream().filter(f -> f > lift.getCurrentFloor()).sorted().forEach(rt.stops::add);
            stopSet.stream().filter(f -> f < lift.getCurrentFloor()).sorted(Comparator.reverseOrder()).forEach(rt.stops::add);
        } else {
            rt.direction = Direction.DOWN;
            stopSet.stream().filter(f -> f < lift.getCurrentFloor()).sorted(Comparator.reverseOrder()).forEach(rt.stops::add);
            stopSet.stream().filter(f -> f > lift.getCurrentFloor()).sorted().forEach(rt.stops::add);
        }

        for (LiftTrip t : rt.pendingTrips) {
            if (t.getTripStatus() == LiftTrip.TripStatus.ASSIGNED) {
                t.setTripStatus(LiftTrip.TripStatus.IN_PROGRESS);
                tripRepo.save(t);
            }
        }

        lift.setActiveTripCount(rt.pendingTrips.size());
        lift.setCurrentLoad(rt.pendingTrips.size());
        liftRepo.save(lift);

        if (rt.stops.isEmpty()) {
            completeTripsAtFloor(lift, rt, lift.getCurrentFloor());
            lift.setLiftStatus(Lift.LiftStatus.IDLE);
            lift.setActiveTripCount(0);
            lift.setCurrentLoad(0);
            lift.setAssignedBlockStart(null);
            lift.setAssignedBlockEnd(null);
            liftRepo.save(lift);
            rt.phase = Phase.IDLE;
            rt.direction = Direction.NONE;
            rt.pendingTrips.clear();
            return;
        }

        rt.phase = Phase.TRAVELING;
        moveToNextStop(lift, rt);
    }

    private void moveToNextStop(Lift lift, LiftRuntime rt) {
        if (rt.stops.isEmpty()) return;
        int nextFloor = rt.stops.iterator().next();
        int distance  = Math.abs(lift.getCurrentFloor() - nextFloor);
        long travelMs = distance * SECONDS_PER_FLOOR * 1000L;

        if      (nextFloor > lift.getCurrentFloor()) rt.direction = Direction.UP;
        else if (nextFloor < lift.getCurrentFloor()) rt.direction = Direction.DOWN;

        rt.previousFloor = lift.getCurrentFloor();
        rt.targetFloor   = nextFloor;
        rt.departTime    = System.currentTimeMillis();
        rt.arrivalTime   = rt.departTime + travelMs;
        rt.phase         = Phase.TRAVELING;

        log.info("[LIFT-{}] Moving {}F→{}F dist={} dir={}", lift.getLiftNumber(),
                lift.getCurrentFloor(), nextFloor, distance, rt.direction);
    }

    private void completeTripsAtFloor(Lift lift, LiftRuntime rt, int floor) {
        LocalDateTime now = LocalDateTime.now();
        List<LiftTrip> done = new ArrayList<>(rt.pendingTrips.stream()
                .filter(t -> t.getToFloor() == floor && t.getTripStatus() == LiftTrip.TripStatus.IN_PROGRESS)
                .toList());
        for (LiftTrip t : done) {
            t.setTripStatus(LiftTrip.TripStatus.COMPLETED);
            t.setCompletedAt(now);
            if (t.getAssignedAt() != null)
                t.setTravelSeconds((int) java.time.Duration.between(t.getAssignedAt(), now).getSeconds());
            tripRepo.save(t);
            rt.pendingTrips.remove(t);
            liftRepo.decrementTripCount(DEFAULT_BUILDING_ID, lift.getLiftNumber());
            liftRepo.incrementTotalTrips(DEFAULT_BUILDING_ID, lift.getLiftNumber());
            log.info("[LIFT-{}] COMPLETED trip #{} at {}F", lift.getLiftNumber(), t.getId(), floor);
        }
    }

    private void boardTripsAtFloor(Lift lift, LiftRuntime rt, int floor) {
        rt.pendingTrips.stream()
                .filter(t -> t.getFromFloor() == floor && t.getTripStatus() == LiftTrip.TripStatus.ASSIGNED)
                .forEach(t -> {
                    t.setTripStatus(LiftTrip.TripStatus.IN_PROGRESS);
                    t.setAssignedAt(LocalDateTime.now());
                    tripRepo.save(t);
                });
    }

    private void drainQueue(Lift lift) {
        List<LiftQueue> waiting = queueRepo.findByBuildingIdAndQueueStatusOrderByQueuedAtAsc(
                DEFAULT_BUILDING_ID, LiftQueue.QueueStatus.WAITING);
        if (waiting.isEmpty()) return;
        LiftRuntime rt = runtimes.get(lift.getId());
        if (rt == null) return;
        Building b = buildingRepo.findById(DEFAULT_BUILDING_ID).orElse(null);
        if (b == null) return;

        for (LiftQueue q : waiting) {
            if (rt.pendingTrips.size() >= lift.getCapacity()) break;
            if (q.getExpiresAt() != null && LocalDateTime.now().isAfter(q.getExpiresAt())) {
                q.setQueueStatus(LiftQueue.QueueStatus.EXPIRED); queueRepo.save(q); continue;
            }
            LiftTrip t = LiftTrip.builder()
                    .user(q.getUser()).building(b).employeeId(q.getEmployeeId())
                    .liftNumber(lift.getLiftNumber()).fromFloor(q.getFromFloor()).toFloor(q.getToFloor())
                    .tripType(q.getTripType()).tripStatus(LiftTrip.TripStatus.ASSIGNED).requestedAt(q.getQueuedAt())
                    .build();
            tripRepo.save(t);
            q.setQueueStatus(LiftQueue.QueueStatus.PROCESSING);
            queueRepo.save(q);
            rt.pendingTrips.add(t);
            log.info("[LIFT-{}] Drained queue #{}", lift.getLiftNumber(), q.getId());
        }
        if (!rt.pendingTrips.isEmpty()) {
            lift.setLiftStatus(Lift.LiftStatus.BUSY); liftRepo.save(lift);
            rt.phase = Phase.WAITING;
            rt.waitDeadline = System.currentTimeMillis() + (WAIT_SECONDS * 1000L);
        }
    }

    public String    getPhase(Long liftId)         { LiftRuntime rt=runtimes.get(liftId); return rt!=null?rt.phase.name():"IDLE"; }
    public long      getWaitRemaining(Long liftId)  { LiftRuntime rt=runtimes.get(liftId); if(rt==null||rt.phase!=Phase.WAITING)return 0; return Math.max(0,(rt.waitDeadline-System.currentTimeMillis())/1000); }
    public Direction getDirection(Long liftId)      { LiftRuntime rt=runtimes.get(liftId); return rt!=null?rt.direction:Direction.NONE; }
    public Map<Long,LiftRuntime> getRuntimes()      { return Collections.unmodifiableMap(runtimes); }

    public enum Phase     { IDLE, WAITING, TRAVELING, HALTING }
    public enum Direction { UP, DOWN, NONE }

    public static class LiftRuntime {
        public Phase          phase         = Phase.IDLE;
        public Direction      direction     = Direction.NONE;
        public long           waitDeadline  = 0;
        public long           haltDeadline  = 0;
        public long           departTime    = 0;
        public long           arrivalTime   = 0;
        public int            targetFloor   = 1;
        public int            previousFloor = 1;
        public List<LiftTrip> pendingTrips  = new ArrayList<>();
        public Set<Integer>   stops         = new LinkedHashSet<>();
    }
}
