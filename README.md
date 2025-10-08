# 🚀 Spring Boot Utilities

![Java Version](https://img.shields.io/badge/Java-21-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-latest-blue)

> **This is my project for building reusable and personal utility services as a microservice architecture.**

This project provides a collection of utility services that can be easily integrated into another Spring Boot application.

### 1️⃣ Email Service
A email management system with time delay feature:
- ✉Template-based HTML emails with dynamic variables
- Multiple file attachments support
- Delay mechanism
- Logging
- Multiple recipients (To, CC, BCC)

### 2️⃣ Database-Driven Config Server
Dynamic configuration management:
- Store configurations in database
- Live refresh via Spring Actuator (`/actuator/refresh`)
- Easy configuration updates through database

---

## Future Plans
- PDF Generation
- Send Teams Message
- Data Export (CSV, Excel, PDF)

---

## 📋 Table of Contents

- [Getting Started](#-getting-started)
- [Usage Examples](#-usage-examples)

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker & Docker Compose (optional, for local development)
- PostgreSQL database

### Quick Start with Docker

1. **Clone the repository:**
```bash
git clone https://github.com/yourusername/spring-boot-personal-utilities.git
cd spring-boot-personal-utilities
```

2. **Setup environment variables:**
```bash
# Edit .env with your actual credentials
cp .env.example .env
```

3. **Start PostgreSQL and pgAdmin:**
```bash
docker-compose build
docker-compose up -d
```

This will start:
- **PostgreSQL** on port `5332`
- **pgAdmin4** on port `5050` → http://localhost:5050

4. **Build and run:**
```bash
mvn clean install
mvn spring-boot:run
```

Utilities will start on **http://localhost:8080**

## Usage Examples

### 1️⃣ Email Service (OAS 3: docs/email-api.json)

#### Basic Email

```java
@Autowired
private EmailService emailService;

public void sendWelcomeEmail(String userEmail, String userName) throws Exception {
    EmailDTO emailDTO = new EmailDTO();
    emailDTO.setEmailId(UUID.randomUUID().toString());
    emailDTO.setEmailType("NOTIFICATION");
    emailDTO.setSender("noreply@yourcompany.com");
    emailDTO.setReceiver(userEmail);
    emailDTO.setSubject("Welcome to Our Platform!");
    emailDTO.setTemplateName("welcome_template");
    
    // Template variables
    Map<String, Object> params = new HashMap<>();
    params.put("userName", userName);
    params.put("year", LocalDate.now().getYear());
    emailDTO.setParams(params);
    
    String result = emailService.sendEmail(emailDTO, null);
    log.info("Email sent: {}", result);
}
```

#### Email with Attachments

```java
public void sendInvoice(MultipartFile[] invoiceFiles) throws Exception {
    EmailDTO emailDTO = new EmailDTO();
    emailDTO.setEmailId("INV-" + UUID.randomUUID().toString());
    emailDTO.setEmailType("INVOICE");
    emailDTO.setSender("billing@yourcompany.com");
    emailDTO.setReceiver("customer@example.com");
    emailDTO.setSubject("Your Invoice #12345");
    emailDTO.setTemplateName("invoice_template");
    emailDTO.setPriority("1"); // High priority
    
    Map<String, Object> params = new HashMap<>();
    params.put("invoiceNumber", "12345");
    params.put("amount", "$1,234.56");
    params.put("dueDate", "2025-10-31");
    emailDTO.setParams(params);
    
    String result = emailService.sendEmail(emailDTO, invoiceFiles);
    log.info("Invoice sent: {}", result);
}
```

#### Email with Multiple Recipients

```java
public void sendAnnouncement() throws Exception {
    EmailDTO emailDTO = new EmailDTO();
    emailDTO.setEmailId(UUID.randomUUID().toString());
    emailDTO.setEmailType("ANNOUNCEMENT");
    emailDTO.setSender("info@yourcompany.com");
    emailDTO.setReceiver("team@example.com");
    emailDTO.setCc("manager@example.com;director@example.com"); // Multiple CC
    emailDTO.setBcc("archive@example.com"); // BCC for record
    emailDTO.setSubject("Q4 Company Update");
    emailDTO.setTemplateName("announcement_template");
    
    String result = emailService.sendEmail(emailDTO, null);
    log.info("Announcement sent: {}", result);
}
```

#### Creating Email Templates

Store HTML templates in the `EMAIL_TEMPLATE` table:

```sql
INSERT INTO EMAIL_TEMPLATE (TEMPLATE_ID, TEMPLATE) 
VALUES (
    'welcome_template',
    '<html>
        <body style="font-family: Arial, sans-serif;">
            <h1>Welcome {{userName}}! 👋</h1>
            <p>Thank you for joining us in {{year}}.</p>
            <p>We''re excited to have you on board!</p>
            <hr/>
            <p style="color: #666;">Best regards,<br/>The Team</p>
        </body>
    </html>'
);
```

#### Using CURL

```bash
curl -X POST http://localhost:8080/api/utilities/mailer/send-email \
  -F 'dto={"emailId":"test-123","emailType":"NOTIFICATION","sender":"noreply@example.com","receiver":"user@example.com","subject":"Test Email","templateName":"welcome_template","params":{"userName":"John Doe","year":"2025"}};type=application/json' \
  -F "files=@/path/to/document.pdf"
```

### 2️⃣ Database-Driven Config Server

#### Reading Configuration

```java
import com.nivleking.springboot.dto.ConfigMapData;

@Service
public class MyService {

    @Autowired
    private ConfigMapData emailHost;

    @Autowired
    private ConfigMapData emailPort;

    @Resource()
    private Map<String, String> emailDelayMap;

    public void useConfig() {
        log.info("SMTP Host: {}", emailHost.getValue());
        log.info("SMTP Port: {}", emailPort.getValue());
        log.info("Promotional email delay: {} ms", emailDelayMap.get("PROMOTIONAL"));
    }
}
```

#### Updating Configuration

1. **Update database:**
```sql
UPDATE CONFIG_SERVER 
SET CONFIG_VALUE = 'smtp.office365.com' 
WHERE CONFIG_NAME = 'com.nivleking.springboot.email.smtp.host';
```

2. **Refresh application:**
```bash
curl -X POST http://localhost:8080/actuator/refresh
```

#### Adding New Configuration

```java
import com.nivleking.springboot.dto.ConfigMapData;

// 1. Add to CONFIG_SERVER table
INSERT INTO CONFIG_SERVER (PROPERTIES, VALUE) VALUES ('com.nivleking.springboot.my.new.config','my-value');

// 2. Create Bean with RefreshScope annotation in UtilitiesConfiguration
@Bean
@RefreshScope
public ConfigMapData myNewConfig() {
    return new ConfigMapData(configServerHolder.getConfig("com.nivleking.springboot.my.new.config", "default-value"));
}

// 3. Inject and use
@Autowired
private ConfigMapData myNewConfig;
```

## 📚 API Documentation
Complete API documentation is available in OpenAPI 3.0.3 format:
- **Email Service OpenAPI JSON**: [`docs/email-api.json`](./docs/email-api.json)
- **SwaggerHub**: [API Documentation](https://app.swaggerhub.com/apis/freelance-a1b/spring-boot-utilities/1.0.0) 🌐