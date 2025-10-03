package com.nivleking.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigMapData {
    private String value;

    public Integer getIntValue() {
        return Integer.parseInt(this.value);
    }

    public Long getLongValue() {
        return Long.parseLong(this.value);
    }

    public BigDecimal getBigDecimalValue() {
        return new BigDecimal(this.value);
    }
}
