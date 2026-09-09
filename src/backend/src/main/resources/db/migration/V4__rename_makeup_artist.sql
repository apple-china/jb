ALTER TABLE makeup_teacher RENAME TO makeup_artist;
ALTER TABLE makeup_artist RENAME CONSTRAINT ck_teacher_work_time TO ck_makeup_artist_work_time;
ALTER TABLE makeup_artist RENAME CONSTRAINT ck_teacher_work_step TO ck_makeup_artist_work_step;

ALTER TABLE app_user RENAME COLUMN teacher_id TO makeup_artist_id;
ALTER TABLE app_user RENAME COLUMN last_teacher_id TO last_makeup_artist_id;
ALTER TABLE app_user RENAME COLUMN display_name TO nickname;
ALTER TABLE app_user ADD COLUMN dingtalk_username varchar(100);
UPDATE app_user SET dingtalk_username=nickname WHERE dingtalk_user_id IS NOT NULL;
ALTER TABLE app_user RENAME CONSTRAINT ck_makeup_teacher_link TO ck_makeup_artist_link;
ALTER TABLE app_user DROP CONSTRAINT ck_makeup_artist_link;
ALTER TABLE app_user ADD CONSTRAINT ck_makeup_artist_link CHECK (
  (role='MAKEUP' AND makeup_artist_id IS NOT NULL) OR role<>'MAKEUP'
);

ALTER TABLE appointment RENAME COLUMN teacher_id TO makeup_artist_id;
ALTER TABLE appointment RENAME COLUMN teacher_name_snapshot TO makeup_artist_name_snapshot;
ALTER INDEX ix_active_appointment_teacher_time RENAME TO ix_active_appointment_makeup_artist_time;

CREATE OR REPLACE FUNCTION protect_dingtalk_identity() RETURNS trigger AS $$
BEGIN
  IF OLD.dingtalk_user_id IS DISTINCT FROM NEW.dingtalk_user_id
     OR OLD.dingtalk_username IS DISTINCT FROM NEW.dingtalk_username THEN
    RAISE EXCEPTION 'DingTalk identity is immutable';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_app_user_dingtalk_identity_immutable
BEFORE UPDATE OF dingtalk_user_id,dingtalk_username ON app_user
FOR EACH ROW EXECUTE FUNCTION protect_dingtalk_identity();
