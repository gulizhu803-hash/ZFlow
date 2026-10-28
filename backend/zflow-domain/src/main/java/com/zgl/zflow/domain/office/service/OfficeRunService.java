package com.zgl.zflow.domain.office.service;

import com.zgl.zflow.domain.office.gateway.IArtifactRenderer;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.types.exception.OfficeException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import java.util.List;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeRunService {
    private final IOfficeRepository repository;
    private final OfficeTransactions transactions;
    private final List<IArtifactRenderer<?>> artifactRenderers;
    public OfficeTransactions.Accepted create(long ownerId,String key,OfficeRequest input) {
        key(key); OfficeRequest request=input.normalize();
        // 能力闸门必须早于任何落库动作：否则未实现的类型会留下 status=QUEUED 的孤儿 run。
        requireImplemented(request);
        String requestHash=hash(request.canonical());
        OfficeRun previous;
        try { previous=repository.findRequest(ownerId,key); }
        catch(RuntimeException failure) {
            // 故障演练实测：MySQL 整库不可用时，第一个数据库访问就失败，此时可以确定“没有提交”。
            if(isDatabaseFailure(failure)) throw unavailable();
            throw failure;
        }
        if(previous!=null) return OfficeTransactions.replay(previous,requestHash);
        try { return transactions.accept(ownerId,key,request); }
        // MyBatis 把连接失败包成 MyBatisSystemException（不是 DataAccessException），所以按类型链判断，
        // 而不是只 catch DataAccessException —— 否则“提交结果未知”的分支永远不会被触发。
        catch(RuntimeException failure) {
            if(!isDatabaseFailure(failure)) throw failure;
            // Probe outside the failed transaction; COMMIT may already have succeeded.
            try {
                OfficeRun committed=repository.findRequest(ownerId,key);
                if(committed!=null) return OfficeTransactions.replay(committed,requestHash);
            } catch(RuntimeException unavailable) {
                if(!isDatabaseFailure(unavailable)) throw unavailable;
                throw unknownCommit();
            }
            throw failure;
        }
    }
    /**
     * 产物能力闸门：DRAWIO 走老的 DiagramSpecRenderer 路径，豁免；
     * 其它类型必须能在注入的渲染器列表里按 type() 找到实现，否则入口即拒绝。
     * 直接依赖 domain 自己的 IArtifactRenderer，不引用 infrastructure 的注册表，保持分层。
     */
    private void requireImplemented(OfficeRequest request) {
        for (String type : request.artifactTypes()) {
            if ("DRAWIO".equals(type) || implemented(type)) continue;
            throw new OfficeException(503, "OFFICE_ARTIFACT_UNSUPPORTED", "该产物类型尚未实现：" + type);
        }
    }
    private boolean implemented(String type) {
        if (artifactRenderers == null) return false;
        for (IArtifactRenderer<?> renderer : artifactRenderers) {
            if (renderer != null && type.equals(renderer.type())) return true;
        }
        return false;
    }
    public static OfficeException unknownCommit() {
        return new OfficeException(503,"OFFICE_COMMIT_OUTCOME_UNKNOWN","暂时无法核实提交结果，请保留原幂等键重试");
    }
    public static OfficeException unavailable() {
        return new OfficeException(503,"OFFICE_DB_UNAVAILABLE","数据库暂不可用，请稍后用相同的幂等键重试");
    }
    /**
     * 判断异常链里是否包含数据库故障。
     * domain 模块不依赖 MyBatis / JDBC，因此按类型名识别，避免把参数校验之类的业务异常误判成数据库故障。
     */
    static boolean isDatabaseFailure(Throwable failure) {
        for(Throwable cause=failure; cause!=null; cause=cause.getCause()) {
            if(cause instanceof DataAccessException || cause instanceof TransactionException) return true;
            if(cause instanceof java.sql.SQLException) return true;
            String name=cause.getClass().getName();
            if(name.startsWith("org.apache.ibatis.exceptions.") || name.startsWith("org.mybatis.spring.")) return true;
            if(cause.getCause()==cause) break;
        }
        return false;
    }
}
