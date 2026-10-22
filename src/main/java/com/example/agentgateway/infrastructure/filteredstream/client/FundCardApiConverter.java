package com.example.agentgateway.infrastructure.filteredstream.client;

import com.example.agentgateway.domain.filteredstream.FundCard;
import com.example.agentgateway.domain.filteredstream.FundDefinition;
import com.example.agentgateway.domain.filteredstream.FundCardSource;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/** 临时卡片 API DTO 与领域对象之间的基础设施层转换器。 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FundCardApiConverter {
    FundCardApiFund toApiFund(FundDefinition source);

    List<FundCardApiFund> toApiFunds(List<FundDefinition> source);

    FundCard toDomain(FundCardApiResponse source);

    default FundCardApiRequest toApiRequest(FundCardSource source) {
        return FundCardApiRequest.builder()
                .fundList(FundCardApiFundList.builder()
                        .subtitle(source.getSubtitle())
                        .list(toApiFunds(source.getList()))
                        .build())
                .build();
    }
}
