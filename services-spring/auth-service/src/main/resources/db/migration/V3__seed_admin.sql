-- Seed admin account for URR platform
-- admin@urr.com / admin123
INSERT INTO users (id, email, password_hash, name, phone, role, created_at, updated_at)
VALUES (
    uuid_generate_v4(),
    'admin@urr.com',
    '$2b$12$1RWdHeNU3.IUYL5.8IC3kuw/mXQiqZ6c3Z7YZ49O9ZYhJjgkeTO96',
    '관리자',
    '010-0000-0000',
    'admin',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (email) DO UPDATE SET role = 'admin';
