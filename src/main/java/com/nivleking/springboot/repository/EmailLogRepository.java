package com.nivleking.springboot.repository;

import com.nivleking.springboot.model.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, BigDecimal> {
    @Query("SELECT a FROM EmailLog a WHERE a.emailId = :emailId ORDER BY a.createdDate DESC")
    List<EmailLog> findAllByEmailId(@Param("emailId") String emailId);
}
