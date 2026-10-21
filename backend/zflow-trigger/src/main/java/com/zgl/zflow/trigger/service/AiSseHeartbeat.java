package com.zgl.zflow.trigger.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流 SSE 的心跳登记处。
 *
 * <p>长任务可能几十秒没有任何事件。连接空闲会被反向代理切断，前端也会误判“卡死”。
 * 这里定时发一个注释帧（不进入 event 流，不会污染协议），
 * 顺带把已经断开却没人注销的连接清掉，避免登记表无限增长。</p>
 */
@Slf4j
@Component
public class AiSseHeartbeat {

    private final Set<SseEmitter> active = ConcurrentHashMap.newKeySet();

    public void register(SseEmitter emitter) {
        if (emitter != null) active.add(emitter);
    }

    public void unregister(SseEmitter emitter) {
        if (emitter != null) active.remove(emitter);
    }

    public int activeCount() {
        return active.size();
    }

    @Scheduled(fixedDelayString = "${zflow.ai.sse-heartbeat-ms:15000}")
    public void beat() {
        for (SseEmitter emitter : active) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception disconnected) {
                // 客户端已经走了：移除登记并尽力关闭，避免占用连接
                active.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 已经完成或已关闭
                }
            }
        }
    }
}
