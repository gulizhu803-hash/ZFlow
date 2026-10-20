package com.zgl.zflow.domain.office.model;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OfficeEvent {
    private Long id;
    private Long seq;
    private String runId;
    private String eventType;
    private String payload;
    private Integer schemaVersion;
    private LocalDateTime createdAt;

}
