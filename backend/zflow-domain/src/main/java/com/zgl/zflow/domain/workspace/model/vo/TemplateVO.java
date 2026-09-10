package com.zgl.zflow.domain.workspace.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateVO {

    // template 信息
    private String templateId;
    private LocalDateTime createTime;

    // user 信息
    private String userName;

    // plaza 信息
    private String plazaTitle;
    private String plazaDesc;
    private Integer likeCount;
    private Integer favorCount;
    private Integer commentCount;

    // agent 信息
    private String agentName;
    private String agentType;
    private String agentDesc;

    // model 信息
    private String apiUrl;
    private String apiCompletionUrl;
    private String modelName;
    private String modelType;

    private List<McpInfo> mcpInfoList;
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpInfo {
        private String mcpName;
        private String mcpType;
        private String mcpParam;
        private String mcpDesc;
        private List<String> requiredSecrets;
    }

    private List<ClientInfo> clientInfoList;
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientInfo {
        private String clientRole;
        private String systemPrompt;
        private String userPrompt;
    }

}
