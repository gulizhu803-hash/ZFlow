package com.zgl.zflow.types.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class OfficeException extends RuntimeException {
    private final int status;
    private final String errorKey;
    private final Map<String, Object> details;

    public OfficeException(int status, String errorKey, String message) {
        this(status, errorKey, message, Map.of());
    }
    public OfficeException(int status, String errorKey, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.errorKey = errorKey;
        this.details = new LinkedHashMap<>(details);
    }
    public int getStatus() { return status; }
    public String getErrorKey() { return errorKey; }
    public Map<String, Object> getDetails() { return details; }
    public static OfficeException notFound() {
        return new OfficeException(404, "OFFICE_RESOURCE_NOT_FOUND", "办公资源不存在或不可访问");
    }
    public static OfficeException invalid(String message) {
        return new OfficeException(400, "INVALID_REQUEST", message);
    }
}
