ALTER TABLE late_notification
  ADD COLUMN title_index smallint,
  ADD COLUMN message_index smallint,
  ADD COLUMN message_text varchar(600);

ALTER TABLE late_notification
  ADD CONSTRAINT ck_late_notification_title_index
    CHECK (title_index IS NULL OR title_index BETWEEN 0 AND 6),
  ADD CONSTRAINT ck_late_notification_message_index
    CHECK (message_index IS NULL OR message_index BETWEEN 0 AND 9);