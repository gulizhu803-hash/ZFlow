package com.zgl.zflow.domain.office.gateway;

import com.zgl.zflow.domain.office.model.OfficeRequest;

/**
 * 产物校验器：校验渲染结果。
 * DRAWIO 继续由 IDiagramValidator 负责，本接口只给 XLSX / DOCX / PPTX 等新类型使用。
 */
public interface IArtifactValidator {
    String type();
    void validate(byte[] content, OfficeRequest request);
}
