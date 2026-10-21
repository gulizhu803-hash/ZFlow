package com.zgl.zflow.domain.office.model;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OfficeRun {
    private Long id;
    private Long ownerId;
    private Long lastEventSeq;
    private String runId;
    private String sessionId;
    private String idempotencyKey;
    private String requestHash;
    private String requestSnapshot;
    private String retryOfRunId;
    private String status;
    private String stage;
    private String traceId;
    private String modelSnapshot;
    private String generationReceipt;
    private String errorCode;
    private String errorMessage;
    private Integer modelAttemptCount;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime cancelRequestedAt;
    /** 当前执行该任务的实例标识；只有持有租约的实例才会续心跳。 */
    private String ownerInstance;
    /** 最近一次心跳时间；超过租约时长即视为实例已失联。 */
    private LocalDateTime heartbeatAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public boolean terminal() { return OfficeStatus.valueOf(status).terminal(); }

}
