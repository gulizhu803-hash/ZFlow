package com.zgl.zflow.types.exception;

public class ZFlowException extends RuntimeException {
    public ZFlowException(String message) {
        super(message);
    }

    public ZFlowException(String message, Throwable cause) {
        super(message, cause);
    }

    public ZFlowException(Throwable cause) {
        super(cause);
    }
}
