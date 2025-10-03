package com.nivleking.springboot.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestTracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String traceId = request.getHeader("X-B3-TraceId");
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString();
            }

            String spanId = request.getHeader("X-B3-SpanId");
            if (spanId == null || spanId.isEmpty()) {
                spanId = UUID.randomUUID().toString();
            }

            MDC.put("X-B3-TraceId", traceId);
            MDC.put("X-B3-SpanId", spanId);

            response.addHeader("X-B3-TraceId", traceId);
            response.addHeader("X-B3-SpanId", spanId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("X-B3-TraceId");
            MDC.remove("X-B3-SpanId");
        }
    }
}