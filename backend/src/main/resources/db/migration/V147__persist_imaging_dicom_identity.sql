-- Store the DICOM-facing identity with its owning patient and imaging order.
-- Existing values are deterministic and non-PHI: patient profile UUIDs and generated accessions.

ALTER TABLE patient_profiles
    ADD COLUMN dicom_patient_id varchar(64),
    ADD COLUMN dicom_patient_issuer varchar(64);

UPDATE patient_profiles
SET dicom_patient_id = id::text,
    dicom_patient_issuer = 'OTCMS';

ALTER TABLE patient_profiles
    ALTER COLUMN dicom_patient_id SET NOT NULL,
    ALTER COLUMN dicom_patient_issuer SET NOT NULL,
    ADD CONSTRAINT ck_patient_profiles_dicom_identity CHECK (
        length(btrim(dicom_patient_id)) BETWEEN 1 AND 64
        AND length(btrim(dicom_patient_issuer)) BETWEEN 1 AND 64
    ),
    ADD CONSTRAINT uk_patient_profiles_dicom_identity UNIQUE (dicom_patient_issuer, dicom_patient_id);

CREATE OR REPLACE FUNCTION set_patient_dicom_identity_defaults()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.dicom_patient_id IS NULL OR btrim(NEW.dicom_patient_id) = '' THEN
        NEW.dicom_patient_id := NEW.id::text;
    END IF;
    IF NEW.dicom_patient_issuer IS NULL OR btrim(NEW.dicom_patient_issuer) = '' THEN
        NEW.dicom_patient_issuer := 'OTCMS';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_patient_profiles_dicom_identity_defaults
    BEFORE INSERT ON patient_profiles
    FOR EACH ROW EXECUTE FUNCTION set_patient_dicom_identity_defaults();

CREATE SEQUENCE clinical_service_order_dicom_accession_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

ALTER TABLE clinical_service_orders
    ADD COLUMN dicom_accession_number varchar(16),
    ADD COLUMN dicom_accession_local_namespace varchar(64);

UPDATE clinical_service_orders
SET dicom_accession_number = 'OT' || lpad(nextval('clinical_service_order_dicom_accession_seq')::text, 14, '0'),
    dicom_accession_local_namespace = 'OTCMS'
WHERE service_type = 'IMAGING';

ALTER TABLE clinical_service_orders
    ADD CONSTRAINT ck_clinical_orders_dicom_accession_pair CHECK (
        (dicom_accession_number IS NULL AND dicom_accession_local_namespace IS NULL)
        OR (length(btrim(dicom_accession_number)) BETWEEN 1 AND 16
            AND length(btrim(dicom_accession_local_namespace)) BETWEEN 1 AND 64)
    );

CREATE UNIQUE INDEX uk_clinical_orders_dicom_accession
    ON clinical_service_orders (dicom_accession_local_namespace, dicom_accession_number)
    WHERE dicom_accession_number IS NOT NULL;

CREATE OR REPLACE FUNCTION set_clinical_order_dicom_accession()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (
        NEW.dicom_accession_number IS DISTINCT FROM OLD.dicom_accession_number
        OR NEW.dicom_accession_local_namespace IS DISTINCT FROM OLD.dicom_accession_local_namespace
    ) AND OLD.dicom_accession_number IS NOT NULL THEN
        RAISE EXCEPTION 'DICOM accession identity is immutable once assigned';
    END IF;

    IF NEW.service_type = 'IMAGING' THEN
        IF NEW.dicom_accession_number IS NULL THEN
            NEW.dicom_accession_number := 'OT' || lpad(nextval('clinical_service_order_dicom_accession_seq')::text, 14, '0');
        END IF;
        IF NEW.dicom_accession_local_namespace IS NULL THEN
            NEW.dicom_accession_local_namespace := 'OTCMS';
        END IF;
    ELSE
        NEW.dicom_accession_number := NULL;
        NEW.dicom_accession_local_namespace := NULL;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_clinical_orders_dicom_accession
    BEFORE INSERT OR UPDATE OF service_type, dicom_accession_number, dicom_accession_local_namespace
    ON clinical_service_orders
    FOR EACH ROW EXECUTE FUNCTION set_clinical_order_dicom_accession();
