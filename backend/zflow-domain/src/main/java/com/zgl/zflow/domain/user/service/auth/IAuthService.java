package com.zgl.zflow.domain.user.service.auth;

import com.zgl.zflow.domain.user.model.dto.AuthDTO;
import com.zgl.zflow.domain.user.model.vo.AuthVO;

public interface IAuthService {

    AuthVO login(AuthDTO dto);

    AuthVO register(AuthDTO dto);

}
