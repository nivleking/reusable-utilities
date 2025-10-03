package com.nivleking.springboot.repository;

import com.nivleking.springboot.model.ConfigServer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

public interface ConfigServerRepository extends JpaRepository<ConfigServer, BigDecimal> {

}
