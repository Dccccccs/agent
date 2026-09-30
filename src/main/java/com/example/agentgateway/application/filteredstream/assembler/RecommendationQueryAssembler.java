package com.example.agentgateway.application.filteredstream.assembler;

import com.example.agentgateway.application.filteredstream.model.RecommendationQuery;
import com.example.agentgateway.domain.filteredstream.SuggestedQueryDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/** 将首帧中的领域推荐问转换为应用层流结束事件对象。 */
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecommendationQueryAssembler {
    RecommendationQuery assemble(SuggestedQueryDefinition source);

    List<RecommendationQuery> assemble(
            List<SuggestedQueryDefinition> source);
}
