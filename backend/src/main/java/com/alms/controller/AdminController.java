package com.alms.controller;

import com.alms.dto.DashboardStats;
import com.alms.dto.LiftStatusDto;
import com.alms.dto.TripDto;
import com.alms.service.LiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminController {

    private final LiftService svc;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStats> dashboard(Authentication auth) {
        return ResponseEntity.ok(svc.getDashboardStats(auth.getName()));
    }

    @GetMapping("/trips")
    public ResponseEntity<List<TripDto>> allTrips(Authentication auth) {
        return ResponseEntity.ok(svc.getAllTrips(auth.getName()));
    }

    @GetMapping("/trips/employee/{employeeId}")
    public ResponseEntity<List<TripDto>> byEmployee(
            @PathVariable String employeeId,
            Authentication auth) {
        return ResponseEntity.ok(svc.getTripsByEmployee(auth.getName(), employeeId));
    }

    @GetMapping("/lifts")
    public ResponseEntity<List<LiftStatusDto>> lifts(Authentication auth) {
        return ResponseEntity.ok(svc.getLiftStatuses(auth.getName()));
    }

    @PatchMapping("/lifts/{buildingId}/{liftNumber}/maintenance")
    public ResponseEntity<Map<String, String>> maintenance(
            @PathVariable Long buildingId,
            @PathVariable int liftNumber,
            @RequestParam boolean active,
            Authentication auth) {
        svc.setLiftMaintenance(auth.getName(), buildingId, liftNumber, active);
        return ResponseEntity.ok(Map.of("message", active ? "Lift set to MAINTENANCE" : "Lift set to IDLE"));
    }
}
