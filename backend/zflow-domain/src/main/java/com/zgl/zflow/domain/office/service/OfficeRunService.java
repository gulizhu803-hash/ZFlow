package com.zgl.zflow.domain.office.service;

import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.types.exception.OfficeException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeRunService {
    private final IOfficeRepository repository;
    private final OfficeTransactions transactions;
    public OfficeTransactions.Accepted create(long ownerId,String key,OfficeRequest input) {
        key(key); OfficeRequest request=input.normalize(); String requestHash=hash(request.canonical());
        OfficeRun previous=repository.findRequest(ownerId,key);
        if(previous!=null) return OfficeTransactions.replay(previous,requestHash);
        try { return transactions.accept(ownerId,key,request); }
        catch(DataAccessException|TransactionException failure) {
            // Probe outside the failed transaction; COMMIT may already have succeeded.
            try {
                OfficeRun committed=repository.findRequest(ownerId,key);
                if(committed!=null) return OfficeTransactions.replay(committed,requestHash);
            } catch(DataAccessException|TransactionException unavailable) { throw unknownCommit(); }
            throw failure;
        }
    }
    public static OfficeException unknownCommit() {
        return new OfficeException(503,"OFFICE_COMMIT_OUTCOME_UNKNOWN","暂时无法核实提交结果，请保留原幂等键重试");
    }
}
