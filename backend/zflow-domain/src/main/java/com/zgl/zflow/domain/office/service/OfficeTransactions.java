package com.zgl.zflow.domain.office.service;

import com.alibaba.fastjson2.*;
import com.zgl.zflow.domain.office.gateway.*;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.types.exception.OfficeException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;

import static com.zgl.zflow.domain.office.model.OfficeJson.*;

/** Short MySQL transactions only; no model, file or SSE IO while holding row locks. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="zflow.office", name="enabled", havingValue="true")
@Transactional(transactionManager="mysqlTransactionManager", rollbackFor=Exception.class)
public class OfficeTransactions {
    private final IOfficeRepository repository;
    private final OfficeSettings settings;
    private final IOfficeGenerationGateway gateway;
    private final org.springframework.context.ApplicationEventPublisher activityPublisher;

    public OfficeSession createSession(long ownerId, String title) {
        title = OfficeRequest.requiredText(title, 200, "title");
        if (repository.lockUser(ownerId) == null) throw OfficeException.notFound();
        OfficeSession s = new OfficeSession();
        s.setSessionId(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(repository.now())
                + "_office_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        s.setOwnerId(ownerId); s.setTitle(title); s.setCreatedAt(repository.now()); s.setUpdatedAt(s.getCreatedAt());
        repository.insertSession(s);
        return s;
    }
    @Transactional(transactionManager="mysqlTransactionManager", propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, rollbackFor=Exception.class)
    public void deleteSession(long ownerId, String sessionId) {
        require(repository.lockSession(ownerId, sessionId));
        if (repository.countSessionReferences(sessionId) > 0)
            throw new OfficeException(409, "OFFICE_SESSION_NOT_EMPTY", "办公会话已有任务或文件，无法删除");
        repository.deleteSession(ownerId, sessionId);
    }
    public Accepted accept(long ownerId, String key, OfficeRequest request) {
        require(repository.lockUser(ownerId));
        OfficeRun previous = repository.findRequest(ownerId, key);
        if (previous != null) return replay(previous, hash(request.canonical()));
        require(repository.lockSession(ownerId, request.sessionId()));
        if (request.retryOfRunId() != null) {
            OfficeRun old = require(repository.run(ownerId, request.retryOfRunId()));
            if (!Set.of("FAILED","CANCELLED").contains(old.getStatus()) ||
                    !request.withoutRetry().canonical().equals(OfficeJson.request(old).withoutRetry().canonical()))
                throw OfficeException.invalid("仅可重试同会话、相同输入的失败或取消任务");
        }
        if (repository.activeRuns(ownerId) >= settings.getMaxActiveRunsPerUser())
            throw new OfficeException(429, "OFFICE_CAPACITY_EXCEEDED", "活动任务已达上限，请稍后重试");
        gateway.checkAvailable();
        OfficeRun r = new OfficeRun();
        r.setRunId(id("run")); r.setOwnerId(ownerId); r.setSessionId(request.sessionId());
        r.setIdempotencyKey(key); r.setRequestHash(hash(request.canonical())); r.setRequestSnapshot(request.canonical());
        r.setRetryOfRunId(request.retryOfRunId()); r.setTraceId(id("trace"));
        r.setStatus("QUEUED"); r.setStage("QUEUED"); r.setLastEventSeq(0L);
        r.setModelSnapshot(gateway.snapshot()); r.setModelAttemptCount(0);
        r.setGenerationReceipt(json(map("schemaVersion",1,"attempts",List.of())));
        r.setCreatedAt(repository.now()); r.setUpdatedAt(r.getCreatedAt());
        repository.insertRun(r);
        event(r, "run.queued", map("status","QUEUED"));
        repository.updateRun(r);
        return new Accepted(r, false);
    }
    public record Accepted(OfficeRun run, boolean replayed) {}
    public static Accepted replay(OfficeRun r, String requestHash) {
        if (!r.getRequestHash().equals(requestHash)) throw reused();
        return new Accepted(r, true);
    }
    public static OfficeException reused() {
        return new OfficeException(409, "IDEMPOTENCY_KEY_REUSED", "幂等键已用于不同的请求");
    }
    public OfficeRun claim(String runId) {
        return claim(runId, null);
    }
    public OfficeRun claim(String runId, String instanceId) {
        OfficeRun r = repository.lockRun(runId);
        if (r == null || !"QUEUED".equals(r.getStatus())) return null;
        LocalDateTime now = repository.now();
        if (!r.getCreatedAt().plusSeconds(settings.getMaxQueueWaitSeconds()).isAfter(now)) {
            terminal(r, "FAILED", "QUEUE_TIMEOUT", "排队等待超时"); return null;
        }
        r.setStatus("RUNNING"); r.setStartedAt(now); r.setDeadlineAt(now.plusSeconds(settings.getRunTimeoutSeconds()));
        r.setOwnerInstance(instanceId); r.setHeartbeatAt(now);
        event(r,"run.started",map("status","RUNNING")); repository.updateRun(r);
        return r;
    }
    /** 实例级心跳：只续自己名下仍在运行的任务。 */
    public void heartbeat(String instanceId) {
        if (instanceId == null) return;
        repository.heartbeat(instanceId);
    }
    /**
     * 回收租约过期的任务。锁内再判断一次心跳，避免“刚续上就被别人回收”的竞态。
     */
    public int recoverExpired(int leaseSeconds) {
        int reclaimed = 0;
        for (String runId : repository.expiredLeases(leaseSeconds)) {
            if (reclaimExpired(runId, leaseSeconds)) reclaimed++;
        }
        return reclaimed;
    }
    private boolean reclaimExpired(String runId, int leaseSeconds) {
        OfficeRun r = repository.lockRun(runId);
        if (r == null || !"RUNNING".equals(r.getStatus())) return false;
        LocalDateTime heartbeat = r.getHeartbeatAt();
        if (heartbeat != null && heartbeat.isAfter(repository.now().minusSeconds(leaseSeconds))) return false;
        terminal(r, "FAILED", "LEASE_EXPIRED", "实例失联超过 " + leaseSeconds + " 秒，任务已被回收");
        return true;
    }
    public OfficeRun checkpoint(String runId, String stage) {
        OfficeRun r = repository.lockRun(runId);
        if (!eligible(r)) return null;
        if (stage != null && !stage.equals(r.getStage())) {
            r.setStage(stage); event(r,"step.started",map("stage",stage)); repository.updateRun(r);
        }
        return r;
    }
    public int startAttempt(String runId, String purpose) {
        OfficeRun r = repository.lockRun(runId);
        if (!eligible(r)) return 0;
        if (r.getModelAttemptCount() >= settings.getMaxModelRequests()) {
            terminal(r,"FAILED","MODEL_REQUEST_BUDGET_EXCEEDED","模型调用次数已达上限"); return 0;
        }
        int count = r.getModelAttemptCount()+1;
        JSONObject receipt = receipt(r);
        receipt.getJSONArray("attempts").add(map("attemptNo",count,"purpose",purpose,
                "startedAt",time(repository.now()),"finishedAt",null,"durationMs",null,"outcome","STARTED",
                "errorKey",null,"providerRequestId",null,"inputTokens",null,"outputTokens",null,"totalTokens",null));
        r.setModelAttemptCount(count); r.setGenerationReceipt(json(receipt)); repository.updateRun(r);
        activityPublisher.publishEvent(new OfficeActivity("model.attempt",r.getStatus(),r.getStage(),null,null));
        return count;
    }
    public void finishAttempt(String runId, int number, IOfficeGenerationGateway.Generation result,
                              IOfficeGenerationGateway.Failure failure) {
        OfficeRun r = repository.lockRun(runId);
        if (!eligible(r)) return;
        JSONObject receipt = receipt(r);
        JSONObject a = receipt.getJSONArray("attempts").getJSONObject(number-1);
        LocalDateTime now = repository.now();
        a.put("finishedAt",time(now));
        a.put("durationMs",Duration.between(java.time.Instant.parse(a.getString("startedAt")),now.toInstant(java.time.ZoneOffset.UTC)).toMillis());
        a.put("outcome",failure == null ? "RESPONDED" : failure.uncertain ? "UNKNOWN" : "FAILED");
        a.put("errorKey",failure == null ? null : failure.errorKey);
        if (result != null) {
            a.put("inputTokens",result.inputTokens()); a.put("outputTokens",result.outputTokens());
            a.put("totalTokens",result.totalTokens()); a.put("providerRequestId",result.providerRequestId());
        }
        r.setGenerationReceipt(json(receipt)); repository.updateRun(r);
    }
    public OfficeRun cancel(long ownerId, String runId) {
        OfficeRun r = require(repository.lockRun(runId));
        if (!Objects.equals(ownerId,r.getOwnerId())) throw OfficeException.notFound();
        if ("QUEUED".equals(r.getStatus())) terminal(r,"CANCELLED",null,null);
        else if ("RUNNING".equals(r.getStatus())) {
            r.setStatus("CANCEL_REQUESTED"); r.setCancelRequestedAt(repository.now());
            event(r,"run.cancel_requested",map("status","CANCEL_REQUESTED")); repository.updateRun(r);
        }
        return r;
    }
    public void fail(String runId, String errorKey, String message) {
        OfficeRun r = repository.lockRun(runId);
        if (!eligible(r)) return;
        // A commit acknowledgement may have been lost. Never overwrite a published output.
        if (repository.runRevisions(r.getOwnerId(),runId).isEmpty())
            terminal(r,"FAILED",errorKey,message);
    }
    public void recover(String runId) {
        OfficeRun r = repository.lockRun(runId);
        if (r == null) return;
        if ("RUNNING".equals(r.getStatus())) terminal(r,"FAILED","PROCESS_RESTARTED","进程重启，原任务已中断");
        else if ("CANCEL_REQUESTED".equals(r.getStatus())) terminal(r,"CANCELLED",null,null);
    }
    public void expire(String runId) {
        OfficeRun r = repository.lockRun(runId);
        if (r == null || r.terminal()) return;
        LocalDateTime now=repository.now();
        if ("QUEUED".equals(r.getStatus()) && !r.getCreatedAt().plusSeconds(settings.getMaxQueueWaitSeconds()).isAfter(now))
            terminal(r,"FAILED","QUEUE_TIMEOUT","排队等待超时");
        else if ("RUNNING".equals(r.getStatus()) && !r.getDeadlineAt().isAfter(now))
            terminal(r,"FAILED","RUN_TIMEOUT","生成运行超时");
        else if ("CANCEL_REQUESTED".equals(r.getStatus()) && !r.getCancelRequestedAt().plusSeconds(settings.getCancelGraceSeconds()).isAfter(now))
            terminal(r,"CANCELLED",null,null);
    }
    public boolean publish(String runId, OfficeArtifact artifact, OfficeRevision revision) {
        OfficeRun r = repository.lockRun(runId);
        if (!eligible(r)) return false;
        LocalDateTime now=repository.now();
        artifact.setCreatedAt(now); artifact.setUpdatedAt(now); revision.setCreatedAt(now);
        repository.insertArtifact(artifact); repository.insertRevision(revision);
        event(r,"artifact.ready",map("artifactId",artifact.getArtifactId(),"version",1,"type","DRAWIO","title",artifact.getTitle()));
        r.setStatus("SUCCEEDED"); r.setStage("COMPLETED"); r.setFinishedAt(now);
        event(r,"run.completed",map("status","SUCCEEDED","artifactIds",List.of(artifact.getArtifactId())));
        repository.updateRun(r);
        return true;
    }
    public Saved save(long ownerId, OfficeRevision revision) {
        OfficeArtifact a = require(repository.lockArtifact(ownerId,revision.getArtifactId()));
        OfficeRevision old = repository.findSave(ownerId,a.getArtifactId(),revision.getSaveRequestKey());
        if (old != null) return replaySave(old,revision.getSaveRequestHash());
        if (!a.getCurrentVersion().equals(revision.getBaseVersion()))
            throw new OfficeException(409,"ARTIFACT_VERSION_CONFLICT","图表已有新版本，你的修改尚未保存",
                    map("artifactId",a.getArtifactId(),"baseVersion",revision.getBaseVersion(),"currentVersion",a.getCurrentVersion()));
        revision.setCreatedAt(repository.now()); repository.insertRevision(revision);
        if (repository.advanceVersion(ownerId,a.getArtifactId(),revision.getBaseVersion(),revision.getVersion()) != 1)
            throw new IllegalStateException("Locked artifact version changed");
        return new Saved(revision,false);
    }
    public record Saved(OfficeRevision revision, boolean replayed) {}
    public static Saved replaySave(OfficeRevision old, String hash) {
        if (!old.getSaveRequestHash().equals(hash)) throw reused();
        return new Saved(old,true);
    }
    private boolean eligible(OfficeRun r) {
        if (r == null || r.terminal() || "QUEUED".equals(r.getStatus())) return false;
        if ("CANCEL_REQUESTED".equals(r.getStatus())) { terminal(r,"CANCELLED",null,null); return false; }
        if (!r.getDeadlineAt().isAfter(repository.now())) { terminal(r,"FAILED","RUN_TIMEOUT","生成运行超时"); return false; }
        return true;
    }
    private void terminal(OfficeRun r, String status, String code, String message) {
        if (r.terminal()) return;
        r.setStatus(status); r.setStage(status); r.setErrorCode(code); r.setErrorMessage(message); r.setFinishedAt(repository.now());
        JSONObject receipt=receipt(r);
        for (Object item:receipt.getJSONArray("attempts")) {
            JSONObject a=(JSONObject)item;
            if ("STARTED".equals(a.getString("outcome"))) { a.put("outcome","UNKNOWN"); a.put("errorKey",code); }
        }
        r.setGenerationReceipt(json(receipt));
        Map<String,Object> payload=map("status",status);
        if ("FAILED".equals(status)) payload.putAll(map("errorKey",code,"message",message,"retryable",true));
        event(r,"FAILED".equals(status)?"run.failed":"run.cancelled",payload); repository.updateRun(r);
    }
    private JSONObject receipt(OfficeRun r) {
        return r.getGenerationReceipt()==null ? JSON.parseObject("{\"schemaVersion\":1,\"attempts\":[]}") : JSON.parseObject(r.getGenerationReceipt());
    }
    private void event(OfficeRun r, String type, Map<String,Object> payload) {
        long seq=r.getLastEventSeq()+1; LocalDateTime now=repository.now();
        payload.putAll(map("runId",r.getRunId(),"seq",seq,"schemaVersion",1,"occurredAt",time(now)));
        OfficeEvent e=new OfficeEvent(); e.setRunId(r.getRunId()); e.setSeq(seq); e.setEventType(type);
        e.setSchemaVersion(1); e.setPayload(json(payload)); e.setCreatedAt(now);
        if (e.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8192) throw new IllegalStateException("Event too large");
        repository.insertEvent(e); r.setLastEventSeq(seq);
        Long elapsed=null;
        if ("run.started".equals(type)) elapsed=Duration.between(r.getCreatedAt(),now).toMillis();
        else if (r.terminal()&&r.getStartedAt()!=null) elapsed=Duration.between(r.getStartedAt(),now).toMillis();
        activityPublisher.publishEvent(new OfficeActivity(type,r.getStatus(),r.getStage(),r.getErrorCode(),elapsed));
    }
    public static <T> T require(T value) { if (value==null) throw OfficeException.notFound(); return value; }
}
