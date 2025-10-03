package com.nivleking.springboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nivleking.springboot.dto.EmailDTO;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class EmailDtoConverter implements Converter<String, EmailDTO> {
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public EmailDTO convert(String source) {
        return objectMapper.readValue(source, EmailDTO.class);
    }
}
