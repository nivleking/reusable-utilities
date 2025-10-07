package com.nivleking.springboot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ConfigMapperService {
    public Map<String, String> configServerMapValueReader(String input) throws Exception {
        Map<String, String> map = new HashMap<>();

        try {
            String cleanInput = input.replaceAll("[{}']", "");
            String[] pairs = cleanInput.split(";");

            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                String[] currencies = keyValue[0].split(",");
                String value = null;
                try {
                    value = keyValue[1];
                } catch (Exception e) {
                    log.info("[CONFIG SERVER MAPPER]["+input+"] contains key that has empty value");
                }

                for (String currency : currencies) {
                    map.put(currency, value);
                }
            }
        } catch (Exception e) {
            log.error("Error set mapper from input: " + e);
        }

        return map;
    }
}
