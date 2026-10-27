-- OTCMS full-domain schema proposal. Review artifact, NOT a Flyway migration.
-- PostgreSQL 15+; run only after V001 auth schema and approved data/rollout plan.
-- btree_gist is needed for UUID equality in GiST exclusion constraints.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Patient identity is independent of login: walk-ins and dependents need no account.
CREATE TABLE patients (
    id uuid PRIMARY KEY,
    account_id uuid UNIQUE REFERENCES accounts(id) ON DELETE RESTRICT,
    full_name text NOT NULL CHECK (length(btrim(full_name)) BETWEEN 2 AND 100),
    date_of_birth date NOT NULL CHECK (date_of_birth <= CURRENT_DATE),
    gender text NOT NULL CHECK (gender IN ('MALE','FEMALE','OTHER')),
    phone_canonical text,
    record_status text NOT NULL DEFAULT 'ACTIVE' CHECK (record_status IN ('ACTIVE','MERGED','ARCHIVED')),
    merged_into_patient_id uuid REFERENCES patients(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_patients_merge CHECK ((record_status = 'MERGED') = (merged_into_patient_id IS NOT NULL)),
    CONSTRAINT chk_patients_no_self_merge CHECK (merged_into_patient_id IS DISTINCT FROM id),
    CONSTRAINT chk_patients_phone CHECK (phone_canonical IS NULL OR phone_canonical ~ '^\+[1-9][0-9]{1,14}$')
);
CREATE INDEX idx_patients_phone ON patients(phone_canonical) WHERE phone_canonical IS NOT NULL;
CREATE INDEX idx_patients_name_dob ON patients(lower(full_name), date_of_birth) WHERE record_status = 'ACTIVE';
CREATE INDEX idx_patients_merged_into ON patients(merged_into_patient_id) WHERE merged_into_patient_id IS NOT NULL;

CREATE TABLE patient_care_relationships (
    id uuid PRIMARY KEY,
    manager_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    relationship_label text NOT NULL CHECK (length(btrim(relationship_label)) BETWEEN 2 AND 80),
    status text NOT NULL CHECK (status IN ('PENDING','CONFIRMED','ENDED')),
    confirmed_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    confirmed_at timestamptz,
    ended_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_care_confirmation CHECK
      ((status = 'PENDING' AND confirmed_at IS NULL AND confirmed_by_account_id IS NULL)
       OR (status IN ('CONFIRMED','ENDED') AND confirmed_at IS NOT NULL AND confirmed_by_account_id IS NOT NULL)),
    CONSTRAINT chk_care_end CHECK ((status = 'ENDED') = (ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_care_current ON patient_care_relationships(manager_account_id,patient_id)
    WHERE status IN ('PENDING','CONFIRMED');
CREATE INDEX idx_care_patient_status ON patient_care_relationships(patient_id,status);

CREATE TABLE staff_profiles (
    account_id uuid PRIMARY KEY REFERENCES accounts(id) ON DELETE RESTRICT,
    staff_code text NOT NULL UNIQUE,
    full_name text NOT NULL CHECK (length(btrim(full_name)) BETWEEN 2 AND 100),
    staff_role text NOT NULL CHECK (staff_role IN ('RECEPTIONIST','ORTHOPEDIC_DOCTOR','IMAGING_TECHNICIAN','ADMINISTRATOR','CLINIC_OWNER')),
    directory_status text NOT NULL DEFAULT 'ACTIVE' CHECK (directory_status IN ('ACTIVE','HIDDEN')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_staff_role_directory ON staff_profiles(staff_role,directory_status);

CREATE TABLE doctor_profiles (
    staff_account_id uuid PRIMARY KEY REFERENCES staff_profiles(account_id) ON DELETE RESTRICT,
    license_number text NOT NULL UNIQUE,
    specialty text NOT NULL,
    biography text,
    published_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE rooms (
    id uuid PRIMARY KEY,
    room_code text NOT NULL UNIQUE,
    display_name text NOT NULL,
    room_kind text NOT NULL CHECK (room_kind IN ('RECEPTION','CONSULTATION','IMAGING','TREATMENT')),
    operational_status text NOT NULL DEFAULT 'AVAILABLE' CHECK (operational_status IN ('AVAILABLE','UNAVAILABLE')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE clinic_services (
    id uuid PRIMARY KEY,
    service_code text NOT NULL UNIQUE,
    name text NOT NULL,
    description text,
    duration_minutes integer NOT NULL CHECK (duration_minutes > 0),
    buffer_minutes integer NOT NULL DEFAULT 0 CHECK (buffer_minutes >= 0),
    list_price numeric(14,2) NOT NULL CHECK (list_price >= 0),
    online_bookable boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_services_active ON clinic_services(name) WHERE is_active;

CREATE TABLE doctor_services (
    doctor_account_id uuid NOT NULL REFERENCES doctor_profiles(staff_account_id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    is_active boolean NOT NULL DEFAULT true,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (doctor_account_id,service_id)
);
CREATE INDEX idx_doctor_services_service ON doctor_services(service_id,doctor_account_id)
    WHERE is_active;

CREATE TABLE doctor_time_off (
    id uuid PRIMARY KEY,
    doctor_account_id uuid NOT NULL REFERENCES doctor_profiles(staff_account_id) ON DELETE RESTRICT,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    reason text NOT NULL CHECK (length(btrim(reason)) > 0),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELLED')),
    created_by_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_doctor_time_off_period CHECK (ends_at > starts_at)
);
CREATE INDEX idx_doctor_time_off_lookup ON doctor_time_off(doctor_account_id,starts_at,ends_at)
    WHERE status = 'ACTIVE';

CREATE TABLE booking_policies (
    policy_key text PRIMARY KEY,
    policy_value_integer integer NOT NULL CHECK (policy_value_integer >= 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO booking_policies(policy_key,policy_value_integer)
VALUES ('BOOKING_WINDOW_DAYS',30),('MINIMUM_BOOKING_NOTICE_MINUTES',120),('RESCHEDULE_NOTICE_HOURS',24)
ON CONFLICT (policy_key) DO NOTHING;

CREATE TABLE room_supported_services (
    room_id uuid NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    PRIMARY KEY (room_id,service_id)
);
CREATE INDEX idx_room_services_service ON room_supported_services(service_id,room_id);

CREATE TABLE work_shifts (
    id uuid PRIMARY KEY,
    staff_account_id uuid NOT NULL REFERENCES staff_profiles(account_id) ON DELETE RESTRICT,
    room_id uuid REFERENCES rooms(id) ON DELETE RESTRICT,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    shift_status text NOT NULL DEFAULT 'ASSIGNED' CHECK (shift_status IN ('ASSIGNED','CANCELLED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_shifts_period CHECK (ends_at > starts_at),
    CONSTRAINT ex_shifts_staff_overlap EXCLUDE USING gist
      (staff_account_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&)
      WHERE (shift_status = 'ASSIGNED')
);
CREATE INDEX idx_shifts_staff_start ON work_shifts(staff_account_id,starts_at);

CREATE TABLE appointments (
    id uuid PRIMARY KEY,
    patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    doctor_account_id uuid NOT NULL REFERENCES doctor_profiles(staff_account_id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    booked_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    visit_kind text NOT NULL CHECK (visit_kind IN ('INITIAL','FOLLOW_UP')),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status text NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED','CHECKED_IN','IN_PROGRESS','COMPLETED','CANCELLED','RESCHEDULE_REQUIRED','NO_SHOW')),
    cancellation_reason text CHECK (cancellation_reason IS NULL OR cancellation_reason IN ('PATIENT_REQUEST','CLINIC_REQUEST','DOCTOR_UNAVAILABLE','OTHER')),
    reschedule_reason text CHECK (reschedule_reason IS NULL OR reschedule_reason IN ('DOCTOR_UNAVAILABLE','TIME_OFF','CLINIC_REQUEST','OTHER')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT chk_appointments_period CHECK (ends_at > starts_at),
    CONSTRAINT uq_appointments_patient UNIQUE (id,patient_id),
    CONSTRAINT ex_appointments_doctor_overlap EXCLUDE USING gist
      (doctor_account_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&)
      WHERE (status IN ('CONFIRMED','CHECKED_IN','IN_PROGRESS'))
);
CREATE INDEX idx_appointments_patient_start ON appointments(patient_id,starts_at DESC);
CREATE INDEX idx_appointments_doctor_start ON appointments(doctor_account_id,starts_at);
CREATE INDEX idx_appointments_service ON appointments(service_id);

CREATE TABLE appointment_time_changes (
    id uuid PRIMARY KEY,
    appointment_id uuid NOT NULL REFERENCES appointments(id) ON DELETE RESTRICT,
    old_starts_at timestamptz NOT NULL,
    old_ends_at timestamptz NOT NULL,
    new_starts_at timestamptz NOT NULL,
    new_ends_at timestamptz NOT NULL,
    change_kind text NOT NULL DEFAULT 'RESCHEDULE' CHECK (change_kind IN ('RESCHEDULE','CLINIC_RESCHEDULE')),
    reason text,
    changed_by_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_time_change_period CHECK (old_ends_at > old_starts_at AND new_ends_at > new_starts_at)
);
CREATE INDEX idx_appointment_changes ON appointment_time_changes(appointment_id,changed_at DESC);

CREATE TABLE visits (
    id uuid PRIMARY KEY,
    patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    appointment_id uuid UNIQUE,
    doctor_account_id uuid REFERENCES doctor_profiles(staff_account_id) ON DELETE RESTRICT,
    checked_in_by_account_id uuid NOT NULL REFERENCES staff_profiles(account_id) ON DELETE RESTRICT,
    checked_in_at timestamptz NOT NULL DEFAULT now(),
    closed_at timestamptz,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT chk_visit_closure CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT chk_visit_close_time CHECK (closed_at IS NULL OR closed_at >= checked_in_at),
    CONSTRAINT fk_visits_appointment_patient FOREIGN KEY (appointment_id,patient_id)
      REFERENCES appointments(id,patient_id) ON DELETE RESTRICT
);
CREATE INDEX idx_visits_patient_checkin ON visits(patient_id,checked_in_at DESC);
CREATE INDEX idx_visits_open ON visits(checked_in_at) WHERE status = 'OPEN';

CREATE TABLE visit_stages (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL REFERENCES visits(id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    room_id uuid REFERENCES rooms(id) ON DELETE RESTRICT,
    unit_price_snapshot numeric(14,2) NOT NULL CHECK (unit_price_snapshot >= 0),
    stage_order integer NOT NULL CHECK (stage_order > 0),
    status text NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING','IN_PROGRESS','COMPLETED','SKIPPED')),
    entered_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    completed_at timestamptz,
    CONSTRAINT uq_visit_stage_order UNIQUE (visit_id,stage_order),
    CONSTRAINT chk_stage_times CHECK (started_at IS NULL OR started_at >= entered_at),
    CONSTRAINT chk_stage_completed CHECK (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at)),
    CONSTRAINT fk_stage_room_service FOREIGN KEY (room_id,service_id)
      REFERENCES room_supported_services(room_id,service_id) ON DELETE RESTRICT
);
CREATE INDEX idx_stages_room_status_entered ON visit_stages(room_id,status,entered_at)
    WHERE status IN ('WAITING','IN_PROGRESS');
CREATE INDEX idx_stages_visit ON visit_stages(visit_id,stage_order);

CREATE TABLE queue_position_events (
    id uuid PRIMARY KEY,
    visit_stage_id uuid NOT NULL REFERENCES visit_stages(id) ON DELETE RESTRICT,
    from_position integer CHECK (from_position IS NULL OR from_position > 0),
    to_position integer NOT NULL CHECK (to_position > 0),
    reason text NOT NULL CHECK (length(btrim(reason)) > 0),
    changed_by_account_id uuid NOT NULL REFERENCES staff_profiles(account_id) ON DELETE RESTRICT,
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_queue_events_stage_time ON queue_position_events(visit_stage_id,changed_at DESC);

CREATE TABLE patient_medical_history_entries (
    id uuid PRIMARY KEY,
    patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    category text NOT NULL CHECK (category IN ('CONDITION','ALLERGY','PROCEDURE','INJURY','ORTHOPEDIC_HISTORY')),
    description text NOT NULL CHECK (length(btrim(description)) > 0),
    recorded_by_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_history_patient_category ON patient_medical_history_entries(patient_id,category,recorded_at DESC);

CREATE TABLE examinations (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL UNIQUE REFERENCES visits(id) ON DELETE RESTRICT,
    doctor_account_id uuid NOT NULL REFERENCES doctor_profiles(staff_account_id) ON DELETE RESTRICT,
    reason_for_visit text,
    symptoms text,
    findings text,
    diagnosis text,
    injury_classification text,
    after_visit_advice text,
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','COMPLETED')),
    completed_at timestamptz,
    patient_released_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_exam_complete CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT chk_exam_release CHECK (patient_released_at IS NULL OR completed_at IS NOT NULL)
);

CREATE TABLE clinical_documents (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL REFERENCES visits(id) ON DELETE RESTRICT,
    document_kind text NOT NULL CHECK (document_kind IN ('EXAMINATION_REPORT','PRESCRIPTION')),
    object_key text NOT NULL UNIQUE,
    media_type text NOT NULL CHECK (media_type = 'application/pdf'),
    checksum_sha256 text NOT NULL CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    byte_size bigint NOT NULL CHECK (byte_size > 0),
    version_number integer NOT NULL CHECK (version_number > 0),
    uploaded_by_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    uploaded_at timestamptz NOT NULL DEFAULT now(),
    released_at timestamptz,
    CONSTRAINT uq_document_version UNIQUE (visit_id,document_kind,version_number)
);
CREATE INDEX idx_documents_visit_kind ON clinical_documents(visit_id,document_kind,version_number DESC);

CREATE TABLE prescriptions (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL REFERENCES visits(id) ON DELETE RESTRICT,
    doctor_account_id uuid NOT NULL REFERENCES doctor_profiles(staff_account_id) ON DELETE RESTRICT,
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ISSUED')),
    instructions text,
    issued_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_prescription_issued CHECK ((status = 'ISSUED') = (issued_at IS NOT NULL))
);
CREATE INDEX idx_prescriptions_visit ON prescriptions(visit_id,created_at DESC);

CREATE TABLE prescription_items (
    prescription_id uuid NOT NULL REFERENCES prescriptions(id) ON DELETE RESTRICT,
    line_number integer NOT NULL CHECK (line_number > 0),
    medicine_name text NOT NULL,
    strength text,
    quantity numeric(12,3) NOT NULL CHECK (quantity > 0),
    quantity_unit text NOT NULL,
    dosage_instructions text NOT NULL,
    PRIMARY KEY (prescription_id,line_number)
);

CREATE TABLE imaging_requests (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL REFERENCES visits(id) ON DELETE RESTRICT,
    requested_by_account_id uuid NOT NULL REFERENCES doctor_profiles(staff_account_id) ON DELETE RESTRICT,
    modality text NOT NULL CHECK (modality IN ('XRAY','CT','MRI')),
    body_area text NOT NULL,
    clinical_reason text NOT NULL,
    instructions text,
    status text NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','ACQUIRING','READY_FOR_REVIEW','COMPLETED','CANCELLED')),
    requested_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);
CREATE INDEX idx_imaging_worklist ON imaging_requests(status,requested_at,id)
    WHERE status IN ('REQUESTED','ACQUIRING','READY_FOR_REVIEW');
CREATE INDEX idx_imaging_requests_visit ON imaging_requests(visit_id,requested_at DESC);

CREATE TABLE imaging_upload_operations (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES imaging_requests(id) ON DELETE RESTRICT,
    idempotency_key text NOT NULL UNIQUE,
    submitted_by_account_id uuid NOT NULL REFERENCES staff_profiles(account_id) ON DELETE RESTRICT,
    status text NOT NULL CHECK (status IN ('PENDING','PROCESSING','LINKED','RECONCILIATION_REQUIRED','CONFLICT')),
    external_study_uid text,
    content_checksum_sha256 text CHECK (content_checksum_sha256 IS NULL OR content_checksum_sha256 ~ '^[0-9a-f]{64}$'),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_upload_operation_request UNIQUE (id,request_id)
);
CREATE INDEX idx_imaging_operations_reconcile ON imaging_upload_operations(status,updated_at)
    WHERE status IN ('PROCESSING','RECONCILIATION_REQUIRED','CONFLICT');
CREATE INDEX idx_imaging_operations_request ON imaging_upload_operations(request_id,created_at DESC);

CREATE TABLE imaging_studies (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES imaging_requests(id) ON DELETE RESTRICT,
    upload_operation_id uuid NOT NULL UNIQUE,
    study_instance_uid text NOT NULL UNIQUE,
    orthanc_study_id text NOT NULL UNIQUE,
    linked_at timestamptz NOT NULL DEFAULT now(),
    released_at timestamptz,
    CONSTRAINT fk_study_upload_request FOREIGN KEY (upload_operation_id,request_id)
      REFERENCES imaging_upload_operations(id,request_id) ON DELETE RESTRICT
);
CREATE INDEX idx_imaging_studies_request ON imaging_studies(request_id,linked_at DESC);

CREATE TABLE imaging_findings (
    study_id uuid PRIMARY KEY REFERENCES imaging_studies(id) ON DELETE RESTRICT,
    doctor_account_id uuid NOT NULL REFERENCES doctor_profiles(staff_account_id) ON DELETE RESTRICT,
    findings text NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
    id uuid PRIMARY KEY,
    invoice_number text NOT NULL UNIQUE,
    visit_id uuid NOT NULL UNIQUE REFERENCES visits(id) ON DELETE RESTRICT,
    created_by_account_id uuid NOT NULL REFERENCES staff_profiles(account_id) ON DELETE RESTRICT,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','VOID')),
    issued_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE INDEX idx_invoices_issued ON invoices(issued_at DESC);

CREATE TABLE invoice_items (
    invoice_id uuid NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    line_number integer NOT NULL CHECK (line_number > 0),
    service_id uuid REFERENCES clinic_services(id) ON DELETE RESTRICT,
    description_snapshot text NOT NULL,
    quantity numeric(12,3) NOT NULL CHECK (quantity > 0),
    unit_price_snapshot numeric(14,2) NOT NULL CHECK (unit_price_snapshot >= 0),
    PRIMARY KEY (invoice_id,line_number)
);
CREATE INDEX idx_invoice_items_service ON invoice_items(service_id) WHERE service_id IS NOT NULL;

CREATE TABLE payment_attempts (
    id uuid PRIMARY KEY,
    invoice_id uuid NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    method text NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER','ONLINE')),
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    currency_code char(3) NOT NULL DEFAULT 'VND' CHECK (currency_code ~ '^[A-Z]{3}$'),
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','FAILED','CANCELLED','REFUNDED')),
    gateway_provider text,
    gateway_reference text,
    idempotency_key text NOT NULL UNIQUE,
    recorded_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    confirmed_at timestamptz,
    CONSTRAINT uq_payment_gateway_ref UNIQUE (gateway_provider,gateway_reference),
    CONSTRAINT chk_payment_confirmation CHECK
      ((status IN ('CONFIRMED','REFUNDED')) = (confirmed_at IS NOT NULL))
);
CREATE INDEX idx_payments_invoice_status ON payment_attempts(invoice_id,status,created_at DESC);
CREATE INDEX idx_payments_confirmed ON payment_attempts(confirmed_at DESC) WHERE status = 'CONFIRMED';

CREATE TABLE patient_reviews (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL UNIQUE REFERENCES visits(id) ON DELETE RESTRICT,
    author_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    rating smallint NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text text NOT NULL CHECK (length(btrim(review_text)) > 0),
    moderation_status text NOT NULL DEFAULT 'PENDING' CHECK (moderation_status IN ('PENDING','PUBLISHED','HIDDEN','REJECTED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    CONSTRAINT chk_review_published CHECK (moderation_status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE INDEX idx_reviews_public ON patient_reviews(published_at DESC,id) WHERE moderation_status = 'PUBLISHED';

CREATE TABLE review_moderation_events (
    id uuid PRIMARY KEY,
    review_id uuid NOT NULL REFERENCES patient_reviews(id) ON DELETE RESTRICT,
    moderator_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    decision text NOT NULL CHECK (decision IN ('PUBLISH','HIDE','REJECT')),
    reason text,
    decided_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_moderation_review ON review_moderation_events(review_id,decided_at DESC);

CREATE TABLE review_replies (
    review_id uuid PRIMARY KEY REFERENCES patient_reviews(id) ON DELETE RESTRICT,
    author_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    reply_text text NOT NULL CHECK (length(btrim(reply_text)) > 0),
    replied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    recipient_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    notification_kind text NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    read_at timestamptz
);
CREATE INDEX idx_notifications_inbox ON notifications(recipient_account_id,created_at DESC,id);
CREATE INDEX idx_notifications_unread ON notifications(recipient_account_id,created_at DESC)
    WHERE read_at IS NULL;

CREATE TABLE website_contents (
    id uuid PRIMARY KEY,
    slug text NOT NULL UNIQUE,
    content_kind text NOT NULL CHECK (content_kind IN ('PAGE','ARTICLE')),
    title text NOT NULL,
    body text NOT NULL,
    published_at timestamptz,
    author_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_contents_published ON website_contents(published_at DESC) WHERE published_at IS NOT NULL;

CREATE TABLE announcements (
    id uuid PRIMARY KEY,
    title text NOT NULL,
    body text NOT NULL,
    audience text NOT NULL CHECK (audience IN ('PUBLIC','PATIENT','STAFF')),
    published_at timestamptz,
    author_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT
);
CREATE INDEX idx_announcements_audience ON announcements(audience,published_at DESC)
    WHERE published_at IS NOT NULL;

CREATE TABLE activity_audits (
    id uuid PRIMARY KEY,
    actor_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    action_code text NOT NULL,
    resource_kind text NOT NULL,
    resource_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    correlation_id text NOT NULL,
    outcome text NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','FAILED'))
);
CREATE INDEX idx_activity_audits_time ON activity_audits(occurred_at DESC,id);
CREATE INDEX idx_activity_audits_resource ON activity_audits(resource_kind,resource_id,occurred_at DESC);
CREATE INDEX idx_activity_audits_actor ON activity_audits(actor_account_id,occurred_at DESC)
    WHERE actor_account_id IS NOT NULL;
