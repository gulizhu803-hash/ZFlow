package com.zgl.zflow.config.office;

import com.zgl.zflow.domain.office.gateway.IArtifactStorage;
import com.zgl.zflow.domain.office.model.OfficeSettings;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.domain.office.service.*;
import com.zgl.zflow.trigger.service.OfficeWorkerControl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeQueueDispatcher implements OfficeWorkerControl {
    private final IOfficeRepository repository;
    private final OfficeTransactions transactions;
    private final OfficeRunExecutor runner;
    private final ThreadPoolTaskExecutor executor;
    private final IArtifactStorage storage;
    private final OfficeSettings settings;
    private final Set<String> scheduled=ConcurrentHashMap.newKeySet();
    private final Map<String,WorkerHandle> workers=new ConcurrentHashMap<>();
    private final Set<String> interrupted=ConcurrentHashMap.newKeySet();
    /** 本实例的身份：写入任务的 owner_instance，并用心跳表明自己还活着。 */
    private final String instanceId="office-"+UUID.randomUUID().toString().replace("-","").substring(0,8);
    private volatile boolean ready;
    public OfficeQueueDispatcher(IOfficeRepository repository,OfficeTransactions transactions,OfficeRunExecutor runner,
            @Qualifier("officeTaskExecutor") ThreadPoolTaskExecutor executor,IArtifactStorage storage,OfficeSettings settings) {
        this.repository=repository;this.transactions=transactions;this.runner=runner;this.executor=executor;this.storage=storage;this.settings=settings;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        // 只回收租约已经过期的任务；仍在心跳的同伴任务不会被误杀
        int reclaimed=transactions.recoverExpired(settings.getLeaseTimeoutSeconds());
        log.info("office startup instance={} orphanFiles={} reclaimed={}",instanceId,storage.countOrphans(new HashSet<>(repository.objectKeys())),reclaimed);
        ready=true;
    }
    @Scheduled(fixedDelayString="${zflow.office.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        if(!ready)return;
        try{transactions.heartbeat(instanceId);}catch(Exception e){log.warn("office heartbeat failed instance={}",instanceId);}
    }
    public String instanceId(){return instanceId;}
    @Scheduled(fixedDelayString="${zflow.office.dispatch-interval-ms:1000}")
    public void dispatch() {
        if(!ready)return;
        for(String id:repository.queued(settings.getExecutor().getQueueCapacity()+settings.getExecutor().getMaxPoolSize())) {
            if(!scheduled.add(id))continue;
            try {
                executor.execute(()->{
                    WorkerHandle handle=new WorkerHandle(Thread.currentThread());
                    workers.put(id,handle);
                    try{runner.execute(id, instanceId);}
                    catch(Exception e){log.warn("office worker failed runId={} errorKey=OFFICE_INTERNAL_ERROR",id);}
                    finally{
                        synchronized(handle){handle.active=false;Thread.interrupted();}
                        workers.remove(id,handle);interrupted.remove(id);scheduled.remove(id);
                    }
                });
            }catch(RejectedExecutionException rejected){scheduled.remove(id);}
        }
    }
    // Spring's default scheduler is a single thread; neither sweep nor dispatch performs model work.
    @Scheduled(fixedDelayString="${zflow.office.dispatch-interval-ms:1000}")
    public void sweep() {
        if(!ready)return;
        for(String id:repository.expired(settings.getMaxQueueWaitSeconds(),settings.getCancelGraceSeconds())) {
            transactions.expire(id); interrupt(id);
        }
        transactions.recoverExpired(settings.getLeaseTimeoutSeconds());
    }
    @Override public void interrupt(String runId) {
        WorkerHandle worker=workers.get(runId);
        if(worker!=null)synchronized(worker){
            if(worker.active){interrupted.add(runId);worker.thread.interrupt();}
        }
    }
    public int activeWorkerCount(){return workers.size();}
    public int cancelledButStillRunningCount(){return interrupted.size();}
    private static final class WorkerHandle {
        final Thread thread;
        boolean active=true;
        WorkerHandle(Thread thread){this.thread=thread;}
    }
}
