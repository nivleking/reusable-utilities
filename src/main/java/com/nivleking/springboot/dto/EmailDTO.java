package com.nivleking.springboot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EmailDTO {
    private String emailId;
    private String emailType;
    private String priority;
    private String sender;
    private String receiver;
    private String cc;
    private String bcc;
    private String subject;
    private String templateName;
    private Object params;
}
