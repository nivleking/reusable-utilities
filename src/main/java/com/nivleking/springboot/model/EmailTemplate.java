package com.nivleking.springboot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "EMAIL_TEMPLATE")
public class EmailTemplate {
    @Id
    private BigDecimal id;
    private String templateId;
    private String template;
}
