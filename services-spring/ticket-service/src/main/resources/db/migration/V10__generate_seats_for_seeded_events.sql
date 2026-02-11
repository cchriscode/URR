-- V10: Generate seats for all events that have seat_layout_id but no seats yet.
-- Replicates SeatGeneratorService.generateSeatsForEvent() logic in pure SQL so that
-- seed events are playable immediately after a fresh cluster creation.
-- Idempotent: only runs for events that currently have 0 seats.

DO $$
DECLARE
  ev          RECORD;
  section     JSONB;
  sec_name    TEXT;
  rows        INT;
  seats_per   INT;
  price       INT;
  start_row   INT;
  row_num     INT;
  seat_num    INT;
  seat_label  TEXT;
BEGIN
  FOR ev IN
    SELECT e.id AS event_id, sl.layout_config
    FROM   events e
    JOIN   seat_layouts sl ON sl.id = e.seat_layout_id
    WHERE  NOT EXISTS (SELECT 1 FROM seats s WHERE s.event_id = e.id)
  LOOP
    FOR section IN
      SELECT jsonb_array_elements(ev.layout_config->'sections')
    LOOP
      sec_name  := section->>'name';
      rows      := (section->>'rows')::INT;
      seats_per := (section->>'seatsPerRow')::INT;
      price     := (section->>'price')::INT;
      start_row := COALESCE((section->>'startRow')::INT, 1);

      FOR row_num IN start_row..(start_row + rows - 1) LOOP
        FOR seat_num IN 1..seats_per LOOP
          seat_label := sec_name || '-' || row_num || '-' || seat_num;
          INSERT INTO seats
            (event_id, section, row_number, seat_number, seat_label, price, status)
          VALUES
            (ev.event_id, sec_name, row_num, seat_num, seat_label, price, 'available');
        END LOOP;
      END LOOP;
    END LOOP;
  END LOOP;
END;
$$;
