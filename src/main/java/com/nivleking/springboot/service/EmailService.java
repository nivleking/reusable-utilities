package com.nivleking.springboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nivleking.springboot.constant.EmailStatus;
import com.nivleking.springboot.constant.RegexValidator;
import com.nivleking.springboot.dto.EmailDTO;
import com.nivleking.springboot.model.EmailTemplate;
import com.nivleking.springboot.repository.EmailLogRepository;
import com.nivleking.springboot.repository.EmailTemplateRepository;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class EmailService {
    @Value("${spring.mail.username}")
    private String emailUsername;

    @Value("${spring.mail.password}")
    private String emailPassword;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailUtilities emailUtilities;

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    @Autowired
    private EmailLogRepository emailLogRepository;

    @Autowired
    private ModelMapper modelMapper;

    /**
     * Send email with optional attachments
     *
     * @param emailDTO Email data transfer object containing recipient, subject, etc.
     * @param files Optional attachments
     * @return String indicating the result
     * @throws MessagingException if there's an error sending the email
     */
    public String sendEmail(EmailDTO emailDTO, MultipartFile[] files) throws Exception {
        BigDecimal retries = BigDecimal.ZERO;
        String emailId = emailDTO.getEmailId();
        LocalDateTime now = LocalDateTime.now();

        try {
            log.info("[SEND EMAIL] Starting email sending process to recipient: {}", emailDTO.getReceiver());
            log.debug("[SEND EMAIL] Email details: id={}, type={}, priority={}", emailId, emailDTO.getEmailType(), emailDTO.getPriority());

            // Generate email ID if not provided
            if (emailId == null || emailId.isEmpty()) {
                emailId = UUID.randomUUID().toString();
                emailDTO.setEmailId(emailId);
                log.debug("[SEND EMAIL] Generated new email ID: {}", emailId);
            }

            // Check if email type requires delay
            if (!emailUtilities.checkIfEmailNeedsDelay(emailDTO.getEmailType())) {
                log.debug("[SEND EMAIL] Email does not use delay! Proceed to normal flow!");

                // Create or update email log without delay
                try {
                    emailId = emailLogRepository.createOrUpdateEmailLog(
                            now,
                            emailDTO.getEmailType(),
                            emailId
                    );

                    if (emailId == null) {
                        log.info("[SEND EMAIL] Email {} already sent successfully. Skipping.", emailId);
                        return "Email already successfully sent to " + emailDTO.getReceiver();
                    }
                } catch (Exception e) {
                    log.error("[SEND EMAIL] Insert log error! {} will not be sent: {}", emailDTO.getEmailType(), emailId, e);
                    throw new Exception("Insert log error! Email id: " + emailId);
                }
            } else {
                log.debug("[SEND EMAIL] Email uses delay concept! EMAIL_TYPE: {}", emailDTO.getEmailType());

                Long delayMillis = emailUtilities.getDelayByEmailType(emailDTO.getEmailType());
                log.debug("[SEND EMAIL] Delay for {} is {} ms", emailDTO.getEmailType(), delayMillis);

                // Create or update email log with delay
                try {
                    emailId = emailLogRepository.checkAndCreateEmailDelay(
                            now,
                            emailDTO.getEmailType(),
                            emailId,
                            delayMillis,
                            retries
                    );

                    // If emailId is null, it means delay is active - return early
                    if (emailId == null) {
                        log.debug("[SEND EMAIL] Email delay is still active! {} will not be sent: {}",
                                emailDTO.getEmailType(), emailDTO.getEmailId());
                        return "Email delay is still active for " + emailDTO.getEmailType() + "! Email will not be sent: " + emailDTO.getEmailId();
                    }
                } catch (Exception e) {
                    log.error("[SEND EMAIL] Email delay check failed: {}", e.getMessage(), e);
                    throw new Exception("Email delay check failed! Email id: " + emailId);
                }
            }

            // Email validations
            log.debug("[SEND EMAIL] Validating email addresses");
            List<String> errors = validateEmails(emailDTO);
            if (!errors.isEmpty()) {
                String errorMsg = String.join(", ", errors);
                log.error("[SEND EMAIL] Email validation failed: {}", errorMsg);
                emailUtilities.saveErrorLog(emailDTO, EmailStatus.FAILED, emailDTO.getEmailId(), retries, "400", "VALIDATION_ERROR", errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            log.debug("[SEND EMAIL] Email validation successful");

            log.debug("[SEND EMAIL] Creating email session with authentication");
            Session session = Session.getInstance(emailUtilities.getDefaultProps(),
                    new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(emailUsername, emailPassword);
                        }
                    });

            MimeMessage message = new MimeMessage(session);
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set basic email properties
            log.debug("[SEND EMAIL] Setting basic email properties");
            message.setSubject(emailDTO.getSubject());
            message.setFrom(new InternetAddress(emailDTO.getSender()));

            // Set email priority if specified (1-5, with 1 being highest priority)
            if (emailDTO.getPriority() != null && !emailDTO.getPriority().isEmpty()) {
                helper.setPriority(Integer.parseInt(emailDTO.getPriority()));
            }

            // Set recipients
            log.debug("[SEND EMAIL] Setting email recipients");
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(emailDTO.getReceiver()));

            if (emailDTO.getCc() != null && !emailDTO.getCc().isEmpty()) {
                log.debug("[SEND EMAIL] Adding CC recipients: {}", emailDTO.getCc());
                message.addRecipients(Message.RecipientType.CC, InternetAddress.parse(emailDTO.getCc()));
            }

            if (emailDTO.getBcc() != null && !emailDTO.getBcc().isEmpty()) {
                log.debug("[SEND EMAIL] Adding BCC recipients: {}", emailDTO.getBcc());
                message.addRecipients(Message.RecipientType.BCC, InternetAddress.parse(emailDTO.getBcc()));
            }

            // Create multipart email
            log.debug("[SEND EMAIL] Creating multipart email");
            Multipart multipart = new MimeMultipart();

            // Process template with parameters
            log.debug("[SEND EMAIL] Processing email template with parameters");
            String templateHtml = getEmailTemplate(emailDTO.getTemplateName());
            Map<String, Object> params = new HashMap<>();

            if (emailDTO.getParams() != null) {
                params = objectMapper.convertValue(emailDTO.getParams(), HashMap.class);
                log.debug("[SEND EMAIL] Template parameters: {}", params);
            }

            String htmlContent = emailUtilities.processTemplate(templateHtml, params);
            log.debug("[SEND EMAIL] Template processing complete");

            // Add HTML content
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent(htmlContent, "text/html; charset=utf-8");
            multipart.addBodyPart(messageBodyPart);
            log.debug("[SEND EMAIL] HTML content added to email");

            // Add attachments if any
            if (files != null && files.length > 0) {
                log.debug("[SEND EMAIL] Processing {} attachment(s)", files.length);
                for (MultipartFile file : files) {
                    if (file != null && !file.isEmpty()) {
                        MimeBodyPart attachPart = new MimeBodyPart();
                        DataSource source = new ByteArrayDataSource(file.getInputStream(), file.getContentType());
                        attachPart.setDataHandler(new DataHandler(source));
                        attachPart.setFileName(file.getOriginalFilename());
                        multipart.addBodyPart(attachPart);
                        log.debug("[SEND EMAIL] Added attachment: {}", file.getOriginalFilename());
                    }
                }
            }

            // Set content and send
            message.setContent(multipart);
            log.info("[SEND EMAIL] Sending email to {}", emailDTO.getReceiver());
            Transport.send(message);
            emailUtilities.saveSuccessLog(emailDTO, emailDTO.getEmailId(), retries);
            log.info("[SEND EMAIL] Email successfully sent to {}", emailDTO.getReceiver());

            return "Email sent successfully to " + emailDTO.getReceiver();
        } catch (Exception e) {
            log.error("[SEND EMAIL] Failed to send email: {}", e.getMessage(), e);
            String status = emailUtilities.checkTimeout(e) ? EmailStatus.TIMEOUT : EmailStatus.FAILED;
            emailUtilities.saveErrorLog(emailDTO, status, emailDTO.getEmailId(), retries, "500", "EMAIL_SEND_ERROR", e.getMessage());
            throw e;
        }
    }

    /**
     * Validate email addresses and required fields
     */
    private List<String> validateEmails(EmailDTO emailDTO) throws Exception {
        List<String> errors = new ArrayList<>();

        if (emailDTO.getSubject() == null || emailDTO.getSubject().isEmpty()) {
            log.warn("[SEND EMAIL] Email validation: Missing subject");
            errors.add("Email subject is required");
        }

        if (emailDTO.getSender() == null || emailDTO.getSender().isEmpty()) {
            log.warn("[SEND EMAIL] Email validation: Missing sender");
            errors.add("Sender email is required");
        } else if (!emailDTO.getSender().matches(RegexValidator.EMAIL_FORMAT)) {
            log.warn("[SEND EMAIL] Email validation: Invalid sender format: {}", emailDTO.getSender());
            errors.add("Invalid sender email: " + emailDTO.getSender());
        }

        // Validate receiver emails
        if (emailDTO.getReceiver() == null || emailDTO.getReceiver().isEmpty()) {
            log.warn("[SEND EMAIL] Email validation: Missing receiver");
            errors.add("Receiver email is required");
        } else {
            String receiver = emailDTO.getReceiver();
            if (receiver.contains(";")) {
                EmailUtilities.EmailResult result = emailUtilities.splitEmails(receiver, "RECEIVER");
                emailDTO.setReceiver(result.getEmails());
                errors.addAll(result.getErrors());
                if (result.getErrors().isEmpty()) {
                    log.debug("[SEND EMAIL] Email receiver valid! -> {}", emailDTO.getReceiver());
                }
            } else if (!receiver.matches(RegexValidator.EMAIL_FORMAT)) {
                log.warn("[SEND EMAIL] Email validation: Invalid receiver format: {}", receiver);
                errors.add("Invalid receiver email: " + receiver);
            }
        }

        // Validate CC emails
        if (emailDTO.getCc() != null && !emailDTO.getCc().isEmpty()) {
            String cc = emailDTO.getCc();
            if (cc.contains(";")) {
                EmailUtilities.EmailResult result = emailUtilities.splitEmails(cc, "CC");
                emailDTO.setCc(result.getEmails());
                errors.addAll(result.getErrors());
                if (result.getErrors().isEmpty()) {
                    log.debug("[SEND EMAIL] Email CC valid! -> {}", emailDTO.getCc());
                }
            } else if (!cc.matches(RegexValidator.EMAIL_FORMAT)) {
                log.warn("[SEND EMAIL] Email validation: Invalid CC format: {}", cc);
                errors.add("Invalid CC email: " + cc);
            }
        } else {
            log.debug("[SEND EMAIL] No Email CCs!");
        }

        // Validate BCC emails
        if (emailDTO.getBcc() != null && !emailDTO.getBcc().isEmpty()) {
            String bcc = emailDTO.getBcc();
            if (bcc.contains(";")) {
                EmailUtilities.EmailResult result = emailUtilities.splitEmails(bcc, "BCC");
                emailDTO.setBcc(result.getEmails());
                errors.addAll(result.getErrors());
                if (result.getErrors().isEmpty()) {
                    log.debug("[SEND EMAIL] Email BCC valid! -> {}", emailDTO.getBcc());
                }
            } else if (!bcc.matches(RegexValidator.EMAIL_FORMAT)) {
                log.warn("[SEND EMAIL] Email validation: Invalid BCC format: {}", bcc);
                errors.add("Invalid BCC email: " + bcc);
            }
        } else {
            log.debug("[SEND EMAIL] No Email BCCs!");
        }

        return errors;
    }

    private String getEmailTemplate(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            return getDefaultEmailTemplate();
        }

        EmailTemplate template = emailTemplateRepository.findByTemplateId(templateName)
                .orElseGet(() -> {
                    log.warn("[SEND EMAIL] Template not found: {}, using default", templateName);
                    return null;
                });

        if (template != null && template.getTemplate() != null && !template.getTemplate().isEmpty()) {
            log.debug("[SEND EMAIL] Using database template: {}", templateName);
            return template.getTemplate();
        }

        log.debug("[SEND EMAIL] Using default email template");
        return getDefaultEmailTemplate();
    }

    private String getDefaultEmailTemplate() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Email Template</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; }\n" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n" +
                "        .header { background-color: #4285f4; color: white; padding: 10px; text-align: center; }\n" +
                "        .content { padding: 20px; background-color: #f9f9f9; }\n" +
                "        .footer { text-align: center; font-size: 12px; color: #999; padding: 10px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <h1>[[${subject}]]</h1>\n" +
                "        </div>\n" +
                "        <div class=\"content\">\n" +
                "            <p>Hello [[${name}]],</p>\n" +
                "            <p>[[${message}]]</p>\n" +
                "            <p>Thank you for your attention.</p>\n" +
                "        </div>\n" +
                "        <div class=\"footer\">\n" +
                "            <p>This is an automated email. Please do not reply.</p>\n" +
                "            <p>© [[${currentYear}]] Nivleking Application</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }
}