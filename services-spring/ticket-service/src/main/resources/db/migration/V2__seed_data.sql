-- Seed data for URR ticketing platform
-- Adapted from original project-ticketing init.sql

-- Insert seat layouts
INSERT INTO seat_layouts (name, description, total_seats, layout_config) VALUES
('small_theater', '소극장 (265석)', 265,
 '{"sections": [{"name": "VIP", "rows": 3, "seatsPerRow": 10, "price": 150000, "startRow": 1}, {"name": "R", "rows": 5, "seatsPerRow": 15, "price": 100000, "startRow": 4}, {"name": "S", "rows": 8, "seatsPerRow": 20, "price": 70000, "startRow": 9}]}'::jsonb),
('large_theater', '대극장 (1250석)', 1250,
 '{"sections": [{"name": "VIP", "rows": 5, "seatsPerRow": 20, "price": 200000, "startRow": 1}, {"name": "R", "rows": 10, "seatsPerRow": 30, "price": 150000, "startRow": 6}, {"name": "S", "rows": 15, "seatsPerRow": 30, "price": 100000, "startRow": 16}, {"name": "A", "rows": 10, "seatsPerRow": 40, "price": 70000, "startRow": 31}]}'::jsonb),
('sports_stadium', '체육관/스타디움 (4900석)', 4900,
 '{"sections": [{"name": "Floor1", "rows": 20, "seatsPerRow": 50, "price": 80000, "startRow": 1}, {"name": "Floor2", "rows": 30, "seatsPerRow": 60, "price": 50000, "startRow": 21}, {"name": "Floor3", "rows": 30, "seatsPerRow": 70, "price": 30000, "startRow": 51}]}'::jsonb);

-- Insert 3 main events with seat layouts
INSERT INTO events (title, description, venue, address, event_date, sale_start_date, sale_end_date, poster_image_url, status, seat_layout_id) VALUES
('2025 콘서트 투어 in 서울', '2025년 최고의 콘서트! 놓치지 마세요.', '올림픽공원 체조경기장', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '15 days' + TIME '19:00', CURRENT_DATE - INTERVAL '5 days' + TIME '10:00', CURRENT_DATE + INTERVAL '14 days' + TIME '23:59', NULL, 'on_sale', (SELECT id FROM seat_layouts WHERE name = 'small_theater')),
('뮤지컬 오페라의 유령', '세계적인 뮤지컬 오페라의 유령', '샤롯데씨어터', '서울특별시 송파구 올림픽로 240', CURRENT_DATE + INTERVAL '30 days' + TIME '19:30', CURRENT_DATE - INTERVAL '3 days' + TIME '10:00', CURRENT_DATE + INTERVAL '29 days' + TIME '23:59', NULL, 'on_sale', (SELECT id FROM seat_layouts WHERE name = 'large_theater')),
('스포츠 경기 - 농구 결승전', '2024-2025 시즌 농구 결승전', '잠실실내체육관', '서울특별시 송파구 올림픽로 25', CURRENT_DATE + INTERVAL '10 days' + TIME '18:00', CURRENT_DATE - INTERVAL '7 days' + TIME '10:00', CURRENT_DATE + INTERVAL '9 days' + TIME '23:59', NULL, 'on_sale', (SELECT id FROM seat_layouts WHERE name = 'sports_stadium'));

-- Insert ticket types for first 3 events
INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description) VALUES
((SELECT id FROM events WHERE title = '2025 콘서트 투어 in 서울'), 'VIP석', 150000, 100, 95, '최고의 시야와 사운드를 즐기실 수 있습니다'),
((SELECT id FROM events WHERE title = '2025 콘서트 투어 in 서울'), 'R석', 100000, 200, 180, '무대를 가까이서 볼 수 있는 좌석'),
((SELECT id FROM events WHERE title = '2025 콘서트 투어 in 서울'), 'S석', 70000, 300, 250, '합리적인 가격의 좌석'),
((SELECT id FROM events WHERE title = '2025 콘서트 투어 in 서울'), '일반석', 50000, 400, 320, '스탠딩 또는 자유석');

INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description) VALUES
((SELECT id FROM events WHERE title = '뮤지컬 오페라의 유령'), 'VIP석', 180000, 80, 75, 'VIP 라운지 이용 가능'),
((SELECT id FROM events WHERE title = '뮤지컬 오페라의 유령'), 'R석', 120000, 150, 140, '최상의 관람석'),
((SELECT id FROM events WHERE title = '뮤지컬 오페라의 유령'), 'S석', 80000, 200, 185, '일반 관람석');

INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description) VALUES
((SELECT id FROM events WHERE title = '스포츠 경기 - 농구 결승전'), '코트사이드', 200000, 50, 48, '선수들을 가장 가까이서'),
((SELECT id FROM events WHERE title = '스포츠 경기 - 농구 결승전'), '1층석', 80000, 300, 280, '1층 일반석'),
((SELECT id FROM events WHERE title = '스포츠 경기 - 농구 결승전'), '2층석', 50000, 500, 450, '2층 일반석');

-- Insert 22 K-POP concert events (all on_sale or upcoming with CURRENT_DATE-based dates)
INSERT INTO events (title, description, venue, address, event_date, sale_start_date, sale_end_date, status, seat_layout_id, artist_name) VALUES
('10CM HOTEL ROOM TOUR', '10CM의 감성 어쿠스틱 공연', '블루스퀘어 마스터카드홀', '서울특별시 용산구 이태원로 294', CURRENT_DATE + INTERVAL '20 days' + TIME '19:00', CURRENT_DATE - INTERVAL '2 days' + TIME '09:00', CURRENT_DATE + INTERVAL '19 days' + TIME '18:00', 'on_sale', (SELECT id FROM seat_layouts WHERE name = 'small_theater'), '10CM'),
('싸이 흠뻑쇼 2025', '싸이의 신나는 여름 콘서트', '잠실종합운동장 주경기장', '서울특별시 송파구 올림픽로 25', CURRENT_DATE + INTERVAL '25 days' + TIME '19:00', CURRENT_DATE + INTERVAL '3 days' + TIME '09:00', CURRENT_DATE + INTERVAL '24 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'sports_stadium'), '싸이'),
('아이유 콘서트 - The Golden Hour', '아이유의 감성 가득한 콘서트', '고척스카이돔', '서울특별시 구로구 경인로 430', CURRENT_DATE + INTERVAL '30 days' + TIME '19:00', CURRENT_DATE + INTERVAL '5 days' + TIME '09:00', CURRENT_DATE + INTERVAL '29 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), '아이유'),
('BTS WORLD TOUR 2025', 'BTS의 화려한 월드투어 서울 공연', '잠실종합운동장 주경기장', '서울특별시 송파구 올림픽로 25', CURRENT_DATE + INTERVAL '35 days' + TIME '19:00', CURRENT_DATE + INTERVAL '7 days' + TIME '09:00', CURRENT_DATE + INTERVAL '34 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'sports_stadium'), 'BTS'),
('BLACKPINK BORN PINK TOUR', 'BLACKPINK의 강렬한 무대', '고척스카이돔', '서울특별시 구로구 경인로 430', CURRENT_DATE + INTERVAL '40 days' + TIME '19:00', CURRENT_DATE + INTERVAL '10 days' + TIME '09:00', CURRENT_DATE + INTERVAL '39 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'BLACKPINK'),
('임영웅 - IM HERO TOUR', '임영웅의 감동 콘서트', '잠실실내체육관', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '42 days' + TIME '19:00', CURRENT_DATE + INTERVAL '12 days' + TIME '09:00', CURRENT_DATE + INTERVAL '41 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), '임영웅'),
('NewJeans SUPER SHY TOUR', '뉴진스의 청량한 무대', '올림픽공원 체조경기장', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '45 days' + TIME '19:00', CURRENT_DATE + INTERVAL '14 days' + TIME '09:00', CURRENT_DATE + INTERVAL '44 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'small_theater'), 'NewJeans'),
('SEVENTEEN FOLLOW TOUR', '세븐틴의 완벽한 퍼포먼스', '고척스카이돔', '서울특별시 구로구 경인로 430', CURRENT_DATE + INTERVAL '48 days' + TIME '19:00', CURRENT_DATE + INTERVAL '16 days' + TIME '09:00', CURRENT_DATE + INTERVAL '47 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'SEVENTEEN'),
('aespa SYNK TOUR', '에스파의 미래형 콘서트', 'KSPO DOME', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '50 days' + TIME '19:00', CURRENT_DATE + INTERVAL '18 days' + TIME '09:00', CURRENT_DATE + INTERVAL '49 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'aespa'),
('NCT DREAM THE DREAM SHOW 3', 'NCT DREAM의 환상적인 무대', '고척스카이돔', '서울특별시 구로구 경인로 430', CURRENT_DATE + INTERVAL '52 days' + TIME '19:00', CURRENT_DATE + INTERVAL '20 days' + TIME '09:00', CURRENT_DATE + INTERVAL '51 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'NCT DREAM'),
('LE SSERAFIM FLAME RISES', '르세라핌의 카리스마 넘치는 공연', '올림픽공원 체조경기장', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '55 days' + TIME '19:00', CURRENT_DATE + INTERVAL '22 days' + TIME '09:00', CURRENT_DATE + INTERVAL '54 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'small_theater'), 'LE SSERAFIM'),
('IVE THE PROM QUEENS TOUR', '아이브의 화려한 무대', 'KSPO DOME', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '58 days' + TIME '19:00', CURRENT_DATE + INTERVAL '24 days' + TIME '09:00', CURRENT_DATE + INTERVAL '57 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'IVE'),
('Stray Kids 5-STAR TOUR', '스트레이 키즈의 폭발적인 에너지', '고척스카이돔', '서울특별시 구로구 경인로 430', CURRENT_DATE + INTERVAL '60 days' + TIME '19:00', CURRENT_DATE + INTERVAL '26 days' + TIME '09:00', CURRENT_DATE + INTERVAL '59 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'Stray Kids'),
('TWICE READY TO BE TOUR', '트와이스의 감성 공연', '잠실실내체육관', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '62 days' + TIME '19:00', CURRENT_DATE + INTERVAL '28 days' + TIME '09:00', CURRENT_DATE + INTERVAL '61 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'TWICE'),
('태양 WHITE NIGHT TOUR', '태양의 감성 가득한 라이브', '올림픽공원 체조경기장', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '65 days' + TIME '19:00', CURRENT_DATE + INTERVAL '30 days' + TIME '09:00', CURRENT_DATE + INTERVAL '64 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'small_theater'), '태양'),
('G-DRAGON ACT III: MOTTE', 'GD의 독보적인 무대', '고척스카이돔', '서울특별시 구로구 경인로 430', CURRENT_DATE + INTERVAL '68 days' + TIME '19:00', CURRENT_DATE + INTERVAL '32 days' + TIME '09:00', CURRENT_DATE + INTERVAL '67 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'G-DRAGON'),
('EXO PLANET 2025', '엑소의 완벽한 퍼포먼스', 'KSPO DOME', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '70 days' + TIME '19:00', CURRENT_DATE + INTERVAL '34 days' + TIME '09:00', CURRENT_DATE + INTERVAL '69 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'EXO'),
('Red Velvet FEEL MY RHYTHM', '레드벨벳의 매력적인 무대', '올림픽공원 체조경기장', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '72 days' + TIME '19:00', CURRENT_DATE + INTERVAL '36 days' + TIME '09:00', CURRENT_DATE + INTERVAL '71 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'small_theater'), 'Red Velvet'),
('TXT ACT: SWEET MIRAGE', '투모로우바이투게더의 청춘 콘서트', '잠실실내체육관', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '75 days' + TIME '19:00', CURRENT_DATE + INTERVAL '38 days' + TIME '09:00', CURRENT_DATE + INTERVAL '74 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'TOMORROW X TOGETHER'),
('ENHYPEN FATE TOUR', '엔하이픈의 강렬한 퍼포먼스', '고척스카이돔', '서울특별시 구로구 경인로 430', CURRENT_DATE + INTERVAL '78 days' + TIME '19:00', CURRENT_DATE + INTERVAL '40 days' + TIME '09:00', CURRENT_DATE + INTERVAL '77 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'large_theater'), 'ENHYPEN'),
('ITZY CHECKMATE TOUR', '있지의 파워풀한 무대', '올림픽공원 체조경기장', '서울특별시 송파구 올림픽로 424', CURRENT_DATE + INTERVAL '80 days' + TIME '19:00', CURRENT_DATE + INTERVAL '42 days' + TIME '09:00', CURRENT_DATE + INTERVAL '79 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'small_theater'), 'ITZY'),
('ZICO KING OF THE ZUNGLE', '지코의 힙합 라이브', 'YES24 라이브홀', '서울특별시 광진구 광나루로 56길 85', CURRENT_DATE + INTERVAL '82 days' + TIME '19:00', CURRENT_DATE + INTERVAL '44 days' + TIME '09:00', CURRENT_DATE + INTERVAL '81 days' + TIME '18:00', 'upcoming', (SELECT id FROM seat_layouts WHERE name = 'small_theater'), 'ZICO');

-- Insert keyword mappings for cross-language search
INSERT INTO keyword_mappings (korean, english, entity_type) VALUES
('싸이', 'PSY', 'artist'),
('아이유', 'IU', 'artist'),
('방탄소년단', 'BTS', 'artist'),
('블랙핑크', 'BLACKPINK', 'artist'),
('임영웅', 'Lim Young Woong', 'artist'),
('뉴진스', 'NewJeans', 'artist'),
('세븐틴', 'SEVENTEEN', 'artist'),
('에스파', 'aespa', 'artist'),
('엔시티 드림', 'NCT DREAM', 'artist'),
('르세라핌', 'LE SSERAFIM', 'artist'),
('아이브', 'IVE', 'artist'),
('스트레이 키즈', 'Stray Kids', 'artist'),
('트와이스', 'TWICE', 'artist'),
('태양', 'TAEYANG', 'artist'),
('태양', 'SOL', 'artist'),
('지드래곤', 'G-DRAGON', 'artist'),
('지드래곤', 'GD', 'artist'),
('엑소', 'EXO', 'artist'),
('레드벨벳', 'Red Velvet', 'artist'),
('투모로우바이투게더', 'TOMORROW X TOGETHER', 'artist'),
('투바투', 'TXT', 'artist'),
('엔하이픈', 'ENHYPEN', 'artist'),
('있지', 'ITZY', 'artist'),
('지코', 'ZICO', 'artist'),
('서울', 'Seoul', 'venue'),
('올림픽', 'Olympic', 'venue'),
('잠실', 'Jamsil', 'venue'),
('고척', 'Gocheok', 'venue'),
('송파', 'Songpa', 'venue'),
('구로', 'Guro', 'venue'),
('용산', 'Yongsan', 'venue'),
('광진', 'Gwangjin', 'venue'),
('체조경기장', 'Gymnastics Arena', 'venue'),
('경기장', 'Stadium', 'venue'),
('돔', 'Dome', 'venue'),
('스카이돔', 'Sky Dome', 'venue'),
('실내체육관', 'Indoor Stadium', 'venue'),
('콘서트', 'Concert', 'general'),
('투어', 'Tour', 'general'),
('공연', 'Performance', 'general'),
('뮤지컬', 'Musical', 'general');

-- Insert news articles (URR branding)
INSERT INTO news (title, content, author, views) VALUES
('URR 서비스 정식 오픈!', '안녕하세요, 우르르입니다.

드디어 URR 서비스가 정식으로 오픈하게 되었습니다!

우르르는 가장 빠르고 안전한 티켓팅 서비스를 제공하기 위해 만들어졌습니다.
실시간 좌석 선택, 공정한 대기열 시스템, 그리고 간편한 결제까지 모든 것을 한 곳에서 경험하실 수 있습니다.

앞으로 더 나은 서비스로 보답하겠습니다.
많은 이용 부탁드립니다!

감사합니다.', '관리자', 125),
('2025년 상반기 콘서트 티켓 오픈 안내', '2025년 상반기 콘서트 티켓팅 일정을 안내드립니다.

다양한 아티스트들의 콘서트가 예정되어 있습니다.
주요 공연 일정:

- 아이유 콘서트 - The Golden Hour
- BTS WORLD TOUR 2025
- BLACKPINK BORN PINK TOUR
- NewJeans SUPER SHY TOUR

모든 티켓은 선착순이며, 공정한 대기열 시스템을 통해 진행됩니다.
티켓 오픈 10분 전부터 대기가 가능하니 참고해주세요.

즐거운 공연 관람 되세요!', '관리자', 89),
('URR 모바일 앱 출시 예정', '우르르 모바일 앱이 곧 출시됩니다!

더욱 편리한 티켓팅을 위한 모바일 앱을 준비 중입니다.
iOS와 Android 모두 지원 예정입니다.

모바일 앱에서는 다음과 같은 기능을 제공할 예정입니다:
- 푸시 알림으로 티켓 오픈 알림 받기
- 더 빠른 결제 프로세스
- 모바일 티켓 QR코드
- 나의 예매 내역 한눈에 보기

많은 기대 부탁드립니다!', '관리자', 45);
