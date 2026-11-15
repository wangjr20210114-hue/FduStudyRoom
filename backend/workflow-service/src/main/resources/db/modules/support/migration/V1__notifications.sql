CREATE TABLE support.notifications (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 recipient_id uuid NOT NULL, channel varchar(16) NOT NULL CHECK (channel IN ('IN_APP','EMAIL')),
 event_key varchar(255) NOT NULL UNIQUE, event_id uuid NOT NULL, payload_hash char(64) NOT NULL,
 source_module varchar(16) NOT NULL CHECK (source_module IN ('M1','M2','M3','D')), source_id uuid NOT NULL,
 title varchar(255) NOT NULL, body text NOT NULL, title_en varchar(255) NOT NULL, body_en text NOT NULL,
 email_address varchar(254), scheduled_at timestamptz NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'PENDING', retry_count integer NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
 next_retry_at timestamptz, sent_at timestamptz, read_at timestamptz, last_error_code varchar(64), delivery_started_at timestamptz,
 UNIQUE(event_id,channel),
 CHECK ((channel='IN_APP' AND status IN ('PENDING','SENT','CANCELLED')) OR
        (channel='EMAIL' AND status IN ('PENDING','SENT','FAILED','CANCELLED','UNKNOWN','NOT_CONFIGURED'))),
 CHECK (channel='IN_APP' OR read_at IS NULL), CHECK (status<>'SENT' OR sent_at IS NOT NULL),
 CHECK (status<>'UNKNOWN' OR next_retry_at IS NULL),
 CHECK (length(trim(title))>0 AND length(trim(body)) BETWEEN 1 AND 4000 AND length(trim(title_en))>0 AND length(trim(body_en)) BETWEEN 1 AND 4000)
);
CREATE INDEX notifications_delivery ON support.notifications(status,scheduled_at,next_retry_at) WHERE channel='EMAIL';
CREATE INDEX notifications_recipient ON support.notifications(recipient_id,created_at DESC,id DESC) WHERE channel='IN_APP';
