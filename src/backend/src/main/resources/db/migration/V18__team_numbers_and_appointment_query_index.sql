CREATE SEQUENCE team_no_seq
  AS integer
  START WITH 100001
  INCREMENT BY 1
  MINVALUE 100001
  MAXVALUE 999999
  NO CYCLE;

ALTER TABLE team ADD COLUMN team_no integer;
ALTER TABLE team ALTER COLUMN team_no SET DEFAULT nextval('team_no_seq');
ALTER SEQUENCE team_no_seq OWNED BY team.team_no;

ALTER TABLE team
  ADD CONSTRAINT ck_team_no_six_digits
  CHECK (team_no IS NULL OR team_no BETWEEN 100001 AND 999999);

CREATE UNIQUE INDEX uq_team_no ON team(team_no) WHERE team_no IS NOT NULL;
CREATE INDEX ix_appointment_booking_date_created
  ON appointment(booking_date,created_at,id);
