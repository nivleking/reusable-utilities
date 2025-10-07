CREATE SEQUENCE IF NOT EXISTS public.seq_email_log
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS public.config_server
(
    id
    numeric
(
    38,
    2
) NOT NULL PRIMARY KEY,
    properties varchar
(
    255
),
    value varchar
(
    255
)
    );

CREATE TABLE IF NOT EXISTS public.email_template
(
    id
    numeric
(
    38,
    2
) NOT NULL PRIMARY KEY,
    template_id varchar
(
    255
) NOT NULL UNIQUE,
    template text NOT NULL
    );

CREATE TABLE IF NOT EXISTS public.email_log
(
    id
    numeric
(
    38,
    2
) NOT NULL PRIMARY KEY,
    created_date timestamp
(
    6
) without time zone,
    email_delay timestamp
(
    6
)
  without time zone,
    email_id varchar
(
    255
),
    email_type varchar
(
    255
),
    error_code varchar
(
    255
),
    error_message text,
    http_code varchar
(
    255
),
    json_input text,
    last_send timestamp
(
    6
)
  without time zone,
    last_updated_date timestamp
(
    6
)
  without time zone,
    number_of_retries numeric
(
    38,
    2
),
    request_id varchar
(
    255
),
    status varchar
(
    255
),
    template_id varchar
(
    255
)
    );

INSERT INTO public.config_server (id, properties, value)
VALUES (1.00, 'com.nivleking.springboot.email.smtp.host', 'smtp.gmail.com'),
       (2.00, 'com.nivleking.springboot.email.smtp.port', '587'),
       (3.00, 'com.nivleking.springboot.email.delay-map', '{''NOTIFICATION_DELAY'': ''300000''}') ON CONFLICT (id) DO
UPDATE
    SET properties = EXCLUDED.properties, value = EXCLUDED.value;

INSERT INTO public.email_template (id, template_id, template)
VALUES (1.00,
        'default_template',
        '<!DOCTYPE html>
   <html>
   <head>
       <meta charset="UTF-8">
       <title>Email Template</title>
       <style>
           body { font-family: Arial, sans-serif; line-height: 1.6; }
           .container { max-width: 600px; margin: 0 auto; padding: 20px; }
           .header { background-color: #4285f4; color: white; padding: 10px; text-align: center; }
           .content { padding: 20px; background-color: #f9f9f9; }
           .footer { text-align: center; font-size: 12px; color: #999; padding: 10px; }
       </style>
   </head>
   <body>
       <div class="container">
           <div class="header">
               <h1>[[${subject}]]</h1>
           </div>
           <div class="content">
               <p>Hello [[${name}]],</p>
               <p>[[${message}]]</p>
               <p>Thank you for your attention.</p>
           </div>
           <div class="footer">
               <p>This is an automated email. Please do not reply.</p>
               <p>© [[${currentYear}]] Nivleking Application</p>
           </div>
       </div>
   </body>
   </html>') ON CONFLICT (id) DO
UPDATE
    SET template_id = EXCLUDED.template_id, template = EXCLUDED.template;

-- Create EMAIL_DELAY procedure
CREATE OR REPLACE PROCEDURE EMAIL_DELAY(
    V_CURRENT_TIME TIMESTAMP,
    V_EMAIL_TYPE VARCHAR,
    V_EMAIL_ID VARCHAR,
    V_DELAY_MILLISECONDS NUMERIC,
    V_MAX_RETRY NUMERIC,
    INOUT R_EMAIL_ID VARCHAR
)
LANGUAGE plpgsql
AS $$
DECLARE
EXISTING_EMAIL_COUNT INTEGER;
   ACTIVE_DELAY_COUNT INTEGER;
   V_DELAY_TIME TIMESTAMP;
   V_STATUS VARCHAR(20);
   V_CURRENT_RETRY NUMERIC;
   V_DUMMY INTEGER;
BEGIN
    -- Acquire lock on any record with this email type to ensure sequential processing
BEGIN
SELECT 1 INTO V_DUMMY
FROM EMAIL_LOG
WHERE EMAIL_TYPE = V_EMAIL_TYPE
    LIMIT 1
        FOR UPDATE NOWAIT;
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            -- No records found for this email type, that's fine - we'll insert first one
            NULL;
WHEN OTHERS THEN
            -- Another session already has the lock or other error
            R_EMAIL_ID := NULL;
            RETURN;
END;

    -- Check if email ID already exists
SELECT COUNT(*), MAX(STATUS) INTO EXISTING_EMAIL_COUNT, V_STATUS
FROM EMAIL_LOG
WHERE EMAIL_ID = V_EMAIL_ID;

-- Check active delays for this email type
SELECT COUNT(*) INTO ACTIVE_DELAY_COUNT
FROM EMAIL_LOG
WHERE EMAIL_TYPE = V_EMAIL_TYPE
  AND EMAIL_DELAY > V_CURRENT_TIME;

-- Calculate delay time
V_DELAY_TIME := V_CURRENT_TIME + (V_DELAY_MILLISECONDS/1000) * INTERVAL '1 second';

    -- If there is an active delay for this email type
    IF (ACTIVE_DELAY_COUNT > 0) THEN
        R_EMAIL_ID := NULL; -- Indicate delay is active
    ELSIF (EXISTING_EMAIL_COUNT > 0) THEN
        -- Email exists but not successful yet - update for retry
        IF V_STATUS != 'SUCCESS' THEN
            -- Get current retry count
SELECT NUMBER_OF_RETRIES INTO V_CURRENT_RETRY
FROM EMAIL_LOG
WHERE EMAIL_ID = V_EMAIL_ID;

-- Check if max retries reached
IF V_CURRENT_RETRY < V_MAX_RETRY THEN
UPDATE EMAIL_LOG
SET STATUS = 'PENDING',
    LAST_UPDATED_DATE = V_CURRENT_TIME,
    EMAIL_DELAY = V_DELAY_TIME,
    NUMBER_OF_RETRIES = NUMBER_OF_RETRIES + 1
WHERE EMAIL_ID = V_EMAIL_ID
  AND STATUS IN ('FAILED', 'TIMEOUT');

R_EMAIL_ID := V_EMAIL_ID; -- Allow retry
ELSE
                -- Max retries reached, don't update status, return NULL
                R_EMAIL_ID := NULL;
END IF;
ELSE
            R_EMAIL_ID := NULL; -- Already successful, no need to retry
END IF;
ELSE
        -- Insert new email log
        INSERT INTO EMAIL_LOG (
            ID,
            EMAIL_ID,
            STATUS,
            EMAIL_DELAY,
            CREATED_DATE,
            LAST_UPDATED_DATE,
            EMAIL_TYPE,
            NUMBER_OF_RETRIES
        ) VALUES (
            nextval('seq_email_log'),
            V_EMAIL_ID,
            'PENDING',
            V_DELAY_TIME,
            V_CURRENT_TIME,
            V_CURRENT_TIME,
            V_EMAIL_TYPE,
            0
        );

        R_EMAIL_ID := V_EMAIL_ID;
END IF;
END;
$$;

-- Create EMAIL_INSERT procedure
CREATE OR REPLACE PROCEDURE EMAIL_INSERT(
    V_CURRENT_TIME TIMESTAMP,
    V_EMAIL_TYPE VARCHAR,
    V_EMAIL_ID VARCHAR,
    INOUT R_EMAIL_ID VARCHAR
)
LANGUAGE plpgsql
AS $$
DECLARE
EXISTING_EMAIL_COUNT INTEGER;
    V_STATUS VARCHAR(20);
    V_CURRENT_RETRY NUMERIC;
    V_DUMMY INTEGER;
BEGIN
    -- Acquire lock to ensure sequential processing
BEGIN
SELECT 1 INTO V_DUMMY
FROM EMAIL_LOG
WHERE EMAIL_ID = V_EMAIL_ID
    LIMIT 1
        FOR UPDATE NOWAIT;
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            -- No records found for this email ID, that's fine
            NULL;
WHEN OTHERS THEN
            -- Another session already has the lock or other error
            R_EMAIL_ID := NULL;
            RETURN;
END;

    -- Check if email ID already exists
SELECT COUNT(*), MAX(STATUS) INTO EXISTING_EMAIL_COUNT, V_STATUS
FROM EMAIL_LOG
WHERE EMAIL_ID = V_EMAIL_ID;

IF (EXISTING_EMAIL_COUNT > 0) THEN
        -- Email exists - check status
        IF V_STATUS = 'SUCCESS' THEN
            -- Email was already sent successfully
            R_EMAIL_ID := NULL;
ELSE
            -- Email exists but not successful - update for retry
SELECT COALESCE(NUMBER_OF_RETRIES, 0) INTO V_CURRENT_RETRY
FROM EMAIL_LOG
WHERE EMAIL_ID = V_EMAIL_ID
    LIMIT 1;

-- Update for retry
UPDATE EMAIL_LOG
SET STATUS = 'PENDING',
    LAST_UPDATED_DATE = V_CURRENT_TIME,
    NUMBER_OF_RETRIES = NUMBER_OF_RETRIES + 1
WHERE EMAIL_ID = V_EMAIL_ID;

R_EMAIL_ID := V_EMAIL_ID; -- Return ID to allow email processing
END IF;
ELSE
        -- Insert new email log
        INSERT INTO EMAIL_LOG (
            ID,
            EMAIL_ID,
            STATUS,
            CREATED_DATE,
            LAST_UPDATED_DATE,
            EMAIL_TYPE,
            NUMBER_OF_RETRIES
        ) VALUES (
            nextval('seq_email_log'),
            V_EMAIL_ID,
            'PENDING',
            V_CURRENT_TIME,
            V_CURRENT_TIME,
            V_EMAIL_TYPE,
            0
        );

        R_EMAIL_ID := V_EMAIL_ID;
END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE;
END;
$$;