package com.example.agentgateway.interfaces.filteredstream.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilteredStreamFrame {
    private final FilteredStreamFrameBody body;
    private final String returnType;
    private final String message;
    /** FALLBACK/REFUSE 帧的错误说明，与 returnType 同级。 */
    private final String errorMsg;

    public static FilteredStreamFrame data(FilteredStreamFrameBody body) {
        return FilteredStreamFrame.builder().body(body).returnType("DATA").build();
    }

    public static FilteredStreamFrame metadata(FilteredStreamFrameBody body) {
        return FilteredStreamFrame.builder()
                .body(body).returnType("METADATA").build();
    }

    /** 兜底答案已随首帧下发，该帧通知客户端切换并说明原因。 */
    public static FilteredStreamFrame fallback(String reason) {
        return fallback(reason, null);
    }

    public static FilteredStreamFrame fallback(String reason, String errorMsg) {
        String upstreamMessage = blankToNull(errorMsg);
        return FilteredStreamFrame.builder()
                .errorMsg(upstreamMessage == null ? reason : upstreamMessage)
                .returnType("FALLBACK")
                .build();
    }

    public static FilteredStreamFrame refuse() {
        return refuse(null);
    }

    public static FilteredStreamFrame refuse(String errorMsg) {
        String message = blankToNull(errorMsg);
        return FilteredStreamFrame.builder()
                .errorMsg(message == null ? "拒绝回答" : message)
                .returnType("REFUSE").build();
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public static FilteredStreamFrame done() {
        return FilteredStreamFrame.builder().returnType("DONE").build();
    }

    public static FilteredStreamFrame done(String originContent) {
        if (originContent == null) {
            return done();
        }
        return FilteredStreamFrame.builder()
                .body(FilteredStreamFrameBody.builder()
                        .originContent(originContent)
                        .build())
                .returnType("DONE")
                .build();
    }
}
