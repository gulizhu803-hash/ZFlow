package com.zgl.zflow.domain.office.model;

import com.zgl.zflow.types.exception.OfficeException;
import java.util.List;
import java.util.Set;

public record OfficeRequest(String sessionId, String prompt, List<String> artifactTypes,
                            String diagramType, String title, String retryOfRunId) {
    public OfficeRequest normalize() {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 128)
            throw OfficeException.invalid("sessionId 必填且不能超过 128 字符");
        String p = requiredText(prompt, 8000, "prompt");
        String t = title == null ? "未命名图表" : requiredText(title, 200, "title");
        if (!List.of("DRAWIO").equals(artifactTypes))
            throw new OfficeException(422, "UNSUPPORTED_ARTIFACT_TYPE", "首版仅支持 DRAWIO");
        String d = diagramType == null ? "FLOWCHART" : diagramType;
        if (!Set.of("FLOWCHART", "ARCHITECTURE", "SEQUENCE", "ER").contains(d))
            throw OfficeException.invalid("diagramType 不合法");
        if (retryOfRunId != null && (retryOfRunId.isBlank() || retryOfRunId.length() > 64))
            throw OfficeException.invalid("retryOfRunId 不合法");
        return new OfficeRequest(sessionId, p, List.of("DRAWIO"), d, t, retryOfRunId);
    }
    public OfficeRequest withoutRetry() { return new OfficeRequest(sessionId, prompt, artifactTypes, diagramType, title, null); }
    public String canonical() {
        return OfficeJson.json(OfficeJson.map("sessionId", sessionId, "prompt", prompt,
                "artifactTypes", artifactTypes, "diagramType", diagramType, "title", title, "retryOfRunId", retryOfRunId));
    }
    public static String requiredText(String value, int max, String name) {
        if (value == null || value.strip().isEmpty() || value.strip().codePointCount(0, value.strip().length()) > max)
            throw OfficeException.invalid(name + " 长度不合法");
        return value.strip();
    }
}
