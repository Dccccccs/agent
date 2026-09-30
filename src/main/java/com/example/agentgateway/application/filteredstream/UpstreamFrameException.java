package com.example.agentgateway.application.filteredstream;

/**
 * 表示外部 API 已经返回了流式帧，但该帧是 ERROR 或无法按协议解析。
 * 用于区分“请求在首帧前失败”和“流式帧处理阶段失败”。
 */
public class UpstreamFrameException extends FilteredStreamException {
    public UpstreamFrameException(String message) {
        super(message);
    }

    public UpstreamFrameException(String message, Throwable cause) {
        super(message, cause);
    }
}
