package com.zgl.zflow.domain.workspace.repository;

import com.zgl.zflow.domain.workspace.model.vo.CommentVO;
import com.zgl.zflow.domain.workspace.model.vo.AgentVO;
import com.zgl.zflow.domain.workspace.model.vo.PlazaVO;
import com.zgl.zflow.domain.workspace.model.vo.RepoVO;
import com.zgl.zflow.domain.workspace.model.vo.TemplateVO;
import com.zgl.zflow.domain.workspace.model.dto.AgentUpdateBaseDTO;
import com.zgl.zflow.domain.workspace.model.dto.AgentUpdateMcpDTO;
import com.zgl.zflow.domain.workspace.model.dto.AgentUpdateModelDTO;
import com.zgl.zflow.domain.workspace.model.dto.AgentCreateDTO;
import com.zgl.zflow.domain.workspace.model.dto.AgentPublishDTO;
import com.zgl.zflow.domain.workspace.model.entity.RolePromptEntity;
import com.zgl.zflow.domain.workspace.model.dto.AgentUpdateSystemPromptDTO;
import com.zgl.zflow.domain.workspace.model.dto.AgentUpdateUserPromptDTO;
import com.zgl.zflow.domain.workspace.model.dto.PlazaCommentAreaDTO;
import com.zgl.zflow.domain.workspace.model.dto.PlazaCommentDTO;
import com.zgl.zflow.domain.workspace.model.dto.PlazaPageDTO;

import java.util.List;

public interface IWorkspaceRepository {

    List<PlazaVO> plazaPage(PlazaPageDTO dto);

    Integer plazaCount(PlazaPageDTO dto);

    List<CommentVO> plazaCommentList(PlazaCommentAreaDTO dto);

    Integer plazaCommentCount(String plazaId);

    void plazaLike(String plazaId, boolean liked);

    void plazaFavor(String plazaId, boolean favored);

    void plazaComment(PlazaCommentDTO dto);

    void plazaDiscomment(String plazaId, String commentId);

    void plazaDelete(String plazaId);

    List<RepoVO> repoList();

    void agentPublish(AgentPublishDTO dto);

    void agentFork(String templateId);

    void agentUpdateBase(AgentUpdateBaseDTO dto);

    void agentUpdateModel(AgentUpdateModelDTO dto);

    void agentUpdateMcp(AgentUpdateMcpDTO dto);

    void agentUpdateUserPrompt(AgentUpdateUserPromptDTO dto);

    void agentUpdateSystemPrompt(AgentUpdateSystemPromptDTO dto);

    TemplateVO agentTemplate(String templateId);

    AgentVO agentDetail(String agentId);

    void agentDelete(String agentId);

    void agentCreate(AgentCreateDTO dto, List<RolePromptEntity> rolePromptList);

}
