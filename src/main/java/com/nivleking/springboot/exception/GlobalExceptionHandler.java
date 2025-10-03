package com.nivleking.springboot.exception;

import com.nivleking.springboot.dto.ApiResponse;
import jakarta.mail.MessagingException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MessagingException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleMessagingException(MessagingException ex) {
        return ApiResponse.error(
                500,
                "Email sending failed: " + ex.getMessage(),
                MDC.get("X-B3-TraceId"),
                MDC.get("X-B3-SpanId")
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleFileSizeException(MaxUploadSizeExceededException ex) {
        return ApiResponse.error(
                400,
                "File size exceeds the maximum allowed size",
                MDC.get("X-B3-TraceId"),
                MDC.get("X-B3-SpanId")
        );
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleIOException(IOException ex) {
        return ApiResponse.error(
                500,
                "File processing error: " + ex.getMessage(),
                MDC.get("X-B3-TraceId"),
                MDC.get("X-B3-SpanId")
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleGenericException(Exception ex) {
        return ApiResponse.error(
                500,
                "An unexpected error occurred: " + ex.getMessage(),
                MDC.get("X-B3-TraceId"),
                MDC.get("X-B3-SpanId")
        );
    }
}