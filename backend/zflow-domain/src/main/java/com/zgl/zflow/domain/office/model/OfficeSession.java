package com.zgl.zflow.domain.office.model;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OfficeSession {
    private Long id;
    private Long ownerId;
    private String sessionId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
