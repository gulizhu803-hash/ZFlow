package com.zgl.zflow.trigger.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zgl.zflow.api.dto.office.OfficeRequests;
import com.zgl.zflow.api.vo.office.OfficeViews;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.domain.office.service.*;
import com.zgl.zflow.domain.util.jwt.UserContext;
import com.zgl.zflow.trigger.service.*;
import com.zgl.zflow.types.exception.OfficeException;
import com.zgl.zflow.types.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.zgl.zflow.domain.office.service.OfficeTransactions.require;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

@RestController
@RequestMapping("/office")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeController implements com.zgl.zflow.api.IOfficeApi {
    private final UserContext userContext;
    private final OfficeSessionService sessions;
    private final OfficeRunService runs;
    private final OfficeQueryService queries;
    private final OfficeTransactions transactions;
    private final ArtifactService artifacts;
    private final IOfficeRepository repository;
    private final OfficeEventStreamService events;
    private final OfficeWorkerControl workers;

    @PostMapping("/sessions")
    public ResponseEntity<Result<?>> createSession(@RequestBody JsonNode body) {
        return ok(201,OfficeViews.session(sessions.create(owner(),OfficeRequests.session(body).title())));
    }
    @GetMapping("/sessions")
    public ResponseEntity<Result<?>> sessions(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        var result=queries.sessions(owner(),page,pageSize);
        return ok(200,new OfficeViews.Page<>(result.items().stream().map(OfficeViews::session).toList(),page,pageSize,result.total()));
    }
    @PostMapping("/runs")
    public ResponseEntity<Result<?>> createRun(@RequestBody JsonNode body,@RequestHeader("Idempotency-Key") String key) {
        var accepted=runs.create(owner(),key,OfficeRequests.run(body));
        return ok(accepted.replayed()?200:202,OfficeViews.accepted(accepted.run(),accepted.replayed()));
    }
    @GetMapping("/runs")
    public ResponseEntity<Result<?>> runs(@RequestParam String sessionId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        var result=queries.runs(owner(),sessionId,page,pageSize);
        return ok(200,new OfficeViews.Page<>(result.items().stream().map(r->runView(r,false,false)).toList(),page,pageSize,result.total()));
    }
    @GetMapping("/runs/{runId}")
    public ResponseEntity<Result<?>> run(@PathVariable String runId,@RequestParam(defaultValue="false") String includeReceipt) {
        var result=queries.run(owner(),runId);
        if(!Set.of("true","false").contains(includeReceipt))throw OfficeException.invalid("includeReceipt 必须为 true 或 false");
        return ok(200,runView(result,true,Boolean.parseBoolean(includeReceipt)));
    }
    @GetMapping(value="/runs/{runId}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable String runId,@RequestParam(required=false) String afterSeq,
            @RequestHeader(value="Last-Event-ID",required=false) String lastEventId) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Accel-Buffering","no")
                .body(events.subscribe(owner(),runId,afterSeq!=null?afterSeq:lastEventId));
    }
    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<Result<?>> cancel(@PathVariable String runId) {
        OfficeRun r=transactions.cancel(owner(),runId);
        if("CANCEL_REQUESTED".equals(r.getStatus()))workers.interrupt(runId);
        return ok("CANCEL_REQUESTED".equals(r.getStatus())?202:200,OfficeViews.cancel(r));
    }
    @GetMapping("/artifacts")
    public ResponseEntity<Result<?>> artifacts(@RequestParam String sessionId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        var result=queries.artifacts(owner(),sessionId,page,pageSize);
        return ok(200,new OfficeViews.Page<>(result.items().stream().map(a->OfficeViews.artifact(a.artifact(),a.revision())).toList(),page,pageSize,result.total()));
    }
    @GetMapping("/artifacts/{artifactId}")
    public ResponseEntity<Result<?>> artifact(@PathVariable String artifactId) {
        var result=queries.artifact(owner(),artifactId);
        return ok(200,OfficeViews.artifact(result.artifact(),result.revision()));
    }
    @GetMapping("/artifacts/{artifactId}/revisions")
    public ResponseEntity<Result<?>> revisions(@PathVariable String artifactId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        var result=queries.revisions(owner(),artifactId,page,pageSize);
        return ok(200,new OfficeViews.Page<>(result.items().stream().map(OfficeViews::revision).toList(),page,pageSize,result.total()));
    }
    @PostMapping("/artifacts/{artifactId}/revisions")
    public ResponseEntity<Result<?>> save(@PathVariable String artifactId,@RequestBody JsonNode body,@RequestHeader("Idempotency-Key") String key) {
        long owner=owner();require(repository.artifact(owner,artifactId));
        var request=OfficeRequests.save(body);
        var saved=artifacts.save(owner,artifactId,request.baseVersion(),request.xml(),key);
        return ok(saved.replayed()?200:201,OfficeViews.saved(saved.revision(),saved.replayed()));
    }
    @GetMapping("/artifacts/{artifactId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String artifactId,@RequestParam(required=false) Integer version) {
        return file(artifacts.content(owner(),artifactId,version),false);
    }
    @GetMapping("/artifacts/{artifactId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String artifactId,@RequestParam(required=false) Integer version) {
        return file(artifacts.content(owner(),artifactId,version),true);
    }
    private ResponseEntity<byte[]> file(ArtifactService.Content file,boolean attachment) {
        var builder=ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).contentLength(file.bytes().length)
                .header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff");
        if(attachment) {
            String name=file.artifact().getTitle().replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]","_");
            builder.header("Content-Disposition",ContentDisposition.attachment().filename(name+".drawio",StandardCharsets.UTF_8).build().toString());
        }
        return builder.body(file.bytes());
    }
    private Map<String,Object> runView(OfficeQueryService.RunDetail result,boolean detail,boolean receipt) {
        var outputs=result.artifacts().stream().map(a->map("artifactId",a.artifact().getArtifactId(),"type",a.artifact().getArtifactType(),
                "title",a.artifact().getTitle(),"version",a.revision().getVersion())).toList();
        return OfficeViews.run(result.run(),outputs,detail,receipt);
    }
    private long owner() {
        Long id=userContext.getUserId();if(id==null)throw new OfficeException(401,"UNAUTHORIZED","请先登录");return id;
    }
    private ResponseEntity<Result<?>> ok(int status,Object data) {
        return ResponseEntity.status(status).header("Cache-Control","private, no-store").body(Result.success(data));
    }
}
