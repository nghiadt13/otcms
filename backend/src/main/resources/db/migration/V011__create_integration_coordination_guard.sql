CREATE TABLE integration_coordination_guard (
    guard_id SMALLINT PRIMARY KEY,
    CONSTRAINT ck_integration_coordination_guard_singleton CHECK (guard_id = 1)
);

INSERT INTO integration_coordination_guard (guard_id) VALUES (1);

CREATE FUNCTION prevent_integration_coordination_guard_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'integration coordination guard row is immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER tr_integration_coordination_guard_immutable
    BEFORE UPDATE OR DELETE ON integration_coordination_guard
    FOR EACH ROW EXECUTE FUNCTION prevent_integration_coordination_guard_mutation();

CREATE TRIGGER tr_integration_coordination_guard_no_truncate
    BEFORE TRUNCATE ON integration_coordination_guard
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_integration_coordination_guard_mutation();
