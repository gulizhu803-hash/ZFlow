package com.zgl.zflow.aop;

import com.zgl.zflow.domain.util.redis.IRedisUtil;
import com.zgl.zflow.types.annotation.CacheEvict;
import com.zgl.zflow.types.enumeration.CacheEvictType;
import jakarta.annotation.Resource;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Aspect
@Component
public class CacheEvictAspect {

    private static final String[] USER_EVICT_PREFIX_LIST = {"ai:", "query:", "user:", "workspace:", "armory:"};
    private static final String[] ADMIN_EVICT_PREFIX_LIST = {"ai:", "query:", "admin:", "armory:"};

    @Resource
    private IRedisUtil redisUtil;

    @AfterReturning("@annotation(cacheEvict)")
    public void afterReturn(CacheEvict cacheEvict) {
        // Repository methods may return before their enclosing service commits.
        // Evicting now lets concurrent readers cache the old database state again.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(cacheEvict);
                }
            });
        } else {
            evict(cacheEvict);
        }
    }

    private void evict(CacheEvict cacheEvict) {

        String keyPrefix = cacheEvict.keyPrefix();
        if (StringUtils.hasText(keyPrefix)) {
            redisUtil.deleteByPrefix(keyPrefix);
        }

        CacheEvictType evictType = cacheEvict.evictType();
        if (evictType != null) {
            if (evictType.equals(CacheEvictType.USER)) {
                deleteByPrefixList(USER_EVICT_PREFIX_LIST);
            } else if (evictType.equals(CacheEvictType.ADMIN)) {
                deleteByPrefixList(ADMIN_EVICT_PREFIX_LIST);
            }
        }
    }

    private void deleteByPrefixList(String[] adminEvictPrefixList) {
        for (String prefix : adminEvictPrefixList) {
            redisUtil.deleteByPrefix(prefix);
        }
    }


}
