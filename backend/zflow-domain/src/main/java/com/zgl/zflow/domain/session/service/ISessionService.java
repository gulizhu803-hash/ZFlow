package com.zgl.zflow.domain.session.service;

import com.zgl.zflow.domain.session.model.vo.MessageVO;
import com.zgl.zflow.domain.session.model.vo.SessionVO;

import java.util.List;

public interface ISessionService {

    List<SessionVO> listSession();

    SessionVO insertSession(String sessionTitle, String sessionType);

    void updateSession(String sessionId, String sessionTitle);

    void deleteSession(String sessionId);

    List<MessageVO> listChatMessage(String sessionId);

    List<MessageVO> listWorkSseMessage(String sessionId);

    List<MessageVO> listWorkAnswerMessage(String sessionId);

    String validateSessionAccess(String sessionId, String expectedSessionType);

}
