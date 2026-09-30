package com.example.agentgateway.infrastructure.filteredstream.validation;

import com.example.agentgateway.application.filteredstream.port.FilteredStreamRequestValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilteredStreamValidationConfiguration {
    @Bean
    @ConditionalOnMissingBean(FilteredStreamRequestValidator.class)
    public FilteredStreamRequestValidator filteredStreamRequestValidator(
            FilteredStreamValidationProperties properties) {
        return new ConfigurableFilteredStreamRequestValidator(properties);
    }
}
