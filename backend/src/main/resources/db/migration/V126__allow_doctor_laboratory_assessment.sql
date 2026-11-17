-- A released laboratory report remains immutable except for the doctor's first assessment.
-- The assessment fields may transition together from NULL to populated exactly once.
CREATE OR REPLACE FUNCTION prevent_result_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.state = 'COMPLETED' OR OLD.released_at IS NOT NULL THEN
      RAISE EXCEPTION 'released or completed results are immutable';
    END IF;
    RETURN OLD;
  END IF;

  IF OLD.released_at IS NOT NULL THEN
    IF OLD.result_type = 'LAB'
       AND OLD.state = 'COMPLETED'
       AND OLD.lab_doctor_assessment IS NULL
       AND OLD.lab_doctor_assessed_by_account_id IS NULL
       AND OLD.lab_doctor_assessed_at IS NULL
       AND NEW.lab_doctor_assessment IS NOT NULL
       AND length(btrim(NEW.lab_doctor_assessment)) BETWEEN 1 AND 4000
       AND NEW.lab_doctor_assessed_by_account_id IS NOT NULL
       AND NEW.lab_doctor_assessed_at IS NOT NULL
       AND (to_jsonb(OLD) - 'lab_doctor_assessment' - 'lab_doctor_assessed_by_account_id' - 'lab_doctor_assessed_at')
           IS NOT DISTINCT FROM
           (to_jsonb(NEW) - 'lab_doctor_assessment' - 'lab_doctor_assessed_by_account_id' - 'lab_doctor_assessed_at') THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'released or completed results are immutable';
  END IF;

  IF OLD.state = 'COMPLETED'
     AND (to_jsonb(OLD) - 'released_at') IS DISTINCT FROM (to_jsonb(NEW) - 'released_at') THEN
    RAISE EXCEPTION 'completed diagnostic findings versions are immutable';
  END IF;
  RETURN NEW;
END $$;
