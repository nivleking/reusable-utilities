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