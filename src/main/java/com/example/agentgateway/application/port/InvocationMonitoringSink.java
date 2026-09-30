package com.example.agentgateway.application.port;

import com.example.agentgateway.application.monitoring.InvocationMonitoringEvent;

/** 可由日志、数据库、消息队列或检索引擎等基础设施实现。 */
public interface InvocationMonitoringSink {
    void save(InvocationMonitoringEvent event);
}
