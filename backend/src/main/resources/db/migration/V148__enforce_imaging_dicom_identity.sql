-- V128 is already applied in some environments. Keep its checksum stable and tighten the rule here.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM clinical_service_orders
        WHERE service_type = 'IMAGING'
          AND (dicom_accession_number IS NULL OR btrim(dicom_accession_number) = ''
               OR dicom_accession_local_namespace IS NULL OR btrim(dicom_accession_local_namespace) = '')
    ) THEN
        RAISE EXCEPTION 'V129: an imaging order is missing its DICOM accession identity';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM clinical_service_orders
        WHERE service_type <> 'IMAGING'
          AND (dicom_accession_number IS NOT NULL OR dicom_accession_local_namespace IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'V129: a non-imaging order has DICOM accession identity';
    END IF;
END
$$;

ALTER TABLE clinical_service_orders
    DROP CONSTRAINT ck_clinical_orders_dicom_accession_pair,
    ADD CONSTRAINT ck_clinical_orders_dicom_accession_pair CHECK (
        (service_type = 'IMAGING'
            AND dicom_accession_number IS NOT NULL
            AND dicom_accession_local_namespace IS NOT NULL
            AND length(btrim(dicom_accession_number)) BETWEEN 1 AND 16
            AND length(btrim(dicom_accession_local_namespace)) BETWEEN 1 AND 64)
        OR (service_type <> 'IMAGING'
            AND dicom_accession_number IS NULL
            AND dicom_accession_local_namespace IS NULL)
    );

DROP TRIGGER tr_clinical_orders_dicom_accession ON clinical_service_orders;

CREATE OR REPLACE FUNCTION set_clinical_order_dicom_accession()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (
        NEW.dicom_accession_number IS DISTINCT FROM OLD.dicom_accession_number
        OR NEW.dicom_accession_local_namespace IS DISTINCT FROM OLD.dicom_accession_local_namespace
    ) THEN
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
    BEFORE INSERT OR UPDATE ON clinical_service_orders
    FOR EACH ROW EXECUTE FUNCTION set_clinical_order_dicom_accession();
