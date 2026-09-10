package com.zgl.zflow.types.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class DependencyConflictException extends ZFlowException {

    public DependencyConflictException(String message) {
        super(message);
    }

}
