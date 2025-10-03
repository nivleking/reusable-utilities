package com.nivleking.springboot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "CONFIG_SERVER")
public class ConfigServer {
    @Id
    private BigDecimal id;
    private String properties;
    private String value;
}
