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

    public static FilteredStreamFrame data(FilteredStreamFrameBody body) {
        return FilteredStreamFrame.builder().body(body).returnType("DATA").build();
    }

    public static FilteredStreamFrame metadata(FilteredStreamFrameBody body) {
        return FilteredStreamFrame.builder()
                .body(body).returnType("METADATA").build();
    }

    /** 兜底答案已随首帧下发，因此该帧只通知客户端切换到兜底回答。 */
    public static FilteredStreamFrame fallback() {
        return FilteredStreamFrame.builder().returnType("FALLBACK").build();
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
