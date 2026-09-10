package com.zgl.zflow.infrastructure.persistent.dao;

import com.zgl.zflow.infrastructure.persistent.po.AiStat;
import com.zgl.zflow.infrastructure.persistent.vo.AiStatValueCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface IAiStatDao {

    void upsert(@Param("statDate") LocalDate statDate,
                @Param("statCategory") String statCategory,
                @Param("statKey") String statKey,
                @Param("statValue") String statValue,
                @Param("delta") Integer delta);

    List<AiStatValueCount> sumByCategoryAndKey(@Param("statCategory") String statCategory,
                                               @Param("statKey") String statKey,
                                               @Param("limit") Integer limit);

    AiStat queryByUnique(@Param("statDate") LocalDate statDate,
                         @Param("statCategory") String statCategory,
                         @Param("statKey") String statKey,
                         @Param("statValue") String statValue);
}
