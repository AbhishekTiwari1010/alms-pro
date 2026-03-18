package com.alms.dto;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TripDto {
    public Long id;
    public String employeeId;
    public String fullName;
    public int liftNumber;
    public int fromFloor;
    public int toFloor;
    public String tripType;
    public String tripStatus;
    public String priority;
    public String routeName;
    public Integer waitSeconds;
    public Integer travelSeconds;
    public LocalDateTime requestedAt;
    public LocalDateTime completedAt;
}
