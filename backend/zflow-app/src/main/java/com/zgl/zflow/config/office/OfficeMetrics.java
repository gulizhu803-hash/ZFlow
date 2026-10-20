package com.zgl.zflow.config.office;

import com.zgl.zflow.domain.office.model.OfficeActivity;
import com.zgl.zflow.trigger.service.OfficeEventStreamService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeMetrics {
    private final MeterRegistry registry;
    public OfficeMetrics(MeterRegistry registry,OfficeQueueDispatcher workers,OfficeEventStreamService streams) {
        this.registry=registry;
        registry.gauge("office.workers.active",workers,OfficeQueueDispatcher::activeWorkerCount);
        registry.gauge("office.workers.interrupted",workers,OfficeQueueDispatcher::cancelledButStillRunningCount);
        registry.gauge("office.sse.active",streams,OfficeEventStreamService::activeConnections);
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void record(OfficeActivity event) {
        registry.counter("office.events","event",event.eventType(),"status",event.status(),"stage",event.stage(),
                "error",event.errorKey()==null?"none":event.errorKey()).increment();
        if(event.elapsedMillis()!=null)registry.timer("run.started".equals(event.eventType())?"office.queue.wait":"office.run.duration",
                "status",event.status()).record(Math.max(0,event.elapsedMillis()),TimeUnit.MILLISECONDS);
    }
}
