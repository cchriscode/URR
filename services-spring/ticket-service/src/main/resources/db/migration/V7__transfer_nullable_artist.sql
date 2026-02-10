ALTER TABLE ticket_transfers DROP CONSTRAINT IF EXISTS ticket_transfers_artist_id_fkey;
ALTER TABLE ticket_transfers ALTER COLUMN artist_id DROP NOT NULL;
