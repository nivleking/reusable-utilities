package com.nivleking.springboot.service;

import com.lowagie.text.DocumentException;
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
     * Parse incoming JSON string that should contain `template` and `data`,
     * generate HTML via Thymeleaf and convert to PDF bytes.
     */
    public byte[] parseThymeleafTemplate(String jsonString) throws DocumentException, IOException {
        log.debug("[PDF-GENERATOR] parseThymeleafTemplate input length: {}", jsonString == null ? 0 : jsonString.length());
        if (jsonString == null || jsonString.trim().isEmpty()) {
            log.error("[PDF-GENERATOR] Empty JSON input");
            throw new IllegalArgumentException("JSON input for PDF generator is empty");
        }

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            log.debug("[PDF-GENERATOR] Parsed JSON object keys: {}", jsonObject.keySet());
            LinkedHashMap<String, Object> jsonMap = pdfJsonUtilities.jsonToMap(jsonObject);

            Object templateObj = jsonMap.get("template");
            Object dataObj = jsonMap.get("data");

            if (templateObj == null) {
                log.error("[PDF-GENERATOR][ERR] Missing `template` in JSON input");
                throw new IllegalArgumentException("Missing `template` in JSON input");
            }

            log.info("[PDF-GENERATOR] Generating HTML from template");
            String html = pdfJsonUtilities.generateHtml(templateObj.toString(), dataObj);

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
