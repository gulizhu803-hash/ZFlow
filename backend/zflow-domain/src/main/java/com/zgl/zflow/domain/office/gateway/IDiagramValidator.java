package com.zgl.zflow.domain.office.gateway;
import com.zgl.zflow.domain.office.model.DiagramValidationReport;
public interface IDiagramValidator {
    String VERSION = "drawio-validator-v1";
    String normalize(String xml);
    DiagramValidationReport validate(String xml, boolean generated);
}
