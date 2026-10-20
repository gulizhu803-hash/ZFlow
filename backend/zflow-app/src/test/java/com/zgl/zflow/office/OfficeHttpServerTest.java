package com.zgl.zflow.office;

import com.alibaba.fastjson2.JSON;
import com.zgl.zflow.config.office.OfficeQueueDispatcher;
import com.zgl.zflow.domain.office.gateway.IArtifactStorage;
import com.zgl.zflow.domain.office.model.OfficeSettings;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.domain.office.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.*;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.DispatcherServlet;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

@EnabledIfEnvironmentVariable(named="ZFLOW_OFFICE_TEST_JDBC_URL",matches=".+")
class OfficeHttpServerTest {
    @Test void realHttpServerAutomaticallyGeneratesSavesReplaysStreamsAndDownloads()throws Exception {
        try(Connection connection=DriverManager.getConnection(System.getenv("ZFLOW_OFFICE_TEST_JDBC_URL"),
                System.getenv().getOrDefault("ZFLOW_OFFICE_TEST_USER","root"),System.getenv("ZFLOW_OFFICE_TEST_PASSWORD"))) {
            assertEquals("zflow_office_test",connection.getCatalog());
            try(var s=connection.createStatement()){
                for(String table:List.of("ai_office_run_event","ai_artifact_revision","ai_artifact","ai_office_run"))s.execute("DROP TABLE IF EXISTS "+table);
            }
            String baseline=Files.readString(Path.of("../docs/mysql/01-schema.sql"))
                    .replaceAll("(?s)CREATE DATABASE IF NOT EXISTS `ai-agent`.*?;","").replace("USE `ai-agent`;","");
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource(baseline.getBytes(StandardCharsets.UTF_8)));
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("../docs/mysql/03-office-artifacts.sql"));
            try(var s=connection.createStatement()){
                s.execute("DELETE FROM ai_session");s.execute("DELETE FROM ai_user");
                s.execute("INSERT INTO ai_user(id,user_name,password,user_role) VALUES (1,'http-owner','test','account')");
            }
        }
        Path root=Files.createTempDirectory("zflow-office-http-");
        try(var context=new AnnotationConfigServletWebServerApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("office-http",Map.of(
                    "zflow.office.enabled","true","zflow.office.generation-mode","fixture","zflow.office.storage-root",root.toString(),
                    "zflow.office.dispatch-interval-ms","100")));
            context.register(OfficeMySqlTest.Config.class,Server.class);context.refresh();
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(Server.class),new String[0],context,Duration.ZERO));
            String base="http://127.0.0.1:"+context.getWebServer().getPort()+"/miniagent/api/v1";
            HttpClient client=HttpClient.newHttpClient();
            var session=send(client,base,"POST","/office/sessions",map("title","HTTP订单图"),null);
            assertEquals(201,session.statusCode(),session.body());
            String sessionId=JSON.parseObject(session.body()).getJSONObject("data").getString("sessionId");
            var request=map("sessionId",sessionId,"prompt","订单系统架构","artifactTypes",List.of("DRAWIO"),"diagramType","ARCHITECTURE","title","订单系统");
            var accepted=send(client,base,"POST","/office/runs",request,"http-live-run");
            assertEquals(202,accepted.statusCode(),accepted.body());
            String runId=JSON.parseObject(accepted.body()).getJSONObject("data").getString("runId");
            String artifactId=null;
            for(int i=0;i<100;i++) {
                var status=send(client,base,"GET","/office/runs/"+runId,null,null);
                var data=JSON.parseObject(status.body()).getJSONObject("data");
                assertNotEquals("FAILED",data.getString("status"),status.body());
                if("SUCCEEDED".equals(data.getString("status"))){artifactId=data.getJSONArray("artifacts").getJSONObject(0).getString("artifactId");break;}
                Thread.sleep(100);
            }
            assertNotNull(artifactId,"Queue dispatcher must finish without direct test calls to the executor");
            var replay=send(client,base,"POST","/office/runs",request,"http-live-run");assertEquals(200,replay.statusCode());
            assertEquals(runId,JSON.parseObject(replay.body()).getJSONObject("data").getString("runId"));
            var stream=send(client,base,"GET","/office/runs/"+runId+"/events?afterSeq=0",null,null);
            assertEquals(200,stream.statusCode());assertTrue(stream.body().contains("event:artifact.ready"));assertTrue(stream.body().contains("event:run.completed"));
            var content=send(client,base,"GET","/office/artifacts/"+artifactId+"/content?version=1",null,null);
            assertEquals(200,content.statusCode());assertTrue(content.body().contains("订单服务"));
            var save=send(client,base,"POST","/office/artifacts/"+artifactId+"/revisions",map("baseVersion",1,"xml",content.body().replace("订单服务","订单服务 HTTP 编辑")),"http-live-save");
            assertEquals(201,save.statusCode(),save.body());
            var download=send(client,base,"GET","/office/artifacts/"+artifactId+"/download?version=1",null,null);
            assertEquals(content.body(),download.body());assertTrue(download.headers().firstValue("Content-Disposition").orElse("").contains("filename*="));
            assertEquals("private, no-store",download.headers().firstValue("Cache-Control").orElse(""));
            Path evidence=Path.of("target/office-verification");Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("order-v1.drawio"),content.body(),StandardCharsets.UTF_8);
            Files.writeString(evidence.resolve("order-v2.drawio"),
                    send(client,base,"GET","/office/artifacts/"+artifactId+"/content?version=2",null,null).body(),StandardCharsets.UTF_8);
            Files.writeString(evidence.resolve("events.txt"),stream.body(),StandardCharsets.UTF_8);
            Files.writeString(evidence.resolve("run.json"),
                    send(client,base,"GET","/office/runs/"+runId+"?includeReceipt=true",null,null).body(),StandardCharsets.UTF_8);
        }
    }
    private HttpResponse<String> send(HttpClient client,String base,String method,String path,Object body,String key)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer 1");
        if(key!=null)request.header("Idempotency-Key",key);
        if(body!=null)request.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json(body),StandardCharsets.UTF_8));
        else request.method(method,HttpRequest.BodyPublishers.noBody());
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    @Configuration static class Server {
        @Bean TomcatServletWebServerFactory servletWebServerFactory(){
            var factory=new TomcatServletWebServerFactory(0);factory.setContextPath("/miniagent/api/v1");
            try{factory.setAddress(java.net.InetAddress.getLoopbackAddress());}catch(Exception e){throw new IllegalStateException(e);}return factory;
        }
        @Bean DispatcherServlet dispatcherServlet(){return new DispatcherServlet();}
        @Bean DispatcherServletRegistrationBean dispatcherServletRegistration(DispatcherServlet servlet){return new DispatcherServletRegistrationBean(servlet,"/");}
        @Bean @Primary OfficeQueueDispatcher officeQueueDispatcher(IOfficeRepository repository,OfficeTransactions transactions,
                OfficeRunExecutor runner,@Qualifier("officeTaskExecutor")ThreadPoolTaskExecutor executor,IArtifactStorage storage,OfficeSettings settings){
            return new OfficeQueueDispatcher(repository,transactions,runner,executor,storage,settings);
        }
    }
}
