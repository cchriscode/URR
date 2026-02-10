CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS daily_stats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    date DATE NOT NULL UNIQUE,
    total_reservations INTEGER DEFAULT 0,
    confirmed_reservations INTEGER DEFAULT 0,
    cancelled_reservations INTEGER DEFAULT 0,
    total_revenue INTEGER DEFAULT 0,
    payment_revenue INTEGER DEFAULT 0,
    new_users INTEGER DEFAULT 0,
    active_users INTEGER DEFAULT 0,
    new_events INTEGER DEFAULT 0,
    active_events INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS event_stats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL UNIQUE,
    total_seats INTEGER DEFAULT 0,
    reserved_seats INTEGER DEFAULT 0,
    available_seats INTEGER DEFAULT 0,
    total_reservations INTEGER DEFAULT 0,
    confirmed_reservations INTEGER DEFAULT 0,
    total_revenue INTEGER DEFAULT 0,
    average_ticket_price INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    last_calculated_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
