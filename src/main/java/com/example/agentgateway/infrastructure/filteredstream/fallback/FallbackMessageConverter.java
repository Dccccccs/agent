package com.example.agentgateway.infrastructure.filteredstream.fallback;

import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.ProcessedCardMapping;
import com.example.agentgateway.domain.filteredstream.ProcessedLinkMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** 将基础设施层配置对象转换成领域兜底消息。 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FallbackMessageConverter {
    // @Singular 生成的单元素 Builder 方法不是独立业务字段。
    @Mapping(target = "link", ignore = true)
    @Mapping(target = "card", ignore = true)
    FallbackMessage convert(FallbackMessageProperties source);

    ProcessedLinkMapping convert(FallbackLinkMappingProperties source);

    ProcessedCardMapping convert(FallbackCardMappingProperties source);
}
