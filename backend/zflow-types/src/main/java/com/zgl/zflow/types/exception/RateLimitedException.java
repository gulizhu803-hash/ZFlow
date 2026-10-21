package com.zgl.zflow.types.exception;

/**
 * 触发限流：由全局异常处理映射为 HTTP 429，并带 Retry-After。
 */
public class RateLimitedException extends ZFlowException {

    private final long retryAfterSeconds;

    public RateLimitedException(String message) {
        this(message, 60);
    }

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
