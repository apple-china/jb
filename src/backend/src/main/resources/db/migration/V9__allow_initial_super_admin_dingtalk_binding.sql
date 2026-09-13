CREATE OR REPLACE FUNCTION protect_dingtalk_identity() RETURNS trigger AS $$
DECLARE
  directory_name varchar(100);
BEGIN
  -- The bootstrap super administrator starts without a DingTalk identity. Allow
  -- exactly one binding, then keep the same immutable rule as every other account.
  IF OLD.role = 'SUPER_ADMIN'
     AND OLD.dingtalk_user_id IS NULL
     AND NEW.dingtalk_user_id IS NOT NULL THEN
    SELECT name
      INTO directory_name
      FROM dingtalk_employee
     WHERE user_id = NEW.dingtalk_user_id
       AND is_active = true;

    IF directory_name IS NULL THEN
      RAISE EXCEPTION 'DingTalk employee must be active in the local directory';
    END IF;

    -- Use the synchronized directory as the canonical name source so a manual
    -- binding only needs to set dingtalk_user_id.
    NEW.dingtalk_username := directory_name;
    RETURN NEW;
  END IF;

  IF OLD.dingtalk_user_id IS DISTINCT FROM NEW.dingtalk_user_id
     OR OLD.dingtalk_username IS DISTINCT FROM NEW.dingtalk_username THEN
    RAISE EXCEPTION 'DingTalk identity is immutable';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;