package com.zgl.zflow.trigger.controller;

import com.zgl.zflow.api.IUserApi;
import com.zgl.zflow.domain.user.model.dto.*;
import com.zgl.zflow.domain.user.model.vo.*;
import com.zgl.zflow.domain.user.service.auth.IAuthService;
import com.zgl.zflow.domain.user.service.auth.LoginThrottle;
import com.zgl.zflow.domain.user.service.query.IQueryService;
import com.zgl.zflow.domain.user.service.setting.ISettingService;
import com.zgl.zflow.types.exception.AuthException;
import com.zgl.zflow.types.result.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController implements IUserApi {

    @Resource
    private IAuthService authService;

    @Resource
    private ISettingService settingService;

    @Resource
    private IQueryService queryService;

    @Resource
    private LoginThrottle loginThrottle;

    @PostMapping("/query/work-agent")
    @Override
    public Result<List<QueryWorkAgentVO>> queryWorkAgentList() {
        return Result.success(queryService.queryWorkAgentList());
    }

    @PostMapping("/query/chat-client")
    @Override
    public Result<List<QueryChatClientVO>> queryChatClientList() {
        return Result.success(queryService.queryChatClientList());
    }

    @PostMapping("/query/mcp")
    @Override
    public Result<List<QueryMcpVO>> queryMcpList() {
        return Result.success(queryService.queryMcpList());
    }

    @PostMapping("/query/rag")
    @Override
    public Result<List<QueryRagVO>> queryRagList() {
        return Result.success(queryService.queryRagList());
    }

    @PostMapping("/query/role")
    @Override
    public Result<Map<String, QueryRoleVO>> queryRoleMap() {
        return Result.success(queryService.queryRoleMap());
    }

    @PostMapping("/query/model")
    public Result<List<QueryModelVO>> queryModelList() {
        return Result.success(queryService.queryModelList());
    }

    @PostMapping("/auth/login")
    @Override
    public Result<AuthVO> login(@Valid @RequestBody AuthDTO dto) {
        String ip = clientIp();
        loginThrottle.check(ip, dto.getUserName());
        try {
            AuthVO auth = authService.login(dto);
            loginThrottle.onSuccess(ip, dto.getUserName());
            return Result.success(auth);
        } catch (AuthException e) {
            loginThrottle.onFailure(ip, dto.getUserName());
            throw e;
        }
    }

    @PostMapping("/auth/refresh")
    @Override
    public Result<AuthVO> refresh() {
        return Result.success(authService.refresh());
    }

    /**
     * 取真实来源 IP：网关会透传 X-Forwarded-For，直连时回落到 remoteAddr。
     */
    private String clientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/auth/register")
    @Override
    public Result<AuthVO> register(@Valid @RequestBody AuthDTO dto) {
        return Result.success(authService.register(dto));
    }

    @PostMapping("/profile/query")
    @Override
    public Result<AuthVO> profileQuery() {
        return Result.success(settingService.profileQuery());
    }

    @PostMapping(value = "/profile/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Override
    public Result<AuthVO> profileEdit(@Valid @RequestPart("profile") ProfileEditDTO dto,
                                      @RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        return Result.success(settingService.profileEdit(dto, avatar));
    }

    @PostMapping(value = "/model/list")
    @Override
    public Result<List<UserApiModelVO>> apiModelList(@RequestParam(required=false, defaultValue = "") String keyword) {
        return Result.success(settingService.apiModelList(keyword));
    }

    @PostMapping(value = "/model/insert")
    @Override
    public Result<Void> apiModelInsert(@Valid @RequestBody SettingApiModelDTO dto) {
        settingService.apiModelInsert(dto);
        return Result.success();
    }

    @PostMapping(value = "/model/update")
    @Override
    public Result<Void> apiModelUpdate(@Valid @RequestBody SettingApiModelDTO dto) {
        settingService.apiModelUpdate(dto);
        return Result.success();
    }

    @PostMapping(value = "/model/delete")
    @Override
    public Result<Void> apiModelDelete(@RequestParam String apiId) {
        settingService.apiModelDelete(apiId);
        return Result.success();
    }

    @PostMapping(value = "/mcp/list")
    @Override
    public Result<List<UserMcpVO>> mcpList(@RequestParam(required = false, defaultValue = "") String keyword) {
        return Result.success(settingService.mcpList(keyword));
    }

    @PostMapping(value = "/mcp/insert")
    @Override
    public Result<Void> mcpInsert(@Valid @RequestBody SettingMcpDTO dto) {
        settingService.mcpInsert(dto);
        return Result.success();
    }

    @PostMapping(value = "/mcp/update")
    @Override
    public Result<Void> mcpUpdate(@Valid @RequestBody SettingMcpDTO dto) {
        settingService.mcpUpdate(dto);
        return Result.success();
    }

    @PostMapping(value = "/mcp/delete")
    @Override
    public Result<Void> mcpDelete(@RequestParam String mcpId) {
        settingService.mcpDelete(mcpId);
        return Result.success();
    }

    @PostMapping(value = "/task/list")
    @Override
    public Result<List<UserTaskVO>> taskList() {
        return Result.success(settingService.taskList());
    }

    @PostMapping(value = "/task/insert")
    @Override
    public Result<Void> taskInsert(@Valid @RequestBody SettingTaskDTO dto) {
        settingService.taskInsert(dto);
        return Result.success();
    }

    @PostMapping(value = "/task/update")
    @Override
    public Result<Void> taskUpdate(@Valid @RequestBody SettingTaskDTO dto) {
        settingService.taskUpdate(dto);
        return Result.success();
    }

    @PostMapping(value = "/task/delete")
    @Override
    public Result<Void> taskDelete(@RequestParam String taskId) {
        settingService.taskDelete(taskId);
        return Result.success();
    }

    @PostMapping(value = "/task/toggle")
    @Override
    public Result<Void> taskToggle(@RequestParam String taskId, @RequestParam Integer taskStatus) {
        settingService.taskToggle(taskId, taskStatus);
        return Result.success();
    }

}
