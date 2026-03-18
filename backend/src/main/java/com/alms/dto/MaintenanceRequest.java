package com.alms.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class MaintenanceRequest {
    @NotBlank public String issue;
    public String notes;
}
