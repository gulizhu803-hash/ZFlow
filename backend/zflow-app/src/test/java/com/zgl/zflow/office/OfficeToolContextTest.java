package com.zgl.zflow.office;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OfficeToolContextTest {
    public static class Probe {
        @Tool(description="Read explicit office execution context")
        public String context(ToolContext context) {
            return context.getContext().get("ownerId")+":"+context.getContext().get("sessionId");
        }
    }
    @Test void springAiOnePassesServerContextWithoutThreadLocal() {
        var callbacks=ToolCallbacks.from(new Probe());
        String result=callbacks[0].call("{}",new ToolContext(Map.of("ownerId",17L,"sessionId","office-session")));
        assertTrue(result.contains("17:office-session"));
    }
}
