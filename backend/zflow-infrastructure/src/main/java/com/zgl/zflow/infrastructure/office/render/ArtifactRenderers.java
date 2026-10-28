package com.zgl.zflow.infrastructure.office.render;

import com.zgl.zflow.domain.office.gateway.IArtifactRenderer;
import com.zgl.zflow.types.exception.OfficeException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 渲染器注册表：按产物类型查找实现，未实现的类型统一返回 503。 */
@Component
public class ArtifactRenderers {

    private final Map<String, IArtifactRenderer<?>> renderers;

    public ArtifactRenderers(List<IArtifactRenderer<?>> renderers) {
        Map<String, IArtifactRenderer<?>> byType = new LinkedHashMap<>();
        for (IArtifactRenderer<?> renderer : renderers) {
            String type = key(renderer.type());
            if (byType.putIfAbsent(type, renderer) != null)
                throw new IllegalStateException("产物渲染器重复注册：" + type);
        }
        this.renderers = byType;
    }

    public IArtifactRenderer<?> rendererFor(String type) {
        IArtifactRenderer<?> renderer = renderers.get(key(type));
        if (renderer == null) throw unsupported();
        return renderer;
    }

    private static String key(String type) { return type == null ? "" : type.strip().toUpperCase(Locale.ROOT); }

    /** PPTX 等只登记、未实现的类型在这里统一失败，不进入渲染分支。 */
    private static OfficeException unsupported() {
        return new OfficeException(503, "OFFICE_ARTIFACT_UNSUPPORTED", "该产物类型尚未实现");
    }
}
