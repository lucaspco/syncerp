-- Initial schema for SyncERP

CREATE TABLE IF NOT EXISTS bling_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj VARCHAR(14) NOT NULL UNIQUE,
    account_name VARCHAR(255) NOT NULL,
    oauth_access_token TEXT,
    oauth_refresh_token TEXT,
    oauth_token_expires_at TIMESTAMP,
    api_status VARCHAR(20) DEFAULT 'INACTIVE',
    last_connected_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj VARCHAR(14) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    payload_hash VARCHAR(64),
    bling_event_id VARCHAR(255),
    processing_status VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,
    retry_count INT DEFAULT 0,
    last_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_event_id UUID NOT NULL REFERENCES webhook_event(id),
    source_cnpj VARCHAR(14) NOT NULL,
    target_cnpj VARCHAR(14) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(255),
    sync_status VARCHAR(20) DEFAULT 'PENDING',
    sync_operation VARCHAR(20),
    error_message TEXT,
    response_payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(source_cnpj) REFERENCES bling_account(cnpj),
    FOREIGN KEY(target_cnpj) REFERENCES bling_account(cnpj)
);

CREATE TABLE IF NOT EXISTS dedup_window (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payload_hash VARCHAR(64) NOT NULL UNIQUE,
    webhook_event_id UUID NOT NULL REFERENCES webhook_event(id),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dead_letter (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_event_id UUID NOT NULL REFERENCES webhook_event(id),
    failure_reason TEXT,
    last_error TEXT,
    reprocessing_count INT DEFAULT 0,
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes para performance
CREATE INDEX idx_webhook_event_cnpj_created ON webhook_event(cnpj, created_at DESC);
CREATE INDEX idx_webhook_event_status ON webhook_event(processing_status);
CREATE INDEX idx_webhook_event_hash ON webhook_event(payload_hash);
CREATE INDEX idx_sync_log_event_id ON sync_log(webhook_event_id);
CREATE INDEX idx_sync_log_status ON sync_log(sync_status);
CREATE INDEX idx_dedup_window_expires ON dedup_window(expires_at);
CREATE INDEX idx_dead_letter_event_id ON dead_letter(webhook_event_id);
