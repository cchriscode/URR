-- Support transfer and membership payment types
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_type VARCHAR(20) DEFAULT 'reservation';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS reference_id UUID;
ALTER TABLE payments ALTER COLUMN reservation_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_type ON payments(payment_type);
CREATE INDEX IF NOT EXISTS idx_payments_reference ON payments(reference_id);
