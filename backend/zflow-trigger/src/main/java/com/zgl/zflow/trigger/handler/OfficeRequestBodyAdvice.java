package com.zgl.zflow.trigger.handler;

import com.zgl.zflow.domain.office.model.OfficeSettings;
import com.zgl.zflow.trigger.controller.OfficeController;
import com.zgl.zflow.types.exception.OfficeException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.io.*;
import java.lang.reflect.Type;

@ControllerAdvice(assignableTypes=OfficeController.class)
@ConditionalOnProperty(prefix="zflow.office",name="enabled",havingValue="true")
public class OfficeRequestBodyAdvice extends RequestBodyAdviceAdapter {
    private final OfficeSettings settings;
    public OfficeRequestBodyAdvice(OfficeSettings settings){this.settings=settings;}
    @Override public boolean supports(MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){return true;}
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c)throws IOException {
        // Read cap applies even to chunked requests without Content-Length.
        byte[] bytes=input.getBody().readNBytes(settings.getMaxJsonRequestBytes()+1);
        if(bytes.length>settings.getMaxJsonRequestBytes())throw new OfficeException(413,"ARTIFACT_CONTENT_TOO_LARGE","请求正文超过大小限制");
        return new HttpInputMessage(){
            public HttpHeaders getHeaders(){return input.getHeaders();}
            public InputStream getBody(){return new ByteArrayInputStream(bytes);}
        };
    }
}
