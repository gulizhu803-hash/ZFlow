package com.zgl.zflow.office;

import com.zgl.zflow.trigger.service.AiSseHeartbeat;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiSseHeartbeatTest {

    @Test
    void keepsHealthyEmitterRegistered() {
        AiSseHeartbeat heartbeat = new AiSseHeartbeat();
        SseEmitter alive = new SseEmitter(60_000L) {
            @Override
            public void send(SseEventBuilder builder) {
                // 连接正常
            }
        };
        heartbeat.register(alive);
        assertEquals(1, heartbeat.activeCount());

        heartbeat.beat();
        assertEquals(1, heartbeat.activeCount(), "健康的连接不应被移除");

        heartbeat.unregister(alive);
        assertEquals(0, heartbeat.activeCount());
    }

    @Test
    void dropsEmitterThatCannotBeWritten() {
        AiSseHeartbeat heartbeat = new AiSseHeartbeat();
        SseEmitter gone = new SseEmitter(60_000L) {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IOException("client disconnected");
            }
        };
        heartbeat.register(gone);

        heartbeat.beat();
        assertEquals(0, heartbeat.activeCount(), "写失败说明客户端已断开，应移出登记表");
    }

    @Test
    void toleratesNullRegistration() {
        AiSseHeartbeat heartbeat = new AiSseHeartbeat();
        heartbeat.register(null);
        heartbeat.unregister(null);
        heartbeat.beat();
        assertEquals(0, heartbeat.activeCount());
    }
}
