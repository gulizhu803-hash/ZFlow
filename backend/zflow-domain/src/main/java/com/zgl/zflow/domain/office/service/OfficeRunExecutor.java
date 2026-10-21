package com.zgl.zflow.domain.office.service;

import com.alibaba.fastjson2.JSON;
import com.zgl.zflow.domain.office.gateway.*;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.types.exception.OfficeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeRunExecutor {
    private final OfficeTransactions transactions;
    private final IOfficeRepository repository;
    private final IOfficeGenerationGateway gateway;
    private final IDiagramValidator validator;
    private final IArtifactStorage storage;
    private final OfficeSettings settings;

    /** 兼容无实例标识的调用（测试与单机脚本）。 */
    public void execute(String runId) {
        execute(runId, null);
    }
    public void execute(String runId, String instanceId) {
        OfficeRun claimed=transactions.claim(runId, instanceId);
        if(claimed==null)return;
        try {
            // MySQL JSON normalizes key order and whitespace; compare values, not serialized bytes.
            if(!JSON.parseObject(claimed.getModelSnapshot()).equals(JSON.parseObject(gateway.snapshot()))) {
                transactions.fail(runId,"GENERATION_CONFIG_CHANGED","生成配置已变化，请重新创建任务");return;
            }
            OfficeRequest request=OfficeJson.request(claimed);
            String xml=null,summary=null; DiagramValidationReport report=null;
            boolean fixture="fixture".equals(JSON.parseObject(claimed.getModelSnapshot()).getString("generationMode"));
            int transportRetries=0,repairs=0;
            while(true) {
                OfficeRun live=transactions.checkpoint(runId,"GENERATING"); if(live==null)return;
                if(fixture)xml=gateway.fixture();
                else {
                    int attempt=transactions.startAttempt(runId,repairs==0?"GENERATE":"REPAIR");if(attempt==0)return;
                    live=transactions.checkpoint(runId,null);if(live==null)return;
                    Duration remaining=Duration.between(repository.now(),live.getDeadlineAt());
                    Duration timeout=remaining.compareTo(Duration.ofSeconds(settings.getModelCallTimeoutSeconds()))<0?remaining:Duration.ofSeconds(settings.getModelCallTimeoutSeconds());
                    try {
                        IOfficeGenerationGateway.Generation result=gateway.generate(request,repairs==0?null:xml,summary,timeout);
                        transactions.finishAttempt(runId,attempt,result,null); xml=unwrap(result.xml());
                    } catch(IOfficeGenerationGateway.Failure failure) {
                        transactions.finishAttempt(runId,attempt,null,failure);
                        if(!failure.retryable||transportRetries>=settings.getMaxTransportRetries())throw failure;
                        long backoff=Math.max(failure.retryAfterMillis,(1L<<transportRetries)*500+ThreadLocalRandom.current().nextLong(250));
                        transportRetries++;
                        OfficeRun current=transactions.checkpoint(runId,null);if(current==null)return;
                        if(backoff>=Duration.between(repository.now(),current.getDeadlineAt()).toMillis())throw failure;
                        Thread.sleep(backoff);continue;
                    }
                }
                if(transactions.checkpoint(runId,"VALIDATING")==null)return;
                xml=validator.normalize(xml); report=validator.validate(xml,true);
                if(report.valid())break;
                if(fixture||repairs>=settings.getMaxValidationRepairs())
                    throw new OfficeException(422,"INVALID_DIAGRAM_XML","生成图表未通过结构与资源校验");
                repairs++;summary=json(report);
            }
            if(transactions.checkpoint(runId,"SAVING")==null)return;
            OfficeArtifact artifact=new OfficeArtifact();
            artifact.setArtifactId(id("art"));artifact.setOwnerId(claimed.getOwnerId());artifact.setSessionId(claimed.getSessionId());
            artifact.setArtifactType("DRAWIO");artifact.setTitle(request.title());artifact.setCurrentVersion(1);
            OfficeRevision revision=new OfficeRevision();
            revision.setRevisionId(id("rev"));revision.setArtifactId(artifact.getArtifactId());revision.setVersion(1);revision.setBaseVersion(0);
            revision.setRunId(runId);revision.setOutputKey("diagram");revision.setCreatedBy(claimed.getOwnerId());
            revision.setContentType("application/xml");revision.setValidationReport(json(report));
            IArtifactStorage.StoredFile file=storage.writeImmutable(claimed.getOwnerId(),artifact.getArtifactId(),revision.getRevisionId(),xml.getBytes(StandardCharsets.UTF_8));
            revision.setObjectKey(file.objectKey());revision.setContentHash(file.contentHash());revision.setSizeBytes(file.sizeBytes());
            transactions.publish(runId,artifact,revision);
        } catch(Exception failure) {
            String key=failure instanceof OfficeException office?office.getErrorKey():
                    failure instanceof IOfficeGenerationGateway.Failure model?model.errorKey:"OFFICE_INTERNAL_ERROR";
            // New transaction probes durable state before failing; unavailable DB leaves recovery to sweep/restart.
            boolean interrupted=Thread.interrupted();
            try { transactions.fail(runId,key,"生成未完成，请查看错误类型后重试"); }
            catch(Exception unavailable) {log.warn("office outcome pending traceId={} runId={} errorKey={}",claimed.getTraceId(),runId,key);}
            finally {if(interrupted)Thread.currentThread().interrupt();}
            log.warn("office execution ended traceId={} runId={} errorKey={}",claimed.getTraceId(),runId,key);
        }
    }
    static String unwrap(String text) {
        if(text==null)return "";
        String value=text.startsWith("\uFEFF")?text.substring(1):text;
        value=value.strip();
        if(value.startsWith("```")&&value.endsWith("```")) {
            int newline=value.indexOf('\n');
            if(newline>=0&&java.util.Set.of("```","```xml","```drawio").contains(value.substring(0,newline).strip()))
                value=value.substring(newline+1,value.length()-3).strip();
        }
        return value;
    }
}
