WITH ranked AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY attendance_event_id
           ORDER BY CASE WHEN start_at <= attendance_evidence_at THEN 0 ELSE 1 END,
                    abs(extract(epoch FROM (attendance_evidence_at - start_at))), start_at, id
         ) AS position
  FROM appointment
  WHERE status = 'ACTIVE' AND attendance_event_id IS NOT NULL
)
UPDATE appointment a
SET attendance_event_id = NULL,
    attendance_evidence_at = NULL,
    attendance_status = CASE
      WHEN now() < a.start_at THEN 'PENDING'
      WHEN now() < a.start_at + interval '10 minutes' THEN 'NOT_ARRIVED'
      ELSE 'LATE'
    END,
    updated_at = now()
FROM ranked r
WHERE a.id = r.id AND r.position > 1;

CREATE UNIQUE INDEX uq_active_appointment_attendance_event
  ON appointment (attendance_event_id)
  WHERE status = 'ACTIVE' AND attendance_event_id IS NOT NULL;
