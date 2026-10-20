package com.zgl.zflow.config.office;

import com.zgl.zflow.domain.office.gateway.*;
import com.zgl.zflow.domain.office.model.OfficeSettings;
import com.zgl.zflow.infrastructure.office.*;
import com.zgl.zflow.properties.SystemModelProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OfficeSettings.class)
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeConfiguration {
    @Bean public IDiagramValidator officeDiagramValidator(OfficeSettings s){return new DiagramValidator(s);}
    @Bean public IArtifactStorage officeArtifactStorage(OfficeSettings s){
        validate(s); return new LocalArtifactStorage(s.getStorageRoot(),s.getMaxXmlBytes());
    }
    @Bean public IOfficeGenerationGateway officeGenerationGateway(OfficeSettings s,SystemModelProperties p)throws IOException{
        return new OfficeGenerationGateway(s,()->new OfficeGenerationGateway.ModelConfig(p.getBaseUrl(),p.getApiKey(),p.getModel()));
    }
    @Bean(name="officeTaskExecutor")
    public ThreadPoolTaskExecutor officeTaskExecutor(OfficeSettings s) {
        return executor("office-worker-",s.getExecutor().getCorePoolSize(),s.getExecutor().getMaxPoolSize(),s.getExecutor().getQueueCapacity());
    }
    @Bean(name="officeSseExecutor")
    public ThreadPoolTaskExecutor officeSseExecutor(OfficeSettings s) {
        return executor("office-sse-",s.getSse().getMaxConnectionsTotal(),s.getSse().getMaxConnectionsTotal(),0);
    }
    private ThreadPoolTaskExecutor executor(String prefix,int core,int max,int capacity) {
        ThreadPoolTaskExecutor e=new ThreadPoolTaskExecutor(); e.setThreadNamePrefix(prefix);
        e.setCorePoolSize(core);e.setMaxPoolSize(max);e.setQueueCapacity(capacity);
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());e.setWaitForTasksToCompleteOnShutdown(false);
        return e;
    }
    private void validate(OfficeSettings s) {
        if(!java.util.Set.of("model","fixture").contains(s.getGenerationMode())||!"SystemModel".equals(s.getModelBean()))
            throw new IllegalArgumentException("M1 仅支持 SystemModel 配置来源与 model/fixture 模式");
        if(s.getMaxModelRequests()<1||s.getMaxModelRequests()>4||s.getMaxTransportRetries()<0||s.getMaxTransportRetries()>2||
                s.getMaxValidationRepairs()<0||s.getMaxValidationRepairs()>1)
            throw new IllegalArgumentException("办公模型调用预算超出 M1 上限");
        if(s.getRunTimeoutSeconds()<1||s.getMaxQueueWaitSeconds()<1||s.getCancelGraceSeconds()<1||
                s.getModelCallTimeoutSeconds()<1||s.getModelConnectTimeoutSeconds()<1||s.getMaxActiveRunsPerUser()<1||
                s.getMaxXmlBytes()<1||s.getMaxXmlBytes()>1048576||s.getMaxJsonRequestBytes()<s.getMaxXmlBytes()||
                s.getMaxModelResponseBytes()<1||s.getMaxModelResponseBytes()>8388608||s.getMaxModelOutputTokens()<1||
                s.getExecutor().getCorePoolSize()<1||s.getExecutor().getMaxPoolSize()<s.getExecutor().getCorePoolSize()||
                s.getExecutor().getQueueCapacity()<0||s.getSse().getMaxConnectionsPerUser()<1||s.getSse().getMaxConnectionsTotal()<1||
                s.getSse().getConnectionSeconds()<1||s.getSse().getConnectionSeconds()>60||s.getSse().getHeartbeatSeconds()<1||
                s.getMaxDiagramPages()<1||s.getMaxDiagramPages()>10||s.getMaxDiagramNodes()<1||s.getMaxDiagramNodes()>500||
                s.getMaxDiagramEdges()<1||s.getMaxDiagramEdges()>500)
            throw new IllegalArgumentException("办公运行、文件或连接限制不合法");
    }
}
