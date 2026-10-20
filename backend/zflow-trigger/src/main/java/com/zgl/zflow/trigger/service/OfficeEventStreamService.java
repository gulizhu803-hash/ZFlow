package com.zgl.zflow.trigger.service;

import com.alibaba.fastjson2.JSON;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.types.exception.OfficeException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.zgl.zflow.domain.office.service.OfficeTransactions.require;

@Service
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeEventStreamService {
    private final IOfficeRepository repository;
    private final OfficeSettings settings;
    private final ThreadPoolTaskExecutor executor;
    private final Map<Long,Integer> connections=new HashMap<>();
    private int total;
    public OfficeEventStreamService(IOfficeRepository repository,OfficeSettings settings,
            @Qualifier("officeSseExecutor") ThreadPoolTaskExecutor executor) {
        this.repository=repository;this.settings=settings;this.executor=executor;
    }
    public SseEmitter subscribe(long ownerId,String runId,String cursor) {
        OfficeRun run=require(repository.run(ownerId,runId));
        long afterSeq;
        try{if(cursor==null)cursor="0";if(!cursor.matches("[0-9]+"))throw new NumberFormatException();afterSeq=Long.parseLong(cursor);}
        catch(NumberFormatException e){throw invalidCursor();}
        if(afterSeq>run.getLastEventSeq())throw invalidCursor();
        synchronized(this) {
            if(total>=settings.getSse().getMaxConnectionsTotal()||connections.getOrDefault(ownerId,0)>=settings.getSse().getMaxConnectionsPerUser())
                throw new OfficeException(429,"OFFICE_CAPACITY_EXCEEDED","事件订阅连接已达上限");
            total++;connections.merge(ownerId,1,Integer::sum);
        }
        SseEmitter emitter=new SseEmitter(settings.getSse().getConnectionSeconds()*1000L);
        AtomicBoolean closed=new AtomicBoolean();
        Runnable release=()->{
            if(closed.compareAndSet(false,true))synchronized(this){
                total--;connections.computeIfPresent(ownerId,(k,n)->n<=1?null:n-1);
            }
        };
        emitter.onCompletion(release);emitter.onTimeout(()->{release.run();emitter.complete();});
        emitter.onError(e->release.run());
        long initial=afterSeq;
        try {
            executor.execute(()->{
                long seq=initial,start=System.nanoTime(),heartbeat=start;
                try {
                    while(!closed.get()&&(System.nanoTime()-start)/1_000_000<settings.getSse().getConnectionSeconds()*1000L) {
                        OfficeRun current=require(repository.run(ownerId,runId));
                        long water=current.getLastEventSeq();
                        for(OfficeEvent event:repository.events(ownerId,runId,seq,water)) {
                            emitter.send(SseEmitter.event().id(event.getSeq().toString()).name(event.getEventType()).data(JSON.parseObject(event.getPayload())));
                            seq=event.getSeq();
                        }
                        if(current.terminal()&&seq>=water)break;
                        if((System.nanoTime()-heartbeat)/1_000_000>=settings.getSse().getHeartbeatSeconds()*1000L) {
                            emitter.send(SseEmitter.event().comment("heartbeat"));heartbeat=System.nanoTime();
                        }
                        Thread.sleep(200);
                    }
                    emitter.complete();
                }catch(Exception disconnected){
                    // A stream may already have sent bytes. Close it; never dispatch a JSON error into SSE.
                    try{emitter.complete();}catch(RuntimeException alreadyClosed){/* Permit is released below. */}
                }
                finally{release.run();}
            });
        }catch(RuntimeException rejected){
            release.run();throw new OfficeException(429,"OFFICE_CAPACITY_EXCEEDED","事件订阅繁忙，请重连");
        }
        return emitter;
    }
    public synchronized int activeConnections(){return total;}
    private OfficeException invalidCursor(){return new OfficeException(400,"INVALID_EVENT_CURSOR","事件游标不合法，请重新查询任务");}
}
