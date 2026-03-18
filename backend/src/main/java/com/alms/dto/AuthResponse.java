package com.alms.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    public String token;
    public String employeeId;
    public String email;
    public String fullName;
    public Integer homeFloor;
    public String role;
    public Long buildingId;
    public String buildingName;
}
