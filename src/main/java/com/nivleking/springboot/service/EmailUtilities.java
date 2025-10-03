package com.nivleking.springboot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nivleking.springboot.constant.EmailStatus;
import com.nivleking.springboot.constant.RegexValidator;
import com.nivleking.springboot.dto.ConfigMapData;
import com.nivleking.springboot.dto.EmailDTO;
import com.nivleking.springboot.model.EmailLog;
import com.nivleking.springboot.repository.EmailLogRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class EmailUtilities {
    @Autowired
    private ConfigMapData emailHost;

    @Autowired
    private ConfigMapData emailPort;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailLogRepository emailLogRepository;

    @Data
    @AllArgsConstructor
    public class EmailResult {
        public String emails;
        public List<String> errors;
    }

    public EmailResult splitEmails(String emails, String emailType) throws Exception {
        String[] emailSplit = emails.split(";");
        List<String> localErrors = new ArrayList<>();
        for(String email : emailSplit) {
            if (!email.matches(RegexValidator.EMAIL_FORMAT)) {
                log.debug("[SEND EMAIL][ERR] Invalid email! -> " + "(" + emailType + ")" + " -> " + email);
                localErrors.add("Invalid email: " + "(" + emailType + ")" + " -> " + email);
            }
        }
        emails = String.join(",", emailSplit);

        return new EmailResult(emails, localErrors);
    }

    private void processList(List<Object> list, Context context, String parentKey) {
        int index = 0;
        for (Object item : list) {
            String key = parentKey + "[" + index + "]";
            if (item instanceof Map<?,?>) {
                Map<String, String> details = objectMapper.convertValue(item, HashMap.class);
                for (Map.Entry<String, String> entry2 : details.entrySet()) {
                    context.setVariable(entry2.getKey(), details.get(entry2.getKey()));
                }
                processParams((Map<String, Object>) item, context);
            } else if (item instanceof List) {
                List<Object> details = objectMapper.convertValue(item, new TypeReference<List<Object>>() {});
                context.setVariable(key, details);
                processList((List<Object>) item, context, key);
            } else {
                context.setVariable(key, item.toString());
            }
            index++;
        }
    }

    private void processParams(Map<String, Object> params, Context context) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                Map<String, String> details = objectMapper.convertValue(value, HashMap.class);
                for (Map.Entry<String, String> entry2 : details.entrySet()) {
                    context.setVariable(entry2.getKey(), details.get(entry2.getKey()));
                }
                processParams((Map<String, Object>) value, context);
            } else if (value instanceof List) {
                List<Object> details = objectMapper.convertValue(value, new TypeReference<List<Object>>() {});
                context.setVariable(key, details);
                processList((List<Object>) value, context, key);
            } else {
                context.setVariable(key, value.toString());
            }
        }
    }

    public String processTemplate(String templateHtml, Map<String, Object> params) {
        Context context = new Context(Locale.getDefault());
        processParams(params, context);

        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.HTML);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        return templateEngine.process(templateHtml, context);
    }

//    public boolean checkIfEmailNeedsDelay(String emailType) {
//        for (Map.Entry<String, String> entry : emailAppDelayConfigurations.entrySet()) {
//            if (emailType.equals(entry.getKey())) {
//                return true;
//            }
//        }
//
//        return false;
//    }

//    public Long getDelayByEmailType(String emailType) {
//        Long delayInMillis = null;
//        try {
//            delayInMillis = Long.parseLong(emailAppDelayConfigurations.get(emailType));
//        } catch (Exception e) {
//            // Delay for 5 Minutes
//            delayInMillis = 300_000L;
//        }
//
//        return delayInMillis;
//    }

    public void insertLog(EmailDTO dto, String emailId, BigDecimal numberOfRetries) {
        try {
            EmailLog emailLog = new EmailLog();
            emailLog.setEmailId(emailId);
            emailLog.setEmailType(dto.getEmailType());
            emailLog.setNumberOfRetries(numberOfRetries);
            emailLog.setStatus(EmailStatus.PENDING);
            emailLog.setTemplateId(dto.getTemplateName());
            emailLog.setJsonInput(objectMapper.writeValueAsString(dto));
            emailLog.setRequestId(MDC.get("X-B3-TraceId"));

            LocalDateTime now = LocalDateTime.now();
            emailLog.setCreatedDate(now);
            emailLog.setLastUpdatedDate(now);

            emailLogRepository.save(emailLog);
            log.debug("[EMAIL LOG] Inserted pending log: {}", emailId);
        } catch(Exception e) {
            log.error("[EMAIL LOG] Failed to insert log: {}", e.getMessage(), e);
        }
    }

    public void saveSuccessLog(EmailDTO dto, String emailId, BigDecimal numberOfRetries) {
        try {
            List<EmailLog> emailLogs = emailLogRepository.findAllByEmailId(emailId);
            EmailLog emailLog = emailLogs.getFirst();
            emailLog.setStatus(EmailStatus.SUCCESS);
            emailLog.setEmailType(dto.getEmailType());
            emailLog.setTemplateId(dto.getTemplateName());
            emailLog.setNumberOfRetries(numberOfRetries);
            emailLog.setJsonInput(objectMapper.writeValueAsString(dto));
            emailLog.setRequestId(MDC.get("X-B3-TraceId"));

            LocalDateTime now = LocalDateTime.now();
            emailLog.setLastSend(now);
            emailLog.setLastUpdatedDate(now);

            emailLogRepository.save(emailLog);
            log.debug("[EMAIL LOG] Updated success log: {}", emailId);
        } catch(Exception e) {
            log.error("[EMAIL LOG] Failed to update success log: {}", e.getMessage(), e);
        }
    }

    public void saveErrorLog(EmailDTO dto, String status, String emailId, BigDecimal numberOfRetries, String httpCode, String errorCode, String errorMessage) {
        try {
            List<EmailLog> emailLogs = emailLogRepository.findAllByEmailId(emailId);
            if (emailLogs == null || emailLogs.isEmpty()) {
                log.warn("[EMAIL LOG] No logs found for email ID: {}, creating new log", emailId);
                EmailLog emailLog = new EmailLog();
                emailLog.setEmailId(emailId);
                emailLog.setStatus(status);
                emailLog.setCreatedDate(LocalDateTime.now());
                setupErrorLog(dto, status, numberOfRetries, httpCode, errorCode, errorMessage, emailLog);
            } else {
                EmailLog emailLog = emailLogs.getFirst();
                setupErrorLog(dto, status, numberOfRetries, httpCode, errorCode, errorMessage, emailLog);
            }
        } catch(Exception e) {
            log.error("[EMAIL LOG] Failed to update error log: {}", e.getMessage(), e);
        }
    }

    private void setupErrorLog(EmailDTO dto, String status, BigDecimal numberOfRetries, String httpCode, String errorCode, String errorMessage, EmailLog emailLog) throws Exception {
        String truncatedErrorMessage = truncateErrorMessage(errorMessage);

        emailLog.setStatus(status);
        emailLog.setEmailType(dto.getEmailType());
        emailLog.setTemplateId(dto.getTemplateName());
        emailLog.setNumberOfRetries(numberOfRetries);
        emailLog.setHttpCode(httpCode);
        emailLog.setErrorCode(errorCode);
        emailLog.setErrorMessage(truncatedErrorMessage);

        emailLog.setJsonInput(objectMapper.writeValueAsString(dto));

        emailLog.setRequestId(MDC.get("X-B3-TraceId"));

        LocalDateTime now = LocalDateTime.now();
        emailLog.setLastSend(now);
        emailLog.setLastUpdatedDate(now);

        emailLogRepository.save(emailLog);
        log.debug("[EMAIL LOG] Updated error log: {}", dto.getEmailId());
    }

    /**
     * Truncates error message to a reasonable size and adds indication if truncated
     */
    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }

        final int MAX_ERROR_LENGTH = 500;

        if (errorMessage.length() <= MAX_ERROR_LENGTH) {
            return errorMessage;
        }

        String beginningPart = errorMessage.substring(0, 300);

        String rootCause;
        int lastCausedByIndex = errorMessage.lastIndexOf("Caused by:");
        if (lastCausedByIndex != -1 && lastCausedByIndex < errorMessage.length() - 100) {
            int endIndex = Math.min(lastCausedByIndex + 200, errorMessage.length());
            rootCause = errorMessage.substring(lastCausedByIndex, endIndex);
        } else {
            rootCause = "";
        }

        return beginningPart + "... [truncated] ..." + rootCause;
    }

    public boolean checkTimeout(Exception e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException ||
                    cause instanceof ConnectTimeoutException ||
                    (cause.getMessage() != null && (
                            cause.getMessage().toLowerCase().contains("timeout") ||
                                    cause.getMessage().toLowerCase().contains("timed out") ||
                                    cause.getMessage().toLowerCase().contains("connection refused") ||
                                    cause.getMessage().toLowerCase().contains("no route to host")
                    ))
            ) {
                return true;
            }
            cause = cause.getCause();
        }

        // Check for timeout in the main exception message too
        if (e.getMessage() != null && (
                e.getMessage().toLowerCase().contains("timeout") ||
                        e.getMessage().toLowerCase().contains("timed out") ||
                        e.getMessage().toLowerCase().contains("connection refused") ||
                        e.getMessage().toLowerCase().contains("no route to host")
        )) {
            return true;
        }

        return false;
    }
//
//    public String generateDate() {
//        Date date = new Date();
//
//        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm:ss.SSS 'WIB'", Locale.ENGLISH);
//
//        return dateFormat.format(date);
//    }
//

    public Properties getDefaultProps() {
        Properties props = System.getProperties();
        props.setProperty("mail.smtp.host", emailHost.getValue());
        props.setProperty("mail.smtp.port", emailPort.getValue());
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.starttls.enable", "true");
        props.setProperty("mail.smtp.connectiontimeout", "10000");
        props.setProperty("mail.smtp.timeout", "10000");
        return props;
    }
}
