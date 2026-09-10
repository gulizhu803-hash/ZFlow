package com.zgl.zflow.aop;

import com.zgl.zflow.domain.util.redis.IRedisUtil;
import com.zgl.zflow.types.annotation.CacheEvict;
import com.zgl.zflow.types.enumeration.CacheEvictType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.*;

class CacheEvictAspectTest {
    private final IRedisUtil redis = mock(IRedisUtil.class);
    private final CacheEvictAspect aspect = new CacheEvictAspect();

    @CacheEvict(evictType = CacheEvictType.USER)
    void mutation() {}

    private void invoke() throws Exception {
        ReflectionTestUtils.setField(aspect, "redisUtil", redis);
        aspect.afterReturn(getClass().getDeclaredMethod("mutation").getAnnotation(CacheEvict.class));
    }

    private void begin() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.clear();
    }

    @Test
    void evictsOnlyAfterDatabaseCommit() throws Exception {
        begin();
        invoke();
        verifyNoInteractions(redis);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(redis).deleteByPrefix("workspace:");
        verify(redis).deleteByPrefix("query:");
    }

    @Test
    void rollbackKeepsCache() throws Exception {
        begin();
        invoke();
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(redis);
    }

    @Test
    void nonTransactionalMutationEvictsImmediately() throws Exception {
        invoke();
        verify(redis).deleteByPrefix("workspace:");
    }
}
