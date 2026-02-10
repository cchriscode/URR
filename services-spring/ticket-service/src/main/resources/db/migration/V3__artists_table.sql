-- V3: Create artists table and link to events

CREATE TABLE IF NOT EXISTS artists (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    image_url TEXT,
    description TEXT,
    membership_price INTEGER NOT NULL DEFAULT 30000,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Seed artists from existing events
INSERT INTO artists (name)
SELECT DISTINCT artist_name
FROM events
WHERE artist_name IS NOT NULL AND artist_name <> ''
ON CONFLICT (name) DO NOTHING;

-- Add artist_id FK to events table
ALTER TABLE events ADD COLUMN IF NOT EXISTS artist_id UUID REFERENCES artists(id);

-- Backfill artist_id from artist_name
UPDATE events e
SET artist_id = a.id
FROM artists a
WHERE e.artist_name = a.name AND e.artist_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_events_artist_id ON events(artist_id);
CREATE INDEX IF NOT EXISTS idx_artists_name ON artists(name);
