package com.example.agentgateway.infrastructure.filteredstream.response;

import com.example.agentgateway.application.filteredstream.port.FilteredStreamResponseMetadataProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 从配置读取独立流式接口固定的 workFlowId。 */
@Component
public class ConfiguredFilteredStreamResponseMetadataProvider
        implements FilteredStreamResponseMetadataProvider {
    private final String workflowId;

    public ConfiguredFilteredStreamResponseMetadataProvider(
            @Value("${filtered-stream.workflow-id:filtered-model-stream-workflow-v1}")
            String workflowId) {
        this.workflowId = workflowId;
    }

    @Override
    public String workflowId() {
        return workflowId;
    }
}
