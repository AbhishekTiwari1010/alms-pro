package com.alms.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FloorStat {
    public int floor;
    public long count;
}
