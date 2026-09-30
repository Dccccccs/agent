package com.example.agentgateway.infrastructure.filteredstream.validation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "filtered-stream.validation")
public class FilteredStreamValidationProperties {
    private List<String> blockedKeywords = new ArrayList<String>();
    private String refusalMessage = "请求内容未通过校验";
}
