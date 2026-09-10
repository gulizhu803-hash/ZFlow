package com.zgl.zflow;

import com.zgl.zflow.mcp.dto.SendMessageToolResponse;
import com.zgl.zflow.mcp.dto.SendTextCardToolRequest;
import com.zgl.zflow.mcp.dto.SendTextToolRequest;
import com.zgl.zflow.mcp.tool.WeComTool;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = McpServerWeComApplication.class)
public class SendMessageTest {

    @Resource
    private WeComTool weComTool;

    @Test
    void testSendTextCardTool() throws Exception {
        SendTextCardToolRequest toolRequest = new SendTextCardToolRequest();
        toolRequest.setTitle("Test");
        toolRequest.setDescription("Test from mcp-server-wecom");
        toolRequest.setUrl("https://example.com");

        SendMessageToolResponse toolResponse = weComTool.sendTextCard(toolRequest);
        System.out.println("WeCom toolResponse\n" + toolResponse);
    }

    @Test
    void testSendTextTool() throws Exception {
        SendTextToolRequest toolRequest = new SendTextToolRequest();
        toolRequest.setContent("Test");

        SendMessageToolResponse toolResponse = weComTool.sendText(toolRequest);
        System.out.println("WeCom response\n" + toolResponse);
    }

}
