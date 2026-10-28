package com.zgl.zflow.domain.office.gateway;

import com.zgl.zflow.domain.office.model.OfficeRequest;

/**
 * 产物渲染器：把模型输出的 spec 渲染成目标文件字节。
 * DRAWIO 仍走 DiagramSpecRenderer；XLSX / DOCX / PPTX 各自实现本接口后注册进 ArtifactRenderers。
 */
public interface IArtifactRenderer<T> {
    String type();
    String contentType();
    String fileExtension();
    T parseSpec(String json);
    byte[] render(T spec, OfficeRequest request);
}
