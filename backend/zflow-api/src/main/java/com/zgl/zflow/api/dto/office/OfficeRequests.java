package com.zgl.zflow.api.dto.office;

import com.fasterxml.jackson.databind.JsonNode;
import com.zgl.zflow.domain.office.model.OfficeRequest;
import com.zgl.zflow.types.exception.OfficeException;
import java.util.*;

/** Office-only strict parsing, independent of the legacy Jackson coercion settings. */
public final class OfficeRequests {
    private OfficeRequests(){}
    public record CreateSession(String title){}
    public record SaveRevision(int baseVersion,String xml){}
    public static CreateSession session(JsonNode body) {
        fields(body,Set.of("title"));return new CreateSession(text(body,"title",true));
    }
    public static OfficeRequest run(JsonNode body) {
        fields(body,Set.of("sessionId","prompt","artifactTypes","diagramType","title","retryOfRunId","theme","targetSlides"));
        JsonNode types=body.get("artifactTypes");List<String> values=new ArrayList<>();
        if(types==null||!types.isArray())throw OfficeException.invalid("artifactTypes 必须为数组");
        for(JsonNode value:types){if(!value.isTextual())throw OfficeException.invalid("artifactTypes 元素必须为字符串");values.add(value.textValue());}
        return new OfficeRequest(text(body,"sessionId",true),text(body,"prompt",true),values,
                text(body,"diagramType",false),text(body,"title",false),text(body,"retryOfRunId",false),
                text(body,"theme",false),integer(body,"targetSlides"));
    }
    public static SaveRevision save(JsonNode body) {
        fields(body,Set.of("baseVersion","xml"));JsonNode version=body.get("baseVersion");
        if(version==null||!version.isIntegralNumber()||!version.canConvertToInt())throw OfficeException.invalid("baseVersion 必须为整数");
        return new SaveRevision(version.intValue(),text(body,"xml",true));
    }
    private static void fields(JsonNode body,Set<String> allowed) {
        if(body==null||!body.isObject())throw OfficeException.invalid("请求体须为 JSON 对象");
        body.fieldNames().forEachRemaining(name->{if(!allowed.contains(name))throw OfficeException.invalid("请求包含不支持的字段");});
    }
    private static String text(JsonNode body,String name,boolean required) {
        JsonNode value=body.get(name);
        if(value==null||value.isNull()){if(required)throw OfficeException.invalid(name+" 必填");return null;}
        if(!value.isTextual())throw OfficeException.invalid(name+" 必须为字符串");
        return value.textValue();
    }
    private static Integer integer(JsonNode body,String name) {
        JsonNode value=body.get(name);
        if(value==null||value.isNull())return null;
        if(!value.isIntegralNumber()||!value.canConvertToInt())throw OfficeException.invalid(name+" 必须为整数");
        return value.intValue();
    }
}
