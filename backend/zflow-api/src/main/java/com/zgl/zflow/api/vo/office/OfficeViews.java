package com.zgl.zflow.api.vo.office;

import com.alibaba.fastjson2.*;
import com.zgl.zflow.domain.office.model.*;
import java.time.*;
import java.util.*;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

/** Explicit HTTP projections: never expose object keys, credentials or internal snapshots. */
public final class OfficeViews {
    public static final String BASE="/miniagent/api/v1/office";
    private OfficeViews(){}
    public record Page<T>(List<T> items,int page,int pageSize,long total){}
    public static Map<String,Object> session(OfficeSession s) {
        return map("sessionId",s.getSessionId(),"title",s.getTitle(),"type","office","createdAt",time(s.getCreatedAt()),"updatedAt",time(s.getUpdatedAt()));
    }
    public static Map<String,Object> revision(OfficeRevision r) {
        String url=BASE+"/artifacts/"+r.getArtifactId();
        return map("revisionId",r.getRevisionId(),"version",r.getVersion(),"baseVersion",r.getBaseVersion(),
                "origin",r.getRunId()==null?"MANUAL":"GENERATED","runId",r.getRunId(),"contentHash",r.getContentHash(),
                "sizeBytes",r.getSizeBytes(),"contentType",r.getContentType(),"createdAt",time(r.getCreatedAt()),
                "validationReport",JSON.parse(r.getValidationReport()),"contentUrl",url+"/content?version="+r.getVersion(),
                "downloadUrl",url+"/download?version="+r.getVersion());
    }
    public static Map<String,Object> artifact(OfficeArtifact a,OfficeRevision r) {
        return map("artifactId",a.getArtifactId(),"sessionId",a.getSessionId(),"type",a.getArtifactType(),"title",a.getTitle(),
                "currentVersion",a.getCurrentVersion(),"revision",revision(r),"createdAt",time(a.getCreatedAt()),"updatedAt",time(a.getUpdatedAt()));
    }
    public static Map<String,Object> accepted(OfficeRun r,boolean replayed) {
        return map("runId",r.getRunId(),"status",r.getStatus(),"replayed",replayed,"statusUrl",BASE+"/runs/"+r.getRunId(),
                "eventsUrl",BASE+"/runs/"+r.getRunId()+"/events?afterSeq=0");
    }
    public static Map<String,Object> cancel(OfficeRun r) {
        return map("runId",r.getRunId(),"status",r.getStatus(),"cancelRequestedAt",time(r.getCancelRequestedAt()),"finishedAt",time(r.getFinishedAt()));
    }
    public static Map<String,Object> saved(OfficeRevision r,boolean replayed) {
        return map("artifactId",r.getArtifactId(),"revisionId",r.getRevisionId(),"version",r.getVersion(),"contentHash",r.getContentHash(),"replayed",replayed);
    }
    public static Map<String,Object> run(OfficeRun r,List<Map<String,Object>> artifacts,boolean detail,boolean includeReceipt) {
        JSONObject snapshot=JSON.parseObject(r.getModelSnapshot()); OfficeRequest request=OfficeJson.request(r);
        Map<String,Object> result=map("runId",r.getRunId(),"sessionId",r.getSessionId(),"title",request.title(),
                "generationMode",snapshot.getString("generationMode"),"status",r.getStatus(),"stage",r.getStage(),
                "createdAt",time(r.getCreatedAt()),"startedAt",time(r.getStartedAt()),"finishedAt",time(r.getFinishedAt()),
                "error",r.getErrorCode()==null?null:map("errorKey",r.getErrorCode(),"message",r.getErrorMessage(),"retryable",true,"traceId",r.getTraceId()),
                "artifacts",artifacts);
        if(detail)result.putAll(map("lastEventSeq",r.getLastEventSeq(),"deadlineAt",time(r.getDeadlineAt()),
                "cancelRequestedAt",time(r.getCancelRequestedAt()),"retryOfRunId",r.getRetryOfRunId(),
                "request",map("prompt",request.prompt(),"artifactTypes",request.artifactTypes(),"diagramType",request.diagramType(),"title",request.title())));
        if(includeReceipt)result.put("generationReceipt",receipt(r,snapshot));
        return result;
    }
    private static Map<String,Object> receipt(OfficeRun r,JSONObject snapshot) {
        List<Map<String,Object>> attempts=new ArrayList<>();
        JSONArray raw=r.getGenerationReceipt()==null?new JSONArray():JSON.parseObject(r.getGenerationReceipt()).getJSONArray("attempts");
        boolean complete=r.terminal()&&r.getModelAttemptCount()>0&&"model".equals(snapshot.getString("generationMode"));
        Map<String,Object> usage=map("inputTokens",null,"outputTokens",null,"totalTokens",null);
        for(Object item:raw) {
            JSONObject a=(JSONObject)item;Map<String,Object> visible=new LinkedHashMap<>();
            for(String field:List.of("attemptNo","purpose","startedAt","finishedAt","durationMs","outcome","errorKey","inputTokens","outputTokens","totalTokens"))visible.put(field,a.get(field));
            for(String field:usage.keySet()) {
                Long value=a.getLong(field);if(value==null)complete=false;
                else usage.put(field,(usage.get(field)==null?0:((Number)usage.get(field)).longValue())+value);
            }
            attempts.add(visible);
        }
        Long duration=r.getStartedAt()==null||r.getFinishedAt()==null?null:Duration.between(r.getStartedAt(),r.getFinishedAt()).toMillis();
        return map("schemaVersion",1,"generationMode",snapshot.getString("generationMode"),"model",snapshot.getString("model"),
                "promptVersion",snapshot.getString("promptVersion"),"generatorVersion",snapshot.getString("generatorVersion"),
                "attemptCount",r.getModelAttemptCount(),"attempts",attempts,"durationMs",duration,"knownUsage",usage,"usageComplete",complete);
    }
}
