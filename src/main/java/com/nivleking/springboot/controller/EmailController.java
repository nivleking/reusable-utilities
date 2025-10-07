package com.nivleking.springboot.controller;

import com.nivleking.springboot.dto.ApiResponse;
import com.nivleking.springboot.dto.ApiResponseV2;
import com.nivleking.springboot.dto.EmailDTO;
import com.nivleking.springboot.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponseV2<String>> sendEmail(
            @RequestParam(name = "files", required = false) MultipartFile[] files,
            @RequestParam(name = "dto") EmailDTO dto
    ) {
        ensureTraceAndSpanIds();
        String traceId = MDC.get("X-B3-TraceId");
        try {
            log.info("Processing email request to: {}", dto.getReceiver());
            String result = emailService.sendEmail(dto, files);

            return ResponseEntity.ok(ApiResponseV2.success(result, "Success", "Sukses", traceId));
        } catch (Exception e) {
            log.error("Email sending failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseV2.error(
                        "500",
                        "Fail to send email",
                        "Gagal mengirimkan email",
                        traceId,
                        e.getMessage()
                    )
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