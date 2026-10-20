package com.zgl.zflow.trigger.handler;

import com.zgl.zflow.trigger.controller.OfficeController;
import com.zgl.zflow.types.exception.OfficeException;
import com.zgl.zflow.types.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import static com.zgl.zflow.domain.office.model.OfficeJson.*;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=OfficeController.class)
public class OfficeExceptionHandler {
    @ExceptionHandler(OfficeException.class)
    public ResponseEntity<Result<?>> office(OfficeException e){return response(e);}
    @ExceptionHandler({HttpMessageNotReadableException.class,MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class,MethodArgumentNotValidException.class})
    public ResponseEntity<Result<?>> invalid(Exception e) {
        for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof OfficeException office)return response(office);
        return response(OfficeException.invalid("请求格式、类型或必填参数不合法"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> unexpected(Exception e) {
        String trace=id("trace");log.error("office request failed traceId={} type={}",trace,e.getClass().getSimpleName());
        return response(new OfficeException(500,"OFFICE_INTERNAL_ERROR","办公请求暂时失败",map("traceId",trace)));
    }
    public static ResponseEntity<Result<?>> response(OfficeException e) {
        Map<String,Object> data=new LinkedHashMap<>(e.getDetails());data.put("errorKey",e.getErrorKey());
        var builder=ResponseEntity.status(e.getStatus()).header("Cache-Control","private, no-store");
        if(e.getStatus()==429)builder.header("Retry-After","2");
        return builder.body(new Result<>(e.getStatus(),e.getMessage(),data));
    }
}
