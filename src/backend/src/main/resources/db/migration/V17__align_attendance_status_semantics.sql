UPDATE appointment
SET attendance_status = CASE
      WHEN attendance_evidence_at IS NOT NULL
        THEN CASE WHEN attendance_evidence_at <= start_at + interval '10 minutes' THEN 'ARRIVED' ELSE 'LATE' END
      WHEN now() < start_at + interval '10 minutes' THEN 'PENDING'
      ELSE 'NOT_ARRIVED'
    END,
    updated_at = now()
WHERE status = 'ACTIVE' AND NOT attendance_frozen;
