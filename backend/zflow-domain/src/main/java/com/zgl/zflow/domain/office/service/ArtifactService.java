package com.zgl.zflow.domain.office.service;

import com.zgl.zflow.domain.office.gateway.*;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.types.exception.OfficeException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import java.nio.charset.StandardCharsets;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;
import static com.zgl.zflow.domain.office.service.OfficeTransactions.require;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class ArtifactService {
    private final IOfficeRepository repository;
    private final OfficeTransactions transactions;
    private final IDiagramValidator validator;
    private final IArtifactStorage storage;

    public OfficeTransactions.Saved save(long ownerId,String artifactId,int baseVersion,String xml,String key) {
        require(repository.artifact(ownerId,artifactId)); key(key);
        if(baseVersion<1 || baseVersion==Integer.MAX_VALUE) throw OfficeException.invalid("baseVersion 不合法");
        String normalized=validator.normalize(xml);
        String requestHash=hash(json(map("artifactId",artifactId,"baseVersion",baseVersion,"contentHash",hash(normalized))));
        OfficeRevision old;
        try { old=repository.findSave(ownerId,artifactId,key); }
        catch(RuntimeException failure) {
            if(OfficeRunService.isDatabaseFailure(failure)) throw OfficeRunService.unavailable();
            throw failure;
        }
        if(old!=null) return OfficeTransactions.replaySave(old,requestHash);
        DiagramValidationReport report=validator.validate(normalized,false);
        if(!report.valid()) throw new OfficeException(422,"INVALID_DIAGRAM_XML","图表校验失败",map("validationReport",report));
        OfficeRevision revision=new OfficeRevision(); revision.setRevisionId(id("rev")); revision.setArtifactId(artifactId);
        revision.setBaseVersion(baseVersion); revision.setVersion(baseVersion+1); revision.setSaveRequestKey(key);
        revision.setSaveRequestHash(requestHash); revision.setOutputKey("diagram"); revision.setCreatedBy(ownerId);
        revision.setValidationReport(json(report)); revision.setContentType("application/xml");
        IArtifactStorage.StoredFile file=storage.writeImmutable(ownerId,artifactId,revision.getRevisionId(),normalized.getBytes(StandardCharsets.UTF_8));
        revision.setObjectKey(file.objectKey()); revision.setContentHash(file.contentHash()); revision.setSizeBytes(file.sizeBytes());
        try { return transactions.save(ownerId,revision); }
        catch(RuntimeException failure) {
            if(!OfficeRunService.isDatabaseFailure(failure)) throw failure;
            try {
                OfficeRevision committed=repository.findSave(ownerId,artifactId,key);
                if(committed!=null) return OfficeTransactions.replaySave(committed,requestHash);
            } catch(RuntimeException unavailableFailure) {
                if(!OfficeRunService.isDatabaseFailure(unavailableFailure)) throw unavailableFailure;
                throw OfficeRunService.unknownCommit();
            }
            throw failure;
        }
    }
    public record Content(OfficeArtifact artifact,OfficeRevision revision,byte[] bytes) {}
    public Content content(long ownerId,String artifactId,Integer version) {
        OfficeArtifact a=require(repository.artifact(ownerId,artifactId));
        int selected=version==null?a.getCurrentVersion():version;
        if(selected<1) throw OfficeException.invalid("version 不合法");
        OfficeRevision r=require(repository.revision(ownerId,artifactId,selected));
        return new Content(a,r,storage.readVerified(r.getObjectKey(),r.getContentHash(),r.getSizeBytes()));
    }
}
