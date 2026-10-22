package com.example.agentgateway.interfaces.filteredstream.assembler;

import com.example.agentgateway.application.filteredstream.model.RecommendationQuery;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.OverRiskInfo;
import com.example.agentgateway.domain.filteredstream.ProcessedContentBlock;
import com.example.agentgateway.domain.filteredstream.ProcessedCardMapping;
import com.example.agentgateway.domain.filteredstream.ProcessedLinkMapping;
import com.example.agentgateway.domain.filteredstream.ProductRiskInfo;
import com.example.agentgateway.domain.filteredstream.UserRiskInfo;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredCardMapping;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredFallbackAnswerBlock;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredLinkMapping;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredOverRiskInfo;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredProductRiskInfo;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredStreamAnswerBlock;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredSuggestedQuery;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredUserRiskInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 只负责无状态的字段映射。协议帧类型分派和 Metadata JSON 处理仍由
 * {@link FilteredStreamFrameAssembler} 负责。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FilteredStreamDtoAssembler {

    FilteredStreamAnswerBlock assembleAnswerBlock(ProcessedContentBlock block);

    @Mapping(target = "type", constant = ProcessedContentBlock.TYPE_TEXT)
    FilteredFallbackAnswerBlock assembleFallbackMetadataAnswerBlock(
            FallbackMessage source);

    List<FilteredFallbackAnswerBlock> assembleFallbackMetadataAnswerBlocks(
            List<FallbackMessage> source);

    FilteredLinkMapping assembleLinkMapping(
            ProcessedLinkMapping source);

    List<FilteredLinkMapping> assembleLinkMappings(
            List<ProcessedLinkMapping> source);

    FilteredCardMapping assembleCardMapping(
            ProcessedCardMapping source);

    List<FilteredCardMapping> assembleCardMappings(
            List<ProcessedCardMapping> source);

    FilteredSuggestedQuery assembleSuggestedQuery(RecommendationQuery source);

    List<FilteredSuggestedQuery> assembleSuggestedQueries(
            List<RecommendationQuery> source);

    FilteredOverRiskInfo assembleOverRiskInfo(OverRiskInfo source);

    FilteredUserRiskInfo assembleUserRiskInfo(UserRiskInfo source);

    FilteredProductRiskInfo assembleProductRiskInfo(ProductRiskInfo source);

    List<FilteredProductRiskInfo> assembleProductRiskInfos(
            List<ProductRiskInfo> source);
}
