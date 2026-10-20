package com.zgl.zflow.infrastructure.office;

import com.zgl.zflow.domain.office.gateway.*;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.types.exception.OfficeException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.*;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.*;
import java.util.function.Supplier;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

public class OfficeGenerationGateway implements IOfficeGenerationGateway {
    public record ModelConfig(String baseUrl,String apiKey,String model) {}
    private final OfficeSettings settings;
    private final Supplier<ModelConfig> config;
    private final HttpClient http;
    private final String prompt;
    private final String fixture;
    public OfficeGenerationGateway(OfficeSettings settings,Supplier<ModelConfig> config) throws IOException {
        this.settings=settings;this.config=config;
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.getModelConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        prompt=new ClassPathResource("office/diagram-system-v1.txt").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        fixture=new ClassPathResource("office/order-architecture.drawio").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
    @Override public String snapshot() {
        boolean sample="fixture".equals(settings.getGenerationMode()); ModelConfig c=config.get();
        return json(map("generationMode",settings.getGenerationMode(),"providerConfigId",sample?null:settings.getModelBean(),
                "configRevision",hash(sample?fixture:json(map("baseUrl",c.baseUrl(),"model",c.model(),"temperature",0.2,
                        "maxOutputTokens",settings.getMaxModelOutputTokens()))),
                "model",sample?null:c.model(),"temperature",sample?null:0.2,"maxOutputTokens",sample?null:settings.getMaxModelOutputTokens(),
                "promptVersion",sample?null:"diagram-system-v1","promptHash",sample?null:hash(prompt),
                "generatorVersion",sample?"fixture-v1":"drawio-xml-v1","validatorVersion",IDiagramValidator.VERSION));
    }
    @Override public void checkAvailable() {
        if("fixture".equals(settings.getGenerationMode()))return;
        ModelConfig c=config.get();
        if(!"model".equals(settings.getGenerationMode())||c==null||blank(c.baseUrl())||blank(c.apiKey())||
                "not-configured".equals(c.apiKey())||blank(c.model()))
            throw new OfficeException(503,"OFFICE_MODEL_UNAVAILABLE","办公模型尚未配置");
    }
    private boolean blank(String value){return value==null||value.isBlank();}
    @Override public String fixture(){return fixture;}
    @Override public Generation generate(OfficeRequest request,String previousXml,String validationSummary,Duration timeout) {
        checkAvailable(); ModelConfig c=config.get();
        try {
            var transport=new BoundedModelHttpRequestFactory(http,timeout,settings.getMaxModelResponseBytes());
            OpenAiApi api=OpenAiApi.builder().baseUrl(c.baseUrl()).apiKey(c.apiKey()).completionsPath("/v1/chat/completions")
                    .restClientBuilder(RestClient.builder().requestFactory(transport))
                    .responseErrorHandler(new ResponseErrorHandler(){
                        public boolean hasError(ClientHttpResponse response)throws IOException{return response.getStatusCode().isError();}
                        public void handleError(ClientHttpResponse response)throws IOException {
                            int status=response.getStatusCode().value();
                            throw new Failure(status==401||status==403?"MODEL_AUTH_FAILED":"MODEL_HTTP_ERROR",
                                    status==429||status==500||status==502||status==503||status==504,false,retryAfter(response));
                        }
                    }).build();
            OpenAiChatModel model=OpenAiChatModel.builder().openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder().model(c.model()).temperature(0.2)
                            .maxTokens(settings.getMaxModelOutputTokens()).internalToolExecutionEnabled(false).build())
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
            String user="图表类型："+request.diagramType()+"\n标题："+request.title()+"\n需求：\n"+request.prompt();
            if(previousXml!=null)user+="\n请修复以下图表，只返回完整 XML。校验摘要：\n"+validationSummary+"\n原图表：\n"+previousXml;
            var response=ChatClient.create(model).prompt().system(prompt).user(user).call().chatResponse();
            if(response==null||response.getResult()==null||response.getResult().getOutput()==null)throw new Failure("MODEL_EMPTY_RESPONSE",false,false,0);
            if("length".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason()))throw new Failure("MODEL_OUTPUT_TRUNCATED",false,false,0);
            // Spring AI may supply zero defaults or derive totals. Preserve only provider-supplied fields.
            var raw=com.alibaba.fastjson2.JSON.parseObject(transport.responseBody());
            var usage=raw.getJSONObject("usage");
            return new Generation(response.getResult().getOutput().getText(),
                    usage==null?null:usage.getLong("prompt_tokens"),usage==null?null:usage.getLong("completion_tokens"),
                    usage==null?null:usage.getLong("total_tokens"),safeRequestId(raw.getString("id")));
        } catch(Failure e){throw e;}
        catch(Exception e) {
            for(Throwable t=e;t!=null;t=t.getCause()) {
                if(t instanceof Failure f)throw f;
                if(t instanceof BoundedModelHttpRequestFactory.ResponseTooLarge)throw new Failure("MODEL_RESPONSE_TOO_LARGE",false,false,0);
            }
            throw new Failure("MODEL_TRANSPORT_ERROR",true,true,0);
        }
    }
    private String safeRequestId(String value){return value==null?null:value.substring(0,Math.min(128,value.length()));}
    private long retryAfter(ClientHttpResponse response) {
        String value=response.getHeaders().getFirst("Retry-After");
        if(value==null)return 0;
        try{return Math.max(0,Math.multiplyExact(Long.parseLong(value),1000));}
        catch(Exception ignored) {
            try{return Math.max(0,Duration.between(Instant.now(),ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());}
            catch(Exception invalid){return 0;}
        }
    }
}
