package com.zgl.zflow.mcp.tool;

import com.zgl.zflow.mcp.dto.BochaSearchToolRequest;
import com.zgl.zflow.mcp.dto.BochaSearchToolResponse;
import com.zgl.zflow.mcp.port.IBochaPort;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class BochaTool {

    @Resource
    private IBochaPort bochaPort;

    @Tool(description = "通过博查进行联网网页搜索，必须提供 query 和 freshness")
    public BochaSearchToolResponse webSearch(BochaSearchToolRequest toolRequest) throws IOException {
        log.info("调用 MCP 工具进行博查联网搜索：query={}, freshness={}", toolRequest.getQuery(), toolRequest.getFreshness());
        return bochaPort.webSearch(toolRequest);
    }

}
