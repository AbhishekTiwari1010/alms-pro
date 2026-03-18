package com.alms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "building")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "employee_id", nullable = false, unique = true)
    private String employeeId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "home_floor", nullable = false)
    private Integer homeFloor = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false)
    private Role userRole = Role.EMPLOYEE;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Role { EMPLOYEE, ADMIN, SUPER_ADMIN }

    public static UserBuilder builder() { return new UserBuilder(); }

    public static class UserBuilder {
        private String employeeId;
        private String email;
        private String passwordHash;
        private String fullName;
        private Integer homeFloor = 1;
        private Building building;
        private Role userRole = Role.EMPLOYEE;
        private Boolean isActive = true;

        public UserBuilder employeeId(String v)  { this.employeeId  = v; return this; }
        public UserBuilder email(String v)        { this.email        = v; return this; }
        public UserBuilder passwordHash(String v) { this.passwordHash = v; return this; }
        public UserBuilder fullName(String v)     { this.fullName     = v; return this; }
        public UserBuilder homeFloor(Integer v)   { this.homeFloor    = v; return this; }
        public UserBuilder building(Building v)   { this.building     = v; return this; }
        public UserBuilder userRole(Role v)       { this.userRole     = v; return this; }
        public UserBuilder isActive(Boolean v)    { this.isActive     = v; return this; }

        public User build() {
            User u = new User();
            u.employeeId  = this.employeeId;
            u.email        = this.email;
            u.passwordHash = this.passwordHash;
            u.fullName     = this.fullName;
            u.homeFloor    = this.homeFloor;
            u.building     = this.building;
            u.userRole     = this.userRole;
            u.isActive     = this.isActive;
            u.createdAt    = LocalDateTime.now();
            return u;
        }
    }
}
