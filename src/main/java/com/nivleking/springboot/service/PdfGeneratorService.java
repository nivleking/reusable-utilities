package com.nivleking.springboot.service;

import com.lowagie.text.DocumentException;
import com.nivleking.springboot.dto.PdfGenerateRequestDTO;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.LinkedHashMap;

@Service
@Slf4j
public class PdfGeneratorService {
    @Autowired
    private PdfJsonUtilities pdfJsonUtilities;

    /**
     * Accept typed DTO (template + data), generate HTML via Thymeleaf and convert to PDF bytes.
     */
    public byte[] parseThymeleafTemplate(PdfGenerateRequestDTO dto) throws DocumentException, IOException {
        if (dto == null) {
            log.error("[PDF-GENERATOR] Request DTO is null");
            throw new IllegalArgumentException("Request DTO is null");
        }

        String template = dto.getTemplate();
        Object dataObj = dto.getData();

        if (template == null || template.trim().isEmpty()) {
            log.error("[PDF-GENERATOR][ERR] Missing `template` in request DTO");
            throw new IllegalArgumentException("Missing `template` in request");
        }

        try {
            log.info("[PDF-GENERATOR] Generating HTML from template");
            String html = pdfJsonUtilities.generateHtml(template, dataObj);

            log.info("[PDF-GENERATOR] Generating PDF bytes from HTML (length ~ {})", html == null ? 0 : html.length());
            byte[] pdfBytes = pdfJsonUtilities.generatePdfFromHtml(html);
            log.debug("[PDF-GENERATOR] PDF bytes generated: {}", pdfBytes == null ? 0 : pdfBytes.length);

            return pdfBytes;
        } catch (DocumentException | IOException e) {
            log.error("[PDF-GENERATOR][ERR] Error generating PDF: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[PDF-GENERATOR][ERR] Unexpected error parsing/generating PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
}
