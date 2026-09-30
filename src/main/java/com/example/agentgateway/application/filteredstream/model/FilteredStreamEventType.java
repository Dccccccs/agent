package com.example.agentgateway.application.filteredstream.model;

/** 对外流式事件的应用层类型。 */
public enum FilteredStreamEventType {
    METADATA,
    CONTENT,
    RECOMMENDATION,
    FALLBACK,
    DONE
}
