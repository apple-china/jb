-- Recover late reminders that were discarded by the former projection type error.
-- Only appointments that are still active, late, and without attendance evidence are retried.
UPDATE late_notification n
SET status = 'PENDING',
    attempt_count = 0,
    last_error_code = NULL,
    updated_at = now()
WHERE n.status = 'FAILED'
  AND EXISTS (
    SELECT 1
    FROM appointment a
    JOIN integration_job j
      ON j.job_type = 'LATE_REMINDER' AND j.business_key = a.id::text
    WHERE a.id = n.appointment_id
      AND a.status = 'ACTIVE'
      AND a.attendance_status = 'LATE'
      AND a.attendance_event_id IS NULL
      AND j.status = 'DEAD'
      AND j.last_error_code = 'INTERNAL'
  );

UPDATE integration_job j
SET status = 'PENDING', attempt_count = 0, next_attempt_at = now(),
    locked_at = NULL, locked_by = NULL, last_error_code = NULL,
    last_error_message = NULL, updated_at = now()
WHERE j.job_type = 'LATE_REMINDER'
  AND j.status = 'DEAD'
  AND j.last_error_code = 'INTERNAL'
  AND EXISTS (
    SELECT 1
    FROM appointment a
    WHERE a.id::text = j.business_key
      AND a.status = 'ACTIVE'
