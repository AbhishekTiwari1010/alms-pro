package com.alms.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class CompleteTripRequest {
    @NotNull public Long tripId;
    @NotNull public Integer liftNumber;
}
