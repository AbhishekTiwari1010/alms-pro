package com.alms.service;

import com.alms.dto.*;
import com.alms.model.*;
import com.alms.repository.*;
import com.alms.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository     userRepo;
    private final BuildingRepository buildingRepo;
    private final PasswordEncoder    encoder;
    private final JwtUtil            jwt;
    private final AuthenticationManager authManager;

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email))       throw new RuntimeException("Email already in use");
        if (userRepo.existsByEmployeeId(req.employeeId)) throw new RuntimeException("Employee ID already exists");

        Long bid = req.buildingId != null ? req.buildingId : 1L;
        Building building = buildingRepo.findById(bid).orElseThrow(() -> new RuntimeException("Building not found"));

        User u = User.builder()
                .email(req.email).employeeId(req.employeeId).fullName(req.fullName)
                .homeFloor(req.homeFloor).passwordHash(encoder.encode(req.password))
                .building(building).userRole(User.Role.EMPLOYEE).build();
        userRepo.save(u);
        return buildResponse(u, building);
    }

    public AuthResponse login(LoginRequest req) {
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email, req.password));
        User u = userRepo.findByEmail(req.email).orElseThrow();
        u.setLastLogin(LocalDateTime.now());
        userRepo.save(u);
        return buildResponse(u, u.getBuilding());
    }

    private AuthResponse buildResponse(User u, Building b) {
        UserDetails ud = org.springframework.security.core.userdetails.User.builder()
                .username(u.getEmail()).password(u.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getUserRole().name())))
                .build();
        return AuthResponse.builder()
                .token(jwt.generate(ud)).employeeId(u.getEmployeeId()).email(u.getEmail())
                .fullName(u.getFullName()).homeFloor(u.getHomeFloor())
                .role(u.getUserRole().name()).buildingId(b.getId()).buildingName(b.getName())
                .build();
    }
}
