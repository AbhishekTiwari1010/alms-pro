package com.alms.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RouteDto {
    public Long id;
    public String routeName;
    public int fromFloor;
    public int toFloor;
    public String icon;
    public String color;
    public int useCount;
}
