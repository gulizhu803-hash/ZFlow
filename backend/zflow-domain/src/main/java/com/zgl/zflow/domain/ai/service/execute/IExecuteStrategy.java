package com.zgl.zflow.domain.ai.service.execute;

import com.zgl.zflow.domain.ai.model.entity.ExecuteRequestEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IExecuteStrategy {

    void execute(ExecuteRequestEntity executeRequestEntity, SseEmitter sseEmitter) throws Exception;

    String getType();

}
