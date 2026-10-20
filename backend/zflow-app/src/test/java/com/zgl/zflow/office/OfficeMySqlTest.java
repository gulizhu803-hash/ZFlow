package com.zgl.zflow.office;

import com.alibaba.fastjson2.*;
import com.zgl.zflow.config.WebMvcConfig;
import com.zgl.zflow.config.ThreadPoolConfig;
import com.zgl.zflow.config.office.OfficeConfiguration;
import com.zgl.zflow.domain.office.gateway.*;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.domain.office.service.*;
import com.zgl.zflow.domain.session.service.SessionService;
import com.zgl.zflow.domain.util.jwt.*;
import com.zgl.zflow.domain.util.random.IRandomUtil;
import com.zgl.zflow.domain.user.model.vo.UserVO;
import com.zgl.zflow.infrastructure.repository.SessionRepository;
import com.zgl.zflow.properties.*;
import com.zgl.zflow.trigger.controller.*;
import com.zgl.zflow.trigger.handler.*;
import com.zgl.zflow.trigger.interceptor.AuthInterceptor;
import com.zgl.zflow.trigger.service.*;
import com.zgl.zflow.types.exception.OfficeException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

/** Runs only against an explicitly named, disposable MySQL 8 test database; never H2. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="ZFLOW_OFFICE_TEST_JDBC_URL",matches=".+")
class OfficeMySqlTest {
    static AtomicBoolean loseCommitReply=new AtomicBoolean();
    static AtomicBoolean failBeforeCommit=new AtomicBoolean();
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;JdbcTemplate jdbc;IOfficeRepository repository;OfficeTransactions tx;
    OfficeRunService runs;OfficeRunExecutor runner;ArtifactService artifacts;OfficeSettings settings;ControlledGateway gateway;
    Path storageRoot;String session;
    @BeforeAll void setup()throws Exception {
        storageRoot=Files.createTempDirectory("zflow-office-mysql-test-");
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("office-test",Map.of(
                "zflow.office.enabled","true","zflow.office.generation-mode","fixture","zflow.office.storage-root",storageRoot.toString())));
        context.register(Config.class);context.refresh();
        jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        try(Connection c=context.getBean(DataSource.class).getConnection()) {
            assertEquals("zflow_office_test",c.getCatalog(),"Only the disposable office database may be reset");
            assertTrue(c.getMetaData().getDatabaseProductVersion().startsWith("8."));
            for(String table:List.of("ai_office_run_event","ai_artifact_revision","ai_artifact","ai_office_run"))
                jdbc.execute("DROP TABLE IF EXISTS "+table);
            String baseline=Files.readString(Path.of("../docs/mysql/01-schema.sql"))
                    .replaceAll("(?s)CREATE DATABASE IF NOT EXISTS `ai-agent`.*?;","").replace("USE `ai-agent`;","");
            ScriptUtils.executeSqlScript(c,new ByteArrayResource(baseline.getBytes(StandardCharsets.UTF_8)));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("../docs/mysql/03-office-artifacts.sql"));
        }
        repository=context.getBean(IOfficeRepository.class);tx=context.getBean(OfficeTransactions.class);
        runs=context.getBean(OfficeRunService.class);runner=context.getBean(OfficeRunExecutor.class);
        artifacts=context.getBean(ArtifactService.class);settings=context.getBean(OfficeSettings.class);gateway=context.getBean(ControlledGateway.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    @BeforeEach void clean() {
        loseCommitReply.set(false);failBeforeCommit.set(false);gateway.reset();settings.setGenerationMode("fixture");settings.setMaxActiveRunsPerUser(3);
        for(String table:List.of("ai_office_run_event","ai_artifact_revision","ai_artifact","ai_office_run","ai_message","ai_session","ai_user"))
            jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO ai_user(id,user_name,password,user_role) VALUES (1,'office-owner','test','account'),(2,'office-admin','test','admin')");
        session=tx.createSession(1,"订单方案").getSessionId();
    }
    OfficeRequest request(){return new OfficeRequest(session,"画订单系统架构图",List.of("DRAWIO"),"ARCHITECTURE","订单系统",null);}
    OfficeRun create(String key){return runs.create(1,key,request()).run();}
    OfficeRun success() {
        OfficeRun r=create(UUID.randomUUID().toString());runner.execute(r.getRunId());
        r=repository.run(1,r.getRunId());assertEquals("SUCCEEDED",r.getStatus(),r.getErrorCode());return r;
    }
    OfficeRevision revision(OfficeRun r){return repository.runRevisions(1,r.getRunId()).get(0);}
    JSONObject data(MvcResult r)throws Exception{return JSON.parseObject(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).getJSONObject("data");}
    MvcResult call(MockHttpServletRequestBuilder b)throws Exception {
        return mvc.perform(b.header("Authorization","Bearer 1")).andReturn();
    }
    MockHttpServletRequestBuilder jsonPost(String path,Object body) {
        return post(path).contentType("application/json").content(json(body));
    }
    @Test void concurrentIdempotencyHasOneRunOneClaimAndOneOutput()throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(10);
        try {
            CountDownLatch start=new CountDownLatch(1);List<Future<String>> futures=new ArrayList<>();
            for(int i=0;i<10;i++)futures.add(pool.submit(()->{start.await();return create("same-key-001").getRunId();}));
            start.countDown();Set<String> ids=new HashSet<>();for(var f:futures)ids.add(f.get(10,TimeUnit.SECONDS));assertEquals(1,ids.size());
            String id=ids.iterator().next();List<Callable<Void>> claims=new ArrayList<>();
            for(int i=0;i<10;i++)claims.add(()->{runner.execute(id);return null;});
            for(var f:pool.invokeAll(claims))f.get();
            assertEquals(1L,repository.countRuns(1,session));assertEquals(1,repository.runRevisions(1,id).size());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_office_run_event WHERE event_type='run.started'",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void replayPrecedesCapacityAndModelChecksAndOwnerKeysAreIndependent() {
        OfficeRun r=create("replay-key");create("capacity-2");create("capacity-3");
        gateway.unavailable=true;
        assertTrue(runs.create(1,"replay-key",request()).replayed());
        assertEquals(429,assertThrows(OfficeException.class,()->create("capacity-4")).getStatus());
        OfficeRequest changed=new OfficeRequest(session,"another request",List.of("DRAWIO"),null,null,null);
        assertEquals(409,assertThrows(OfficeException.class,()->runs.create(1,"replay-key",changed)).getStatus());
        gateway.unavailable=false;
        String otherSession=tx.createSession(2,"其他人").getSessionId();
        var other=runs.create(2,"replay-key",new OfficeRequest(otherSession,"x",List.of("DRAWIO"),null,null,null));
        assertNotEquals(r.getRunId(),other.run().getRunId());
    }
    @Test void concurrentSavesConflictAndOldVersionsRemainImmutable()throws Exception {
        OfficeRevision v1=revision(success());String xml=gateway.fixture();
        var pool=Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> actions=List.of(
                    ()->{try{return artifacts.save(1,v1.getArtifactId(),1,xml.replace("订单服务","订单服务A"),"save-key-a");}catch(OfficeException e){return e;}},
                    ()->{try{return artifacts.save(1,v1.getArtifactId(),1,xml.replace("订单服务","订单服务B"),"save-key-b");}catch(OfficeException e){return e;}});
            var results=pool.invokeAll(actions);int saved=0,conflict=0;
            for(var f:results){Object value=f.get();if(value instanceof OfficeTransactions.Saved)saved++;else {assertEquals(409,((OfficeException)value).getStatus());conflict++;}}
            assertEquals(1,saved);assertEquals(1,conflict);assertEquals(2,repository.countRevisions(1,v1.getArtifactId()));
            assertEquals(v1.getContentHash(),hash(artifacts.content(1,v1.getArtifactId(),1).bytes()));
            var first=repository.revisions(1,v1.getArtifactId(),0,20).get(0);
            String original=new String(artifacts.content(1,v1.getArtifactId(),2).bytes(),StandardCharsets.UTF_8);
            assertTrue(artifacts.save(1,v1.getArtifactId(),1,original,first.getSaveRequestKey()).replayed());
            assertEquals(2,repository.countRevisions(1,v1.getArtifactId()));
        }finally{pool.shutdownNow();}
    }
    @Test void sameSaveKeyReplayAfterFurtherEditsReturnsFirstVersion() {
        OfficeRevision v1=revision(success());
        var v2=artifacts.save(1,v1.getArtifactId(),1,gateway.fixture(),"save-repeat");
        artifacts.save(1,v1.getArtifactId(),2,gateway.fixture(),"save-later");
        var replay=artifacts.save(1,v1.getArtifactId(),1,gateway.fixture(),"save-repeat");
        assertTrue(replay.replayed());assertEquals(v2.revision().getRevisionId(),replay.revision().getRevisionId());
        assertEquals(409,assertThrows(OfficeException.class,()->artifacts.save(1,v1.getArtifactId(),2,gateway.fixture(),"save-repeat")).getStatus());
    }
    @Test void commitAcknowledgementLossFindsCommittedRunAndRevision() {
        loseCommitReply.set(true);var accepted=runs.create(1,"lost-run-commit",request());
        assertTrue(accepted.replayed());assertEquals(1,repository.countRuns(1,session));
        runner.execute(accepted.run().getRunId());OfficeRevision v1=revision(accepted.run());
        loseCommitReply.set(true);var saved=artifacts.save(1,v1.getArtifactId(),1,gateway.fixture(),"lost-save-commit");
        assertTrue(saved.replayed());assertEquals(2,repository.countRevisions(1,v1.getArtifactId()));
        assertNotNull(artifacts.content(1,v1.getArtifactId(),2).bytes());
    }
    @Test void filePreparedButRolledBackSaveIsNotPublished()throws Exception {
        OfficeRevision v1=revision(success());failBeforeCommit.set(true);
        assertThrows(RuntimeException.class,()->artifacts.save(1,v1.getArtifactId(),1,gateway.fixture(),"rollback-save"));
        assertEquals(1,repository.countRevisions(1,v1.getArtifactId()));assertEquals(1,repository.artifact(1,v1.getArtifactId()).getCurrentVersion());
        try(var files=Files.walk(storageRoot)){assertTrue(files.filter(p->p.getFileName().toString().equals("content.drawio")).count()>=2);}
        assertNotNull(artifacts.content(1,v1.getArtifactId(),1));
    }
    @Test void queueAndRunTimeoutsAndRestartRespectTerminalStates() {
        OfficeRun queued=create("expired-queue");
        jdbc.update("UPDATE ai_office_run SET created_at=TIMESTAMPADD(SECOND,-601,UTC_TIMESTAMP(3)) WHERE run_id=?",queued.getRunId());
        assertNull(tx.claim(queued.getRunId()));assertEquals("QUEUE_TIMEOUT",repository.run(1,queued.getRunId()).getErrorCode());
        OfficeRun running=create("expired-run");tx.claim(running.getRunId());
        jdbc.update("UPDATE ai_office_run SET deadline_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE run_id=?",running.getRunId());
        assertNull(tx.checkpoint(running.getRunId(),"SAVING"));assertEquals("RUN_TIMEOUT",repository.run(1,running.getRunId()).getErrorCode());
        OfficeRun restart=create("restart-run");tx.claim(restart.getRunId());tx.startAttempt(restart.getRunId(),"GENERATE");tx.recover(restart.getRunId());
        var recovered=repository.run(1,restart.getRunId());assertEquals("PROCESS_RESTARTED",recovered.getErrorCode());
        assertTrue(recovered.getGenerationReceipt().contains("UNKNOWN"));
        var unknown=JSON.parseObject(recovered.getGenerationReceipt()).getJSONArray("attempts").getJSONObject(0);
        assertTrue(unknown.containsKey("inputTokens"));assertNull(unknown.get("inputTokens"));
        long seq=recovered.getLastEventSeq();tx.fail(restart.getRunId(),"IGNORED","ignored");
        assertEquals(seq,repository.run(1,restart.getRunId()).getLastEventSeq());
    }
    @Test void repeatedCancelKeepsClockAndNeverPublishesLateResults()throws Exception {
        OfficeRun queued=create("cancel-queued");assertEquals("CANCELLED",tx.cancel(1,queued.getRunId()).getStatus());assertNull(tx.claim(queued.getRunId()));
        settings.setGenerationMode("model");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        gateway.action=()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return gateway.result();};
        OfficeRun r=create("cancel-running");var worker=Executors.newSingleThreadExecutor();
        try {
            Future<?> done=worker.submit(()->runner.execute(r.getRunId()));assertTrue(entered.await(5,TimeUnit.SECONDS));
            LocalDateTime first=tx.cancel(1,r.getRunId()).getCancelRequestedAt();
            assertEquals(first,tx.cancel(1,r.getRunId()).getCancelRequestedAt());
            release.countDown();done.get(5,TimeUnit.SECONDS);
            assertEquals("CANCELLED",repository.run(1,r.getRunId()).getStatus());assertTrue(repository.runRevisions(1,r.getRunId()).isEmpty());
        }finally{release.countDown();worker.shutdownNow();}
    }
    @Test void expiredFinalPublishCannotExposeArtifactEvenBeforeSweep() {
        OfficeRun r=create("final-deadline");tx.claim(r.getRunId());
        jdbc.update("UPDATE ai_office_run SET deadline_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE run_id=?",r.getRunId());
        assertFalse(tx.publish(r.getRunId(),new OfficeArtifact(),new OfficeRevision()));
        assertEquals("RUN_TIMEOUT",repository.run(1,r.getRunId()).getErrorCode());assertEquals(0,repository.countArtifacts(1,session));
    }
    @Test void changedConfigurationFailsBeforeGatewayAndRetryInputMustMatch() {
        OfficeRun r=create("config-snapshot");gateway.changed=true;runner.execute(r.getRunId());
        assertEquals("GENERATION_CONFIG_CHANGED",repository.run(1,r.getRunId()).getErrorCode());
        assertEquals(0,repository.run(1,r.getRunId()).getModelAttemptCount());
        gateway.changed=false;
        var req=new OfficeRequest(session,request().prompt(),List.of("DRAWIO"),"ARCHITECTURE","订单系统",r.getRunId());
        assertNotNull(runs.create(1,"retry-original",req));
        assertEquals(400,assertThrows(OfficeException.class,()->runs.create(1,"retry-changed",new OfficeRequest(session,"changed",List.of("DRAWIO"),null,null,r.getRunId()))).getStatus());
    }
    @Test void generationBudgetIncludesTransportFailuresAndRepair() {
        settings.setGenerationMode("model");java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        gateway.action=()->{
            int n=calls.incrementAndGet();
            if(n<=2)throw new IOfficeGenerationGateway.Failure("MODEL_HTTP_ERROR",true,false,0);
            if(n==3)return new IOfficeGenerationGateway.Generation("<invalid/>",null,null,null,null);
            return gateway.result();
        };
        OfficeRun r=create("generation-budget");runner.execute(r.getRunId());OfficeRun result=repository.run(1,r.getRunId());
        assertEquals("SUCCEEDED",result.getStatus(),result.getErrorCode());assertEquals(4,calls.get());assertEquals(4,result.getModelAttemptCount());
        JSONObject receipt=JSON.parseObject(result.getGenerationReceipt());assertEquals(4,receipt.getJSONArray("attempts").size());
        assertEquals("REPAIR",receipt.getJSONArray("attempts").getJSONObject(3).getString("purpose"));
    }
    @Test void sessionDeleteAndCreateShareLockAndNonemptyLegacyDeleteIs409()throws Exception {
        OfficeRun r=create("session-lock");
        MvcResult deletion=call(post("/session/delete").param("sessionId",session));
        assertEquals(409,deletion.getResponse().getStatus());assertEquals("OFFICE_SESSION_NOT_EMPTY",data(deletion).getString("errorKey"));
        String emptySession=tx.createSession(1,"empty").getSessionId();
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{try{tx.deleteSession(1,emptySession);return true;}catch(OfficeException e){assertEquals(409,e.getStatus());return false;}});
            var c=pool.submit(()->{try{return runs.create(1,"race-session",new OfficeRequest(emptySession,"x",List.of("DRAWIO"),null,null,null)).run();}
                catch(OfficeException e){assertEquals(404,e.getStatus());return null;}});
            boolean deleted=a.get(5,TimeUnit.SECONDS);OfficeRun created=c.get(5,TimeUnit.SECONDS);
            assertEquals(deleted,created==null);
        }finally{pool.shutdownNow();}
        assertNotNull(repository.run(1,r.getRunId()));
    }
    @Test void httpFixtureRoundTripVersionUrlsReceiptAndValidationErrors()throws Exception {
        MvcResult accepted=call(jsonPost("/office/runs",request()).header("Idempotency-Key","http-fixture"));
        assertEquals(202,accepted.getResponse().getStatus());String id=data(accepted).getString("runId");runner.execute(id);
        MvcResult detail=call(get("/office/runs/"+id).param("includeReceipt","true"));assertEquals(200,detail.getResponse().getStatus());
        JSONObject run=data(detail);assertEquals("fixture",run.getString("generationMode"));assertEquals(0,run.getJSONObject("generationReceipt").getIntValue("attemptCount"));
        assertFalse(detail.getResponse().getContentAsString().contains("objectKey"));
        String art=run.getJSONArray("artifacts").getJSONObject(0).getString("artifactId");
        JSONObject meta=data(call(get("/office/artifacts/"+art)));
        assertTrue(meta.getJSONObject("revision").getString("contentUrl").endsWith("?version=1"));
        MvcResult save=call(jsonPost("/office/artifacts/"+art+"/revisions",map("baseVersion",1,"xml",gateway.fixture())).header("Idempotency-Key","http-save"));
        assertEquals(201,save.getResponse().getStatus());
        MvcResult conflict=call(jsonPost("/office/artifacts/"+art+"/revisions",map("baseVersion",1,"xml",gateway.fixture())).header("Idempotency-Key","http-conflict"));
        assertEquals(409,conflict.getResponse().getStatus());assertEquals(2,data(conflict).getIntValue("currentVersion"));
        MvcResult download=call(get("/office/artifacts/"+art+"/download").param("version","1"));
        assertEquals(200,download.getResponse().getStatus());assertTrue(download.getResponse().getHeader("Content-Disposition").contains("filename*="));
        assertEquals("private, no-store",download.getResponse().getHeader("Cache-Control"));
        assertEquals(400,call(jsonPost("/office/runs",map("sessionId",session,"prompt","x","artifactTypes",List.of("DRAWIO"),"knowledgeIds",List.of("secret"))).header("Idempotency-Key","unknown-field")).getResponse().getStatus());
        assertEquals(400,call(jsonPost("/office/runs",request())).getResponse().getStatus());
        assertEquals(400,call(get("/office/runs/"+id).param("includeReceipt","yes")).getResponse().getStatus());
        MvcResult invalid=call(jsonPost("/office/artifacts/"+art+"/revisions",map("baseVersion",2,"xml","<bad/>")).header("Idempotency-Key","invalid-save"));
        assertEquals(422,invalid.getResponse().getStatus());assertFalse(data(invalid).getJSONObject("validationReport").getBooleanValue("valid"));
        assertEquals(1,data(call(get("/office/runs/"+id))).getJSONArray("artifacts").getJSONObject(0).getIntValue("version"));
    }
    @Test void allResourceReadsDenyOtherOwnerIncludingAdminAndSse()throws Exception {
        OfficeRun r=success();OfficeRevision v=revision(r);
        for(String path:List.of("/office/runs/"+r.getRunId(),"/office/runs/"+r.getRunId()+"/events",
                "/office/artifacts/"+v.getArtifactId(),"/office/artifacts/"+v.getArtifactId()+"/revisions",
                "/office/artifacts/"+v.getArtifactId()+"/content","/office/artifacts/"+v.getArtifactId()+"/download")) {
            assertEquals(404,mvc.perform(get(path).header("Authorization","Bearer 2")).andReturn().getResponse().getStatus(),path);
        }
        assertEquals(401,mvc.perform(get("/office/sessions")).andReturn().getResponse().getStatus());
        assertEquals(404,mvc.perform(post("/office/runs/"+r.getRunId()+"/cancel").header("Authorization","Bearer 2")).andReturn().getResponse().getStatus());
    }
    @Test void sseReplayHasOrderedTerminalEventsAndClearsOriginalThreadIdentity()throws Exception {
        OfficeRun r=success();
        MvcResult initial=call(get("/office/runs/"+r.getRunId()+"/events").param("afterSeq","3"));
        assertTrue(initial.getRequest().isAsyncStarted());assertNull(context.getBean(UserContext.class).getUserId());
        initial.getAsyncResult(5000);
        MvcResult completed=mvc.perform(asyncDispatch(initial)).andReturn();
        String stream=completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(stream.contains("event:artifact.ready"));assertTrue(stream.contains("event:run.completed"));
        assertTrue(stream.indexOf("event:artifact.ready")<stream.indexOf("event:run.completed"));
        MvcResult caughtUp=call(get("/office/runs/"+r.getRunId()+"/events").param("afterSeq",r.getLastEventSeq().toString()));
        caughtUp.getAsyncResult(5000);mvc.perform(asyncDispatch(caughtUp));
        assertEquals(0,context.getBean(OfficeEventStreamService.class).activeConnections());
        assertEquals(400,call(get("/office/runs/"+r.getRunId()+"/events").param("afterSeq","999")).getResponse().getStatus());
    }
    @Test void jsonBodyWithoutContentLengthIsActuallyBoundedAndOldSseIdentityCleanupStillWorks()throws Exception {
        int original=settings.getMaxJsonRequestBytes();settings.setMaxJsonRequestBytes(100);
        try{assertEquals(413,call(jsonPost("/office/sessions",map("title","a".repeat(150)))).getResponse().getStatus());}
        finally{settings.setMaxJsonRequestBytes(original);}
        UserContext users=context.getBean(UserContext.class);AuthInterceptor auth=context.getBean(AuthInterceptor.class);
        var request=new MockHttpServletRequest("GET","/miniagent/api/v1/admin/dashboard");request.addHeader("Authorization","Bearer 1");
        assertFalse(auth.preHandle(request,new MockHttpServletResponse(),new Object()));assertNull(users.getUser());
        request=new MockHttpServletRequest("OPTIONS","/office/sessions");assertTrue(auth.preHandle(request,new MockHttpServletResponse(),new Object()));assertNull(users.getUser());
        for(String path:List.of("/ai/chat","/ai/work","/office/runs/x/events")){
            request=new MockHttpServletRequest("POST",path);request.addHeader("Authorization","Bearer 1");
            assertTrue(auth.preHandle(request,new MockHttpServletResponse(),new Object()));assertEquals(1L,users.getUserId());
            auth.afterConcurrentHandlingStarted(request,new MockHttpServletResponse(),new Object());assertNull(users.getUser());
        }
    }
    @Test void originalExecutorAndOfficeExecutorCoexistAndUtcRoundTrips() {
        assertNotNull(context.getBean("threadPoolExecutor"));assertNotNull(context.getBean("officeTaskExecutor"));
        OfficeRun r=create("utc-roundtrip");
        assertTrue(Math.abs(Duration.between(r.getCreatedAt(),LocalDateTime.now(ZoneOffset.UTC)).toSeconds())<5);
        assertEquals(r.getCreatedAt(),repository.run(1,r.getRunId()).getCreatedAt());
    }

    @Test void cancellationGraceExpiresOnceAndLateAttemptsCannotAppendEvents() {
        OfficeRun r=create("cancel-grace");tx.claim(r.getRunId());int attempt=tx.startAttempt(r.getRunId(),"GENERATE");
        tx.cancel(1,r.getRunId());
        jdbc.update("UPDATE ai_office_run SET cancel_requested_at=TIMESTAMPADD(SECOND,-16,UTC_TIMESTAMP(3)) WHERE run_id=?",r.getRunId());
        LocalDateTime first=repository.run(1,r.getRunId()).getCancelRequestedAt();
        assertEquals(first,tx.cancel(1,r.getRunId()).getCancelRequestedAt());
        tx.expire(r.getRunId());OfficeRun terminal=repository.run(1,r.getRunId());
        assertEquals("CANCELLED",terminal.getStatus());
        tx.finishAttempt(r.getRunId(),attempt,gateway.result(),null);tx.checkpoint(r.getRunId(),"SAVING");tx.expire(r.getRunId());
        assertEquals(terminal.getLastEventSeq(),repository.run(1,r.getRunId()).getLastEventSeq());
    }

    @Test void sseConnectionLimitReleasesPermitsWhenTaskFinishes()throws Exception {
        OfficeRun r=create("sse-limit");List<MvcResult> opened=new ArrayList<>();
        for(int i=0;i<3;i++)opened.add(call(get("/office/runs/"+r.getRunId()+"/events")));
        MvcResult fourth=call(get("/office/runs/"+r.getRunId()+"/events"));
        assertEquals(429,fourth.getResponse().getStatus());assertEquals("2",fourth.getResponse().getHeader("Retry-After"));
        runner.execute(r.getRunId());
        for(MvcResult request:opened){request.getAsyncResult(5000);mvc.perform(asyncDispatch(request));}
        assertEquals(0,context.getBean(OfficeEventStreamService.class).activeConnections());
    }

    @Test void generatedCommitReplyLossNeverCreatesASecondTerminalEvent() {
        OfficeRun r=create("publish-reply");tx.claim(r.getRunId());
        OfficeArtifact a=new OfficeArtifact();a.setArtifactId(id("art"));a.setOwnerId(1L);a.setSessionId(session);
        a.setArtifactType("DRAWIO");a.setTitle("发布确认丢失");a.setCurrentVersion(1);
        OfficeRevision v=new OfficeRevision();v.setRevisionId(id("rev"));v.setArtifactId(a.getArtifactId());v.setVersion(1);v.setBaseVersion(0);
        v.setRunId(r.getRunId());v.setOutputKey("diagram");v.setCreatedBy(1L);v.setContentType("application/xml");
        v.setValidationReport(json(context.getBean(IDiagramValidator.class).validate(gateway.fixture(),true)));
        var file=context.getBean(IArtifactStorage.class).writeImmutable(1,a.getArtifactId(),v.getRevisionId(),gateway.fixture().getBytes(StandardCharsets.UTF_8));
        v.setObjectKey(file.objectKey());v.setContentHash(file.contentHash());v.setSizeBytes(file.sizeBytes());
        loseCommitReply.set(true);assertThrows(RuntimeException.class,()->tx.publish(r.getRunId(),a,v));
        tx.fail(r.getRunId(),"SHOULD_NOT_WIN","ignored");
        assertEquals("SUCCEEDED",repository.run(1,r.getRunId()).getStatus());
        assertEquals(1,repository.runRevisions(1,r.getRunId()).size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_office_run_event WHERE run_id=? AND event_type IN ('run.completed','run.failed','run.cancelled')",Integer.class,r.getRunId()));
        assertNotNull(artifacts.content(1,a.getArtifactId(),1));
    }

    @Test void migrationReapplicationIsDetected()throws Exception {
        try(Connection c=context.getBean(DataSource.class).getConnection()){
            assertThrows(org.springframework.jdbc.datasource.init.ScriptStatementFailedException.class,
                    ()->ScriptUtils.executeSqlScript(c,new FileSystemResource("../docs/mysql/03-office-artifacts.sql")));
        }
    }

    @Test void actualDrawioEditorExportCanBeSavedAsNewImmutableRevision()throws Exception {
        String edited=new ClassPathResource("office/editor-roundtrip.drawio").getContentAsString(StandardCharsets.UTF_8);
        assertTrue(edited.contains("编辑验证"));
        OfficeRevision v1=revision(success());
        var saved=artifacts.save(1,v1.getArtifactId(),1,edited,"editor-roundtrip");
        assertEquals(2,saved.revision().getVersion());
        assertEquals(hash(edited),saved.revision().getContentHash());
        assertEquals(edited,new String(artifacts.content(1,v1.getArtifactId(),2).bytes(),StandardCharsets.UTF_8));
        assertEquals(v1.getContentHash(),hash(artifacts.content(1,v1.getArtifactId(),1).bytes()));
    }

    @Test void actualSpringAiHttpRequestsRespectCombinedRetryAndRepairBudget()throws Exception {
        settings.setGenerationMode("model");
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            int call=calls.incrementAndGet();exchange.getRequestBody().readAllBytes();
            int status=call==1?429:call==2?503:200;
            String body=call<=2?"{\"error\":{\"message\":\"temporary\"}}":json(map("id","request-"+call,
                    "object","chat.completion","model","counted-model","choices",List.of(map("index",0,
                    "message",map("role","assistant","content",call==3?"<invalid/>":gateway.fixture()),"finish_reason","stop")),
                    "usage",map("prompt_tokens",12,"completion_tokens",34,"total_tokens",46)));
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            gateway.live=new com.zgl.zflow.infrastructure.office.OfficeGenerationGateway(settings,()->
                    new com.zgl.zflow.infrastructure.office.OfficeGenerationGateway.ModelConfig(
                            "http://127.0.0.1:"+server.getAddress().getPort(),"test-key","counted-model"));
            OfficeRun r=create("counted-http-budget");runner.execute(r.getRunId());OfficeRun result=repository.run(1,r.getRunId());
            assertEquals("SUCCEEDED",result.getStatus(),result.getErrorCode());assertEquals(4,calls.get());
            assertEquals(4,result.getModelAttemptCount());
            var last=JSON.parseObject(result.getGenerationReceipt()).getJSONArray("attempts").getJSONObject(3);
            assertEquals("REPAIR",last.getString("purpose"));assertEquals(46L,last.getLong("totalTokens"));
        }finally{gateway.live=null;server.stop(0);}
    }

    @Configuration @EnableWebMvc @EnableTransactionManagement
    @MapperScan("com.zgl.zflow.infrastructure.persistent.dao")
    @Import({OfficeConfiguration.class,OfficeTransactions.class,OfficeSessionService.class,OfficeRunService.class,
            OfficeRunExecutor.class,ArtifactService.class,OfficeQueryService.class,OfficeController.class,OfficeEventStreamService.class,
            OfficeExceptionHandler.class,OfficeSessionExceptionHandler.class,OfficeRequestBodyAdvice.class,
            GlobalExceptionHandler.class,UserContext.class,AuthInterceptor.class,WebMvcConfig.class,ThreadPoolConfig.class,
            SessionController.class,SessionService.class,SessionRepository.class})
    static class Config {
        @Bean DataSource mysqlDataSource() {
            DriverManagerDataSource target=new DriverManagerDataSource(System.getenv("ZFLOW_OFFICE_TEST_JDBC_URL"),
                    System.getenv().getOrDefault("ZFLOW_OFFICE_TEST_USER","root"),System.getenv("ZFLOW_OFFICE_TEST_PASSWORD"));
            return new DelegatingDataSource(target) {
                @Override public Connection getConnection()throws SQLException {
                    Connection real=super.getConnection();
                    return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                        if(method.getName().equals("commit")&&failBeforeCommit.compareAndSet(true,false))throw new SQLException("Injected before commit","08S01");
                        try {
                            Object result=method.invoke(real,args);
                            if(method.getName().equals("commit")&&loseCommitReply.compareAndSet(true,false))throw new SQLException("Injected lost commit acknowledgement","08S01");
                            return result;
                        }catch(InvocationTargetException e){throw e.getCause();}
                    });
                }
            };
        }
        @Bean(name={"transactionManager","mysqlTransactionManager"})
        JdbcTransactionManager transactionManager(DataSource ds){return new JdbcTransactionManager(ds);}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds)throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
            factory.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
            return factory.getObject();
        }
        @Bean SqlSessionTemplate mysqlTemplate(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean SystemModelProperties systemModelProperties(){return new SystemModelProperties();}
        @Bean @Primary ControlledGateway controlledGateway(@org.springframework.beans.factory.annotation.Qualifier("officeGenerationGateway") IOfficeGenerationGateway delegate){return new ControlledGateway(delegate);}
        @Bean OfficeWorkerControl officeWorkerControl(){return id->{};}
        @Bean IRandomUtil randomUtil(){IRandomUtil util=mock(IRandomUtil.class);when(util.uuid()).thenAnswer(i->UUID.randomUUID().toString());return util;}
        @Bean IJwtUtil jwtUtil(){
            IJwtUtil jwt=mock(IJwtUtil.class);
            when(jwt.parseToken(anyString())).thenAnswer(i->{long id=Long.parseLong(i.getArgument(0));
                return UserVO.builder().userId(id).userName("test-"+id).userRole(id==2?"admin":"account").build();});
            return jwt;
        }
    }
    static class ControlledGateway implements IOfficeGenerationGateway {
        final IOfficeGenerationGateway delegate;volatile boolean unavailable,changed;
        volatile IOfficeGenerationGateway live;
        volatile java.util.function.Supplier<Generation> action;
        ControlledGateway(IOfficeGenerationGateway delegate){this.delegate=delegate;reset();}
        void reset(){unavailable=false;changed=false;live=null;action=this::result;}
        Generation result(){return new Generation(fixture(),null,null,null,null);}
        public String snapshot(){
            var snapshot=JSON.parseObject(live==null?delegate.snapshot():live.snapshot());
            if(changed)snapshot.put("generatorVersion","changed");
            return snapshot.toJSONString();
        }
        public void checkAvailable(){if(unavailable)throw new OfficeException(503,"OFFICE_MODEL_UNAVAILABLE","unavailable");}
        public String fixture(){return delegate.fixture();}
        public Generation generate(OfficeRequest r,String x,String s,Duration d){return live==null?action.get():live.generate(r,x,s,d);}
    }
}
