package com.alms.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LiftStatusDto {
    public Long    id;
    public int     liftNumber;
    public String  liftName;
    public int     currentFloor;
    public String  liftStatus;
    public int     capacity;
    public int     currentLoad;
    public Integer assignedBlockStart;
    public Integer assignedBlockEnd;
    public int     activeTripCount;
    public long    totalTrips;
    public String  phase;          // IDLE, WAITING, TRAVELING, HALTING
    public long    waitRemaining;  // seconds left in accumulation window
}
