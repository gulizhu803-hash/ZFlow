package com.zgl.zflow.domain.office.gateway;
import com.zgl.zflow.domain.office.model.OfficeRequest;
import java.time.Duration;

public interface IOfficeGenerationGateway {
    String snapshot();
    void checkAvailable();
    String fixture();
    Generation generate(OfficeRequest request, String previousXml, String validationSummary, Duration timeout);
    record Generation(String xml, Long inputTokens, Long outputTokens, Long totalTokens, String providerRequestId) {}
    class Failure extends RuntimeException {
        public final String errorKey;
        public final boolean retryable;
        public final boolean uncertain;
        public final long retryAfterMillis;
        public Failure(String errorKey, boolean retryable, boolean uncertain, long retryAfterMillis) {
            super(errorKey);
            this.errorKey = errorKey; this.retryable = retryable; this.uncertain = uncertain; this.retryAfterMillis = retryAfterMillis;
        }
    }
}
