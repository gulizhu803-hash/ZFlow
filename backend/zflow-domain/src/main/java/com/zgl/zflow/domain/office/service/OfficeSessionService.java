package com.zgl.zflow.domain.office.service;
import com.zgl.zflow.domain.office.model.OfficeSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeSessionService {
    private final OfficeTransactions transactions;
    public OfficeSession create(long ownerId,String title) { return transactions.createSession(ownerId,title); }
    public void delete(long ownerId,String sessionId) { transactions.deleteSession(ownerId,sessionId); }
}
