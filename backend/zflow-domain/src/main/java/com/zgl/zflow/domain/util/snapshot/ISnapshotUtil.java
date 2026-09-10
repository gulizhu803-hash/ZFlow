package com.zgl.zflow.domain.util.snapshot;

import com.zgl.zflow.domain.workspace.model.vo.TemplateVO;

import java.util.List;

public interface ISnapshotUtil {

    String buildSnapshot(String agentId);

    SnapshotView parseSnapshot(String snapshotRaw);

    List<TemplateVO.ClientInfo> toTemplateClientInfoList(List<SnapshotView.PromptView> promptViewList);

    List<TemplateVO.McpInfo> toTemplateMcpInfoList(List<SnapshotView.McpView> mcpViewList);

}
