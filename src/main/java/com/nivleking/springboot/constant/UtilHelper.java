package com.nivleking.springboot.constant;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UtilHelper {
    public static void ensureTraceAndSpanIds() {
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
