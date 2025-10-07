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