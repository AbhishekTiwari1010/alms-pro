package com.alms.dto;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
public class UserDto {
    public Long id;
    public String employeeId;
    public String email;
    public String fullName;
    public Integer homeFloor;
    public String userRole;
    public Boolean isActive;
    public LocalDateTime createdAt;
    public LocalDateTime lastLogin;
}
