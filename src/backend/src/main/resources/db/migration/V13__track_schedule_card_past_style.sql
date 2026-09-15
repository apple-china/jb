ALTER TABLE appointment
  ADD COLUMN card_past_refreshed_at timestamptz;

CREATE INDEX ix_appointment_pending_past_card_refresh
  ON appointment (booking_date, start_at)
  WHERE status = 'ACTIVE' AND card_past_refreshed_at IS NULL;
