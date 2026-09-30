package com.example.agentgateway.application.filteredstream;

/** 本用例统一使用的运行时异常。 */
public class FilteredStreamException extends RuntimeException {
    public FilteredStreamException(String message) {
        super(message);
    }

    public FilteredStreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
