package com.example.agentgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class AgentGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentGatewayApplication.class, args);
    }
}