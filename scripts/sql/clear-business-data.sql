BEGIN;
DELETE FROM mock_card_call_log;
DELETE FROM mock_card_delivery;
DELETE FROM integration_job;
DELETE FROM late_notification;
DELETE FROM daily_card;
DELETE FROM idempotency_record;
DELETE FROM appointment_operation_counter;
DELETE FROM audit_log WHERE entity_type='APPOINTMENT';
DELETE FROM appointment;
DELETE FROM gate_event;
UPDATE app_user
SET last_makeup_artist_id=NULL,
    last_start_time=NULL,
    last_team_id=NULL,
    updated_at=now(),
    version=version+1
WHERE last_makeup_artist_id IS NOT NULL OR last_start_time IS NOT NULL OR last_team_id IS NOT NULL;
COMMIT;
