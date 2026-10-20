package com.zgl.zflow.office;

import com.sun.net.httpserver.HttpServer;
import com.zgl.zflow.domain.office.gateway.IOfficeGenerationGateway;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.infrastructure.office.OfficeGenerationGateway;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OfficeModelGatewayTest {
    HttpServer server;OfficeSettings settings;OfficeGenerationGateway gateway;AtomicInteger requests;
    volatile int status=200;
    volatile String response;
    volatile long bodyDelay;
    @BeforeEach void start()throws Exception {
        requests=new AtomicInteger();settings=new OfficeSettings();settings.setGenerationMode("model");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            requests.incrementAndGet();exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type","application/json");
            byte[] body=response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,body.length);
            try{Thread.sleep(bodyDelay);exchange.getResponseBody().write(body);}catch(Exception ignored){}finally{exchange.close();}
        });server.start();
        gateway=new OfficeGenerationGateway(settings,()->new OfficeGenerationGateway.ModelConfig("http://127.0.0.1:"+server.getAddress().getPort(),"test-key","test-model"));
        response=OfficeJson.json(OfficeJson.map("id","test-response","object","chat.completion","model","test-model","choices",
                List.of(OfficeJson.map("index",0,"message",OfficeJson.map("role","assistant","content",gateway.fixture()),"finish_reason","stop"))));
    }
    @AfterEach void stop(){server.stop(0);}
    OfficeRequest request(){return new OfficeRequest("s","订单架构",List.of("DRAWIO"),"ARCHITECTURE","订单图",null);}
    @Test void missingUsageRemainsUnknownAndNoHiddenRetries() {
        var result=gateway.generate(request(),null,null,Duration.ofSeconds(5));
        assertNotNull(result.xml());assertNull(result.inputTokens());assertNull(result.outputTokens());assertNull(result.totalTokens());assertEquals(1,requests.get());
    }
    @Test void preservesOnlyReturnedUsageFields() {
        var json=com.alibaba.fastjson2.JSON.parseObject(response);json.put("usage",OfficeJson.map("prompt_tokens",13,"completion_tokens",21));response=json.toJSONString();
        var result=gateway.generate(request(),null,null,Duration.ofSeconds(5));
        assertEquals(13L,result.inputTokens());assertEquals(21L,result.outputTokens());assertNull(result.totalTokens());
    }
    @Test void nonRetryableAuthAndRetryable429EachMakeOneRequest() {
        status=401;response="{\"error\":{\"message\":\"secret provider message\"}}";
        var auth=assertThrows(IOfficeGenerationGateway.Failure.class,()->gateway.generate(request(),null,null,Duration.ofSeconds(5)));
        assertFalse(auth.retryable);assertEquals(1,requests.get());assertFalse(auth.getMessage().contains("secret"));
        status=429;
        var limited=assertThrows(IOfficeGenerationGateway.Failure.class,()->gateway.generate(request(),null,null,Duration.ofSeconds(5)));
        assertTrue(limited.retryable);assertEquals(2,requests.get());
    }
    @Test void slowResponseBodyRespectsWholeCallTimeout() {
        bodyDelay=2000;long start=System.nanoTime();
        var timeout=assertThrows(IOfficeGenerationGateway.Failure.class,()->gateway.generate(request(),null,null,Duration.ofMillis(250)));
        assertTrue(timeout.uncertain);assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<1500);
    }
    @Test void oversizedProviderResponseIsBounded() {
        settings.setMaxModelResponseBytes(100);
        var failure=assertThrows(IOfficeGenerationGateway.Failure.class,()->gateway.generate(request(),null,null,Duration.ofSeconds(5)));
        assertEquals("MODEL_RESPONSE_TOO_LARGE",failure.errorKey);assertFalse(failure.retryable);
    }
}
