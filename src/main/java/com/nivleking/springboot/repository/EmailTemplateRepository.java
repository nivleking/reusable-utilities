package com.nivleking.springboot.repository;

import com.nivleking.springboot.model.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, BigDecimal> {
    Optional<EmailTemplate> findByTemplateId(String templateId);
}