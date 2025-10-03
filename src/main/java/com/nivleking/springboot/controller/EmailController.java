package com.nivleking.springboot.controller;

import com.nivleking.springboot.dto.ApiResponse;
import com.nivleking.springboot.dto.EmailDTO;
import com.nivleking.springboot.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/utilities/mailer")
public class EmailController {
    @Autowired
    private EmailService emailService;

    @PostMapping("send-email")
    public ApiResponse<String> sendEmail(
            @RequestParam(name = "files", required = false) MultipartFile[] files,
            @RequestParam(name = "dto") EmailDTO dto
    ) {
        ensureTraceAndSpanIds();
        try {
            log.info("Processing email request to: {}", dto.getReceiver());
            String result = emailService.sendEmail(dto, files);

            return ApiResponse.success(
                    result,
                    "Email sent successfully",
                    MDC.get("X-B3-TraceId"),
                    MDC.get("X-B3-SpanId")
            );
        } catch (Exception e) {
            log.error("Email sending failed: {}", e.getMessage(), e);
            return ApiResponse.error(
                    500,
                    "Failed to send email: " + e.getMessage(),
                    MDC.get("X-B3-TraceId"),
                    MDC.get("X-B3-SpanId")
            );
        }
    }

    private void ensureTraceAndSpanIds() {
        String traceId = MDC.get("X-B3-TraceId");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
            MDC.put("X-B3-TraceId", traceId);
            log.debug("Generated new trace ID: {}", traceId);
        }

        String spanId = MDC.get("X-B3-SpanId");
        if (spanId == null || spanId.isEmpty()) {
            spanId = UUID.randomUUID().toString();
            MDC.put("X-B3-SpanId", spanId);
            log.debug("Generated new span ID: {}", spanId);
        }
    }
}