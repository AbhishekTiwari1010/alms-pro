package com.alms.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateRouteRequest {
    @NotBlank public String routeName;
    @NotNull @Min(1) @Max(200) public Integer fromFloor;
    @NotNull @Min(1) @Max(200) public Integer toFloor;
    public String icon = "arrow-up";
    public String color = "#00d4ff";
}
