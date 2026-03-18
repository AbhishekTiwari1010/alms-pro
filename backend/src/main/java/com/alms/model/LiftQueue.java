package com.alms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lift_queue")
@Getter @Setter @NoArgsConstructor
public class LiftQueue {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id",     nullable = false) private User     user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "building_id", nullable = false) private Building building;

    @Column(name = "employee_id", nullable = false) private String  employeeId;
    @Column(name = "from_floor",  nullable = false) private Integer fromFloor;
    @Column(name = "to_floor",    nullable = false) private Integer toFloor;
    @Column(name = "route_name")                    private String  routeName;

    @Enumerated(EnumType.STRING) @Column(name = "trip_type",    nullable = false) private LiftTrip.TripType tripType;
    @Enumerated(EnumType.STRING) @Column(name = "priority",     nullable = false) private LiftTrip.Priority priority    = LiftTrip.Priority.NORMAL;
    @Enumerated(EnumType.STRING) @Column(name = "queue_status", nullable = false) private QueueStatus       queueStatus = QueueStatus.WAITING;

    @Column(name = "queued_at",  nullable = false, updatable = false) private LocalDateTime queuedAt  = LocalDateTime.now();
    @Column(name = "expires_at")                                       private LocalDateTime expiresAt;

    public enum QueueStatus { WAITING, PROCESSING, DONE, EXPIRED }

    public static LiftQueueBuilder builder() { return new LiftQueueBuilder(); }

    public static class LiftQueueBuilder {
        private User user; private Building building; private String employeeId;
        private Integer fromFloor; private Integer toFloor; private String routeName;
        private LiftTrip.TripType tripType; private LiftTrip.Priority priority = LiftTrip.Priority.NORMAL;
        private QueueStatus queueStatus = QueueStatus.WAITING;
        private LocalDateTime queuedAt = LocalDateTime.now(); private LocalDateTime expiresAt;

        public LiftQueueBuilder user(User v)                    { this.user        = v; return this; }
        public LiftQueueBuilder building(Building v)             { this.building    = v; return this; }
        public LiftQueueBuilder employeeId(String v)             { this.employeeId  = v; return this; }
        public LiftQueueBuilder fromFloor(Integer v)             { this.fromFloor   = v; return this; }
        public LiftQueueBuilder toFloor(Integer v)               { this.toFloor     = v; return this; }
        public LiftQueueBuilder routeName(String v)              { this.routeName   = v; return this; }
        public LiftQueueBuilder tripType(LiftTrip.TripType v)    { this.tripType    = v; return this; }
        public LiftQueueBuilder priority(LiftTrip.Priority v)    { this.priority    = v; return this; }
        public LiftQueueBuilder queueStatus(QueueStatus v)       { this.queueStatus = v; return this; }
        public LiftQueueBuilder expiresAt(LocalDateTime v)       { this.expiresAt   = v; return this; }

        public LiftQueue build() {
            LiftQueue q = new LiftQueue();
            q.user = this.user; q.building = this.building; q.employeeId = this.employeeId;
            q.fromFloor = this.fromFloor; q.toFloor = this.toFloor; q.routeName = this.routeName;
            q.tripType = this.tripType; q.priority = this.priority; q.queueStatus = this.queueStatus;
            q.queuedAt = this.queuedAt; q.expiresAt = this.expiresAt;
            return q;
        }
    }
}
