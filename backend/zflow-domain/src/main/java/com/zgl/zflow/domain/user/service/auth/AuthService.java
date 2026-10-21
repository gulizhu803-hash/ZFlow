package com.zgl.zflow.domain.user.service.auth;

import com.zgl.zflow.domain.user.model.vo.UserVO;
import com.zgl.zflow.domain.user.repository.IUserRepository;
import com.zgl.zflow.domain.util.jwt.IJwtUtil;
import com.zgl.zflow.domain.util.jwt.UserContext;
import com.zgl.zflow.domain.util.oss.IOssUtil;
import com.zgl.zflow.domain.user.model.dto.AuthDTO;
import com.zgl.zflow.domain.user.model.vo.AuthVO;
import com.zgl.zflow.types.exception.AuthException;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.zgl.zflow.types.constant.ExceptionMessage.*;

@Service
public class AuthService implements IAuthService {

    @Resource
    private IUserRepository userRepository;

    @Resource
    private IJwtUtil jwtUtil;

    @Resource
    private IOssUtil ossUtil;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private UserContext userContext;

    /**
     * 用当前有效 token 换一枚新的短期 token。
     * 旧 token 到期前仍然可用（无状态 JWT 的固有代价），
     * 真正吊销需要 jti 黑名单或轮换 JWT_SECRET。
     */
    @Override
    public AuthVO refresh() {
        Long userId = userContext.getUserId();
        if (userId == null) {
            throw new AuthException(AUTH_USER_NOT_EXISTS);
        }
        UserVO userVO = userRepository.queryUserByUserName(userContext.getUserName());
        if (userVO == null || Integer.valueOf(0).equals(userVO.getUserStatus())) {
            throw new AuthException(AUTH_USER_UNAVAILABLE);
        }
        return buildAuthResponse(userVO);
    }

    @Override
    public AuthVO login(AuthDTO dto) {

        String userName = dto.getUserName();
        String password = dto.getPassword();

        UserVO userVO = userRepository.queryUserByUserName(userName);
        if (userVO == null) {
            throw new AuthException(AUTH_USER_NOT_EXISTS);
        }
        if (Integer.valueOf(0).equals(userVO.getUserStatus())) {
            throw new AuthException(AUTH_USER_UNAVAILABLE);
        }
        if (!passwordEncoder.matches(password, userVO.getPassword())) {
            throw new AuthException(AUTH_LOGIN_FAIL);
        }

        return buildAuthResponse(userVO);
    }

    @Override
    public AuthVO register(AuthDTO dto) {

        String userName = dto.getUserName();
        String password = dto.getPassword();

        if (userRepository.queryUserByUserName(userName) != null) {
            throw new AuthException(AUTH_USER_ALREADY_EXISTS);
        }

        String encodedPwd = passwordEncoder.encode(password);
        UserVO userVO = userRepository.insertUser(userName, encodedPwd);
        return buildAuthResponse(userVO);
    }

    private AuthVO buildAuthResponse(UserVO userVO) {
        String token = jwtUtil.generateToken(userVO);
        return AuthVO.builder()
                .token(token)
                .userName(userVO.getUserName())
                .userRole(userVO.getUserRole())
                .userAvatar(ossUtil.getObjectUrl(userVO.getUserAvatar()))
                .userStatus(userVO.getUserStatus())
                .build();
    }

}
