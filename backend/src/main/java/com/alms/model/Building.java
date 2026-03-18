package com.alms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "buildings")
@Getter @Setter @NoArgsConstructor
public class Building {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) private String  code;
    @Column(nullable = false)                private String  name;
    @Column(name = "total_floors", nullable = false) private Integer totalFloors = 60;
    @Column(name = "total_lifts",  nullable = false) private Integer totalLifts  = 6;
    @Column(name = "is_active",    nullable = false) private Boolean isActive    = true;
    @Column(name = "created_at",   nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
