package com.example.agentgateway.application.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@Data
public class AixzChatRequest {
    @NotBlank(message = "question不能为空")
    private String question;

    @NotBlank(message = "msgId不能为空")
    private String msgId;

    private String sessionId;
    private String uid;
    private String appId;
    private String rewriteQuestion;

    @Valid
    private Slot slot;

    private Object extraParam;
}
