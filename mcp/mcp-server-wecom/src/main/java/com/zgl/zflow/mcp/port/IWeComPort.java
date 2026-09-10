package com.zgl.zflow.mcp.port;

import com.zgl.zflow.mcp.dto.SendMessageToolResponse;
import com.zgl.zflow.mcp.dto.SendTextCardToolRequest;
import com.zgl.zflow.mcp.dto.SendTextToolRequest;

import java.io.IOException;

public interface IWeComPort {

    SendMessageToolResponse sendTextCard(SendTextCardToolRequest toolRequest) throws IOException;

    SendMessageToolResponse sendText(SendTextToolRequest toolRequest) throws IOException;

}
