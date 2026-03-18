package com.alms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lifts")
@Getter @Setter @NoArgsConstructor
@ToString(exclude = "building")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Lift {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "lift_number", nullable = false) private Integer liftNumber;
    @Column(name = "lift_name",   nullable = false) private String  liftName;
    @Column(name = "current_floor", nullable = false) private Integer currentFloor = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "lift_status", nullable = false)
    private LiftStatus liftStatus = LiftStatus.IDLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "lift_type", nullable = false)
    private LiftType liftType = LiftType.STANDARD;

    @Column(name = "capacity",             nullable = false) private Integer capacity        = 10;
    @Column(name = "current_load",         nullable = false) private Integer currentLoad     = 0;
    @Column(name = "assigned_block_start")                   private Integer assignedBlockStart;
    @Column(name = "assigned_block_end")                     private Integer assignedBlockEnd;
    @Column(name = "active_trip_count",    nullable = false) private Integer activeTripCount  = 0;
    @Column(name = "total_trips",          nullable = false) private Long    totalTrips        = 0L;
    @Column(name = "last_maintenance")                       private LocalDateTime lastMaintenance;
    @Column(name = "last_updated")                           private LocalDateTime lastUpdated = LocalDateTime.now();

    public enum LiftStatus { IDLE, BUSY, MAINTENANCE, OFFLINE }
    public enum LiftType   { STANDARD, EXPRESS, FREIGHT, VIP }

    public boolean isServingBlock(int floor) {
        return liftStatus == LiftStatus.BUSY
            && assignedBlockStart != null && assignedBlockEnd != null
            && floor >= assignedBlockStart && floor <= assignedBlockEnd;
    }
    public int distanceTo(int floor) { return Math.abs(currentFloor - floor); }
    public boolean hasCapacity()     { return currentLoad < capacity; }
}
