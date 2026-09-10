package com.zgl.zflow.trigger.listener;

import com.alibaba.fastjson2.JSON;
import com.zgl.zflow.domain.util.jwt.UserContext;
import com.zgl.zflow.domain.util.mq.IMqService;
import com.zgl.zflow.domain.util.mq.MqEventDTO;
import com.zgl.zflow.domain.util.mq.MqEventEntity;
import com.zgl.zflow.domain.util.redis.IRedisUtil;
import com.zgl.zflow.domain.workspace.model.dto.AgentPublishDTO;
import com.zgl.zflow.domain.workspace.model.dto.PlazaCommentDTO;
import com.zgl.zflow.domain.workspace.service.IWorkspaceService;
import com.zgl.zflow.types.exception.MissingException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.zgl.zflow.types.constant.ExceptionMessage.MQ_EVENT_UNKNOWN;
import static com.zgl.zflow.types.constant.RedisConstant.WORKSPACE_MQ_PROCESSED_PREFIX;

@Slf4j
@Component
public class WorkspaceMqMainListener {

    @Resource
    private IMqService mqService;

    @Resource
    private IWorkspaceService workspaceService;

    @Resource
    private UserContext userContext;

    @Resource
    private IRedisUtil redisUtil;

    @Value("${miniagent.mq.max-retry}")
    private int maxRetry;

    @RabbitListener(queues = "${miniagent.mq.main-queue}")
    public void onMessage(String message) {
        MqEventEntity event = null;
        try {
            event = JSON.parseObject(message, MqEventEntity.class);
            if (event == null || !StringUtils.hasText(event.getEventId()) || event.getEventType() == null) {
                log.error("【Listener】无效消息体：message={}", message);
                return;
            }

            String processedKey = WORKSPACE_MQ_PROCESSED_PREFIX + event.getEventId();
            Integer processed = redisUtil.getValue(processedKey, Integer.class);
            if (processed != null && processed == 1) {
                log.info("【Listener】任务已处理，跳过重复消费：message={}", message);
                return;
            }

            userContext.set(event.getUserInfo());

            switch (event.getEventType()) {
                case PLAZA_LIKE -> {
                    MqEventDTO payload = mqService.parsePayload(event);
                    workspaceService.executePlazaLike(payload.getPlazaId(), Boolean.TRUE.equals(payload.getLiked()));
                }
                case PLAZA_FAVOR -> {
                    MqEventDTO payload = mqService.parsePayload(event);
                    workspaceService.executePlazaFavor(payload.getPlazaId(), Boolean.TRUE.equals(payload.getFavored()));
                }
                case PLAZA_COMMENT -> {
                    MqEventDTO payload = mqService.parsePayload(event);
                    workspaceService.executePlazaComment(PlazaCommentDTO.builder()
                            .plazaId(payload.getPlazaId())
                            .commentContent(payload.getCommentContent())
                            .build());
                }
                case PLAZA_DISCOMMENT -> {
                    MqEventDTO payload = mqService.parsePayload(event);
                    workspaceService.executePlazaDiscomment(payload.getPlazaId(), payload.getCommentId());
                }
                case PLAZA_DELETE -> {
                    MqEventDTO payload = mqService.parsePayload(event);
                    workspaceService.executePlazaDelete(payload.getPlazaId());
                }
                case AGENT_PUBLISH -> {
                    MqEventDTO payload = mqService.parsePayload(event);
                    workspaceService.executeAgentPublish(AgentPublishDTO.builder()
                            .agentId(payload.getAgentId())
                            .plazaTitle(payload.getPlazaTitle())
                            .plazaDesc(payload.getPlazaDesc())
                            .build());
                }
                case AGENT_FORK -> {
                    MqEventDTO payload = mqService.parsePayload(event);
                    workspaceService.executeAgentFork(payload.getTemplateId());
                }
                case AGENT_DELETE -> {
                    MqEventDTO payload = mqService.parsePayload(event);
                    workspaceService.executeAgentDelete(payload.getAgentId());
                }
                default -> throw new MissingException(String.format(MQ_EVENT_UNKNOWN, event.getEventType()));
            }

            redisUtil.setValue(processedKey, 1, 86400);
        } catch (Exception e) {
            if (event == null) {
                log.error("【Listener】无效消息体：message={}", message);
                return;
            }
            int retryCount = event.getRetryCount();
            event.setRetryCount(++retryCount);
            event.setErrorMessage(e.getMessage());
            if (retryCount < maxRetry) {
                mqService.sendRetry(event);
                log.error("【Listener】任务消费失败，发送重试队列：eventId={}, eventType={}, retryCount={}",
                        event.getEventId(), event.getEventType(), retryCount, e);
            } else {
                mqService.sendDead(event);
                log.error("【Listener】任务消费失败，进入死信队列：eventId={}, eventType={}, retryCount={}",
                        event.getEventId(), event.getEventType(), retryCount, e);
            }
        } finally {
            userContext.clear();
        }
    }

}
