package com.nivleking.springboot.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "EMAIL_LOG")
@Component
@RequestScope
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class EmailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_EMAIL_LOG")
    @SequenceGenerator(name = "SEQ_EMAIL_LOG", initialValue = 1, allocationSize = 1, sequenceName = "SEQ_EMAIL_LOG")
    @Column(name = "ID")
    private BigDecimal id;
    @Column(name = "EMAIL_ID")
    private String emailId;
    @Column(name = "EMAIL_TYPE")
    private String emailType;
    @Column(name = "STATUS")
    private String status;
    @Column(name = "TEMPLATE_ID")
    private String templateId;
    @Column(name = "NUMBER_OF_RETRIES")
    private BigDecimal numberOfRetries;
    @Column(name = "JSON_INPUT", columnDefinition = "TEXT")
    private String jsonInput;
    @Column(name = "LAST_SEND")
    private LocalDateTime lastSend;
    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;
    @Column(name = "LAST_UPDATED_DATE")
    private LocalDateTime lastUpdatedDate;
    @Column(name = "EMAIL_DELAY")
    private LocalDateTime emailDelay;
    @Column(name = "REQUEST_ID")
    private String requestId;
    @Column(name = "HTTP_CODE")
    private String httpCode;
    @Column(name = "ERROR_CODE")
    private String errorCode;
    @Column(name = "ERROR_MESSAGE", columnDefinition = "TEXT")
    private String errorMessage;
}
