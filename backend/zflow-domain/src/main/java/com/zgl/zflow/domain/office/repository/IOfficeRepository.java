package com.zgl.zflow.domain.office.repository;

import com.zgl.zflow.domain.office.model.*;
import java.time.LocalDateTime;
import java.util.List;

/** MySQL operations. Lock methods are only used inside OfficeTransactions. */
public interface IOfficeRepository {
    LocalDateTime now();
    Long lockUser(long ownerId);
    OfficeSession session(long ownerId, String sessionId);
    OfficeSession lockSession(long ownerId, String sessionId);
    void insertSession(OfficeSession session);
    int deleteSession(long ownerId, String sessionId);
    List<OfficeSession> sessions(long ownerId, int offset, int limit);
    long countSessions(long ownerId);
    long countSessionReferences(String sessionId);
    OfficeRun run(long ownerId, String runId);
    OfficeRun findRequest(long ownerId, String key);
    OfficeRun lockRun(String runId);
    List<OfficeRun> runs(long ownerId, String sessionId, int offset, int limit);
    long countRuns(long ownerId, String sessionId);
    long activeRuns(long ownerId);
    void insertRun(OfficeRun run);
    int updateRun(OfficeRun run);
    List<String> queued(int limit);
    List<String> unfinished();
    List<String> expired(int queueWait, int cancelGrace);
    void insertEvent(OfficeEvent event);
    List<OfficeEvent> events(long ownerId, String runId, long afterSeq, long waterMark);
    OfficeArtifact artifact(long ownerId, String artifactId);
    OfficeArtifact lockArtifact(long ownerId, String artifactId);
    void insertArtifact(OfficeArtifact artifact);
    int advanceVersion(long ownerId, String artifactId, int baseVersion, int version);
    List<OfficeArtifact> artifacts(long ownerId, String sessionId, int offset, int limit);
    long countArtifacts(long ownerId, String sessionId);
    OfficeRevision revision(long ownerId, String artifactId, int version);
    OfficeRevision findSave(long ownerId, String artifactId, String key);
    List<OfficeRevision> revisions(long ownerId, String artifactId, int offset, int limit);
    long countRevisions(long ownerId, String artifactId);
    List<OfficeRevision> runRevisions(long ownerId, String runId);
    void insertRevision(OfficeRevision revision);
    List<String> objectKeys();
}
