-- Add payment tracking to memberships for pending payment flow
ALTER TABLE artist_memberships ADD COLUMN IF NOT EXISTS payment_reference_id UUID;
