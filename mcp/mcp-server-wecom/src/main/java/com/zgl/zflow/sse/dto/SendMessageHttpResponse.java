package com.zgl.zflow.sse.dto;

import lombok.Data;

@Data
public class SendMessageHttpResponse {

    private Integer errcode;

    private String errmsg;

    private String msgid;

}
