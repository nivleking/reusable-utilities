package com.nivleking.springboot.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApiResponseV2<T> {
    private ErrorSchema errorSchema;
    private OutputSchema<T> outputSchema;

    public static <T> ApiResponseV2<T> success(T data, String message, String messageIndo, String requestId) {
        ErrorMessage errorMessage = ErrorMessage.builder()
                .indonesian(messageIndo)
                .english(message)
                .build();

        return ApiResponseV2.<T>builder()
                .errorSchema(ErrorSchema.builder()
                        .statusCode("200")
                        .errorMessage(errorMessage)
                        .build())
                .outputSchema(OutputSchema.<T>builder()
                        .requestId(requestId)
                        .data(data)
                        .build())
                .build();
    }

    public static <T> ApiResponseV2<T> error(String statusCode, String message, String messageIndo, String requestId, T data) {
        ErrorMessage errorMessage = ErrorMessage.builder()
                .indonesian(messageIndo)
                .english(message)
                .build();

        return ApiResponseV2.<T>builder()
                .errorSchema(ErrorSchema.builder()
                        .statusCode(statusCode)
                        .errorMessage(errorMessage)
                        .build())
                .outputSchema(OutputSchema.<T>builder()
                        .requestId(requestId)
                        .data(data)
                        .build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ErrorSchema {
        private String statusCode;
        private ErrorMessage errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ErrorMessage {
        private String indonesian;
        private String english;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class OutputSchema<T> {
        private String requestId;
        private T data;
    }
}