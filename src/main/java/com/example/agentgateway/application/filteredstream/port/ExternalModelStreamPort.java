package com.example.agentgateway.application.filteredstream.port;

import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamRequestContext;
import reactor.core.publisher.Flux;

/**
 * 外部模型流的应用层端口。应用层通过该接口消费统一帧，不直接依赖
 * WebClient 或外部厂商的 JSON 协议。
 */
public interface ExternalModelStreamPort {
    /**
     * 发起一次外部模型流式请求。
     *
     * <p>返回值必须支持取消传播：下游取消 Flux 订阅时，实现方应停止读取并
     * 取消对应的 HTTP 请求。请求头参数通过请求上下文显式传递，实现方只
     * 应向外部 API 转发业务需要的白名单请求头。</p>
     */
    Flux<UpstreamStreamFrame> openStream(
            FilteredStreamRequestContext requestContext);
}
