package com.zgl.zflow.domain.office.model;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OfficeArtifact {
    private Long id;
    private Long ownerId;
    private String artifactId;
    private String sessionId;
    private String artifactType;
    private String title;
    private Integer currentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
