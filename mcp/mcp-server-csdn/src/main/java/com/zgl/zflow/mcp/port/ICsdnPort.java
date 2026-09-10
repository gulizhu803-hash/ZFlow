package com.zgl.zflow.mcp.port;

import com.zgl.zflow.mcp.dto.SaveArticleToolRequest;
import com.zgl.zflow.mcp.dto.SaveArticleToolResponse;

import java.io.IOException;

public interface ICsdnPort {

    SaveArticleToolResponse saveArticle(SaveArticleToolRequest request) throws IOException;

}
