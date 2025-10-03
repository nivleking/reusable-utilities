package com.nivleking.springboot.config;

import com.nivleking.springboot.constant.ConfigServerMap;
import com.nivleking.springboot.dto.ConfigMapData;
import com.nivleking.springboot.model.ConfigServer;
import com.nivleking.springboot.repository.ConfigServerRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class UtilitiesConfiguration {
    @Autowired
    private ConfigServerRepository configServerRepository;

    @Component
    @RefreshScope
    public static class ConfigServerHolder {
        private Map<String, String> configs = new HashMap<>();

        public Map<String, String> getConfigs() {
            return configs;
        }

        public void setConfigs(Map<String, String> configs) {
            this.configs = configs;
        }
    }

    @Autowired
    private ConfigServerHolder configServerHolder;

    @PostConstruct
    public void loadConfig() {
        List<ConfigServer> configServers = configServerRepository.findAll();
        Map<String, String> hashMap = new HashMap<>();
        log.info("[CONFIG SERVER] Loading configuration from database...");
        for (ConfigServer configServer : configServers) {
            hashMap.put(configServer.getProperties(), configServer.getValue());
            log.debug("[CONFIG SERVER] Loaded config: {} = {}", configServer.getProperties(), configServer.getValue());
        }
        configServerHolder.setConfigs(hashMap);
    }


    @Bean
    @RefreshScope
    public ConfigMapData emailHost() {
        return new ConfigMapData(configServerHolder.getConfigs().get(ConfigServerMap.EMAIL_HOST));
    }

    @Bean
    @RefreshScope
    public ConfigMapData emailPort() {
        return new ConfigMapData(configServerHolder.getConfigs().get(ConfigServerMap.EMAIL_PORT));
    }

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        return mapper;
    }
}