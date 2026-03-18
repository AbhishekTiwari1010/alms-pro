package com.alms.dto;
import com.alms.model.LiftTrip;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class LiftRequestDto {
    @NotNull @Min(1) @Max(200) public Integer fromFloor;
    @NotNull @Min(1) @Max(200) public Integer toFloor;
    @NotNull public LiftTrip.TripType tripType;
    public String routeName;
}
