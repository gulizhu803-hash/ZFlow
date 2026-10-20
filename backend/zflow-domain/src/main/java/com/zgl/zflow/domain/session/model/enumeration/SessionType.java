package com.zgl.zflow.domain.session.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum SessionType {

    CHAT("chat", "Chat"),
    WORK("work", "Work"),
    OFFICE("office", "Office");

    private String type;

    private String name;

}
