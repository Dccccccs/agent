package com.example.agentgateway.application.filteredstream.model;

import lombok.Getter;

/** 流结束前需要输出的推荐问。 */
@Getter
public class RecommendationQuery {
    private final String query;
    private final String intent;

    public RecommendationQuery(String query, String intent) {
        this.query = query;
        this.intent = intent;
    }
}
