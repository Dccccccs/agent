package com.example.agentgateway.infrastructure.filteredstream.fallback;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 将 filtered-stream.fallback-answer 配置绑定为基础设施层对象。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "filtered-stream.fallback-answer")
public class FallbackMessageProperties {
    private String content = "抱歉，当前服务暂时不可用，请稍后再试。";
    private List<FallbackLinkMappingProperties> linkMapping =
            new ArrayList<FallbackLinkMappingProperties>();
    private List<FallbackCardMappingProperties> cardMapping =
            new ArrayList<FallbackCardMappingProperties>();
}
