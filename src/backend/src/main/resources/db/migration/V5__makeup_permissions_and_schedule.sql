ALTER TABLE app_user
  ADD COLUMN can_create_appointments boolean NOT NULL DEFAULT false;

UPDATE app_user SET can_create_appointments=true WHERE role='MAKEUP';

ALTER TABLE makeup_artist
  ADD COLUMN schedule_enabled boolean NOT NULL DEFAULT true;
