-- V140: tách nhóm xác nhận/phát hành kết quả khỏi imaging_studies sang imaging_study_releases (1 - 0..1).
-- Giữ acknowledged_at ở imaging_studies (dùng trong ck_imaging_studies_linked).
-- Dòng nguồn: mọi dòng có ít nhất một cột nhóm này khác giá trị mặc định (không chỉ dòng có event id) để không mất last_safe_error_code/attempt count.
-- Khác biệt có chủ ý: ck_imaging_studies_findings_released (findings_released_at chỉ khi state = LINKED) liên quan 2 bảng,
-- không còn kiểm bằng CHECK; code phát hành chỉ ghi khi study LINKED.

CREATE TABLE imaging_study_releases (
    imaging_study_id             uuid         PRIMARY KEY REFERENCES imaging_studies (id) ON DELETE CASCADE,
    confirmation_event_id        uuid,
    confirmation_attempt_count   integer      NOT NULL DEFAULT 0,
    next_confirmation_attempt_at timestamptz,
    last_safe_error_code         varchar(64),
    release_event_id             uuid,
    release_payload_sha256       char(64),
    released_findings_id         uuid,
    released_report_id           uuid,
    released_report_sha256       char(64),
    findings_released_at         timestamptz,
    updated_at                   timestamptz  NOT NULL,
    CONSTRAINT ck_imaging_releases_confirmation_attempts CHECK (confirmation_attempt_count >= 0 AND confirmation_attempt_count <= 8),
    CONSTRAINT ck_imaging_releases_sha256 CHECK (
        (release_payload_sha256 IS NULL OR release_payload_sha256 ~ '^[0-9a-f]{64}$')
        AND (released_report_sha256 IS NULL OR released_report_sha256 ~ '^[0-9a-f]{64}$')),
    CONSTRAINT uk_imaging_releases_confirmation_event UNIQUE (confirmation_event_id),
    CONSTRAINT uk_imaging_releases_release_event UNIQUE (release_event_id)
);

INSERT INTO imaging_study_releases (imaging_study_id, confirmation_event_id, confirmation_attempt_count,
    next_confirmation_attempt_at, last_safe_error_code, release_event_id, release_payload_sha256, released_findings_id,
    released_report_id, released_report_sha256, findings_released_at, updated_at)
SELECT id, confirmation_event_id, confirmation_attempt_count, next_confirmation_attempt_at, last_safe_error_code,
       release_event_id, release_payload_sha256, released_findings_id, released_report_id, released_report_sha256,
       findings_released_at, updated_at
FROM imaging_studies
WHERE confirmation_event_id IS NOT NULL OR confirmation_attempt_count <> 0 OR next_confirmation_attempt_at IS NOT NULL
   OR last_safe_error_code IS NOT NULL OR release_event_id IS NOT NULL OR release_payload_sha256 IS NOT NULL
   OR released_findings_id IS NOT NULL OR released_report_id IS NOT NULL OR released_report_sha256 IS NOT NULL
   OR findings_released_at IS NOT NULL;

DO $$
DECLARE expected bigint; actual bigint;
BEGIN
    SELECT count(*) INTO expected FROM imaging_studies
    WHERE confirmation_event_id IS NOT NULL OR confirmation_attempt_count <> 0 OR next_confirmation_attempt_at IS NOT NULL
       OR last_safe_error_code IS NOT NULL OR release_event_id IS NOT NULL OR release_payload_sha256 IS NOT NULL
       OR released_findings_id IS NOT NULL OR released_report_id IS NOT NULL OR released_report_sha256 IS NOT NULL
       OR findings_released_at IS NOT NULL;
    SELECT count(*) INTO actual FROM imaging_study_releases;
    IF expected <> actual THEN RAISE EXCEPTION 'V140 copy check: % release rows expected, % copied', expected, actual; END IF;
END $$;

-- Job thử xác nhận lại: bảng mới chỉ có các dòng có lịch; lọc state = CONFIRMATION_PENDING bằng JOIN imaging_studies.
CREATE INDEX ix_imaging_releases_confirm_due ON imaging_study_releases (next_confirmation_attempt_at)
    WHERE next_confirmation_attempt_at IS NOT NULL;
-- state nằm ở imaging_studies: index này cho planner bắt đầu từ các study đang CONFIRMATION_PENDING (như index cũ có điều kiện state).
CREATE INDEX ix_imaging_studies_confirmation_pending ON imaging_studies (id) WHERE state = 'CONFIRMATION_PENDING';

-- Gỡ constraint/index cũ rồi drop cột.
DROP INDEX ix_imaging_studies_confirm_due;
ALTER TABLE imaging_studies
    DROP CONSTRAINT uk_imaging_studies_confirmation_event,
    DROP CONSTRAINT uk_imaging_studies_release_event,
    DROP CONSTRAINT ck_imaging_studies_confirmation_attempts,
    DROP CONSTRAINT ck_imaging_studies_findings_released,
    DROP CONSTRAINT ck_imaging_studies_sha256;
ALTER TABLE imaging_studies ADD CONSTRAINT ck_imaging_studies_sha256 CHECK (payload_sha256 ~ '^[0-9a-f]{64}$');
ALTER TABLE imaging_studies
    DROP COLUMN confirmation_event_id,
    DROP COLUMN confirmation_attempt_count,
    DROP COLUMN next_confirmation_attempt_at,
    DROP COLUMN last_safe_error_code,
    DROP COLUMN release_event_id,
    DROP COLUMN release_payload_sha256,
    DROP COLUMN released_findings_id,
    DROP COLUMN released_report_id,
    DROP COLUMN released_report_sha256,
    DROP COLUMN findings_released_at;
