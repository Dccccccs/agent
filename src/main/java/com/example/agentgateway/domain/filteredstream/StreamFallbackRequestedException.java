package com.example.agentgateway.domain.filteredstream;

/** 模型在正文中输出兜底控制标记，要求结束当前答案并切换到兜底话术。 */
public class StreamFallbackRequestedException extends RuntimeException {
    public StreamFallbackRequestedException() {
        super("上游正文请求兜底");
    }
}
