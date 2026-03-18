package com.alms.controller;

import com.alms.dto.*;
import com.alms.service.LiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lift")
@RequiredArgsConstructor
public class LiftController {

    private final LiftService svc;

    @PostMapping("/request")
    public ResponseEntity<AssignmentResponse> request(
            @Valid @RequestBody LiftRequestDto req,
            Authentication auth) {
        return ResponseEntity.ok(svc.requestLift(auth.getName(), req));
    }

    @GetMapping("/status")
    public ResponseEntity<List<LiftStatusDto>> status(Authentication auth) {
        return ResponseEntity.ok(svc.getLiftStatuses(auth.getName()));
    }

    @GetMapping("/trips")
    public ResponseEntity<List<TripDto>> myTrips(Authentication auth) {
        return ResponseEntity.ok(svc.getMyTrips(auth.getName()));
    }

    @PostMapping("/routes")
    public ResponseEntity<RouteDto> createRoute(
            @Valid @RequestBody CreateRouteRequest req,
            Authentication auth) {
        return ResponseEntity.ok(svc.createRoute(auth.getName(), req));
    }

    @GetMapping("/routes")
    public ResponseEntity<List<RouteDto>> getRoutes(Authentication auth) {
        return ResponseEntity.ok(svc.getMyRoutes(auth.getName()));
    }

    @DeleteMapping("/routes/{id}")
    public ResponseEntity<Map<String, String>> deleteRoute(
            @PathVariable Long id,
            Authentication auth) {
        svc.deleteRoute(auth.getName(), id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @PostMapping("/maintenance/{liftId}")
    public ResponseEntity<Map<String, String>> reportMaintenance(
            @PathVariable Long liftId,
            @Valid @RequestBody MaintenanceRequest req,
            Authentication auth) {
        svc.reportMaintenance(auth.getName(), liftId, req);
        return ResponseEntity.ok(Map.of("message", "Maintenance reported"));
    }
}
