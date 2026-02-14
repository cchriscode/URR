-- Admin audit logging table for tracking all administrative actions
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_user_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    request_summary TEXT,
    response_status INTEGER,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_admin_user ON admin_audit_logs(admin_user_id);
CREATE INDEX idx_audit_logs_created_at ON admin_audit_logs(created_at);
CREATE INDEX idx_audit_logs_action ON admin_audit_logs(action);
