package com.zgl.zflow.domain.ai.service.task;

import com.zgl.zflow.domain.ai.model.vo.ScheduleVO;

import java.util.List;

public interface ITaskService {

    List<ScheduleVO> queryScheduleTaskList();

    void refreshTaskRegistry();

    void cancelTask(String taskId);

    void registerTask(ScheduleVO scheduleVO);

}
