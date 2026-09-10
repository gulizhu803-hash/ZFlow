package com.zgl.zflow.api;

import com.zgl.zflow.domain.session.model.vo.MessageVO;
import com.zgl.zflow.domain.session.model.vo.SessionVO;
import com.zgl.zflow.types.result.Result;

import java.util.List;

public interface ISessionApi {

    Result<List<SessionVO>> listSession();

    Result<SessionVO> insertSession(String sessionTitle, String sessionType);

    Result<Void> updateSession(String sessionId, String sessionTitle);

    Result<Void> deleteSession(String sessionId);

    Result<List<MessageVO>> listChatMessage(String sessionId);

    Result<List<MessageVO>> listWorkSseMessage(String sessionId);

    Result<List<MessageVO>> listWorkAnswerSession(String sessionId);
}
