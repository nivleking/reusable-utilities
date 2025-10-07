package com.nivleking.springboot;

import com.nivleking.springboot.constant.EmailStatus;
import com.nivleking.springboot.dto.EmailDTO;
import com.nivleking.springboot.model.EmailTemplate;
import com.nivleking.springboot.repository.EmailLogRepository;
import com.nivleking.springboot.repository.EmailTemplateRepository;
import com.nivleking.springboot.service.EmailService;
import com.nivleking.springboot.service.EmailUtilities;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @InjectMocks
    private EmailService emailService;

    @Mock
    private EmailUtilities emailUtilities;

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @Mock
    private EmailLogRepository emailLogRepository;

    @Captor
    private ArgumentCaptor<EmailDTO> emailDTOCaptor;

    private EmailDTO validEmailDTO;
    private Map<String, String> emailDelayMap;
    private Session mockSession;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Initialize standard valid email DTO
        validEmailDTO = new EmailDTO();
        validEmailDTO.setEmailId(UUID.randomUUID().toString());
        validEmailDTO.setEmailType("NOTIFICATION");
        validEmailDTO.setSender("sender@example.com");
        validEmailDTO.setReceiver("receiver@example.com");
        validEmailDTO.setSubject("Test Subject");
        validEmailDTO.setTemplateName("test_template");

        // Setup email delay map
        emailDelayMap = new HashMap<>();
        emailDelayMap.put("PROMOTIONAL", "3000");

        // Setup mock session
        mockSession = Session.getInstance(new Properties());

        // Setup mock behavior
        ReflectionTestUtils.setField(emailService, "emailUsername", "test@example.com");
        ReflectionTestUtils.setField(emailService, "emailPassword", "password");

        lenient().when(emailUtilities.getDefaultProps()).thenReturn(new Properties());
    }

    @Test
    public void testSendEmail_Success() throws Exception {
        // Arrange
        when(emailUtilities.checkIfEmailNeedsDelay(anyString())).thenReturn(false);
        when(emailLogRepository.createOrUpdateEmailLog(any(), anyString(), anyString())).thenReturn(validEmailDTO.getEmailId());

        // Mock template retrieval
        EmailTemplate mockTemplate = new EmailTemplate();
        mockTemplate.setTemplateId("test_template");
        mockTemplate.setTemplate("<html><body>Hello {{name}}</body></html>");
        when(emailTemplateRepository.findByTemplateId(anyString())).thenReturn(Optional.of(mockTemplate));

        // Mock template processing
        when(emailUtilities.processTemplate(anyString(), any())).thenReturn("<html><body>Hello John</body></html>");

        // Mock send functionality using PowerMockito
        try (MockedStatic<jakarta.mail.Transport> mockedTransport = mockStatic(jakarta.mail.Transport.class)) {
            // Act
            String result = emailService.sendEmail(validEmailDTO, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.contains("successfully"));

            // Verify transport was called
            mockedTransport.verify(() -> jakarta.mail.Transport.send(any(MimeMessage.class)), times(1));

            // Verify success log was saved
            verify(emailUtilities).saveSuccessLog(emailDTOCaptor.capture(), eq(validEmailDTO.getEmailId()), any(BigDecimal.class));
            assertEquals(validEmailDTO.getEmailId(), emailDTOCaptor.getValue().getEmailId());
        }
    }

    @Test
    public void testSendEmail_WithDelay() throws Exception {
        // Arrange
        when(emailUtilities.checkIfEmailNeedsDelay(anyString())).thenReturn(true);
        when(emailUtilities.getDelayByEmailType(anyString())).thenReturn(3000L);
        when(emailLogRepository.checkAndCreateEmailDelay(any(), anyString(), anyString(), anyLong(), any())).thenReturn(validEmailDTO.getEmailId());

        EmailTemplate mockTemplate = new EmailTemplate();
        mockTemplate.setTemplateId("test_template");
        mockTemplate.setTemplate("<html><body>Hello {{name}}</body></html>");
        when(emailTemplateRepository.findByTemplateId(anyString())).thenReturn(Optional.of(mockTemplate));

        when(emailUtilities.processTemplate(anyString(), any())).thenReturn("<html><body>Hello John</body></html>");

        try (MockedStatic<jakarta.mail.Transport> mockedTransport = mockStatic(jakarta.mail.Transport.class)) {
            // Act
            String result = emailService.sendEmail(validEmailDTO, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.contains("successfully"));

            // Verify delay was checked
            verify(emailUtilities).checkIfEmailNeedsDelay(validEmailDTO.getEmailType());
            verify(emailUtilities).getDelayByEmailType(validEmailDTO.getEmailType());
        }
    }

    @Test
    public void testSendEmail_WithDelayActive() throws Exception {
        // Arrange
        when(emailUtilities.checkIfEmailNeedsDelay(anyString())).thenReturn(true);
        when(emailUtilities.getDelayByEmailType(anyString())).thenReturn(3000L);
        when(emailLogRepository.checkAndCreateEmailDelay(any(), anyString(), anyString(), anyLong(), any())).thenReturn(null);

        // Act
        String result = emailService.sendEmail(validEmailDTO, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("delay is still active"));

        // Verify no email was sent
        verify(emailTemplateRepository, never()).findByTemplateId(anyString());
    }

    @Test
    public void testSendEmail_ValidationFailure() throws Exception {
        // Arrange
        EmailDTO invalidDTO = new EmailDTO();
        invalidDTO.setEmailId(UUID.randomUUID().toString());
        invalidDTO.setEmailType("NOTIFICATION");
        invalidDTO.setSender("invalid-email");  // Invalid email format
        invalidDTO.setReceiver("receiver@example.com");
        invalidDTO.setSubject("Test Subject");

        when(emailUtilities.checkIfEmailNeedsDelay(anyString())).thenReturn(false);
        when(emailLogRepository.createOrUpdateEmailLog(any(), anyString(), anyString())).thenReturn(invalidDTO.getEmailId());

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            emailService.sendEmail(invalidDTO, null);
        });

        assertTrue(exception.getMessage().contains("Invalid sender email"));

        // Verify error log was saved
        verify(emailUtilities).saveErrorLog(eq(invalidDTO), eq(EmailStatus.FAILED), eq(invalidDTO.getEmailId()),
                any(BigDecimal.class), eq("400"), eq("VALIDATION_ERROR"), anyString());
    }

    @Test
    public void testSendEmail_WithAttachments() throws Exception {
        // Arrange
        when(emailUtilities.checkIfEmailNeedsDelay(anyString())).thenReturn(false);
        when(emailLogRepository.createOrUpdateEmailLog(any(), anyString(), anyString())).thenReturn(validEmailDTO.getEmailId());

        EmailTemplate mockTemplate = new EmailTemplate();
        mockTemplate.setTemplateId("test_template");
        mockTemplate.setTemplate("<html><body>Hello {{name}}</body></html>");
        when(emailTemplateRepository.findByTemplateId(anyString())).thenReturn(Optional.of(mockTemplate));
        when(emailUtilities.processTemplate(anyString(), any())).thenReturn("<html><body>Hello John</body></html>");

        // Mock attachments
        MultipartFile[] files = new MultipartFile[1];
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("test data".getBytes()));
        files[0] = mockFile;

        try (MockedStatic<jakarta.mail.Transport> mockedTransport = mockStatic(jakarta.mail.Transport.class)) {
            // Act
            String result = emailService.sendEmail(validEmailDTO, files);

            // Assert
            assertNotNull(result);
            assertTrue(result.contains("successfully"));
        }
    }

    @Test
    public void testSendEmail_WithCcAndBcc() throws Exception {
        // Arrange
        EmailDTO emailWithCcBcc = new EmailDTO();
        emailWithCcBcc.setEmailId(UUID.randomUUID().toString());
        emailWithCcBcc.setEmailType("NOTIFICATION");
        emailWithCcBcc.setSender("sender@example.com");
        emailWithCcBcc.setReceiver("receiver@example.com");
        emailWithCcBcc.setCc("cc@example.com");
        emailWithCcBcc.setBcc("bcc@example.com");
        emailWithCcBcc.setSubject("Test Subject");
        emailWithCcBcc.setTemplateName("test_template");

        when(emailUtilities.checkIfEmailNeedsDelay(anyString())).thenReturn(false);
        when(emailLogRepository.createOrUpdateEmailLog(any(), anyString(), anyString())).thenReturn(emailWithCcBcc.getEmailId());

        EmailTemplate mockTemplate = new EmailTemplate();
        mockTemplate.setTemplateId("test_template");
        mockTemplate.setTemplate("<html><body>Hello {{name}}</body></html>");
        when(emailTemplateRepository.findByTemplateId(anyString())).thenReturn(Optional.of(mockTemplate));
        when(emailUtilities.processTemplate(anyString(), any())).thenReturn("<html><body>Hello John</body></html>");

        try (MockedStatic<jakarta.mail.Transport> mockedTransport = mockStatic(jakarta.mail.Transport.class)) {
            // Act
            String result = emailService.sendEmail(emailWithCcBcc, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.contains("successfully"));
        }
    }

    @Test
    public void testSendEmail_MultipleBccRecipients() throws Exception {
        // Arrange
        EmailDTO emailWithMultipleBcc = new EmailDTO();
        emailWithMultipleBcc.setEmailId(UUID.randomUUID().toString());
        emailWithMultipleBcc.setEmailType("NOTIFICATION");
        emailWithMultipleBcc.setSender("sender@example.com");
        emailWithMultipleBcc.setReceiver("receiver@example.com");
        emailWithMultipleBcc.setBcc("bcc1@example.com;bcc2@example.com");
        emailWithMultipleBcc.setSubject("Test Subject");

        when(emailUtilities.checkIfEmailNeedsDelay(anyString())).thenReturn(false);
        when(emailLogRepository.createOrUpdateEmailLog(any(), anyString(), anyString())).thenReturn(emailWithMultipleBcc.getEmailId());

        // Mock split emails
        EmailUtilities.EmailResult mockResult = new EmailUtilities().new EmailResult("bcc1@example.com,bcc2@example.com", new ArrayList<>());
        when(emailUtilities.splitEmails(anyString(), eq("BCC"))).thenReturn(mockResult);

        EmailTemplate mockTemplate = new EmailTemplate();
        mockTemplate.setTemplate("<html><body>Default template</body></html>");
        lenient().when(emailTemplateRepository.findByTemplateId(anyString())).thenReturn(Optional.of(mockTemplate));
        when(emailUtilities.processTemplate(anyString(), any())).thenReturn("<html><body>Default template</body></html>");

        try (MockedStatic<jakarta.mail.Transport> mockedTransport = mockStatic(jakarta.mail.Transport.class)) {
            // Act
            String result = emailService.sendEmail(emailWithMultipleBcc, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.contains("successfully"));
        }
    }

    @Test
    public void testSendEmail_TemplateNotFound() throws Exception {
        // Arrange
        when(emailUtilities.checkIfEmailNeedsDelay(anyString())).thenReturn(false);
        when(emailLogRepository.createOrUpdateEmailLog(any(), anyString(), anyString())).thenReturn(validEmailDTO.getEmailId());

        // Template not found, should use default
        when(emailTemplateRepository.findByTemplateId(anyString())).thenReturn(Optional.empty());
        when(emailUtilities.processTemplate(anyString(), any())).thenReturn("<html><body>Default template</body></html>");

        try (MockedStatic<jakarta.mail.Transport> mockedTransport = mockStatic(jakarta.mail.Transport.class)) {
            // Act
            String result = emailService.sendEmail(validEmailDTO, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.contains("successfully"));
        }
    }
}