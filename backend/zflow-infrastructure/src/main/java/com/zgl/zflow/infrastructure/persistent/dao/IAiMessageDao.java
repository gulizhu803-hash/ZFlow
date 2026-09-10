package com.zgl.zflow.infrastructure.persistent.dao;

import com.zgl.zflow.infrastructure.persistent.po.AiMessage;
import com.zgl.zflow.infrastructure.persistent.vo.MessageDailyCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAiMessageDao {

    List<AiMessage> queryBySessionAndType(@Param("sessionId") String sessionId,
                                          @Param("messageType") String messageType);

    int countBySessionAndType(@Param("sessionId") String sessionId,
                              @Param("messageType") String messageType);

    Integer maxSeqBySessionAndType(@Param("sessionId") String sessionId,
                                   @Param("messageType") String messageType);

    List<MessageDailyCount> countByDateRange(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    int countAll();

    void insert(AiMessage aiMessage);

    void deleteBySessionId(@Param("sessionId") String sessionId);
}
