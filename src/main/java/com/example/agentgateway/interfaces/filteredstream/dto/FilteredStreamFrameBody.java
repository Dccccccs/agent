package com.example.agentgateway.interfaces.filteredstream.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilteredStreamFrameBody {
    private final List<FilteredStreamAnswerBlock> answer;
    private final String operate;
    private final Object sections;
    private final String prompt;
    private final String riskTip;
    private final List<Object> agentAnswerSlots;
    private final List<FilteredSuggestedQuery> suggestedQuery;
    /** DONE 前的 METADATA 帧中，保留首帧原结构并筛选产品列表。 */
    private final FilteredOverRiskInfo overRiskInfo;
    private final String recordId;
    /** 正常完成的 DONE 帧中，上游正文帧未经替换的拼接内容。 */
    private final String originContent;
}
