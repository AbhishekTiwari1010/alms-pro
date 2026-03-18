package com.alms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_routes")
@Getter @Setter @NoArgsConstructor
public class SavedRoute {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "route_name", nullable = false) private String  routeName;
    @Column(name = "from_floor", nullable = false) private Integer fromFloor;
    @Column(name = "to_floor",   nullable = false) private Integer toFloor;
    @Column(name = "icon")                         private String  icon     = "arrow-up";
    @Column(name = "color")                        private String  color    = "#00d4ff";
    @Column(name = "use_count",  nullable = false) private Integer useCount = 0;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public static SavedRouteBuilder builder() { return new SavedRouteBuilder(); }

    public static class SavedRouteBuilder {
        private User user; private String routeName; private Integer fromFloor; private Integer toFloor;
        private String icon = "arrow-up"; private String color = "#00d4ff"; private Integer useCount = 0;

        public SavedRouteBuilder user(User v)       { this.user      = v; return this; }
        public SavedRouteBuilder routeName(String v) { this.routeName = v; return this; }
        public SavedRouteBuilder fromFloor(Integer v){ this.fromFloor = v; return this; }
        public SavedRouteBuilder toFloor(Integer v)  { this.toFloor   = v; return this; }
        public SavedRouteBuilder icon(String v)      { this.icon      = v; return this; }
        public SavedRouteBuilder color(String v)     { this.color     = v; return this; }

        public SavedRoute build() {
            SavedRoute r = new SavedRoute();
            r.user = this.user; r.routeName = this.routeName; r.fromFloor = this.fromFloor;
            r.toFloor = this.toFloor; r.icon = this.icon; r.color = this.color;
            r.useCount = this.useCount; r.createdAt = LocalDateTime.now();
            return r;
        }
    }
}
