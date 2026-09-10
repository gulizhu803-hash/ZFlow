package com.zgl.zflow.domain.admin.model.enumeration;

import com.zgl.zflow.types.exception.MissingException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.zgl.zflow.types.constant.ExceptionMessage.ENUM_STR_NULL;
import static com.zgl.zflow.types.constant.ExceptionMessage.ENUM_TYPE_UNKNOWN;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum AiConfigType {

    PROMPT("提示词", "prompt"),
    MCP("工具", "mcp"),
    ADVISOR("顾问", "advisor")
    ;

    public static AiConfigType fromString(String str) {
        if (str == null) {
            throw new MissingException(String.format(ENUM_STR_NULL, "AiConfigType"));
        }

        for (AiConfigType configType : values()) {
            if (configType.type.equals(str)) {
                return configType;
            }
        }

        throw new MissingException(String.format(ENUM_TYPE_UNKNOWN, "AiConfigType", str));
    }

    private String name;

    private String type;

}
