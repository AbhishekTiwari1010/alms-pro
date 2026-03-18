package com.alms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lift_trips")
@Getter @Setter @NoArgsConstructor
public class LiftTrip {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id",     nullable = false) private User     user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "building_id", nullable = false) private Building building;

    @Column(name = "employee_id", nullable = false) private String  employeeId;
    @Column(name = "lift_number", nullable = false) private Integer liftNumber = -1;
    @Column(name = "from_floor",  nullable = false) private Integer fromFloor;
    @Column(name = "to_floor",    nullable = false) private Integer toFloor;
    @Column(name = "route_name")                    private String  routeName;

    @Enumerated(EnumType.STRING) @Column(name = "trip_type",   nullable = false) private TripType   tripType;
    @Enumerated(EnumType.STRING) @Column(name = "trip_status", nullable = false) private TripStatus tripStatus = TripStatus.ASSIGNED;
    @Enumerated(EnumType.STRING) @Column(name = "priority",    nullable = false) private Priority   priority   = Priority.NORMAL;

    @Column(name = "wait_seconds")   private Integer waitSeconds;
    @Column(name = "travel_seconds") private Integer travelSeconds;
    @Column(name = "requested_at",   nullable = false, updatable = false) private LocalDateTime requestedAt = LocalDateTime.now();
    @Column(name = "assigned_at")    private LocalDateTime assignedAt;
    @Column(name = "completed_at")   private LocalDateTime completedAt;

    public enum TripType   { ENTRY, EXIT, INTER_FLOOR, SCHEDULED }
    public enum TripStatus { QUEUED, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED }
    public enum Priority   { NORMAL, HIGH, EMERGENCY }

    public static LiftTripBuilder builder() { return new LiftTripBuilder(); }

    public static class LiftTripBuilder {
        private User user; private Building building; private String employeeId;
        private Integer liftNumber = -1; private Integer fromFloor; private Integer toFloor;
        private String routeName; private TripType tripType; private TripStatus tripStatus = TripStatus.ASSIGNED;
        private Priority priority = Priority.NORMAL; private Integer waitSeconds; private Integer travelSeconds;
        private LocalDateTime requestedAt = LocalDateTime.now(); private LocalDateTime assignedAt; private LocalDateTime completedAt;

        public LiftTripBuilder user(User v)               { this.user         = v; return this; }
        public LiftTripBuilder building(Building v)        { this.building     = v; return this; }
        public LiftTripBuilder employeeId(String v)        { this.employeeId   = v; return this; }
        public LiftTripBuilder liftNumber(Integer v)       { this.liftNumber   = v; return this; }
        public LiftTripBuilder fromFloor(Integer v)        { this.fromFloor    = v; return this; }
        public LiftTripBuilder toFloor(Integer v)          { this.toFloor      = v; return this; }
        public LiftTripBuilder routeName(String v)         { this.routeName    = v; return this; }
        public LiftTripBuilder tripType(TripType v)        { this.tripType     = v; return this; }
        public LiftTripBuilder tripStatus(TripStatus v)    { this.tripStatus   = v; return this; }
        public LiftTripBuilder priority(Priority v)        { this.priority     = v; return this; }
        public LiftTripBuilder waitSeconds(Integer v)      { this.waitSeconds  = v; return this; }
        public LiftTripBuilder travelSeconds(Integer v)    { this.travelSeconds= v; return this; }
        public LiftTripBuilder requestedAt(LocalDateTime v){ this.requestedAt  = v; return this; }
        public LiftTripBuilder assignedAt(LocalDateTime v) { this.assignedAt   = v; return this; }
        public LiftTripBuilder completedAt(LocalDateTime v){ this.completedAt  = v; return this; }

        public LiftTrip build() {
            LiftTrip t = new LiftTrip();
            t.user = this.user; t.building = this.building; t.employeeId = this.employeeId;
            t.liftNumber = this.liftNumber; t.fromFloor = this.fromFloor; t.toFloor = this.toFloor;
            t.routeName = this.routeName; t.tripType = this.tripType; t.tripStatus = this.tripStatus;
            t.priority = this.priority; t.waitSeconds = this.waitSeconds; t.travelSeconds = this.travelSeconds;
            t.requestedAt = this.requestedAt; t.assignedAt = this.assignedAt; t.completedAt = this.completedAt;
            return t;
        }
    }
}
