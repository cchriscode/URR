-- Ticket Transfer System
CREATE TABLE IF NOT EXISTS ticket_transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    seller_id UUID NOT NULL,
    buyer_id UUID,
    artist_id UUID NOT NULL REFERENCES artists(id),
    original_price INTEGER NOT NULL,
    transfer_fee INTEGER NOT NULL DEFAULT 0,
    transfer_fee_percent INTEGER NOT NULL DEFAULT 0,
    total_price INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'listed',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transfers_status_artist ON ticket_transfers(status, artist_id);
CREATE INDEX idx_transfers_seller ON ticket_transfers(seller_id);
CREATE INDEX idx_transfers_reservation ON ticket_transfers(reservation_id);
