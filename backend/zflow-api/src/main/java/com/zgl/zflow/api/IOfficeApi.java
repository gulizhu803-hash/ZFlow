package com.zgl.zflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.zgl.zflow.types.result.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Office M1 contract. JSON bodies are strictly decoded by OfficeRequests. */
public interface IOfficeApi {
    ResponseEntity<Result<?>> createSession(JsonNode body);
    ResponseEntity<Result<?>> sessions(int page,int pageSize);
    ResponseEntity<Result<?>> createRun(JsonNode body,String key);
    ResponseEntity<Result<?>> runs(String sessionId,int page,int pageSize);
    ResponseEntity<Result<?>> run(String runId,String includeReceipt);
    ResponseEntity<SseEmitter> events(String runId,String afterSeq,String lastEventId);
    ResponseEntity<Result<?>> cancel(String runId);
    ResponseEntity<Result<?>> artifacts(String sessionId,int page,int pageSize);
    ResponseEntity<Result<?>> artifact(String artifactId);
    ResponseEntity<Result<?>> revisions(String artifactId,int page,int pageSize);
    ResponseEntity<Result<?>> save(String artifactId,JsonNode body,String key);
    ResponseEntity<byte[]> content(String artifactId,Integer version);
    ResponseEntity<byte[]> download(String artifactId,Integer version);
}
