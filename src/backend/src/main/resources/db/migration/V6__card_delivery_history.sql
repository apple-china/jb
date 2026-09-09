ALTER TABLE daily_card ADD COLUMN IF NOT EXISTS first_delivered_at timestamptz;

UPDATE daily_card
SET first_delivered_at = updated_at
WHERE delivered_version > 0;
