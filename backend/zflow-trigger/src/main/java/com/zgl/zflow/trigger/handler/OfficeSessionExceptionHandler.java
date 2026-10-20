package com.zgl.zflow.trigger.handler;
import com.zgl.zflow.trigger.controller.SessionController;
import com.zgl.zflow.types.exception.OfficeException;
import com.zgl.zflow.types.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=SessionController.class)
public class OfficeSessionExceptionHandler {
    @ExceptionHandler(OfficeException.class)
    public ResponseEntity<Result<?>> office(OfficeException e){return OfficeExceptionHandler.response(e);}
}
