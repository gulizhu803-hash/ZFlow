package com.zgl.zflow.domain.user.service.auth;

import com.zgl.zflow.domain.util.redis.IRedisUtil;
import com.zgl.zflow.types.exception.RateLimitedException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 登录失败计数：同一账号 5 次 / 5 分钟，同一来源 IP 20 次 / 1 分钟。
 * 计数放在 Redis，多实例共享；成功登录只清账号维度，不清 IP 维度，
 * 避免攻击者用正确密码“洗白”同 IP 的其他尝试。
 */
@Slf4j
@Component
public class LoginThrottle {

    private static final String PREFIX = "login:fail:";
    private static final int USER_LIMIT = 5;
    private static final long USER_WINDOW_SECONDS = 300;
    private static final int IP_LIMIT = 20;
    private static final long IP_WINDOW_SECONDS = 60;

    @Resource
    private IRedisUtil redisUtil;

    public void check(String ip, String userName) {
        if (StringUtils.hasText(userName) && count(userKey(userName)) >= USER_LIMIT) {
            throw new RateLimitedException("该账号尝试次数过多，请 5 分钟后再试", USER_WINDOW_SECONDS);
        }
        if (StringUtils.hasText(ip) && count(ipKey(ip)) >= IP_LIMIT) {
            throw new RateLimitedException("当前网络尝试次数过多，请 1 分钟后再试", IP_WINDOW_SECONDS);
        }
    }

    public void onFailure(String ip, String userName) {
        if (StringUtils.hasText(userName)) {
            redisUtil.increment(userKey(userName), USER_WINDOW_SECONDS);
        }
        if (StringUtils.hasText(ip)) {
            redisUtil.increment(ipKey(ip), IP_WINDOW_SECONDS);
        }
    }

    public void onSuccess(String ip, String userName) {
        if (StringUtils.hasText(userName)) {
            redisUtil.deleteByKey(userKey(userName));
        }
    }

    private int count(String key) {
        String value = redisUtil.getValue(key, String.class);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String userKey(String userName) {
        return PREFIX + "user:" + userName.toLowerCase();
    }

    private String ipKey(String ip) {
        return PREFIX + "ip:" + ip;
    }
}
