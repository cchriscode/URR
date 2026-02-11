-- Seed: Standing concert events (no seat_layout_id = no seat map, ticket-type-only purchase)
-- Run manually against the target DB — NOT via Flyway migration.
-- Usage: psql $DATABASE_URL -f scripts/seed-standing-events.sql
--
-- NOTE: Subqueries use LIMIT 1 and ORDER BY id to be deterministic if titles are duplicated.

INSERT INTO events (title, description, venue, address, event_date, sale_start_date, sale_end_date,
                    status, seat_layout_id, artist_name) VALUES
('ZICO STANDING NIGHT 2025', '지코의 스탠딩 전용 힙합 라이브 공연. 좌석 없이 자유롭게 즐기세요!',
 'YES24 라이브홀', '서울특별시 광진구 광나루로 56길 85',
 CURRENT_DATE + INTERVAL '18 days' + TIME '20:00',
 CURRENT_DATE - INTERVAL '1 days' + TIME '10:00',
 CURRENT_DATE + INTERVAL '17 days' + TIME '23:59',
 'on_sale', NULL, 'ZICO'),

('힙합 페스티벌 2025 - STANDING ONLY', '국내 최고 힙합 아티스트들의 스탠딩 페스티벌',
 '올림픽공원 88잔디마당', '서울특별시 송파구 올림픽로 424',
 CURRENT_DATE + INTERVAL '22 days' + TIME '17:00',
 CURRENT_DATE - INTERVAL '3 days' + TIME '10:00',
 CURRENT_DATE + INTERVAL '21 days' + TIME '23:59',
 'on_sale', NULL, NULL),

('인디 밴드 나이트 - STANDING', '인디 밴드들의 자유로운 스탠딩 공연',
 'YES24 라이브홀', '서울특별시 광진구 광나루로 56길 85',
 CURRENT_DATE + INTERVAL '27 days' + TIME '19:00',
 CURRENT_DATE + INTERVAL '2 days' + TIME '10:00',
 CURRENT_DATE + INTERVAL '26 days' + TIME '23:59',
 'upcoming', NULL, NULL);

-- Ticket types for standing events
INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description) VALUES
((SELECT id FROM events WHERE title = 'ZICO STANDING NIGHT 2025' ORDER BY id LIMIT 1),
 '스탠딩 일반', 88000, 500, 500, '스탠딩 전 구역 자유 입장'),
((SELECT id FROM events WHERE title = 'ZICO STANDING NIGHT 2025' ORDER BY id LIMIT 1),
 '스탠딩 얼리버드', 69000, 100, 100, '얼리버드 할인 티켓 (조기 마감)');

INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description) VALUES
((SELECT id FROM events WHERE title = '힙합 페스티벌 2025 - STANDING ONLY' ORDER BY id LIMIT 1),
 '1일권', 99000, 3000, 3000, '페스티벌 1일 자유 입장'),
((SELECT id FROM events WHERE title = '힙합 페스티벌 2025 - STANDING ONLY' ORDER BY id LIMIT 1),
 'VIP 1일권', 149000, 500, 500, 'VIP 전용 구역 + 굿즈 포함');

INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description) VALUES
((SELECT id FROM events WHERE title = '인디 밴드 나이트 - STANDING' ORDER BY id LIMIT 1),
 '스탠딩', 45000, 300, 300, '스탠딩 전 구역 자유 입장');
