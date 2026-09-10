package com.zgl.zflow.mcp.port;

import com.zgl.zflow.mcp.dto.BochaSearchToolRequest;
import com.zgl.zflow.mcp.dto.BochaSearchToolResponse;

import java.io.IOException;

public interface IBochaPort {

    BochaSearchToolResponse webSearch(BochaSearchToolRequest toolRequest) throws IOException;

}
