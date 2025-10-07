package com.nivleking.springboot.repository;

import com.nivleking.springboot.model.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, BigDecimal> {
    @Query("SELECT a FROM EmailLog a WHERE a.emailId = :emailId ORDER BY a.createdDate DESC")
    List<EmailLog> findAllByEmailId(@Param("emailId") String emailId);

    @Procedure(procedureName = "EMAIL_DELAY", outputParameterName = "R_EMAIL_ID")
    String checkAndCreateEmailDelay(
            @Param("V_CURRENT_TIME") LocalDateTime currentTime,
            @Param("V_EMAIL_TYPE") String emailType,
            @Param("V_EMAIL_ID") String emailId,
            @Param("V_DELAY_MILLISECONDS") Long delayMilliseconds,
            @Param("V_MAX_RETRY") BigDecimal maxRetry
    );

    @Procedure(procedureName = "EMAIL_INSERT", outputParameterName = "R_EMAIL_ID")
    String createOrUpdateEmailLog(
            @Param("V_CURRENT_TIME") LocalDateTime currentTime,
            @Param("V_EMAIL_TYPE") String emailType,
            @Param("V_EMAIL_ID") String emailId
    );
}
