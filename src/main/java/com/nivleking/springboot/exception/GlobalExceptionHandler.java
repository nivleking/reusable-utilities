package com.nivleking.springboot.exception;

import com.nivleking.springboot.dto.ApiResponse;
import com.nivleking.springboot.dto.ApiResponseV2;
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
    public ApiResponseV2<String> handleMessagingException(MessagingException ex) {
        return ApiResponseV2.error(
                "500",
                "Fail to send email",
                "Gagal mengirimkan email",
                MDC.get("X-B3-TraceId"),
                ex.getMessage()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponseV2<String> handleFileSizeException(MaxUploadSizeExceededException ex) {
        return ApiResponseV2.error(
                "400",
                "File size exceeds the maximum allowed size",
                "File size melebihi batas ukuran maksimum yang diperbolehkan",
                MDC.get("X-B3-TraceId"),
                ex.getMessage()
        );
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponseV2<String> handleIOException(IOException ex) {
        return ApiResponseV2.error(
                "500",
                "File processing error",
                "File processing error",
                MDC.get("X-B3-TraceId"),
                ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponseV2<String> handleGenericException(Exception ex) {
        return ApiResponseV2.error(
                "500",
                "An unexpected error occurred",
                "An unexpected error occurred",
                MDC.get("X-B3-TraceId"),
                ex.getMessage()
        );
    }
}