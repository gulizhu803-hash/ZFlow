package com.zgl.zflow.domain.office.model;
import java.util.List;
public record DiagramValidationReport(int schemaVersion, String validatorVersion, boolean valid,
        int pageCount, int nodeCount, int edgeCount, List<Issue> issues, boolean truncated) {
    public record Issue(String code, String severity, String pageId, String cellId, String message) {}
}
