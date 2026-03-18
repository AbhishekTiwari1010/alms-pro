package com.alms.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AssignmentResponse {
    public int liftNumber;
    public String liftName;
    public String status;
    public Long tripId;
    public Long queueId;
    public int blockStart;
    public int blockEnd;
    public int queuePosition;
    public String message;
}
