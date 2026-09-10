package com.zgl.zflow.mcp.port;

import com.zgl.zflow.mcp.dto.SendEmailToolRequest;
import com.zgl.zflow.mcp.dto.SendEmailToolResponse;

import java.io.IOException;

public interface IEmailPort {

    SendEmailToolResponse sendEmail(SendEmailToolRequest toolRequest) throws IOException;

}
