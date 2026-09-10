package com.zgl.zflow.types.exception;

public class MissingException extends ZFlowException {
    public MissingException(String message) {
        super(message);
    }

    public MissingException(String message, Throwable cause) {
        super(message, cause);
    }

    public MissingException(Throwable cause) {
        super(cause);
    }
}
