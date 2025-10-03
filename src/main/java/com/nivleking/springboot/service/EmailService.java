package com.nivleking.springboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nivleking.springboot.constant.EmailStatus;
import com.nivleking.springboot.constant.RegexValidator;
import com.nivleking.springboot.dto.EmailDTO;
import com.nivleking.springboot.model.EmailTemplate;
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
        try {
            log.info("[SEND EMAIL] Starting email sending process to recipient: {}", emailDTO.getReceiver());
            log.debug("[SEND EMAIL] Email details: id={}, type={}, priority={}", emailDTO.getEmailId(), emailDTO.getEmailType(), emailDTO.getPriority());

            // Save initial log entry
            emailUtilities.insertLog(emailDTO, emailDTO.getEmailId(), retries);

            // Validate emails and collect errors
            log.debug("[SEND EMAIL] Validating email addresses");
            List<String> errors = validateEmails(emailDTO);
            if (!errors.isEmpty()) {
                String errorMsg = String.join(", ", errors);
                log.error("[SEND EMAIL] Email validation failed: {}", errorMsg);
                emailUtilities.saveErrorLog(emailDTO, EmailStatus.FAILED, emailDTO.getEmailId(), retries, "400", "VALIDATION_ERROR", errorMsg);
                throw new IllegalArgumentException("Email validation failed: " + errorMsg);
            }
            log.debug("[SEND EMAIL] Email validation successful");

            // Create auth-enabled session for Gmail
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
    private List<String> validateEmails(EmailDTO emailDTO) {
        List<String> errors = new ArrayList<>();

        if (emailDTO.getSubject() == null || emailDTO.getSubject().isEmpty()) {
            log.warn("Email validation: Missing subject");
            errors.add("Email subject is required");
        }

        if (emailDTO.getSender() == null || emailDTO.getSender().isEmpty()) {
            log.warn("Email validation: Missing sender");
            errors.add("Sender email is required");
        } else if (!emailDTO.getSender().matches(RegexValidator.EMAIL_FORMAT)) {
            log.warn("Email validation: Invalid sender format: {}", emailDTO.getSender());
            errors.add("Invalid sender email: " + emailDTO.getSender());
        }

        if (emailDTO.getReceiver() == null || emailDTO.getReceiver().isEmpty()) {
            log.warn("Email validation: Missing receiver");
            errors.add("Receiver email is required");
        } else if (!emailDTO.getReceiver().matches(RegexValidator.EMAIL_FORMAT)) {
            log.warn("Email validation: Invalid receiver format: {}", emailDTO.getReceiver());
            errors.add("Invalid receiver email: " + emailDTO.getReceiver());
        }

        if (emailDTO.getCc() != null && !emailDTO.getCc().isEmpty() &&
                !emailDTO.getCc().matches(RegexValidator.EMAIL_FORMAT)) {
            log.warn("Email validation: Invalid CC format: {}", emailDTO.getCc());
            errors.add("Invalid CC email: " + emailDTO.getCc());
        }

        if (emailDTO.getBcc() != null && !emailDTO.getBcc().isEmpty() &&
                !emailDTO.getBcc().matches(RegexValidator.EMAIL_FORMAT)) {
            log.warn("Email validation: Invalid BCC format: {}", emailDTO.getBcc());
            errors.add("Invalid BCC email: " + emailDTO.getBcc());
        }

        return errors;
    }

    private String getEmailTemplate(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            return getDefaultEmailTemplate();
        }

        // Try to find template in database
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