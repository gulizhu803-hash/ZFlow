package com.zgl.zflow.domain.office.model;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OfficeRevision {
    private Long id;
    private Long createdBy;
    private Long sizeBytes;
    private String revisionId;
    private String artifactId;
    private String saveRequestKey;
    private String saveRequestHash;
    private String runId;
    private String outputKey;
    private String objectKey;
    private String contentHash;
    private String contentType;
    private String structuredContent;
    private String sourceSnapshot;
    private String validationReport;
    private Integer version;
    private Integer baseVersion;
    private LocalDateTime createdAt;

}
