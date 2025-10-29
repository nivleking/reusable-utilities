package com.nivleking.springboot.service;

import com.lowagie.text.DocumentException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

@Component
@Slf4j
public class PdfJsonUtilities {
    public LinkedHashMap<String, Object> jsonToMap(JSONObject jsonObject) throws JSONException {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();

        if (jsonObject == null) {
            log.debug("[PDF-UTIL] JSONObject is null!");
            return map;
        }

        log.debug("[PDF-UTIL] Converting JSONObject to Map. keys={}", jsonObject.keySet());
        try {
            map = processMap(jsonObject);
            log.debug("[PDF-UTIL] Conversion complete. map size={}", map.size());
        } catch (JSONException e) {
            log.error("[PDF-UTIL] jsonToMap JSONException: {}", e.getMessage(), e);
            throw e;
        }

        return map;
    }

    public LinkedHashMap<String, Object> processMap(JSONObject jsonObject) throws JSONException {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();

        Iterator<String> keyItrs = jsonObject.keys();

        while (keyItrs.hasNext()) {
            String key = keyItrs.next();
            Object value = jsonObject.get(key);

            if (value instanceof JSONArray) {
                log.debug("[PDF-UTIL] Processing JSONArray for key: {}", key);
                value = processList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                log.debug("[PDF-UTIL] Processing nested JSONObject for key: {}", key);
                value = processMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public List<Object> processList(JSONArray jsonArray) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONArray) {
                log.debug("[PDF-UTIL] processList encountered nested JSONArray at index {}", i);
                value = processList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                log.debug("[PDF-UTIL] processList encountered nested JSONObject at index {}", i);
                value = processMap((JSONObject) value);
            }
            list.add(value);
        }
        log.debug("[PDF-UTIL] processList completed (size={})", list.size());
        return list;
    }

    public String generateHtml(String html, Object data) {
        if (html == null) {
            log.warn("[PDF-UTIL] Template HTML is null, using empty string");
            html = "";
        }

        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.HTML);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        try {
            log.debug("[PDF-UTIL] Generating HTML for data: {}", data == null ? "null" : data.toString());
            Context context = new Context();
            context.setVariable("data", data);
            String processed = templateEngine.process(html, context);
            log.debug("[PDF-UTIL] HTML generation complete (length={})", processed == null ? 0 : processed.length());
            return processed;
        } catch (Exception e) {
            log.error("[PDF-UTIL] Error while processing Thymeleaf template: {}", e.getMessage(), e);
            throw e;
        }
    }

    public byte[] generatePdfFromHtml(String html) throws DocumentException, IOException {
        if (html == null) {
            log.warn("[PDF-UTIL] generatePdfFromHtml received null html, returning empty bytes");
            return new byte[0];
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        try {
            log.debug("[PDF-UTIL] Setting document for renderer (html length={})", html.length());
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);
            renderer.finishPDF();
            outputStream.flush();
            log.debug("[PDF-UTIL] PDF creation finished (bytes={})", outputStream.size());
            return outputStream.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("[PDF-UTIL] Error creating PDF from HTML: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[PDF-UTIL] Unexpected error creating PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Unexpected PDF generation error: " + e.getMessage(), e);
        } finally {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
