package com.example.agentgateway.infrastructure.textvalidation;

import com.example.agentgateway.domain.textvalidation.FundChangeAnalysisValidationRule;
import com.example.agentgateway.domain.textvalidation.FundInvestStrategyValidationRule;
import com.example.agentgateway.domain.textvalidation.SceneTextValidationRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 文本格式校验场景注册。新增场景时注册新的规则 Bean 即可。 */
@Configuration
public class TextValidationConfiguration {
    @Bean
    public SceneTextValidationRule fundChangeAnalysisValidationRule() {
        return new FundChangeAnalysisValidationRule();
    }

    @Bean
    public SceneTextValidationRule fundInvestStrategyValidationRule() {
        return new FundInvestStrategyValidationRule();
    }
}
