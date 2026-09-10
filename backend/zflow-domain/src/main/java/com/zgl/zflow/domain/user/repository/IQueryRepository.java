package com.zgl.zflow.domain.user.repository;

import com.zgl.zflow.domain.user.model.vo.*;

import java.util.List;

public interface IQueryRepository {

    List<QueryChatClientVO> queryChatClientList();

    List<QueryRagVO> queryRagList();

    List<QueryMcpVO> queryMcpList();

    List<QueryWorkAgentVO> queryWorkAgentList();

    List<QueryModelVO> queryModelList();
}
