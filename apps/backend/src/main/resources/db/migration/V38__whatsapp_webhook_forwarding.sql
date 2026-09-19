-- Provider-wide routing outbox, not tenant-owned CRM data. Only filtered receipts/opt-outs.
CREATE TABLE whatsapp_webhook_forwards (
    id varchar(64) PRIMARY KEY,
    payload text,
    attempts integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    delivered_at timestamptz,
    last_failure varchar(64)
);
CREATE INDEX whatsapp_webhook_forwards_pending ON whatsapp_webhook_forwards (next_attempt_at)
    WHERE delivered_at IS NULL;
CREATE INDEX whatsapp_webhook_forwards_delivered ON whatsapp_webhook_forwards (delivered_at)
    WHERE delivered_at IS NOT NULL;
