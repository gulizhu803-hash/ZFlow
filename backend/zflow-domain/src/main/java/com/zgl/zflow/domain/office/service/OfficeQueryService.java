package com.zgl.zflow.domain.office.service;

import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static com.zgl.zflow.domain.office.model.OfficeJson.page;
import static com.zgl.zflow.domain.office.service.OfficeTransactions.require;

/** Consistent short read snapshots; HTTP controllers and file IO stay outside transactions. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
@Transactional(transactionManager="mysqlTransactionManager",readOnly=true)
public class OfficeQueryService {
    private final IOfficeRepository repository;
    public record Page<T>(List<T> items,long total){}
    public record ArtifactVersion(OfficeArtifact artifact,OfficeRevision revision){}
    public record RunDetail(OfficeRun run,List<ArtifactVersion> artifacts){}
    public Page<OfficeSession> sessions(long owner,int number,int size) {
        page(number,size);return new Page<>(repository.sessions(owner,(number-1)*size,size),repository.countSessions(owner));
    }
    public Page<RunDetail> runs(long owner,String session,int number,int size) {
        require(repository.session(owner,session));page(number,size);
        return new Page<>(repository.runs(owner,session,(number-1)*size,size).stream().map(this::detail).toList(),repository.countRuns(owner,session));
    }
    public RunDetail run(long owner,String id){return detail(require(repository.run(owner,id)));}
    private RunDetail detail(OfficeRun run) {
        return new RunDetail(run,repository.runRevisions(run.getOwnerId(),run.getRunId()).stream().map(r->
                new ArtifactVersion(require(repository.artifact(run.getOwnerId(),r.getArtifactId())),r)).toList());
    }
    public Page<ArtifactVersion> artifacts(long owner,String session,int number,int size) {
        require(repository.session(owner,session));page(number,size);
        return new Page<>(repository.artifacts(owner,session,(number-1)*size,size).stream().map(this::version).toList(),repository.countArtifacts(owner,session));
    }
    public ArtifactVersion artifact(long owner,String id){return version(require(repository.artifact(owner,id)));}
    private ArtifactVersion version(OfficeArtifact a) {
        return new ArtifactVersion(a,require(repository.revision(a.getOwnerId(),a.getArtifactId(),a.getCurrentVersion())));
    }
    public Page<OfficeRevision> revisions(long owner,String id,int number,int size) {
        require(repository.artifact(owner,id));page(number,size);
        return new Page<>(repository.revisions(owner,id,(number-1)*size,size),repository.countRevisions(owner,id));
    }
}
