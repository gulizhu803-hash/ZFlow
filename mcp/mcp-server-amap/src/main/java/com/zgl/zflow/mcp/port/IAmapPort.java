package com.zgl.zflow.mcp.port;

import com.zgl.zflow.mcp.dto.CheckWeatherToolRequest;
import com.zgl.zflow.mcp.dto.CheckWeatherToolResponse;

import java.io.IOException;

public interface IAmapPort {

    CheckWeatherToolResponse checkWeather(CheckWeatherToolRequest toolRequest) throws IOException;

}
