-- V4: Per-artist membership system with tiers and point tracking

CREATE TABLE IF NOT EXISTS artist_memberships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    artist_id UUID NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
    tier VARCHAR(20) NOT NULL DEFAULT 'SILVER',
    points INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    joined_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, artist_id)
);

CREATE TABLE IF NOT EXISTS membership_point_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    membership_id UUID NOT NULL REFERENCES artist_memberships(id) ON DELETE CASCADE,
    action_type VARCHAR(50) NOT NULL,
    points INTEGER NOT NULL,
    description TEXT,
    reference_id UUID,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memberships_user ON artist_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_memberships_artist ON artist_memberships(artist_id);
CREATE INDEX IF NOT EXISTS idx_memberships_user_artist ON artist_memberships(user_id, artist_id);
CREATE INDEX IF NOT EXISTS idx_memberships_status ON artist_memberships(status);
CREATE INDEX IF NOT EXISTS idx_point_logs_membership ON membership_point_logs(membership_id);
