CREATE TABLE clinical_imaging_request_replays (
    id UUID PRIMARY KEY,
    doctor_profile_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    ordering_round_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_clinical_imaging_replay_doctor_key UNIQUE (doctor_profile_id, idempotency_key),
    CONSTRAINT uk_clinical_imaging_replay_round UNIQUE (ordering_round_id),
    CONSTRAINT fk_clinical_imaging_replay_round FOREIGN KEY (ordering_round_id)
        REFERENCES clinical_ordering_rounds(id) ON DELETE RESTRICT,
    CONSTRAINT ck_clinical_imaging_replay_key CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT ck_clinical_imaging_replay_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);
