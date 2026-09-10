package com.zgl.zflow.domain.user.repository;

import com.zgl.zflow.domain.user.model.vo.UserApiModelVO;
import com.zgl.zflow.domain.user.model.vo.UserMcpVO;
import com.zgl.zflow.domain.user.model.vo.UserTaskVO;
import com.zgl.zflow.domain.user.model.vo.UserVO;
import com.zgl.zflow.domain.user.model.dto.SettingApiModelDTO;
import com.zgl.zflow.domain.user.model.dto.SettingMcpDTO;
import com.zgl.zflow.domain.user.model.dto.SettingTaskDTO;

import java.util.List;

public interface IUserRepository {

    UserVO queryUserByUserName(String userName);

    UserVO queryUserById(Long userId);

    UserVO insertUser(String userName, String password);

    UserVO updateUser(Long userId, String userName, String password, String userAvatar);

    List<UserApiModelVO> apiModelList(String keyword);

    void apiModelInsert(SettingApiModelDTO dto, String apiId, String modelId);

    void apiModelUpdate(SettingApiModelDTO dto);

    void apiModelDelete(String apiId);

    List<UserMcpVO> mcpList(String keyword);

    void mcpInsert(SettingMcpDTO dto, String mcpId);

    void mcpUpdate(SettingMcpDTO dto);

    void mcpDelete(String mcpId);

    List<UserTaskVO> taskList();

    void taskInsert(SettingTaskDTO dto, String taskId);

    void taskUpdate(SettingTaskDTO dto);

    void taskDelete(String taskId);

    void taskToggle(String taskId, Integer taskStatus);

}
