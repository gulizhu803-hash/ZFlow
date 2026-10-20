package com.zgl.zflow.domain.office.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zflow.office")
public class OfficeSettings {
    private boolean enabled;
    private String generationMode = "model";
    private String modelBean = "SystemModel";
    private String storageRoot;
    private Executor executor = new Executor();
    private Sse sse = new Sse();
    private long dispatchIntervalMs = 1000;
    private int maxActiveRunsPerUser = 3;
    private int maxQueueWaitSeconds = 600;
    private int runTimeoutSeconds = 300;
    private int cancelGraceSeconds = 15;
    private int modelConnectTimeoutSeconds = 10;
    private int modelCallTimeoutSeconds = 120;
    private int maxModelRequests = 4;
    private int maxTransportRetries = 2;
    private int maxValidationRepairs = 1;
    private int maxModelOutputTokens = 8192;
    private int maxModelResponseBytes = 8388608;
    private int maxXmlBytes = 1048576;
    private int maxJsonRequestBytes = 8388608;
    private int maxDiagramPages = 10;
    private int maxDiagramNodes = 500;
    private int maxDiagramEdges = 500;
    @Data public static class Executor {
        private int corePoolSize = 2;
        private int maxPoolSize = 2;
        private int queueCapacity = 16;
    }
    @Data public static class Sse {
        private int heartbeatSeconds = 15;
        private int connectionSeconds = 60;
        private int maxConnectionsPerUser = 3;
        private int maxConnectionsTotal = 20;
    }
}
