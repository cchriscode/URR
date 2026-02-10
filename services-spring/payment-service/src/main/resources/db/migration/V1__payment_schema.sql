CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reservation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    event_id UUID,
    order_id VARCHAR(64) UNIQUE NOT NULL,
    payment_key VARCHAR(200) UNIQUE,
    amount INTEGER NOT NULL,
    method VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    toss_order_name VARCHAR(255),
    toss_status VARCHAR(50),
    toss_requested_at TIMESTAMPTZ,
    toss_approved_at TIMESTAMPTZ,
    toss_receipt_url TEXT,
    toss_checkout_url TEXT,
    toss_response JSONB,
    refund_amount INTEGER DEFAULT 0,
    refund_reason TEXT,
    refunded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_id UUID REFERENCES payments(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    endpoint VARCHAR(255),
    method VARCHAR(10),
    request_headers JSONB,
    request_body JSONB,
    response_status INTEGER,
    response_body JSONB,
    error_code VARCHAR(50),
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
