package com.zgl.zflow.domain.office.model;

import com.zgl.zflow.types.exception.OfficeException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record OfficeRequest(String sessionId, String prompt, List<String> artifactTypes,
                            String diagramType, String title, String retryOfRunId,
                            String theme, Integer targetSlides) {

    /** 已登记的产物类型。PPTX 只登记类型，渲染阶段由 ArtifactRenderers 明确返回“尚未实现”。 */
    public static final List<String> SUPPORTED_ARTIFACT_TYPES = List.of("DRAWIO", "XLSX", "DOCX", "PPTX");
    public static final String DEFAULT_THEME = "default";
    private static final Set<String> SUPPORTED = Set.copyOf(SUPPORTED_ARTIFACT_TYPES);
    private static final Set<String> DIAGRAM_TYPES = Set.of("FLOWCHART", "ARCHITECTURE", "SEQUENCE", "ER");
    private static final Pattern THEME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    /** 兼容改动前的 6 参构造：未给出的 theme / targetSlides 走默认值。 */
    public OfficeRequest(String sessionId, String prompt, List<String> artifactTypes,
                         String diagramType, String title, String retryOfRunId) {
        this(sessionId, prompt, artifactTypes, diagramType, title, retryOfRunId, null, 0);
    }

    public OfficeRequest normalize() {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 128)
            throw OfficeException.invalid("sessionId 必填且不能超过 128 字符");
        String p = requiredText(prompt, 8000, "prompt");
        String t = title == null ? "未命名图表" : requiredText(title, 200, "title");
        List<String> types = normalizeTypes(artifactTypes);
        // diagramType 只服务于 DRAWIO：不含 DRAWIO 的请求既不校验也不保留它。
        String d = null;
        if (types.contains("DRAWIO")) {
            d = diagramType == null ? "FLOWCHART" : diagramType;
            if (!DIAGRAM_TYPES.contains(d)) throw OfficeException.invalid("diagramType 不合法");
        }
        String th = theme == null || theme.isBlank() ? DEFAULT_THEME : theme.strip();
        if (!THEME.matcher(th).matches()) throw OfficeException.invalid("theme 不合法：" + th);
        int slides = targetSlides == null ? 0 : targetSlides;
        if (slides < 0) throw OfficeException.invalid("targetSlides 不能为负数：" + slides);
        if (retryOfRunId != null && (retryOfRunId.isBlank() || retryOfRunId.length() > 64))
            throw OfficeException.invalid("retryOfRunId 不合法");
        return new OfficeRequest(sessionId, p, List.copyOf(types), d, t, retryOfRunId, th, slides);
    }
    public OfficeRequest withoutRetry() { return new OfficeRequest(sessionId, prompt, artifactTypes, diagramType, title, null, theme, targetSlides); }
    public String canonical() {
        Map<String, Object> values = OfficeJson.map("sessionId", sessionId, "prompt", prompt,
                "artifactTypes", artifactTypes, "diagramType", diagramType, "title", title, "retryOfRunId", retryOfRunId);
        // 新字段只在非默认值时进入哈希，保证旧 DRAWIO 请求的 canonical() 与改动前逐字相同。
        String th = theme == null || theme.isBlank() ? DEFAULT_THEME : theme;
        if (!DEFAULT_THEME.equals(th)) values.put("theme", th);
        int slides = targetSlides == null ? 0 : targetSlides;
        if (slides != 0) values.put("targetSlides", slides);
        return OfficeJson.json(values);
    }
    public static String requiredText(String value, int max, String name) {
        if (value == null || value.strip().isEmpty() || value.strip().codePointCount(0, value.strip().length()) > max)
            throw OfficeException.invalid(name + " 长度不合法");
        return value.strip();
    }
    /** 白名单闸门：非空、元素全部已登记、按请求顺序去重；拒绝时把非法值带进错误信息。 */
    private static List<String> normalizeTypes(List<String> requested) {
        if (requested == null || requested.isEmpty())
            throw new OfficeException(422, "UNSUPPORTED_ARTIFACT_TYPE",
                    "artifactTypes 不能为空，支持的类型：" + String.join("/", SUPPORTED_ARTIFACT_TYPES));
        List<String> types = new ArrayList<>();
        Set<String> rejected = new LinkedHashSet<>();
        for (String type : requested) {
            if (type != null && SUPPORTED.contains(type)) {
                if (!types.contains(type)) types.add(type);
            } else {
                rejected.add(String.valueOf(type));
            }
        }
        if (!rejected.isEmpty())
            throw new OfficeException(422, "UNSUPPORTED_ARTIFACT_TYPE",
                    "不支持的产物类型：" + String.join(",", rejected) + "，支持的类型：" + String.join("/", SUPPORTED_ARTIFACT_TYPES));
        return types;
    }
}
