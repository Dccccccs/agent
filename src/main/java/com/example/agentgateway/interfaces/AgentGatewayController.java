package com.example.agentgateway.interfaces;

import com.example.agentgateway.application.AgentGatewayService;
import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.application.response.stream.AgentStreamResponse;
import com.example.agentgateway.interfaces.assembler.AgentApiAssembler;
import com.example.agentgateway.application.dto.AixzChatRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentGatewayController {
    private static final long STREAM_TIMEOUT_MILLIS = 60_000L;

    private final AgentGatewayService gatewayService;
    private final AgentApiAssembler apiAssembler;

    public AgentGatewayController(AgentGatewayService gatewayService,
                                  AgentApiAssembler apiAssembler) {
        this.gatewayService = gatewayService;
        this.apiAssembler = apiAssembler;
    }

    @PostMapping("/query")
    public ResponseEntity<ResponseBodyEmitter> query(
            @Valid @RequestBody AixzChatRequest request) throws IOException {
        AgentResponse response = gatewayService.query(
                apiAssembler.assemble(request));
        if (response instanceof AgentStreamResponse) {
            return streamingResponse((AgentStreamResponse) response);
        }
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(STREAM_TIMEOUT_MILLIS);
        emitter.send(response);
        emitter.complete();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(emitter);
    }

    private ResponseEntity<ResponseBodyEmitter> streamingResponse(
            final AgentStreamResponse streamResponse) {
        final SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        AgentGatewaySseSession session = new AgentGatewaySseSession(
                emitter, streamResponse);
        session.start();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

}
