CREATE INDEX idx_outbox_retry_pending
    ON dispatch_outbox(region_code, status, created_at)
    WHERE status = 'RETRY_PENDING';
