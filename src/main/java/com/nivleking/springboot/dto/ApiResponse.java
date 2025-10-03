package com.nivleking.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;
    private String traceId;
    private String spanId;

    public static <T> ApiResponse<T> success(T data, String message, String traceId, String spanId) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .traceId(traceId)
                .spanId(spanId)
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message, String traceId, String spanId) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .traceId(traceId)
                .spanId(spanId)
                .build();
    }
}