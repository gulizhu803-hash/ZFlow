package com.zgl.zflow.mcp.tool;

import com.zgl.zflow.mcp.dto.SendTextToolRequest;
import com.zgl.zflow.mcp.port.IWeComPort;
import com.zgl.zflow.mcp.dto.SendTextCardToolRequest;
import com.zgl.zflow.mcp.dto.SendMessageToolResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class WeComTool {

    @Resource
    private IWeComPort weComPort;

    @Tool(description = "企业微信发送应用消息，类型为文本卡片")
    public SendMessageToolResponse sendTextCard(SendTextCardToolRequest toolRequest) throws IOException {
        log.info("调用 MCP 工具进行企业微信应用文本卡片消息");
        return weComPort.sendTextCard(toolRequest);
    }

    @Tool(description = "企业微信发送应用消息，类型为纯文本")
    public SendMessageToolResponse sendText(SendTextToolRequest toolRequest) throws IOException {
        log.info("调用 MCP 工具进行企业微信应用文本消息");
        return weComPort.sendText(toolRequest);
    }

}
