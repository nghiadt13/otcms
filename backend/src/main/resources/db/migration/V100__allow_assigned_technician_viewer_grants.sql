ALTER TABLE imaging_viewer_grants
    DROP CONSTRAINT ck_imaging_grant_purpose;

ALTER TABLE imaging_viewer_grants
    ADD CONSTRAINT ck_imaging_grant_purpose
        CHECK (purpose IN ('DOCTOR_REVIEW', 'PATIENT_VIEW', 'TECHNICIAN_ASSIGNED'));
