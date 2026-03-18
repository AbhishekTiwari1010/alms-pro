package com.alms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_log")
@Getter @Setter @NoArgsConstructor
public class MaintenanceLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lift_id",     nullable = false) private Lift lift;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reported_by", nullable = false) private User reportedBy;

    @Column(name = "issue",      nullable = false)             private String  issue;
    @Column(name = "notes",      columnDefinition = "TEXT")    private String  notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_status", nullable = false)
    private LogStatus logStatus = LogStatus.REPORTED;

    @Column(name = "created_at",  nullable = false, updatable = false) private LocalDateTime createdAt  = LocalDateTime.now();
    @Column(name = "resolved_at")                                        private LocalDateTime resolvedAt;

    public enum LogStatus { REPORTED, IN_PROGRESS, RESOLVED }

    public static MaintenanceLogBuilder builder() { return new MaintenanceLogBuilder(); }

    public static class MaintenanceLogBuilder {
        private Lift lift; private User reportedBy; private String issue; private String notes;
        private LogStatus logStatus = LogStatus.REPORTED;

        public MaintenanceLogBuilder lift(Lift v)          { this.lift       = v; return this; }
        public MaintenanceLogBuilder reportedBy(User v)    { this.reportedBy = v; return this; }
        public MaintenanceLogBuilder issue(String v)       { this.issue      = v; return this; }
        public MaintenanceLogBuilder notes(String v)       { this.notes      = v; return this; }
        public MaintenanceLogBuilder logStatus(LogStatus v){ this.logStatus  = v; return this; }

        public MaintenanceLog build() {
            MaintenanceLog m = new MaintenanceLog();
            m.lift = this.lift; m.reportedBy = this.reportedBy; m.issue = this.issue;
            m.notes = this.notes; m.logStatus = this.logStatus; m.createdAt = LocalDateTime.now();
            return m;
        }
    }
}
