package com.alms.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank public String fullName;
    @Email @NotBlank public String email;
    @NotBlank public String employeeId;
    @NotNull @Min(1) @Max(200) public Integer homeFloor;
    @NotBlank @Size(min = 6) public String password;
    public Long buildingId;
}
