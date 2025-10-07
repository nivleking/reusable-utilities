CREATE SEQUENCE IF NOT EXISTS public.seq_email_log
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS public.config_server (
    id numeric(38,2) NOT NULL PRIMARY KEY,
    properties varchar(255),
    value varchar(255)
);

CREATE TABLE IF NOT EXISTS public.email_template (
    id numeric(38,2) NOT NULL PRIMARY KEY,
    template_id varchar(255) NOT NULL UNIQUE,
    template text NOT NULL
);

CREATE TABLE IF NOT EXISTS public.email_log (
    id numeric(38,2) NOT NULL PRIMARY KEY,
    created_date timestamp(6) without time zone,
    email_delay timestamp(6) without time zone,
    email_id varchar(255),
    email_type varchar(255),
    error_code varchar(255),
    error_message text,
    http_code varchar(255),
    json_input text,
    last_send timestamp(6) without time zone,
    last_updated_date timestamp(6) without time zone,
    number_of_retries numeric(38,2),
    request_id varchar(255),
    status varchar(255),
    template_id varchar(255)
);

INSERT INTO public.config_server (id, properties, value)
VALUES
    (1.00, 'com.nivleking.springboot.email.smtp.host', 'smtp.gmail.com'),
    (2.00, 'com.nivleking.springboot.email.smtp.port', '587')
    ON CONFLICT (id) DO UPDATE
SET properties = EXCLUDED.properties, value = EXCLUDED.value;

INSERT INTO public.email_template (id, template_id, template)
VALUES (
       1.00,
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
       </html>'
       )
    ON CONFLICT (id) DO UPDATE
SET template_id = EXCLUDED.template_id, template = EXCLUDED.template;