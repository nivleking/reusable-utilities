package com.nivleking.springboot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nivleking.springboot.dto.PdfGenerateRequestDTO;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nivleking.springboot.constant.ResponseMessages;
import com.nivleking.springboot.constant.UtilHelper;
import com.nivleking.springboot.dto.ApiResponseV2;
import com.nivleking.springboot.service.PdfGeneratorService;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/api/utilities/pdf-generator")
public class PdfGeneratorController {
    @Autowired
    private PdfGeneratorService pdfGeneratorService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponseV2<Object>> generatePdf(
            @RequestBody PdfGenerateRequestDTO dto
    ) throws Exception {
        UtilHelper.ensureTraceAndSpanIds();
        MDC.put("input", objectMapper.writeValueAsString(dto));
        String traceId = MDC.get("X-B3-TraceId");
        try {
            log.info("[PDF-GENERATOR] Received generate request with traceId {}", traceId);
            byte[] data = pdfGeneratorService.parseThymeleafTemplate(dto);
            log.info("[PDF-GENERATOR] PDF generation succeeded (bytes={}) for traceId {}", data == null ? 0 : data.length, traceId);

            return ResponseEntity.ok(ApiResponseV2.success(
                    data,
                    ResponseMessages.ENG_SUCCESS_CODE,
                    ResponseMessages.ID_SUCCESS_CODE,
                    traceId
            ));
        } catch (Exception e) {
            log.error("[PDF-GENERATOR] PDF generation failed (trace={}): {}", traceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseV2.error(
                        "500",
                        ResponseMessages.ENG_FAIL_GENERATE_PDF,
                        ResponseMessages.ID_FAIL_GENERATE_PDF,
                        traceId,
                        e.getMessage()
                    )
                );
        }
    }
}
