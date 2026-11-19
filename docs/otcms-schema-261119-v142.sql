--
-- PostgreSQL database dump
--

\restrict qAcxlxA6NAjwBUeUbVbbXK3cmjIvGe5vWBHQuMbsY9vzAkITTRBECfiiTFQ9RbB

-- Dumped from database version 18.1
-- Dumped by pg_dump version 18.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


--
-- Name: enforce_appointment_visit_link(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_appointment_visit_link() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    target_appointment_id UUID;
    target_visit_id UUID;
    appointment_status VARCHAR(16);
    appointment_visit_id UUID;
    visit_appointment_id UUID;
    visit_source VARCHAR(16);
BEGIN
    IF TG_TABLE_NAME = 'appointments' THEN
        target_appointment_id := NEW.id;
    ELSE
        target_visit_id := NEW.id;
        target_appointment_id := NEW.originating_appointment_id;
        IF target_appointment_id IS NULL THEN
            RETURN NULL;
        END IF;
        SELECT a.visit_id INTO appointment_visit_id
        FROM appointments a WHERE a.id = target_appointment_id;
        IF appointment_visit_id IS DISTINCT FROM target_visit_id THEN
            RAISE EXCEPTION 'visit must be the appointment linked visit' USING ERRCODE = '23514';
        END IF;
    END IF;

    SELECT a.status, a.visit_id INTO appointment_status, appointment_visit_id
    FROM appointments a WHERE a.id = target_appointment_id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    IF TG_TABLE_NAME = 'appointments' THEN
        IF NEW.booking_source = 'DOCTOR_FOLLOW_UP' AND NEW.source_appointment_id IS NOT NULL THEN
            SELECT v.originating_appointment_id INTO visit_appointment_id
            FROM visits v WHERE v.id = NEW.source_visit_id;
            IF visit_appointment_id IS DISTINCT FROM NEW.source_appointment_id THEN
                RAISE EXCEPTION 'follow-up source appointment must match the source visit' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;

    IF appointment_visit_id IS NOT NULL THEN
        SELECT v.originating_appointment_id, v.source INTO visit_appointment_id, visit_source
        FROM visits v WHERE v.id = appointment_visit_id;
        IF visit_appointment_id IS DISTINCT FROM target_appointment_id OR visit_source IS DISTINCT FROM 'SCHEDULED' THEN
            RAISE EXCEPTION 'appointment visit link is inconsistent' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF appointment_status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') AND appointment_visit_id IS NULL THEN
        RAISE EXCEPTION 'checked-in appointment requires a linked visit' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;


--
-- Name: enforce_walk_in_reception_queue_visit(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_walk_in_reception_queue_visit() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    visit_source VARCHAR(16);
    visit_arrived_at TIMESTAMPTZ;
BEGIN
    IF NEW.stage <> 'RECEPTION' THEN
        RETURN NEW;
    END IF;
    SELECT source, arrived_at INTO visit_source, visit_arrived_at
    FROM visits WHERE id = NEW.visit_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'queue entry Visit does not exist' USING ERRCODE = '23503';
    END IF;
    IF visit_source IS DISTINCT FROM 'WALK_IN' THEN
        RAISE EXCEPTION 'only WALK_IN Visits may enter reception queue' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' AND NEW.status IS DISTINCT FROM 'WAITING' THEN
        RAISE EXCEPTION 'new reception queue entries must start in WAITING' USING ERRCODE = '23514';
    END IF;
    IF NEW.arrived_at IS DISTINCT FROM visit_arrived_at THEN
        RAISE EXCEPTION 'queue entry arrival time must match Visit arrival time' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_result_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_result_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.state = 'COMPLETED' OR OLD.released_at IS NOT NULL THEN
      RAISE EXCEPTION 'released or completed results are immutable';
    END IF;
    RETURN OLD;
  END IF;
  IF OLD.released_at IS NOT NULL THEN
    RAISE EXCEPTION 'released or completed results are immutable';
  END IF;
  IF OLD.state = 'COMPLETED' AND (to_jsonb(OLD) - 'released_at') IS DISTINCT FROM (to_jsonb(NEW) - 'released_at') THEN
    RAISE EXCEPTION 'completed diagnostic findings versions are immutable';
  END IF;
  RETURN NEW;
END $$;


--
-- Name: protect_patient_care_relationship_history(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_patient_care_relationship_history() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Care relationship history must be retained';
    END IF;
    IF (NEW.id, NEW.requester_account_id, NEW.dependent_patient_profile_id, NEW.confirming_account_id, NEW.requested_at)
        IS DISTINCT FROM
       (OLD.id, OLD.requester_account_id, OLD.dependent_patient_profile_id, OLD.confirming_account_id, OLD.requested_at) THEN
        RAISE EXCEPTION 'Care relationship identity must be retained';
    END IF;
    IF OLD.state = 'REVOKED'
       OR (OLD.state = 'CONFIRMED' AND NEW.state <> 'REVOKED')
       OR (OLD.state = 'REQUESTED' AND NEW.state NOT IN ('CONFIRMED', 'REVOKED')) THEN
        RAISE EXCEPTION 'Invalid care relationship transition';
    END IF;
    IF OLD.state = 'CONFIRMED' AND NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at THEN
        RAISE EXCEPTION 'Confirmation evidence must be retained';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_patient_caregiver_r3_metadata(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_patient_caregiver_r3_metadata() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.relationship IS NULL THEN
            RAISE EXCEPTION 'New care links require R3 metadata' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NEW.relationship IS DISTINCT FROM OLD.relationship THEN
            RAISE EXCEPTION 'Care relationship must be retained' USING ERRCODE = '23514';
        END IF;
        IF (OLD.verified_at IS NOT NULL OR OLD.relationship IS NULL)
            AND (NEW.verification_method, NEW.verified_by_account_id, NEW.verified_at)
                IS DISTINCT FROM (OLD.verification_method, OLD.verified_by_account_id, OLD.verified_at) THEN
            RAISE EXCEPTION 'Verification history must be retained' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_queued_visit_source_and_arrival(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_queued_visit_source_and_arrival() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM queue_entries q WHERE q.visit_id = OLD.id AND q.stage = 'RECEPTION') THEN
        IF NEW.source IS DISTINCT FROM OLD.source THEN
            RAISE EXCEPTION 'Visit source cannot change after a reception queue entry exists' USING ERRCODE = '23514';
        END IF;
        IF NEW.arrived_at IS DISTINCT FROM OLD.arrived_at THEN
            RAISE EXCEPTION 'Visit arrival time cannot change after a reception queue entry exists' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_walk_in_reception_queue_entry_lifecycle(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_walk_in_reception_queue_entry_lifecycle() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.stage IS DISTINCT FROM 'RECEPTION' THEN
        RETURN NEW;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.stage IS DISTINCT FROM OLD.stage
       OR NEW.visit_id IS DISTINCT FROM OLD.visit_id
       OR NEW.arrived_at IS DISTINCT FROM OLD.arrived_at
       OR NEW.entered_at IS DISTINCT FROM OLD.entered_at THEN
        RAISE EXCEPTION 'reception queue entry identity and arrival fields are immutable' USING ERRCODE = '55000';
    END IF;
    IF OLD.status IS DISTINCT FROM 'WAITING' THEN
        RAISE EXCEPTION 'closed reception queue entries cannot be reopened or changed' USING ERRCODE = '55000';
    END IF;
    IF NEW.status IS DISTINCT FROM 'LEFT_RECEPTION' THEN
        RAISE EXCEPTION 'active reception queue entries may only transition to LEFT_RECEPTION' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: queue_entries_default_sort_order(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.queue_entries_default_sort_order() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.sort_order IS NULL THEN
        NEW.sort_order := (extract(epoch FROM coalesce(NEW.arrived_at, NEW.entered_at)) * 1000)::bigint;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: reject_audit_log_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_audit_log_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'audit log rows are immutable' USING ERRCODE='55000'; END;
$$;


--
-- Name: set_appointment_occupied_until(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_appointment_occupied_until() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.occupied_until := NEW.end_time + make_interval(mins => NEW.buffer_minutes);
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts (
    id uuid NOT NULL,
    normalized_email character varying(254),
    account_type character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    password_hash character varying(255),
    email_verified_at timestamp with time zone,
    failed_credential_count integer DEFAULT 0 NOT NULL,
    locked_until timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    full_name character varying(100),
    username text,
    must_change_password boolean DEFAULT false NOT NULL,
    display_name character varying(100),
    avatar_content bytea,
    avatar_content_type character varying(32),
    avatar_updated_at timestamp with time zone,
    terms_version character varying(64),
    privacy_version character varying(64),
    consent_actor_type character varying(16),
    consent_accepted_at timestamp with time zone,
    CONSTRAINT ck_accounts_avatar_complete CHECK ((((avatar_content IS NULL) = (avatar_content_type IS NULL)) AND ((avatar_content IS NULL) = (avatar_updated_at IS NULL)))),
    CONSTRAINT ck_accounts_avatar_size CHECK (((avatar_content IS NULL) OR ((octet_length(avatar_content) >= 1) AND (octet_length(avatar_content) <= 2097152)))),
    CONSTRAINT ck_accounts_avatar_type CHECK (((avatar_content_type IS NULL) OR ((avatar_content_type)::text = ANY ((ARRAY['image/jpeg'::character varying, 'image/png'::character varying, 'image/webp'::character varying])::text[])))),
    CONSTRAINT ck_accounts_consent_actor CHECK (((consent_actor_type IS NULL) OR ((consent_actor_type)::text = ANY ((ARRAY['PATIENT'::character varying, 'GUARDIAN'::character varying])::text[])))),
    CONSTRAINT ck_accounts_consent_complete CHECK ((((terms_version IS NULL) = (privacy_version IS NULL)) AND ((terms_version IS NULL) = (consent_actor_type IS NULL)) AND ((terms_version IS NULL) = (consent_accepted_at IS NULL)))),
    CONSTRAINT ck_accounts_display_name CHECK (((display_name IS NULL) OR ((length(TRIM(BOTH FROM display_name)) >= 2) AND (length(TRIM(BOTH FROM display_name)) <= 100)))),
    CONSTRAINT ck_accounts_email_required CHECK (((normalized_email IS NOT NULL) OR (((account_type)::text <> 'PATIENT'::text) AND (username IS NOT NULL)))),
    CONSTRAINT ck_accounts_failed_credentials CHECK ((failed_credential_count >= 0)),
    CONSTRAINT ck_accounts_status CHECK (((status)::text = ANY ((ARRAY['PENDING_VERIFICATION'::character varying, 'ACTIVE'::character varying, 'TEMPORARILY_LOCKED'::character varying, 'DISABLED'::character varying, 'ARCHIVED'::character varying])::text[]))),
    CONSTRAINT ck_accounts_type CHECK (((account_type)::text = ANY (ARRAY[('PATIENT'::character varying)::text, ('RECEPTIONIST'::character varying)::text, ('DOCTOR'::character varying)::text, ('LAB_TECHNICIAN'::character varying)::text, ('IMAGING_TECHNICIAN'::character varying)::text, ('OWNER'::character varying)::text, ('ADMIN'::character varying)::text]))),
    CONSTRAINT ck_accounts_username_not_blank CHECK (((username IS NULL) OR (username ~ '[^[:space:]]'::text)))
);


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    source character varying(64) NOT NULL,
    category character varying(32) NOT NULL,
    action character varying(64) NOT NULL,
    record_type character varying(48) NOT NULL,
    entity_id uuid,
    record_label text,
    correction_reason text,
    visible_in_activity_log boolean DEFAULT true NOT NULL,
    actor_account_id uuid,
    actor_type character varying(32),
    before_state jsonb,
    after_state jsonb,
    metadata jsonb,
    correlation_id character varying(128),
    client_ip character varying(64),
    user_agent text,
    identifier_hmac character varying(255),
    outcome character varying(64),
    dedup_key character varying(200)
);


--
-- Name: TABLE audit_logs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_logs IS 'Append-only audit trail for all modules (final-db). No FKs: entries must outlive the audited rows.';


--
-- Name: COLUMN audit_logs.source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.audit_logs.source IS 'Name of the legacy audit table this row replaces; prefix of the activity log id.';


--
-- Name: activity_log_entries; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.activity_log_entries AS
 SELECT ((
        CASE source
            WHEN 'staff_resource_audits'::text THEN 'staff'::text
            WHEN 'room_management_audits'::text THEN 'room'::text
            WHEN 'service_audits'::text THEN 'service'::text
            WHEN 'content_audits'::text THEN 'content'::text
            WHEN 'account_management_audits'::text THEN 'account'::text
            WHEN 'appointment_audits'::text THEN 'appointment'::text
            WHEN 'visit_audits'::text THEN 'visit'::text
            WHEN 'visit_assignment_audits'::text THEN 'care'::text
            WHEN 'room_reservation_audits'::text THEN 'room_reservation'::text
            WHEN 'walk_in_reception_queue_history'::text THEN 'queue'::text
            WHEN 'diagnostic_audit_events'::text THEN 'diagnostic'::text
            WHEN 'diagnostic_history_access_audits'::text THEN 'diagnostic_access'::text
            ELSE 'log'::text
        END || ':'::text) || (id)::text) AS id,
    occurred_at,
    actor_account_id,
    (category)::text AS category,
    (action)::text AS action,
    (record_type)::text AS record_type,
    entity_id AS record_id,
    record_label,
    correction_reason
   FROM public.audit_logs a
  WHERE visible_in_activity_log;


--
-- Name: appointment_arrivals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointment_arrivals (
    appointment_id uuid NOT NULL,
    arrived_at timestamp with time zone,
    arrival_source character varying(24),
    arrival_recorded_at timestamp with time zone,
    arrival_recorded_by uuid,
    arrival_reason character varying(500),
    checkin_actor_account_id uuid,
    checkin_checked_in_at timestamp with time zone,
    CONSTRAINT ck_appointment_arrivals_all_or_none CHECK ((((arrived_at IS NULL) AND (arrival_source IS NULL) AND (arrival_recorded_at IS NULL) AND (arrival_recorded_by IS NULL)) OR ((arrived_at IS NOT NULL) AND (arrival_source IS NOT NULL) AND (arrival_recorded_at IS NOT NULL) AND (arrival_recorded_by IS NOT NULL)))),
    CONSTRAINT ck_appointment_arrivals_checkin_all_or_none CHECK (((checkin_actor_account_id IS NULL) = (checkin_checked_in_at IS NULL))),
    CONSTRAINT ck_appointment_arrivals_reason CHECK ((((arrived_at IS NULL) AND (arrival_source IS NULL) AND (arrival_reason IS NULL)) OR ((arrived_at IS NOT NULL) AND ((arrival_source)::text = ANY ((ARRAY['PATIENT_QR'::character varying, 'LEGACY_RECEPTIONIST'::character varying])::text[])) AND (arrival_reason IS NULL)) OR ((arrived_at IS NOT NULL) AND ((arrival_source)::text = 'RECEPTIONIST'::text) AND (arrival_reason IS NOT NULL) AND ((length(btrim((arrival_reason)::text)) >= 1) AND (length(btrim((arrival_reason)::text)) <= 500))))),
    CONSTRAINT ck_appointment_arrivals_source CHECK (((arrival_source IS NULL) OR ((arrival_source)::text = ANY ((ARRAY['PATIENT_QR'::character varying, 'RECEPTIONIST'::character varying, 'LEGACY_RECEPTIONIST'::character varying])::text[]))))
);


--
-- Name: appointments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    patient_profile_id uuid NOT NULL,
    service_id uuid NOT NULL,
    doctor_profile_id uuid NOT NULL,
    status character varying(16) DEFAULT 'CONFIRMED'::character varying NOT NULL,
    booking_source character varying(24) NOT NULL,
    purpose character varying(1000) NOT NULL,
    start_time timestamp with time zone NOT NULL,
    end_time timestamp with time zone NOT NULL,
    duration_minutes integer NOT NULL,
    buffer_minutes integer DEFAULT 0 NOT NULL,
    visit_id uuid,
    source_visit_id uuid,
    source_appointment_id uuid,
    ordering_doctor_profile_id uuid,
    created_by_account_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    occupied_until timestamp with time zone NOT NULL,
    payment_deadline timestamp with time zone,
    CONSTRAINT ck_appointments_booking_source CHECK (((booking_source)::text = ANY ((ARRAY['PATIENT_ONLINE'::character varying, 'RECEPTIONIST_ASSISTED'::character varying, 'DOCTOR_FOLLOW_UP'::character varying])::text[]))),
    CONSTRAINT ck_appointments_occupied_until CHECK ((occupied_until >= end_time)),
    CONSTRAINT ck_appointments_payment_deadline CHECK ((((status)::text <> 'AWAITING_PAYMENT'::text) OR (payment_deadline IS NOT NULL))),
    CONSTRAINT ck_appointments_source_evidence CHECK (((((booking_source)::text = 'DOCTOR_FOLLOW_UP'::text) AND (source_visit_id IS NOT NULL) AND (ordering_doctor_profile_id IS NOT NULL)) OR (((booking_source)::text <> 'DOCTOR_FOLLOW_UP'::text) AND (source_visit_id IS NULL) AND (source_appointment_id IS NULL) AND (ordering_doctor_profile_id IS NULL)))),
    CONSTRAINT ck_appointments_status CHECK (((status)::text = ANY ((ARRAY['AWAITING_PAYMENT'::character varying, 'PAYMENT_EXPIRED'::character varying, 'CONFIRMED'::character varying, 'CHECKED_IN'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying, 'NO_SHOW'::character varying])::text[]))),
    CONSTRAINT ck_appointments_values CHECK ((((length(TRIM(BOTH FROM purpose)) >= 1) AND (length(TRIM(BOTH FROM purpose)) <= 1000)) AND (duration_minutes > 0) AND (buffer_minutes >= 0) AND (end_time = (start_time + make_interval(mins => duration_minutes))) AND (version >= 0))),
    CONSTRAINT ck_appointments_visit_status_link CHECK (((((status)::text <> ALL ((ARRAY['CHECKED_IN'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying])::text[])) OR (visit_id IS NOT NULL)) AND (((status)::text <> ALL ((ARRAY['AWAITING_PAYMENT'::character varying, 'PAYMENT_EXPIRED'::character varying, 'CONFIRMED'::character varying, 'NO_SHOW'::character varying])::text[])) OR (visit_id IS NULL))))
);


--
-- Name: clinic_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinic_services (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(160) NOT NULL,
    description text,
    duration_minutes integer NOT NULL,
    buffer_minutes integer DEFAULT 0 NOT NULL,
    online_bookable boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    price numeric(12,2) DEFAULT 0.00 NOT NULL,
    service_type character varying(16) DEFAULT 'EXAMINATION'::character varying NOT NULL,
    modality character varying(16),
    patient_note text,
    lab_indicators text DEFAULT '[]'::text NOT NULL,
    CONSTRAINT ck_clinic_services_buffer CHECK ((buffer_minutes >= 0)),
    CONSTRAINT ck_clinic_services_duration CHECK ((duration_minutes > 0)),
    CONSTRAINT ck_clinic_services_modality CHECK (((((service_type)::text = 'IMAGING'::text) AND ((modality)::text = ANY ((ARRAY['XRAY'::character varying, 'ULTRASOUND'::character varying, 'CT'::character varying, 'MRI'::character varying])::text[]))) OR (((service_type)::text <> 'IMAGING'::text) AND (modality IS NULL)))),
    CONSTRAINT ck_clinic_services_price_nonnegative CHECK ((price >= (0)::numeric)),
    CONSTRAINT ck_clinic_services_type CHECK (((service_type)::text = ANY ((ARRAY['EXAMINATION'::character varying, 'IMAGING'::character varying, 'LABORATORY'::character varying])::text[])))
);


--
-- Name: clinical_examination_diagnoses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_examination_diagnoses (
    id uuid NOT NULL,
    examination_id uuid NOT NULL,
    code character varying(8) NOT NULL,
    type character varying(16) NOT NULL,
    CONSTRAINT ck_clinical_diagnosis_icd_groups CHECK (((code)::text ~* '^[MS][0-9]{2}(\.[A-Z0-9]{1,4})?$'::text)),
    CONSTRAINT clinical_examination_diagnoses_type_check CHECK (((type)::text = ANY ((ARRAY['PRIMARY'::character varying, 'SECONDARY'::character varying])::text[])))
);


--
-- Name: clinical_examinations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_examinations (
    id uuid NOT NULL,
    visit_id uuid NOT NULL,
    reason_for_visit text,
    symptoms text,
    examination_findings text,
    injury_body_site character varying(120),
    injury_side character varying(24),
    injury_mechanism text,
    status character varying(16) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    home_care_advice text,
    activity_advice text,
    rehabilitation_advice text,
    warning_signs text,
    follow_up_recommendation text,
    follow_up_indicated boolean,
    injury_description text,
    treatment_directions text[],
    CONSTRAINT ck_exam_injury_side CHECK (((injury_side IS NULL) OR ((injury_side)::text = ANY ((ARRAY['LEFT'::character varying, 'RIGHT'::character varying, 'BOTH'::character varying, 'NOT_APPLICABLE'::character varying])::text[])))),
    CONSTRAINT ck_exam_treatment_directions CHECK (((treatment_directions IS NULL) OR (((cardinality(treatment_directions) >= 1) AND (cardinality(treatment_directions) <= 4)) AND (array_position(treatment_directions, NULL::text) IS NULL) AND (treatment_directions <@ ARRAY['HOME_CARE'::text, 'MEDICATION'::text, 'PHYSICAL_THERAPY'::text, 'PROCEDURE_ELSEWHERE'::text]) AND (cardinality(array_positions(treatment_directions, 'HOME_CARE'::text)) <= 1) AND (cardinality(array_positions(treatment_directions, 'MEDICATION'::text)) <= 1) AND (cardinality(array_positions(treatment_directions, 'PHYSICAL_THERAPY'::text)) <= 1) AND (cardinality(array_positions(treatment_directions, 'PROCEDURE_ELSEWHERE'::text)) <= 1)))),
    CONSTRAINT clinical_examinations_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'COMPLETED'::character varying])::text[])))
);


--
-- Name: COLUMN clinical_examinations.follow_up_indicated; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.clinical_examinations.follow_up_indicated IS 'Explicit Doctor recommendation; NULL means not yet stated';


--
-- Name: clinical_medical_document_payloads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_medical_document_payloads (
    document_id uuid NOT NULL,
    content bytea NOT NULL,
    CONSTRAINT clinical_medical_document_payloads_content_check CHECK (((octet_length(content) >= 1) AND (octet_length(content) <= 2097152)))
);


--
-- Name: clinical_medical_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_medical_documents (
    id uuid NOT NULL,
    visit_id uuid NOT NULL,
    patient_profile_id uuid NOT NULL,
    document_type character varying(32) NOT NULL,
    title character varying(255) NOT NULL,
    original_filename character varying(255) NOT NULL,
    media_type character varying(80) NOT NULL,
    byte_size bigint NOT NULL,
    released_at timestamp with time zone,
    released_by_doctor_profile_id uuid,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    source character varying(16) DEFAULT 'UPLOADED'::character varying NOT NULL,
    CONSTRAINT ck_clinical_document_release CHECK ((((released_at IS NULL) AND (released_by_doctor_profile_id IS NULL)) OR ((released_at IS NOT NULL) AND (released_by_doctor_profile_id IS NOT NULL)))),
    CONSTRAINT ck_clinical_document_report_pdf CHECK ((((document_type)::text <> 'CLINIC_EXAMINATION_REPORT'::text) OR ((media_type)::text = 'application/pdf'::text))),
    CONSTRAINT clinical_medical_documents_byte_size_check CHECK (((byte_size >= 1) AND (byte_size <= 2097152))),
    CONSTRAINT clinical_medical_documents_document_type_check CHECK (((document_type)::text = ANY ((ARRAY['CLINIC_EXAMINATION_REPORT'::character varying, 'EXTERNAL_REFERRAL'::character varying, 'EXTERNAL_REPORT'::character varying])::text[]))),
    CONSTRAINT clinical_medical_documents_media_type_check CHECK (((media_type)::text = ANY ((ARRAY['application/pdf'::character varying, 'image/jpeg'::character varying, 'image/png'::character varying])::text[]))),
    CONSTRAINT clinical_medical_documents_original_filename_check CHECK ((length(TRIM(BOTH FROM original_filename)) > 0)),
    CONSTRAINT clinical_medical_documents_source_check CHECK (((source)::text = ANY ((ARRAY['GENERATED'::character varying, 'UPLOADED'::character varying])::text[]))),
    CONSTRAINT clinical_medical_documents_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REPLACED'::character varying])::text[]))),
    CONSTRAINT clinical_medical_documents_title_check CHECK ((length(TRIM(BOTH FROM title)) > 0)),
    CONSTRAINT clinical_medical_documents_version_check CHECK ((version >= 1))
);


--
-- Name: clinical_ordering_rounds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_ordering_rounds (
    id uuid NOT NULL,
    visit_id uuid NOT NULL,
    round_number integer NOT NULL,
    ordered_by_doctor_id uuid NOT NULL,
    ordered_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_clinical_rounds_round_number CHECK ((round_number > 0))
);


--
-- Name: clinical_prescription_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_prescription_items (
    id uuid NOT NULL,
    prescription_id uuid NOT NULL,
    line_number integer NOT NULL,
    medicine character varying(255) NOT NULL,
    dose character varying(255) NOT NULL,
    frequency character varying(255) NOT NULL,
    usage character varying(2000) NOT NULL,
    CONSTRAINT clinical_prescription_items_dose_check CHECK ((length(TRIM(BOTH FROM dose)) > 0)),
    CONSTRAINT clinical_prescription_items_frequency_check CHECK ((length(TRIM(BOTH FROM frequency)) > 0)),
    CONSTRAINT clinical_prescription_items_line_number_check CHECK ((line_number > 0)),
    CONSTRAINT clinical_prescription_items_medicine_check CHECK ((length(TRIM(BOTH FROM medicine)) > 0)),
    CONSTRAINT clinical_prescription_items_usage_check CHECK ((length(TRIM(BOTH FROM usage)) > 0))
);


--
-- Name: clinical_prescriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_prescriptions (
    id uuid NOT NULL,
    visit_id uuid NOT NULL,
    patient_profile_id uuid NOT NULL,
    doctor_profile_id uuid NOT NULL,
    status character varying(16) NOT NULL,
    issued_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    issuance_key character varying(128),
    issuance_expected_version bigint,
    CONSTRAINT ck_clinical_prescription_issuance CHECK (((((status)::text = 'DRAFT'::text) AND (issued_at IS NULL) AND (issuance_key IS NULL) AND (issuance_expected_version IS NULL)) OR (((status)::text = 'ISSUED'::text) AND (issued_at IS NOT NULL) AND (issuance_key IS NOT NULL) AND ((length(TRIM(BOTH FROM issuance_key)) >= 1) AND (length(TRIM(BOTH FROM issuance_key)) <= 128)) AND (issuance_expected_version IS NOT NULL) AND (issuance_expected_version >= 1) AND (version = (issuance_expected_version + 1))))),
    CONSTRAINT ck_clinical_prescription_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ISSUED'::character varying])::text[]))),
    CONSTRAINT ck_clinical_prescription_version CHECK ((version >= 1))
);


--
-- Name: clinical_service_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_service_orders (
    id uuid NOT NULL,
    order_code character varying(32) NOT NULL,
    ordering_round_id uuid NOT NULL,
    visit_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    service_id uuid NOT NULL,
    service_name_snapshot character varying(255) NOT NULL,
    service_type character varying(16) NOT NULL,
    requesting_doctor_id uuid NOT NULL,
    clinical_reason text NOT NULL,
    body_area character varying(255),
    instructions text,
    price_snapshot numeric(12,2) NOT NULL,
    order_status character varying(24) NOT NULL,
    payment_status character varying(24) NOT NULL,
    cancel_reason text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    room_id uuid,
    lab_status character varying(24),
    lab_review_required boolean DEFAULT false NOT NULL,
    doctor_result_assessment text,
    doctor_result_assessed_by_account_id uuid,
    doctor_result_assessed_at timestamp with time zone,
    CONSTRAINT ck_clinical_lab_review CHECK (((NOT lab_review_required) OR ((service_type)::text = 'LABORATORY'::text))),
    CONSTRAINT ck_clinical_lab_status CHECK (((lab_status IS NULL) OR (((service_type)::text = 'LABORATORY'::text) AND ((lab_status)::text = ANY ((ARRAY['ASSIGNED'::character varying, 'COLLECTED'::character varying, 'REJECTED'::character varying, 'RESULT_AVAILABLE'::character varying])::text[]))))),
    CONSTRAINT ck_clinical_order_result_assessment CHECK ((((doctor_result_assessment IS NULL) AND (doctor_result_assessed_by_account_id IS NULL) AND (doctor_result_assessed_at IS NULL)) OR ((doctor_result_assessment IS NOT NULL) AND ((length(btrim(doctor_result_assessment)) >= 1) AND (length(btrim(doctor_result_assessment)) <= 4000)) AND (doctor_result_assessed_by_account_id IS NOT NULL) AND (doctor_result_assessed_at IS NOT NULL)))),
    CONSTRAINT clinical_service_orders_order_status_check CHECK (((order_status)::text = ANY ((ARRAY['ORDERED'::character varying, 'RESULTS_AVAILABLE'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT clinical_service_orders_payment_status_check CHECK (((payment_status)::text = ANY ((ARRAY['PENDING_PAYMENT'::character varying, 'PAID'::character varying])::text[]))),
    CONSTRAINT clinical_service_orders_price_snapshot_check CHECK ((price_snapshot >= (0)::numeric)),
    CONSTRAINT clinical_service_orders_service_type_check CHECK (((service_type)::text = ANY ((ARRAY['IMAGING'::character varying, 'LABORATORY'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: diagnostic_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.diagnostic_reports (
    id uuid NOT NULL,
    service_order_id uuid CONSTRAINT diagnostic_reports_request_id_not_null NOT NULL,
    visit_id uuid NOT NULL,
    study_ref text,
    version_number integer CONSTRAINT diagnostic_reports_report_version_not_null NOT NULL,
    report_storage_ref text,
    report_sha256 character varying(64),
    validation_status character varying(16),
    uploaded_by_doctor_id uuid,
    uploaded_at timestamp with time zone,
    validated_at timestamp with time zone,
    released_at timestamp with time zone,
    result_type character varying(16) DEFAULT 'IMAGING'::character varying NOT NULL,
    state character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    findings_text text,
    correction_reason text,
    lab_values jsonb,
    report_id uuid,
    occurred_at timestamp with time zone,
    recorded_by_doctor_id uuid,
    completed_at timestamp with time zone,
    recorded_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    lab_sample_id uuid,
    lab_recorded_by_account_id uuid,
    lab_confirmed_by_account_id uuid,
    lab_confirmed_at timestamp with time zone,
    lab_correction_source text,
    CONSTRAINT ck_results_completed CHECK ((((state)::text <> 'COMPLETED'::text) OR ((completed_at IS NOT NULL) AND ((((result_type)::text = 'IMAGING'::text) AND (findings_text IS NOT NULL) AND ((report_id IS NULL) OR ((validation_status)::text = 'VALIDATED'::text))) OR (((result_type)::text = 'LAB'::text) AND (lab_sample_id IS NOT NULL) AND (lab_recorded_by_account_id IS NOT NULL) AND (lab_confirmed_by_account_id IS NOT NULL) AND (lab_confirmed_at IS NOT NULL) AND (lab_values IS NOT NULL) AND (jsonb_typeof(lab_values) = 'array'::text) AND (jsonb_array_length(lab_values) > 0)))))),
    CONSTRAINT ck_results_findings_text CHECK (((findings_text IS NULL) OR (length(btrim(findings_text)) > 0))),
    CONSTRAINT ck_results_imaging_study CHECK ((((result_type)::text = 'LAB'::text) OR (study_ref IS NOT NULL))),
    CONSTRAINT ck_results_released CHECK (((released_at IS NULL) OR (((state)::text = 'COMPLETED'::text) AND ((report_id IS NULL) OR ((validation_status)::text = 'VALIDATED'::text))))),
    CONSTRAINT ck_results_report_all_or_none CHECK ((((report_id IS NULL) = (report_storage_ref IS NULL)) AND ((report_id IS NULL) = (report_sha256 IS NULL)) AND ((report_id IS NULL) = (validation_status IS NULL)))),
    CONSTRAINT ck_results_sha256 CHECK (((report_sha256 IS NULL) OR ((report_sha256)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT ck_results_state CHECK (((state)::text = ANY ((ARRAY['DRAFT'::character varying, 'COMPLETED'::character varying])::text[]))),
    CONSTRAINT ck_results_study_ref CHECK (((study_ref IS NULL) OR (length(btrim(study_ref)) > 0))),
    CONSTRAINT ck_results_type CHECK (((result_type)::text = ANY ((ARRAY['IMAGING'::character varying, 'LAB'::character varying])::text[]))),
    CONSTRAINT ck_results_validation CHECK (((validation_status IS NULL) OR (((validation_status)::text = ANY ((ARRAY['PENDING'::character varying, 'VALIDATED'::character varying, 'INVALID'::character varying])::text[])) AND (((validation_status)::text = 'PENDING'::text) = (validated_at IS NULL))))),
    CONSTRAINT ck_results_version CHECK ((version_number > 0))
);


--
-- Name: doctor_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor_services (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT doctor_service_assignments_id_not_null NOT NULL,
    doctor_profile_id uuid CONSTRAINT doctor_service_assignments_doctor_profile_id_not_null NOT NULL,
    service_id uuid CONSTRAINT doctor_service_assignments_service_id_not_null NOT NULL,
    active boolean DEFAULT true CONSTRAINT doctor_service_assignments_active_not_null NOT NULL,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT doctor_service_assignments_created_at_not_null NOT NULL,
    updated_at timestamp with time zone DEFAULT now() CONSTRAINT doctor_service_assignments_updated_at_not_null NOT NULL
);


--
-- Name: idempotency_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    scope character varying(40) NOT NULL,
    owner_id uuid NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    request_fingerprint character varying(64) NOT NULL,
    resource_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_idempotency_records_fingerprint CHECK (((request_fingerprint)::text ~ '^[0-9a-fA-F]{64}$'::text)),
    CONSTRAINT ck_idempotency_records_key CHECK (((length(btrim((idempotency_key)::text)) >= 1) AND (length(btrim((idempotency_key)::text)) <= 255))),
    CONSTRAINT ck_idempotency_records_scope CHECK (((scope)::text = ANY ((ARRAY['APPOINTMENT_CHECKIN'::character varying, 'WALK_IN_VISIT'::character varying, 'ORDER_ROUND_SUBMIT'::character varying, 'ORDER_REVIEW'::character varying, 'ORDER_CANCEL'::character varying, 'ORDER_TRANSFER'::character varying, 'RESULT_DRAFT'::character varying, 'RESULT_CORRECTION'::character varying, 'RESULT_UPLOAD'::character varying, 'RESULT_COMPLETE'::character varying, 'IMAGING_UPLOAD'::character varying, 'IMAGING_SEND'::character varying, 'PAYMENT_ATTEMPT'::character varying])::text[])))
);


--
-- Name: imaging_order_executions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imaging_order_executions (
    service_order_id uuid NOT NULL,
    imaging_type character varying(80) NOT NULL,
    ordering_doctor_account_id uuid NOT NULL,
    execution_status character varying(32) NOT NULL,
    cancellation_cause character varying(64),
    cancellation_reason text,
    attempt_id uuid,
    technician_account_id uuid,
    first_use_type character varying(32),
    first_use_event_id uuid,
    first_use_at timestamp with time zone,
    first_use_source_id character varying(128),
    study_ref text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_ioe_attempt_state CHECK (((((execution_status)::text = ANY ((ARRAY['PENDING'::character varying, 'CANCELLED'::character varying])::text[])) AND (attempt_id IS NULL)) OR (((execution_status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'RESULT_AVAILABLE'::character varying, 'REVIEWED'::character varying])::text[])) AND (attempt_id IS NOT NULL)))),
    CONSTRAINT ck_ioe_cancellation CHECK (
CASE
    WHEN ((execution_status)::text = 'CANCELLED'::text) THEN (((cancellation_cause)::text = ANY ((ARRAY['PATIENT_DECLINED'::character varying, 'CLINIC_UNABLE'::character varying])::text[])) AND (cancellation_reason IS NOT NULL))
    ELSE ((cancellation_cause IS NULL) AND (cancellation_reason IS NULL))
END),
    CONSTRAINT ck_ioe_in_progress_no_study CHECK ((((execution_status)::text IS DISTINCT FROM 'IN_PROGRESS'::text) OR (study_ref IS NULL))),
    CONSTRAINT ck_ioe_result_has_study CHECK ((((execution_status)::text <> ALL ((ARRAY['RESULT_AVAILABLE'::character varying, 'REVIEWED'::character varying])::text[])) OR (study_ref IS NOT NULL))),
    CONSTRAINT ck_ioe_status CHECK (((execution_status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_PROGRESS'::character varying, 'RESULT_AVAILABLE'::character varying, 'REVIEWED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_ioe_version CHECK ((version >= 0))
);


--
-- Name: imaging_reconciliation_cases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imaging_reconciliation_cases (
    id uuid NOT NULL,
    operation_id uuid,
    event_id uuid,
    category character varying(32) NOT NULL,
    state character varying(16) DEFAULT 'OPEN'::character varying NOT NULL,
    safe_reason_code character varying(64) NOT NULL,
    assigned_owner_queue character varying(64) NOT NULL,
    opened_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    updated_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    resolved_at timestamp with time zone,
    resolution_ref character varying(128),
    classification character varying(24) DEFAULT 'ORPHANED'::character varying NOT NULL,
    safe_evidence_sha256 character(64),
    resolution_decision character varying(40),
    CONSTRAINT ck_imaging_recon_classification CHECK (((classification)::text = ANY ((ARRAY['LINKED'::character varying, 'RETRYABLE'::character varying, 'ORPHANED'::character varying, 'CONFLICT'::character varying])::text[]))),
    CONSTRAINT ck_imaging_recon_evidence_sha256 CHECK (((safe_evidence_sha256 IS NULL) OR (safe_evidence_sha256 ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT ck_imaging_recon_identity CHECK (((operation_id IS NOT NULL) <> (event_id IS NOT NULL))),
    CONSTRAINT ck_imaging_recon_reason CHECK (((safe_reason_code)::text ~ '^[A-Z][A-Z0-9_]{0,63}$'::text)),
    CONSTRAINT ck_imaging_recon_resolution CHECK (((((state)::text = 'RESOLVED'::text) AND (resolved_at IS NOT NULL)) OR (((state)::text <> 'RESOLVED'::text) AND (resolved_at IS NULL)))),
    CONSTRAINT ck_imaging_recon_resolution_decision CHECK (((resolution_decision IS NULL) OR ((resolution_decision)::text = ANY ((ARRAY['KEEP_QUARANTINED'::character varying, 'EXTERNAL_RESOLUTION_CONFIRMED'::character varying, 'START_REVIEW'::character varying])::text[])))),
    CONSTRAINT ck_imaging_recon_state CHECK (((state)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'RESOLVED'::character varying])::text[])))
);


--
-- Name: imaging_studies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imaging_studies (
    study_ref character varying(128),
    service_order_id uuid CONSTRAINT imaging_studies_request_id_not_null NOT NULL,
    attempt_id uuid NOT NULL,
    visit_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    archive_study_ref text,
    study_instance_uid character varying(64),
    modality character varying(16),
    series_count integer DEFAULT 0 NOT NULL,
    instance_count integer DEFAULT 0 NOT NULL,
    verification_status character varying(24) DEFAULT 'NOT_EVALUATED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    id uuid NOT NULL,
    actor_account_id uuid NOT NULL,
    state character varying(32) NOT NULL,
    payload_sha256 character(64) NOT NULL,
    payload_size_bytes bigint DEFAULT 0 NOT NULL,
    dicom_index jsonb,
    verification_evidence jsonb DEFAULT '[]'::jsonb NOT NULL,
    safe_outcome_code character varying(64),
    acknowledged_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_imaging_studies_linked CHECK ((((state)::text <> 'LINKED'::text) OR ((study_ref IS NOT NULL) AND (archive_study_ref IS NOT NULL) AND (study_instance_uid IS NOT NULL) AND (acknowledged_at IS NOT NULL)))),
    CONSTRAINT ck_imaging_studies_safe_outcome CHECK (((safe_outcome_code IS NULL) OR ((safe_outcome_code)::text ~ '^[A-Z][A-Z0-9_]{0,63}$'::text))),
    CONSTRAINT ck_imaging_studies_sha256 CHECK ((payload_sha256 ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_imaging_studies_sizes CHECK (((payload_size_bytes >= 0) AND (series_count >= 0) AND (instance_count >= 0))),
    CONSTRAINT ck_imaging_studies_state CHECK (((state)::text = ANY ((ARRAY['PROCESSING'::character varying, 'ARCHIVED_NOT_SENT'::character varying, 'CONFIRMATION_PENDING'::character varying, 'LINKED'::character varying, 'QUARANTINED'::character varying, 'FAILED'::character varying, 'RECONCILIATION_REQUIRED'::character varying])::text[]))),
    CONSTRAINT ck_imaging_studies_verification CHECK (((verification_status)::text = ANY ((ARRAY['NOT_EVALUATED'::character varying, 'CONFIRMED_MATCH'::character varying, 'MISMATCH'::character varying, 'UNKNOWN'::character varying])::text[]))),
    CONSTRAINT ck_imaging_studies_version CHECK ((version >= 0))
);


--
-- Name: imaging_study_releases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imaging_study_releases (
    imaging_study_id uuid NOT NULL,
    confirmation_event_id uuid,
    confirmation_attempt_count integer DEFAULT 0 NOT NULL,
    next_confirmation_attempt_at timestamp with time zone,
    last_safe_error_code character varying(64),
    release_event_id uuid,
    release_payload_sha256 character(64),
    released_findings_id uuid,
    released_report_id uuid,
    released_report_sha256 character(64),
    findings_released_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_imaging_releases_confirmation_attempts CHECK (((confirmation_attempt_count >= 0) AND (confirmation_attempt_count <= 8))),
    CONSTRAINT ck_imaging_releases_sha256 CHECK ((((release_payload_sha256 IS NULL) OR (release_payload_sha256 ~ '^[0-9a-f]{64}$'::text)) AND ((released_report_sha256 IS NULL) OR (released_report_sha256 ~ '^[0-9a-f]{64}$'::text))))
);


--
-- Name: imaging_viewer_grants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imaging_viewer_grants (
    id uuid NOT NULL,
    token_sha256 character(64) NOT NULL,
    actor_account_id uuid NOT NULL,
    purpose character varying(24) NOT NULL,
    study_ref character varying(128) NOT NULL,
    authorized_version_ref character varying(128) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    CONSTRAINT ck_imaging_grant_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_imaging_grant_purpose CHECK (((purpose)::text = ANY ((ARRAY['DOCTOR_REVIEW'::character varying, 'PATIENT_VIEW'::character varying, 'TECHNICIAN_ASSIGNED'::character varying])::text[]))),
    CONSTRAINT ck_imaging_grant_revocation CHECK (((revoked_at IS NULL) OR (revoked_at >= created_at))),
    CONSTRAINT ck_imaging_grant_sha256 CHECK ((token_sha256 ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: invoice_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_items (
    invoice_id uuid NOT NULL,
    line_number integer NOT NULL,
    service_id uuid,
    description_snapshot character varying(255) NOT NULL,
    quantity integer NOT NULL,
    unit_price_snapshot numeric(14,2) NOT NULL,
    service_order_id uuid,
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    payment_attempt_id uuid,
    voided_at timestamp with time zone,
    refund_status character varying(16),
    refund_reason text,
    refunded_at timestamp with time zone,
    refunded_by_account_id uuid,
    CONSTRAINT ck_invoice_items_line CHECK ((line_number > 0)),
    CONSTRAINT ck_invoice_items_price CHECK ((unit_price_snapshot >= (0)::numeric)),
    CONSTRAINT ck_invoice_items_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_invoice_items_refund_status CHECK (((refund_status IS NULL) OR ((refund_status)::text = ANY ((ARRAY['REFUND_PENDING'::character varying, 'REFUNDED'::character varying])::text[]))))
);


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
    id uuid NOT NULL,
    invoice_number character varying(48) NOT NULL,
    patient_profile_id uuid NOT NULL,
    visit_id uuid,
    appointment_id uuid,
    source_type character varying(16) NOT NULL,
    source_fingerprint character varying(64) NOT NULL,
    status character varying(16) DEFAULT 'OPEN'::character varying NOT NULL,
    created_by_account_id uuid NOT NULL,
    issued_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    clinical_ordering_round_id uuid,
    CONSTRAINT ck_invoices_source CHECK (((((source_type)::text = 'VISIT'::text) AND (visit_id IS NOT NULL) AND (appointment_id IS NULL) AND (clinical_ordering_round_id IS NULL)) OR (((source_type)::text = 'APPOINTMENT'::text) AND (appointment_id IS NOT NULL) AND (visit_id IS NULL) AND (clinical_ordering_round_id IS NULL)) OR (((source_type)::text = 'CLINICAL_ROUND'::text) AND (clinical_ordering_round_id IS NOT NULL) AND (visit_id IS NULL) AND (appointment_id IS NULL)))),
    CONSTRAINT ck_invoices_source_fingerprint CHECK (((source_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_invoices_source_type CHECK (((source_type)::text = ANY ((ARRAY['VISIT'::character varying, 'APPOINTMENT'::character varying, 'CLINICAL_ROUND'::character varying])::text[]))),
    CONSTRAINT ck_invoices_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'PAID'::character varying, 'VOID'::character varying])::text[]))),
    CONSTRAINT ck_invoices_version CHECK ((version >= 0))
);


--
-- Name: laboratory_samples; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.laboratory_samples (
    id uuid NOT NULL,
    service_order_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    sample_code character varying(128) NOT NULL,
    verified_by_account_id uuid NOT NULL,
    verified_at timestamp with time zone NOT NULL,
    collected_by_account_id uuid NOT NULL,
    collected_at timestamp with time zone NOT NULL,
    rejected_by_account_id uuid,
    rejected_at timestamp with time zone,
    rejection_reason text,
    rejection_source text,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_lab_sample_collection_time CHECK ((collected_at >= verified_at)),
    CONSTRAINT ck_lab_sample_rejected CHECK ((((rejected_at IS NULL) AND (rejected_by_account_id IS NULL) AND (rejection_reason IS NULL) AND (rejection_source IS NULL)) OR ((rejected_at IS NOT NULL) AND (rejected_by_account_id IS NOT NULL) AND (rejected_at >= collected_at) AND (rejection_reason IS NOT NULL) AND (length(btrim(rejection_reason)) > 0) AND (rejection_source IS NOT NULL) AND (length(btrim(rejection_source)) > 0)))),
    CONSTRAINT laboratory_samples_attempt_number_check CHECK ((attempt_number > 0)),
    CONSTRAINT laboratory_samples_sample_code_check CHECK ((length(btrim((sample_code)::text)) > 0))
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    recipient_account_id uuid NOT NULL,
    type character varying(48) NOT NULL,
    title character varying(240) NOT NULL,
    content text NOT NULL,
    related_kind character varying(24),
    related_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    read_at timestamp with time zone,
    dedupe_key character varying(120),
    CONSTRAINT ck_notifications_related_kind CHECK (((related_kind)::text = ANY ((ARRAY['APPOINTMENT'::character varying, 'VISIT'::character varying, 'VISIT_RESULT'::character varying, 'PAYMENT'::character varying, 'REVIEW'::character varying])::text[]))),
    CONSTRAINT ck_notifications_related_pair CHECK (((related_kind IS NULL) = (related_id IS NULL)))
);


--
-- Name: patient_caregivers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_caregivers (
    id uuid CONSTRAINT patient_care_relationships_id_not_null NOT NULL,
    requester_account_id uuid CONSTRAINT patient_care_relationships_requester_account_id_not_null NOT NULL,
    dependent_patient_profile_id uuid CONSTRAINT patient_care_relationships_dependent_patient_profile_i_not_null NOT NULL,
    confirming_account_id uuid,
    state character varying(16) CONSTRAINT patient_care_relationships_state_not_null NOT NULL,
    requested_at timestamp with time zone CONSTRAINT patient_care_relationships_requested_at_not_null NOT NULL,
    confirmed_at timestamp with time zone,
    revoked_at timestamp with time zone,
    revoked_by_account_id uuid,
    relationship character varying(16),
    verification_method character varying(24),
    pending_reason character varying(16),
    verified_by_account_id uuid,
    verified_at timestamp with time zone,
    CONSTRAINT ck_care_r3_evidence CHECK (((relationship IS NULL) OR (((verified_at IS NULL) OR (verified_at >= requested_at)) AND (((verification_method)::text IS DISTINCT FROM 'EMAIL_OTP'::text) OR (confirming_account_id IS NOT NULL)) AND ((((state)::text = 'REQUESTED'::text) AND (pending_reason IS NOT NULL) AND (verification_method IS NULL) AND (verified_by_account_id IS NULL) AND (verified_at IS NULL)) OR (((state)::text = 'CONFIRMED'::text) AND (pending_reason IS NULL) AND (verification_method IS NOT NULL) AND (verified_by_account_id IS NOT NULL) AND (verified_at IS NOT NULL) AND (confirmed_at IS NOT NULL) AND (verified_at = confirmed_at)) OR (((state)::text = 'REVOKED'::text) AND (pending_reason IS NULL) AND (((confirmed_at IS NULL) AND (verification_method IS NULL) AND (verified_by_account_id IS NULL) AND (verified_at IS NULL)) OR ((confirmed_at IS NOT NULL) AND (verification_method IS NOT NULL) AND (verified_by_account_id IS NOT NULL) AND (verified_at IS NOT NULL) AND (verified_at = confirmed_at)))))))),
    CONSTRAINT ck_care_r3_method CHECK (((verification_method)::text = ANY ((ARRAY['EMAIL_OTP'::character varying, 'COUNTER_CHECK'::character varying, 'CREATED_WITHOUT_OTP'::character varying])::text[]))),
    CONSTRAINT ck_care_r3_pending CHECK (((pending_reason)::text = ANY ((ARRAY['OTP'::character varying, 'COUNTER'::character varying])::text[]))),
    CONSTRAINT ck_care_r3_relationship CHECK (((relationship)::text = ANY ((ARRAY['PARENT'::character varying, 'CHILD'::character varying, 'SPOUSE'::character varying, 'SIBLING'::character varying])::text[]))),
    CONSTRAINT ck_care_relationship_chronology CHECK ((((confirmed_at IS NULL) OR (confirmed_at >= requested_at)) AND ((revoked_at IS NULL) OR (revoked_at >= COALESCE(confirmed_at, requested_at))))),
    CONSTRAINT ck_care_relationship_distinct_accounts CHECK ((requester_account_id <> confirming_account_id)),
    CONSTRAINT ck_care_relationship_state_evidence CHECK (((((state)::text = 'REQUESTED'::text) AND (confirmed_at IS NULL) AND (revoked_at IS NULL) AND (revoked_by_account_id IS NULL)) OR (((state)::text = 'CONFIRMED'::text) AND (confirmed_at IS NOT NULL) AND (revoked_at IS NULL) AND (revoked_by_account_id IS NULL)) OR (((state)::text = 'REVOKED'::text) AND (revoked_at IS NOT NULL) AND (revoked_by_account_id IS NOT NULL)))),
    CONSTRAINT patient_care_relationships_state_check CHECK (((state)::text = ANY ((ARRAY['REQUESTED'::character varying, 'CONFIRMED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: patient_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_profiles (
    account_id uuid,
    full_name character varying(100) NOT NULL,
    phone_canonical character varying(16),
    phone_owner_type character varying(16),
    date_of_birth date NOT NULL,
    gender character varying(16) NOT NULL,
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    address text,
    national_id character varying(20),
    identity_document_type character varying(16),
    version bigint DEFAULT 0 NOT NULL,
    conditions character varying(2000) DEFAULT ''::character varying NOT NULL,
    allergies character varying(2000) DEFAULT ''::character varying NOT NULL,
    procedures character varying(2000) DEFAULT ''::character varying NOT NULL,
    injuries character varying(2000) DEFAULT ''::character varying NOT NULL,
    orthopedic_history character varying(2000) DEFAULT ''::character varying NOT NULL,
    medical_history_updated_at timestamp with time zone,
    medical_history_updated_by uuid,
    medical_history_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_patient_profiles_contact_pair CHECK (((account_id IS NULL) OR ((phone_canonical IS NOT NULL) AND (phone_owner_type IS NOT NULL)))),
    CONSTRAINT ck_patient_profiles_gender CHECK (((gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT ck_patient_profiles_identity_document_type CHECK (((identity_document_type)::text = ANY ((ARRAY['CCCD'::character varying, 'PERSONAL_ID'::character varying, 'PASSPORT'::character varying])::text[]))),
    CONSTRAINT ck_patient_profiles_medical_history_state CHECK ((((medical_history_updated_at IS NULL) = (medical_history_version = 0)) AND ((medical_history_updated_at IS NULL) = (medical_history_updated_by IS NULL)))),
    CONSTRAINT ck_patient_profiles_medical_history_version CHECK ((medical_history_version >= 0)),
    CONSTRAINT ck_patient_profiles_phone_owner CHECK (((phone_owner_type)::text = ANY ((ARRAY['PATIENT'::character varying, 'GUARDIAN'::character varying])::text[])))
);


--
-- Name: patient_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_reviews (
    id uuid NOT NULL,
    visit_id uuid NOT NULL,
    patient_profile_id uuid NOT NULL,
    author_account_id uuid NOT NULL,
    rating integer NOT NULL,
    content text NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    reply_text text,
    replied_at timestamp with time zone,
    replied_by_account_id uuid,
    submitted_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_patient_reviews_reply CHECK ((((reply_text IS NULL) AND (replied_at IS NULL) AND (replied_by_account_id IS NULL)) OR ((reply_text IS NOT NULL) AND (replied_at IS NOT NULL) AND (replied_by_account_id IS NOT NULL)))),
    CONSTRAINT patient_reviews_content_check CHECK (((length(btrim(content)) >= 10) AND (length(btrim(content)) <= 1000))),
    CONSTRAINT patient_reviews_rating_check CHECK (((rating >= 1) AND (rating <= 5))),
    CONSTRAINT patient_reviews_reply_text_check CHECK (((reply_text IS NULL) OR ((length(btrim(reply_text)) >= 1) AND (length(btrim(reply_text)) <= 1000)))),
    CONSTRAINT patient_reviews_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'HIDDEN'::character varying])::text[]))),
    CONSTRAINT patient_reviews_version_check CHECK ((version >= 0))
);


--
-- Name: payment_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_attempts (
    id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    channel character varying(16) NOT NULL,
    counter_method character varying(24),
    provider character varying(32),
    amount numeric(14,2) NOT NULL,
    currency_code character varying(3) DEFAULT 'VND'::character varying NOT NULL,
    status character varying(32) NOT NULL,
    created_by_account_id uuid NOT NULL,
    provider_order_code bigint,
    provider_transaction_reference character varying(128),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone,
    confirmed_at timestamp with time zone,
    reconciliation_reason character varying(500),
    last_event_fingerprint character varying(64),
    signature_verified boolean,
    provider_payload jsonb,
    CONSTRAINT ck_payment_attempt_amount CHECK ((amount > (0)::numeric)),
    CONSTRAINT ck_payment_attempt_channel CHECK (((channel)::text = ANY ((ARRAY['COUNTER'::character varying, 'ONLINE'::character varying, 'COUNTER_QR'::character varying])::text[]))),
    CONSTRAINT ck_payment_attempt_confirmation CHECK ((((status)::text = 'CONFIRMED'::text) = (confirmed_at IS NOT NULL))),
    CONSTRAINT ck_payment_attempt_counter CHECK (((((channel)::text = 'COUNTER'::text) AND (counter_method IS NOT NULL) AND (provider IS NULL) AND (provider_order_code IS NULL)) OR (((channel)::text = ANY ((ARRAY['ONLINE'::character varying, 'COUNTER_QR'::character varying])::text[])) AND (counter_method IS NULL) AND (provider IS NOT NULL) AND (provider_order_code IS NOT NULL)))),
    CONSTRAINT ck_payment_attempt_counter_method CHECK (((counter_method IS NULL) OR ((counter_method)::text = ANY ((ARRAY['CASH'::character varying, 'BANK_TRANSFER'::character varying, 'OTHER'::character varying])::text[])))),
    CONSTRAINT ck_payment_attempt_currency CHECK (((currency_code)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ck_payment_attempt_event_fingerprint CHECK (((last_event_fingerprint IS NULL) OR ((last_event_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT ck_payment_attempt_event_pair CHECK (((last_event_fingerprint IS NULL) = (signature_verified IS NULL))),
    CONSTRAINT ck_payment_attempt_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'EXPIRED'::character varying, 'RECONCILIATION_REQUIRED'::character varying])::text[])))
);


--
-- Name: queue_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.queue_entries (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    visit_id uuid NOT NULL,
    stage character varying(16) NOT NULL,
    status character varying(32) NOT NULL,
    arrived_at timestamp with time zone,
    entered_at timestamp with time zone NOT NULL,
    left_at timestamp with time zone,
    exit_reason character varying(24),
    doctor_profile_id uuid,
    room_id uuid,
    service_id uuid,
    clinic_date date,
    queue_number integer,
    queued_at timestamp with time zone,
    started_at timestamp with time zone,
    awaiting_at timestamp with time zone,
    finished_at timestamp with time zone,
    sort_order bigint NOT NULL,
    called_at timestamp with time zone,
    absent_at timestamp with time zone,
    service_order_id uuid,
    CONSTRAINT ck_queue_entries_exam CHECK ((((stage)::text <> 'EXAM'::text) OR ((doctor_profile_id IS NOT NULL) AND (clinic_date IS NOT NULL) AND (arrived_at IS NULL) AND (left_at IS NULL) AND (exit_reason IS NULL) AND ((((status)::text = 'PENDING_PAYMENT'::text) AND (queue_number IS NULL)) OR ((status)::text = 'CANCELLED'::text) OR (((status)::text = ANY ((ARRAY['WAITING'::character varying, 'CALLED'::character varying, 'ABSENT'::character varying, 'IN_EXAMINATION'::character varying, 'AWAITING_CONCLUSION'::character varying, 'COMPLETED'::character varying])::text[])) AND (queue_number IS NOT NULL) AND (queue_number > 0)))))),
    CONSTRAINT ck_queue_entries_reception CHECK ((((stage)::text <> 'RECEPTION'::text) OR ((arrived_at IS NOT NULL) AND (doctor_profile_id IS NULL) AND (room_id IS NULL) AND (service_id IS NULL) AND (clinic_date IS NULL) AND (queue_number IS NULL) AND (queued_at IS NULL) AND (started_at IS NULL) AND (awaiting_at IS NULL) AND (finished_at IS NULL) AND ((((status)::text = 'WAITING'::text) AND (left_at IS NULL) AND (exit_reason IS NULL)) OR (((status)::text = 'LEFT_RECEPTION'::text) AND (left_at IS NOT NULL) AND (exit_reason IS NOT NULL) AND ((exit_reason)::text = ANY ((ARRAY['CARE_STARTED'::character varying, 'CANCELLED'::character varying, 'VISIT_CLOSED'::character varying])::text[]))))))),
    CONSTRAINT ck_queue_entries_service CHECK ((((stage)::text <> 'SERVICE'::text) OR ((room_id IS NOT NULL) AND (service_order_id IS NOT NULL)))),
    CONSTRAINT ck_queue_entries_stage CHECK (((stage)::text = ANY ((ARRAY['RECEPTION'::character varying, 'EXAM'::character varying, 'SERVICE'::character varying, 'RESULT_REVIEW'::character varying])::text[])))
);


--
-- Name: room_reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_reservations (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT visit_room_reservations_id_not_null NOT NULL,
    visit_id uuid CONSTRAINT visit_room_reservations_visit_id_not_null NOT NULL,
    assignment_id uuid CONSTRAINT visit_room_reservations_assignment_id_not_null NOT NULL,
    room_id uuid CONSTRAINT visit_room_reservations_room_id_not_null NOT NULL,
    step_code character varying(64) CONSTRAINT visit_room_reservations_step_code_not_null NOT NULL,
    capability_code character varying(64) CONSTRAINT visit_room_reservations_capability_code_not_null NOT NULL,
    starts_at timestamp with time zone CONSTRAINT visit_room_reservations_starts_at_not_null NOT NULL,
    expected_ends_at timestamp with time zone CONSTRAINT visit_room_reservations_expected_ends_at_not_null NOT NULL,
    reserved_until timestamp with time zone CONSTRAINT visit_room_reservations_reserved_until_not_null NOT NULL,
    cleanup_buffer_minutes integer CONSTRAINT visit_room_reservations_cleanup_buffer_minutes_not_null NOT NULL,
    status character varying(16) CONSTRAINT visit_room_reservations_status_not_null NOT NULL,
    actual_ended_at timestamp with time zone,
    assigned_at timestamp with time zone CONSTRAINT visit_room_reservations_assigned_at_not_null NOT NULL,
    CONSTRAINT ck_visit_room_reservation_actual_end CHECK (((actual_ended_at IS NULL) OR (actual_ended_at >= starts_at))),
    CONSTRAINT ck_visit_room_reservation_buffer CHECK ((cleanup_buffer_minutes >= 0)),
    CONSTRAINT ck_visit_room_reservation_capability CHECK (((length(TRIM(BOTH FROM capability_code)) >= 1) AND (length(TRIM(BOTH FROM capability_code)) <= 64))),
    CONSTRAINT ck_visit_room_reservation_interval CHECK (((starts_at < expected_ends_at) AND (starts_at <= reserved_until))),
    CONSTRAINT ck_visit_room_reservation_status CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'CLEANING'::character varying, 'RELEASED'::character varying])::text[]))),
    CONSTRAINT ck_visit_room_reservation_step CHECK (((length(TRIM(BOTH FROM step_code)) >= 1) AND (length(TRIM(BOTH FROM step_code)) <= 64)))
);


--
-- Name: room_supported_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_supported_services (
    room_id uuid NOT NULL,
    service_id uuid NOT NULL
);


--
-- Name: rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rooms (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT visit_rooms_id_not_null NOT NULL,
    room_code character varying(64) CONSTRAINT visit_rooms_room_code_not_null NOT NULL,
    display_label character varying(128) CONSTRAINT visit_rooms_display_label_not_null NOT NULL,
    active boolean DEFAULT true CONSTRAINT visit_rooms_active_not_null NOT NULL,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT visit_rooms_created_at_not_null NOT NULL,
    updated_at timestamp with time zone DEFAULT now() CONSTRAINT visit_rooms_updated_at_not_null NOT NULL,
    room_type character varying(16) DEFAULT 'EXAMINATION'::character varying NOT NULL,
    description text DEFAULT ''::text NOT NULL,
    capabilities text[] DEFAULT '{}'::text[] NOT NULL,
    CONSTRAINT ck_rooms_type CHECK (((room_type)::text = ANY ((ARRAY['EXAMINATION'::character varying, 'IMAGING'::character varying, 'LABORATORY'::character varying])::text[]))),
    CONSTRAINT ck_visit_rooms_code CHECK (((length(TRIM(BOTH FROM room_code)) >= 1) AND (length(TRIM(BOTH FROM room_code)) <= 64))),
    CONSTRAINT ck_visit_rooms_label CHECK (((length(TRIM(BOTH FROM display_label)) >= 1) AND (length(TRIM(BOTH FROM display_label)) <= 128)))
);


--
-- Name: sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sessions (
    id uuid CONSTRAINT auth_sessions_id_not_null NOT NULL,
    account_id uuid CONSTRAINT auth_sessions_account_id_not_null NOT NULL,
    token_family_id uuid CONSTRAINT auth_sessions_token_family_id_not_null NOT NULL,
    refresh_token_hash character varying(255) CONSTRAINT auth_sessions_refresh_token_hash_not_null NOT NULL,
    created_at timestamp with time zone CONSTRAINT auth_sessions_created_at_not_null NOT NULL,
    expires_at timestamp with time zone CONSTRAINT auth_sessions_expires_at_not_null NOT NULL,
    last_rotated_at timestamp with time zone,
    revoked_at timestamp with time zone,
    revoke_reason character varying(64),
    client_ip character varying(64),
    user_agent character varying(512),
    remember_me boolean DEFAULT true NOT NULL
);


--
-- Name: staff_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_profiles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    account_id uuid NOT NULL,
    display_name character varying(160) NOT NULL,
    work_contact character varying(254),
    photo_content bytea,
    photo_content_type character varying(32),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    is_doctor boolean DEFAULT false NOT NULL,
    in_active_directory boolean DEFAULT true NOT NULL,
    publicly_visible boolean DEFAULT false NOT NULL,
    accepting_new_bookings boolean DEFAULT false NOT NULL,
    specialties text DEFAULT ''::text NOT NULL,
    qualifications text DEFAULT ''::text NOT NULL,
    biography text DEFAULT ''::text NOT NULL,
    consultation_information text DEFAULT ''::text NOT NULL,
    photo_url character varying(2048),
    CONSTRAINT ck_staff_profile_name CHECK ((length(TRIM(BOTH FROM display_name)) > 0)),
    CONSTRAINT ck_staff_profile_photo CHECK ((((photo_content IS NULL) AND (photo_content_type IS NULL)) OR ((photo_content IS NOT NULL) AND ((photo_content_type)::text = ANY ((ARRAY['image/jpeg'::character varying, 'image/png'::character varying, 'image/webp'::character varying])::text[]))))),
    CONSTRAINT ck_staff_profile_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REMOVED'::character varying])::text[]))),
    CONSTRAINT ck_staff_profiles_doctor_flags CHECK ((is_doctor OR ((NOT publicly_visible) AND (NOT accepting_new_bookings))))
);


--
-- Name: verification_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.verification_codes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    account_id uuid NOT NULL,
    purpose character varying(24) NOT NULL,
    code_hash character varying(255) NOT NULL,
    target_email character varying(254),
    link_id uuid,
    payload jsonb,
    failed_attempts integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    superseded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_verification_codes_care_link_link CHECK ((((purpose)::text <> 'CARE_LINK'::text) OR (link_id IS NOT NULL))),
    CONSTRAINT ck_verification_codes_consumed CHECK (((consumed_at IS NULL) OR (consumed_at >= created_at))),
    CONSTRAINT ck_verification_codes_email_change_target CHECK ((((purpose)::text <> 'EMAIL_CHANGE'::text) OR (target_email IS NOT NULL))),
    CONSTRAINT ck_verification_codes_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_verification_codes_failed_attempts CHECK (((failed_attempts >= 0) AND (failed_attempts <= 5))),
    CONSTRAINT ck_verification_codes_payload_open CHECK (((payload IS NULL) OR (consumed_at IS NULL))),
    CONSTRAINT ck_verification_codes_payload_purpose CHECK (((payload IS NULL) OR ((purpose)::text = 'DEPENDENT_ADD'::text))),
    CONSTRAINT ck_verification_codes_purpose CHECK (((purpose)::text = ANY ((ARRAY['EMAIL_VERIFY'::character varying, 'EMAIL_CHANGE'::character varying, 'PASSWORD_RESET'::character varying, 'CARE_LINK'::character varying, 'DEPENDENT_ADD'::character varying])::text[]))),
    CONSTRAINT ck_verification_codes_superseded CHECK (((superseded_at IS NULL) OR (superseded_at >= created_at))),
    CONSTRAINT ck_verification_codes_target_email CHECK (((target_email IS NULL) OR (((target_email)::text = lower(btrim((target_email)::text))) AND (length((target_email)::text) > 0))))
);


--
-- Name: visit_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.visit_assignments (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT visit_care_assignments_id_not_null NOT NULL,
    visit_id uuid CONSTRAINT visit_care_assignments_visit_id_not_null NOT NULL,
    doctor_profile_id uuid CONSTRAINT visit_care_assignments_doctor_profile_id_not_null NOT NULL,
    starts_at timestamp with time zone CONSTRAINT visit_care_assignments_starts_at_not_null NOT NULL,
    ends_at timestamp with time zone CONSTRAINT visit_care_assignments_ends_at_not_null NOT NULL,
    status character varying(16) CONSTRAINT visit_care_assignments_status_not_null NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() CONSTRAINT visit_care_assignments_assigned_at_not_null NOT NULL,
    released_at timestamp with time zone,
    step_code character varying(64),
    expected_duration_minutes integer,
    actual_ended_at timestamp with time zone,
    CONSTRAINT ck_visit_care_actual_end CHECK (((actual_ended_at IS NULL) OR (actual_ended_at >= starts_at))),
    CONSTRAINT ck_visit_care_assignment_interval CHECK ((ends_at > starts_at)),
    CONSTRAINT ck_visit_care_assignment_release CHECK ((((status)::text = 'RELEASED'::text) = (released_at IS NOT NULL))),
    CONSTRAINT ck_visit_care_assignment_status CHECK (((status)::text = ANY ((ARRAY['RESERVED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'RELEASED'::character varying])::text[]))),
    CONSTRAINT ck_visit_care_expected_duration CHECK (((expected_duration_minutes IS NULL) OR (expected_duration_minutes > 0)))
);


--
-- Name: visits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.visits (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT clinic_visits_id_not_null NOT NULL,
    patient_profile_id uuid CONSTRAINT clinic_visits_patient_profile_id_not_null NOT NULL,
    source character varying(16) CONSTRAINT clinic_visits_source_not_null NOT NULL,
    originating_appointment_id uuid,
    prior_appointment_id uuid,
    arrived_at timestamp with time zone CONSTRAINT clinic_visits_arrived_at_not_null NOT NULL,
    checked_in_at timestamp with time zone CONSTRAINT clinic_visits_checked_in_at_not_null NOT NULL,
    created_by_account_id uuid CONSTRAINT clinic_visits_created_by_account_id_not_null NOT NULL,
    status character varying(16) DEFAULT 'OPEN'::character varying CONSTRAINT clinic_visits_status_not_null NOT NULL,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT clinic_visits_created_at_not_null NOT NULL,
    updated_at timestamp with time zone DEFAULT now() CONSTRAINT clinic_visits_updated_at_not_null NOT NULL,
    imaging_result_ready_at timestamp with time zone,
    findings_released_at timestamp with time zone,
    incomplete_at timestamp with time zone,
    CONSTRAINT ck_clinic_visits_check_in_time CHECK ((checked_in_at >= arrived_at)),
    CONSTRAINT ck_clinic_visits_incomplete_at CHECK ((((status)::text = 'INCOMPLETE'::text) = (incomplete_at IS NOT NULL))),
    CONSTRAINT ck_clinic_visits_source CHECK (((source)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'WALK_IN'::character varying])::text[]))),
    CONSTRAINT ck_clinic_visits_source_link CHECK (((((source)::text = 'SCHEDULED'::text) AND (originating_appointment_id IS NOT NULL) AND (prior_appointment_id IS NULL)) OR (((source)::text = 'WALK_IN'::text) AND (originating_appointment_id IS NULL)))),
    CONSTRAINT ck_clinic_visits_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying, 'INCOMPLETE'::character varying])::text[])))
);


--
-- Name: website_contents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.website_contents (
    id uuid NOT NULL,
    type character varying(32) NOT NULL,
    title character varying(200) NOT NULL,
    summary text DEFAULT ''::text NOT NULL,
    body text NOT NULL,
    status character varying(16) NOT NULL,
    cover_image_id uuid,
    published_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by uuid NOT NULL,
    updated_by uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    audience character varying(16),
    recipient_role character varying(32),
    CONSTRAINT ck_website_contents_announcement CHECK (((((type)::text = 'ANNOUNCEMENT'::text) = (audience IS NOT NULL)) AND ((audience IS NULL) OR ((audience)::text = ANY ((ARRAY['PUBLIC'::character varying, 'PATIENT'::character varying, 'STAFF'::character varying])::text[]))) AND (((COALESCE(audience, ''::character varying))::text = 'STAFF'::text) = (recipient_role IS NOT NULL)) AND ((recipient_role IS NULL) OR ((recipient_role)::text = ANY ((ARRAY['ORTHOPEDIC_DOCTOR'::character varying, 'RECEPTIONIST'::character varying, 'IMAGING_TECHNICIAN'::character varying, 'LAB_TECHNICIAN'::character varying])::text[]))) AND (((type)::text <> 'ANNOUNCEMENT'::text) OR (((status)::text = 'PUBLISHED'::text) AND (length(btrim((title)::text)) > 0) AND (length(btrim(body)) > 0))))),
    CONSTRAINT website_contents_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying])::text[]))),
    CONSTRAINT website_contents_title_check CHECK ((length(TRIM(BOTH FROM title)) > 0)),
    CONSTRAINT website_contents_type_check CHECK (((type)::text = ANY ((ARRAY['ABOUT_PAGE'::character varying, 'HEALTH_ARTICLE'::character varying, 'CLINIC_INFO'::character varying, 'ANNOUNCEMENT'::character varying])::text[])))
);


--
-- Name: website_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.website_images (
    id uuid NOT NULL,
    content bytea NOT NULL,
    content_type character varying(32) NOT NULL,
    CONSTRAINT website_images_content_check CHECK (((octet_length(content) >= 1) AND (octet_length(content) <= 2097152))),
    CONSTRAINT website_images_content_type_check CHECK (((content_type)::text = ANY ((ARRAY['image/jpeg'::character varying, 'image/png'::character varying, 'image/webp'::character varying])::text[])))
);


--
-- Name: work_shifts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_shifts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    staff_profile_id uuid NOT NULL,
    room_id uuid,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_work_shifts_interval CHECK ((ends_at > starts_at))
);


--
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (id);


--
-- Name: appointment_arrivals appointment_arrivals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_arrivals
    ADD CONSTRAINT appointment_arrivals_pkey PRIMARY KEY (appointment_id);


--
-- Name: appointments appointments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: sessions auth_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT auth_sessions_pkey PRIMARY KEY (id);


--
-- Name: clinic_services clinic_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_services
    ADD CONSTRAINT clinic_services_pkey PRIMARY KEY (id);


--
-- Name: visits clinic_visits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT clinic_visits_pkey PRIMARY KEY (id);


--
-- Name: clinical_examination_diagnoses clinical_examination_diagnoses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_examination_diagnoses
    ADD CONSTRAINT clinical_examination_diagnoses_pkey PRIMARY KEY (id);


--
-- Name: clinical_examinations clinical_examinations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_examinations
    ADD CONSTRAINT clinical_examinations_pkey PRIMARY KEY (id);


--
-- Name: clinical_examinations clinical_examinations_visit_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_examinations
    ADD CONSTRAINT clinical_examinations_visit_id_key UNIQUE (visit_id);


--
-- Name: clinical_medical_document_payloads clinical_medical_document_payloads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_medical_document_payloads
    ADD CONSTRAINT clinical_medical_document_payloads_pkey PRIMARY KEY (document_id);


--
-- Name: clinical_medical_documents clinical_medical_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_medical_documents
    ADD CONSTRAINT clinical_medical_documents_pkey PRIMARY KEY (id);


--
-- Name: clinical_ordering_rounds clinical_ordering_rounds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_ordering_rounds
    ADD CONSTRAINT clinical_ordering_rounds_pkey PRIMARY KEY (id);


--
-- Name: clinical_prescription_items clinical_prescription_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_prescription_items
    ADD CONSTRAINT clinical_prescription_items_pkey PRIMARY KEY (id);


--
-- Name: clinical_prescriptions clinical_prescriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_prescriptions
    ADD CONSTRAINT clinical_prescriptions_pkey PRIMARY KEY (id);


--
-- Name: clinical_service_orders clinical_service_orders_order_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_service_orders
    ADD CONSTRAINT clinical_service_orders_order_code_key UNIQUE (order_code);


--
-- Name: clinical_service_orders clinical_service_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_service_orders
    ADD CONSTRAINT clinical_service_orders_pkey PRIMARY KEY (id);


--
-- Name: diagnostic_reports diagnostic_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT diagnostic_reports_pkey PRIMARY KEY (id);


--
-- Name: doctor_services doctor_service_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_services
    ADD CONSTRAINT doctor_service_assignments_pkey PRIMARY KEY (id);


--
-- Name: appointments ex_appointments_doctor_occupied_range; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT ex_appointments_doctor_occupied_range EXCLUDE USING gist (doctor_profile_id WITH =, tstzrange(start_time, occupied_until, '[)'::text) WITH &&) WHERE (((status)::text = ANY ((ARRAY['AWAITING_PAYMENT'::character varying, 'CONFIRMED'::character varying, 'CHECKED_IN'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying])::text[])));


--
-- Name: room_reservations ex_visit_room_reservation_no_overlap; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_reservations
    ADD CONSTRAINT ex_visit_room_reservation_no_overlap EXCLUDE USING gist (room_id WITH =, tstzrange(starts_at, reserved_until, '[)'::text) WITH &&) WHERE (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'CLEANING'::character varying])::text[])));


--
-- Name: work_shifts ex_work_shifts_room_no_overlap; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT ex_work_shifts_room_no_overlap EXCLUDE USING gist (room_id WITH =, tstzrange(starts_at, ends_at, '[)'::text) WITH &&) WHERE ((room_id IS NOT NULL));


--
-- Name: work_shifts ex_work_shifts_staff_no_overlap; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT ex_work_shifts_staff_no_overlap EXCLUDE USING gist (staff_profile_id WITH =, tstzrange(starts_at, ends_at, '[)'::text) WITH &&);


--
-- Name: idempotency_records idempotency_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT idempotency_records_pkey PRIMARY KEY (id);


--
-- Name: imaging_order_executions imaging_order_executions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_order_executions
    ADD CONSTRAINT imaging_order_executions_pkey PRIMARY KEY (service_order_id);


--
-- Name: imaging_reconciliation_cases imaging_reconciliation_cases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_reconciliation_cases
    ADD CONSTRAINT imaging_reconciliation_cases_pkey PRIMARY KEY (id);


--
-- Name: imaging_studies imaging_studies_archive_study_ref_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_studies
    ADD CONSTRAINT imaging_studies_archive_study_ref_key UNIQUE (archive_study_ref);


--
-- Name: imaging_studies imaging_studies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_studies
    ADD CONSTRAINT imaging_studies_pkey PRIMARY KEY (id);


--
-- Name: imaging_studies imaging_studies_study_instance_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_studies
    ADD CONSTRAINT imaging_studies_study_instance_uid_key UNIQUE (study_instance_uid);


--
-- Name: imaging_studies imaging_studies_study_ref_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_studies
    ADD CONSTRAINT imaging_studies_study_ref_key UNIQUE (study_ref);


--
-- Name: imaging_study_releases imaging_study_releases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_study_releases
    ADD CONSTRAINT imaging_study_releases_pkey PRIMARY KEY (imaging_study_id);


--
-- Name: imaging_viewer_grants imaging_viewer_grants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_viewer_grants
    ADD CONSTRAINT imaging_viewer_grants_pkey PRIMARY KEY (id);


--
-- Name: imaging_viewer_grants imaging_viewer_grants_token_sha256_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_viewer_grants
    ADD CONSTRAINT imaging_viewer_grants_token_sha256_key UNIQUE (token_sha256);


--
-- Name: invoice_items invoice_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT invoice_items_pkey PRIMARY KEY (invoice_id, line_number);


--
-- Name: invoices invoices_invoice_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_invoice_number_key UNIQUE (invoice_number);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: laboratory_samples laboratory_samples_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.laboratory_samples
    ADD CONSTRAINT laboratory_samples_pkey PRIMARY KEY (id);


--
-- Name: laboratory_samples laboratory_samples_sample_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.laboratory_samples
    ADD CONSTRAINT laboratory_samples_sample_code_key UNIQUE (sample_code);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: patient_caregivers patient_care_relationships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_caregivers
    ADD CONSTRAINT patient_care_relationships_pkey PRIMARY KEY (id);


--
-- Name: patient_profiles patient_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_profiles
    ADD CONSTRAINT patient_profiles_pkey PRIMARY KEY (id);


--
-- Name: patient_reviews patient_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_reviews
    ADD CONSTRAINT patient_reviews_pkey PRIMARY KEY (id);


--
-- Name: payment_attempts payment_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts
    ADD CONSTRAINT payment_attempts_pkey PRIMARY KEY (id);


--
-- Name: queue_entries queue_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_entries
    ADD CONSTRAINT queue_entries_pkey PRIMARY KEY (id);


--
-- Name: room_supported_services room_supported_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_supported_services
    ADD CONSTRAINT room_supported_services_pkey PRIMARY KEY (room_id, service_id);


--
-- Name: staff_profiles staff_profiles_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_profiles
    ADD CONSTRAINT staff_profiles_account_id_key UNIQUE (account_id);


--
-- Name: staff_profiles staff_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_profiles
    ADD CONSTRAINT staff_profiles_pkey PRIMARY KEY (id);


--
-- Name: accounts uk_accounts_normalized_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT uk_accounts_normalized_email UNIQUE (normalized_email);


--
-- Name: accounts uk_accounts_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT uk_accounts_username UNIQUE (username);


--
-- Name: appointments uk_appointments_id_patient; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uk_appointments_id_patient UNIQUE (id, patient_profile_id);


--
-- Name: appointments uk_appointments_id_source_visit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uk_appointments_id_source_visit UNIQUE (id, source_visit_id);


--
-- Name: appointments uk_appointments_id_visit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uk_appointments_id_visit UNIQUE (id, visit_id);


--
-- Name: appointments uk_appointments_visit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uk_appointments_visit UNIQUE (visit_id);


--
-- Name: audit_logs uk_audit_logs_dedup_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT uk_audit_logs_dedup_key UNIQUE (dedup_key);


--
-- Name: sessions uk_auth_sessions_refresh_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT uk_auth_sessions_refresh_hash UNIQUE (refresh_token_hash);


--
-- Name: visits uk_clinic_visits_id_patient; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT uk_clinic_visits_id_patient UNIQUE (id, patient_profile_id);


--
-- Name: clinical_service_orders uk_clinical_orders_id_visit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_service_orders
    ADD CONSTRAINT uk_clinical_orders_id_visit UNIQUE (id, visit_id);


--
-- Name: clinical_prescription_items uk_clinical_prescription_line; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_prescription_items
    ADD CONSTRAINT uk_clinical_prescription_line UNIQUE (prescription_id, line_number);


--
-- Name: clinical_prescriptions uk_clinical_prescription_visit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_prescriptions
    ADD CONSTRAINT uk_clinical_prescription_visit UNIQUE (visit_id);


--
-- Name: clinical_ordering_rounds uk_clinical_round_visit_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_ordering_rounds
    ADD CONSTRAINT uk_clinical_round_visit_number UNIQUE (visit_id, round_number);


--
-- Name: doctor_services uk_doctor_service_assignment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_services
    ADD CONSTRAINT uk_doctor_service_assignment UNIQUE (doctor_profile_id, service_id);


--
-- Name: imaging_study_releases uk_imaging_releases_confirmation_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_study_releases
    ADD CONSTRAINT uk_imaging_releases_confirmation_event UNIQUE (confirmation_event_id);


--
-- Name: imaging_study_releases uk_imaging_releases_release_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_study_releases
    ADD CONSTRAINT uk_imaging_releases_release_event UNIQUE (release_event_id);


--
-- Name: imaging_order_executions uk_ioe_id_study; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_order_executions
    ADD CONSTRAINT uk_ioe_id_study UNIQUE (service_order_id, study_ref);


--
-- Name: patient_profiles uk_patient_profiles_account_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_profiles
    ADD CONSTRAINT uk_patient_profiles_account_id UNIQUE (account_id);


--
-- Name: patient_reviews uk_patient_reviews_visit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_reviews
    ADD CONSTRAINT uk_patient_reviews_visit UNIQUE (visit_id);


--
-- Name: diagnostic_reports uq_diagnostic_reports_storage_ref; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT uq_diagnostic_reports_storage_ref UNIQUE (report_storage_ref);


--
-- Name: idempotency_records uq_idempotency_records_scope_owner_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT uq_idempotency_records_scope_owner_key UNIQUE (scope, owner_id, idempotency_key);


--
-- Name: invoice_items uq_invoice_items_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT uq_invoice_items_id UNIQUE (id);


--
-- Name: laboratory_samples uq_lab_sample_attempt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.laboratory_samples
    ADD CONSTRAINT uq_lab_sample_attempt UNIQUE (service_order_id, attempt_number);


--
-- Name: laboratory_samples uq_lab_sample_order_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.laboratory_samples
    ADD CONSTRAINT uq_lab_sample_order_id UNIQUE (service_order_id, id);


--
-- Name: payment_attempts uq_payment_provider_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts
    ADD CONSTRAINT uq_payment_provider_order UNIQUE (provider, provider_order_code);


--
-- Name: payment_attempts uq_payment_provider_transaction; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts
    ADD CONSTRAINT uq_payment_provider_transaction UNIQUE (provider, provider_transaction_reference);


--
-- Name: diagnostic_reports uq_results_report_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT uq_results_report_id UNIQUE (report_id);


--
-- Name: diagnostic_reports uq_results_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT uq_results_version UNIQUE (service_order_id, version_number);


--
-- Name: visit_assignments uq_visit_care_assignments_id_visit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit_assignments
    ADD CONSTRAINT uq_visit_care_assignments_id_visit UNIQUE (id, visit_id);


--
-- Name: verification_codes verification_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_codes
    ADD CONSTRAINT verification_codes_pkey PRIMARY KEY (id);


--
-- Name: visit_assignments visit_care_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit_assignments
    ADD CONSTRAINT visit_care_assignments_pkey PRIMARY KEY (id);


--
-- Name: room_reservations visit_room_reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_reservations
    ADD CONSTRAINT visit_room_reservations_pkey PRIMARY KEY (id);


--
-- Name: rooms visit_rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms
    ADD CONSTRAINT visit_rooms_pkey PRIMARY KEY (id);


--
-- Name: rooms visit_rooms_room_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms
    ADD CONSTRAINT visit_rooms_room_code_key UNIQUE (room_code);


--
-- Name: website_contents website_contents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_contents
    ADD CONSTRAINT website_contents_pkey PRIMARY KEY (id);


--
-- Name: website_images website_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_images
    ADD CONSTRAINT website_images_pkey PRIMARY KEY (id);


--
-- Name: work_shifts work_shifts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT work_shifts_pkey PRIMARY KEY (id);


--
-- Name: ix_accounts_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_status_created ON public.accounts USING btree (status, created_at);


--
-- Name: ix_appointment_arrivals_arrived_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointment_arrivals_arrived_at ON public.appointment_arrivals USING btree (arrived_at, appointment_id) WHERE (arrived_at IS NOT NULL);


--
-- Name: ix_appointment_arrivals_checkin_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointment_arrivals_checkin_actor ON public.appointment_arrivals USING btree (checkin_actor_account_id) WHERE (checkin_actor_account_id IS NOT NULL);


--
-- Name: ix_appointment_arrivals_recorded_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointment_arrivals_recorded_by ON public.appointment_arrivals USING btree (arrival_recorded_by) WHERE (arrival_recorded_by IS NOT NULL);


--
-- Name: ix_appointments_awaiting_deadline; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointments_awaiting_deadline ON public.appointments USING btree (payment_deadline, id) WHERE ((status)::text = 'AWAITING_PAYMENT'::text);


--
-- Name: ix_appointments_doctor_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointments_doctor_start ON public.appointments USING btree (doctor_profile_id, start_time, id);


--
-- Name: ix_appointments_patient_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointments_patient_start ON public.appointments USING btree (patient_profile_id, start_time DESC, id);


--
-- Name: ix_appointments_source_appointment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointments_source_appointment ON public.appointments USING btree (source_appointment_id) WHERE (source_appointment_id IS NOT NULL);


--
-- Name: ix_appointments_source_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointments_source_visit ON public.appointments USING btree (source_visit_id) WHERE (source_visit_id IS NOT NULL);


--
-- Name: ix_appointments_status_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_appointments_status_start ON public.appointments USING btree (status, start_time, id);


--
-- Name: ix_audit_logs_activity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_activity ON public.audit_logs USING btree (visible_in_activity_log, occurred_at DESC);


--
-- Name: ix_audit_logs_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_actor ON public.audit_logs USING btree (actor_account_id);


--
-- Name: ix_audit_logs_rate_limit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_rate_limit ON public.audit_logs USING btree (source, action, outcome, identifier_hmac, occurred_at);


--
-- Name: ix_audit_logs_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_logs_record ON public.audit_logs USING btree (record_type, entity_id);


--
-- Name: ix_auth_sessions_account_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_account_active ON public.sessions USING btree (account_id, created_at, id) WHERE (revoked_at IS NULL);


--
-- Name: ix_auth_sessions_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_expires_at ON public.sessions USING btree (expires_at);


--
-- Name: ix_auth_sessions_revoked_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_revoked_at ON public.sessions USING btree (revoked_at) WHERE (revoked_at IS NOT NULL);


--
-- Name: ix_auth_sessions_token_family; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_token_family ON public.sessions USING btree (token_family_id);


--
-- Name: ix_care_counter_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_care_counter_pending ON public.patient_caregivers USING btree (requested_at, id) WHERE (((state)::text = 'REQUESTED'::text) AND ((pending_reason)::text = 'COUNTER'::text));


--
-- Name: ix_clinic_services_active_online; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinic_services_active_online ON public.clinic_services USING btree (active, online_bookable, name);


--
-- Name: ix_clinic_visits_patient_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinic_visits_patient_created ON public.visits USING btree (patient_profile_id, created_at DESC, id);


--
-- Name: ix_clinical_documents_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinical_documents_patient ON public.clinical_medical_documents USING btree (patient_profile_id, created_at DESC, id DESC);


--
-- Name: ix_clinical_documents_released; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinical_documents_released ON public.clinical_medical_documents USING btree (visit_id, created_at DESC, id DESC) WHERE (released_at IS NOT NULL);


--
-- Name: ix_clinical_documents_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinical_documents_visit ON public.clinical_medical_documents USING btree (visit_id, created_at DESC, id DESC);


--
-- Name: ix_clinical_orders_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinical_orders_room ON public.clinical_service_orders USING btree (room_id) WHERE (room_id IS NOT NULL);


--
-- Name: ix_clinical_orders_round; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinical_orders_round ON public.clinical_service_orders USING btree (ordering_round_id);


--
-- Name: ix_clinical_orders_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinical_orders_visit ON public.clinical_service_orders USING btree (visit_id);


--
-- Name: ix_clinical_prescription_issued_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinical_prescription_issued_patient ON public.clinical_prescriptions USING btree (patient_profile_id, issued_at DESC, id DESC) WHERE ((status)::text = 'ISSUED'::text);


--
-- Name: ix_clinical_rounds_visit_latest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_clinical_rounds_visit_latest ON public.clinical_ordering_rounds USING btree (visit_id, round_number DESC);


--
-- Name: ix_doctor_service_active_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_doctor_service_active_service ON public.doctor_services USING btree (service_id, doctor_profile_id) WHERE active;


--
-- Name: ix_idempotency_records_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_idempotency_records_resource ON public.idempotency_records USING btree (resource_id);


--
-- Name: ix_imaging_grant_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_grant_active ON public.imaging_viewer_grants USING btree (expires_at, id) WHERE (revoked_at IS NULL);


--
-- Name: ix_imaging_grant_owner_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_grant_owner_expiry ON public.imaging_viewer_grants USING btree (study_ref, actor_account_id, expires_at);


--
-- Name: ix_imaging_recon_classification_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_recon_classification_queue ON public.imaging_reconciliation_cases USING btree (classification, state, updated_at, id);


--
-- Name: ix_imaging_recon_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_recon_queue ON public.imaging_reconciliation_cases USING btree (state, updated_at, id);


--
-- Name: ix_imaging_releases_confirm_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_releases_confirm_due ON public.imaging_study_releases USING btree (next_confirmation_attempt_at) WHERE (next_confirmation_attempt_at IS NOT NULL);


--
-- Name: ix_imaging_studies_dicom_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_studies_dicom_index ON public.imaging_studies USING gin (dicom_index jsonb_path_ops);


--
-- Name: ix_imaging_studies_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_studies_owner ON public.imaging_studies USING btree (service_order_id, attempt_id, created_at DESC);


--
-- Name: ix_imaging_studies_reconciliation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_studies_reconciliation ON public.imaging_studies USING btree (updated_at, id) WHERE ((state)::text = 'RECONCILIATION_REQUIRED'::text);


--
-- Name: ix_imaging_studies_service_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_studies_service_order ON public.imaging_studies USING btree (service_order_id);


--
-- Name: ix_imaging_studies_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_imaging_studies_visit ON public.imaging_studies USING btree (visit_id);


--
-- Name: ix_invoice_items_invoice_payment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_invoice_items_invoice_payment ON public.invoice_items USING btree (invoice_id, payment_attempt_id);


--
-- Name: ix_invoice_items_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_invoice_items_service ON public.invoice_items USING btree (service_id) WHERE (service_id IS NOT NULL);


--
-- Name: ix_invoices_patient_issued; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_invoices_patient_issued ON public.invoices USING btree (patient_profile_id, issued_at DESC, id);


--
-- Name: ix_invoices_status_issued; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_invoices_status_issued ON public.invoices USING btree (status, issued_at DESC, id);


--
-- Name: ix_ioe_pending_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ioe_pending_created ON public.imaging_order_executions USING btree (created_at, service_order_id) WHERE ((execution_status)::text = 'PENDING'::text);


--
-- Name: ix_ioe_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ioe_status ON public.imaging_order_executions USING btree (execution_status);


--
-- Name: ix_notifications_recipient_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notifications_recipient_created ON public.notifications USING btree (recipient_account_id, created_at DESC, id DESC);


--
-- Name: ix_notifications_recipient_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notifications_recipient_unread ON public.notifications USING btree (recipient_account_id, created_at DESC, id DESC) WHERE (read_at IS NULL);


--
-- Name: ix_patient_care_relationship_dependent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_patient_care_relationship_dependent ON public.patient_caregivers USING btree (dependent_patient_profile_id);


--
-- Name: ix_patient_profiles_medical_history_updated_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_patient_profiles_medical_history_updated_by ON public.patient_profiles USING btree (medical_history_updated_by);


--
-- Name: ix_patient_profiles_national_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_patient_profiles_national_id ON public.patient_profiles USING btree (national_id);


--
-- Name: ix_patient_profiles_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_patient_profiles_phone ON public.patient_profiles USING btree (phone_canonical);


--
-- Name: ix_patient_reviews_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_patient_reviews_author ON public.patient_reviews USING btree (author_account_id);


--
-- Name: ix_patient_reviews_patient_submitted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_patient_reviews_patient_submitted ON public.patient_reviews USING btree (patient_profile_id, submitted_at DESC, id);


--
-- Name: ix_patient_reviews_replier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_patient_reviews_replier ON public.patient_reviews USING btree (replied_by_account_id) WHERE (replied_by_account_id IS NOT NULL);


--
-- Name: ix_patient_reviews_status_submitted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_patient_reviews_status_submitted ON public.patient_reviews USING btree (status, submitted_at DESC, id);


--
-- Name: ix_payment_attempts_invoice_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_payment_attempts_invoice_state ON public.payment_attempts USING btree (invoice_id, status, created_at DESC, id);


--
-- Name: ix_payment_attempts_reconcile; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_payment_attempts_reconcile ON public.payment_attempts USING btree (status, created_at, id) WHERE ((status)::text = 'RECONCILIATION_REQUIRED'::text);


--
-- Name: ix_queue_entries_doctor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_queue_entries_doctor ON public.queue_entries USING btree (doctor_profile_id) WHERE (doctor_profile_id IS NOT NULL);


--
-- Name: ix_queue_entries_exam_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_queue_entries_exam_date ON public.queue_entries USING btree (clinic_date, doctor_profile_id) WHERE ((stage)::text = 'EXAM'::text);


--
-- Name: ix_queue_entries_reception_fifo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_queue_entries_reception_fifo ON public.queue_entries USING btree (arrived_at, id) WHERE (((stage)::text = 'RECEPTION'::text) AND ((status)::text = 'WAITING'::text));


--
-- Name: ix_queue_entries_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_queue_entries_room ON public.queue_entries USING btree (room_id) WHERE (room_id IS NOT NULL);


--
-- Name: ix_queue_entries_room_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_queue_entries_room_order ON public.queue_entries USING btree (stage, room_id, status, sort_order);


--
-- Name: ix_queue_entries_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_queue_entries_service ON public.queue_entries USING btree (service_id) WHERE (service_id IS NOT NULL);


--
-- Name: ix_queue_entries_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_queue_entries_visit ON public.queue_entries USING btree (visit_id, stage);


--
-- Name: ix_results_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_results_visit ON public.diagnostic_reports USING btree (visit_id);


--
-- Name: ix_room_supported_services_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_room_supported_services_service ON public.room_supported_services USING btree (service_id, room_id);


--
-- Name: ix_rooms_capabilities; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_rooms_capabilities ON public.rooms USING gin (capabilities);


--
-- Name: ix_staff_profiles_public_directory; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_staff_profiles_public_directory ON public.staff_profiles USING btree (display_name, id) WHERE (is_doctor AND publicly_visible AND in_active_directory);


--
-- Name: ix_staff_profiles_status_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_staff_profiles_status_name ON public.staff_profiles USING btree (status, display_name, id);


--
-- Name: ix_verification_codes_account_purpose_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_verification_codes_account_purpose_created ON public.verification_codes USING btree (account_id, purpose, created_at DESC);


--
-- Name: ix_verification_codes_link; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_verification_codes_link ON public.verification_codes USING btree (link_id) WHERE (link_id IS NOT NULL);


--
-- Name: ix_verification_codes_payload_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_verification_codes_payload_expiry ON public.verification_codes USING btree (expires_at) WHERE (payload IS NOT NULL);


--
-- Name: ix_verification_codes_purpose_account_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_verification_codes_purpose_account_created ON public.verification_codes USING btree (purpose, account_id, created_at);


--
-- Name: ix_verification_codes_purpose_link_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_verification_codes_purpose_link_created ON public.verification_codes USING btree (purpose, link_id, created_at);


--
-- Name: ix_visit_care_assignments_doctor_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_visit_care_assignments_doctor_active ON public.visit_assignments USING btree (doctor_profile_id, starts_at, ends_at) WHERE ((status)::text = ANY ((ARRAY['RESERVED'::character varying, 'IN_PROGRESS'::character varying])::text[]));


--
-- Name: ix_visit_care_assignments_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_visit_care_assignments_visit ON public.visit_assignments USING btree (visit_id, assigned_at, id);


--
-- Name: ix_visit_room_reservations_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_visit_room_reservations_assignment ON public.room_reservations USING btree (assignment_id, status, starts_at);


--
-- Name: ix_visit_room_reservations_room_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_visit_room_reservations_room_time ON public.room_reservations USING btree (room_id, starts_at, reserved_until);


--
-- Name: ix_visits_open_checked_in; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_visits_open_checked_in ON public.visits USING btree (checked_in_at) WHERE ((status)::text = 'OPEN'::text);


--
-- Name: ix_website_contents_announcement_public; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_website_contents_announcement_public ON public.website_contents USING btree (published_at DESC, id) WHERE (((type)::text = 'ANNOUNCEMENT'::text) AND ((audience)::text = 'PUBLIC'::text));


--
-- Name: ix_website_contents_public_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_website_contents_public_type ON public.website_contents USING btree (type, updated_at DESC, id) WHERE ((status)::text = 'PUBLISHED'::text);


--
-- Name: ix_work_shifts_room_interval; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_work_shifts_room_interval ON public.work_shifts USING btree (room_id, starts_at, ends_at) WHERE (room_id IS NOT NULL);


--
-- Name: uk_clinic_visits_originating_appointment; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_clinic_visits_originating_appointment ON public.visits USING btree (originating_appointment_id) WHERE (originating_appointment_id IS NOT NULL);


--
-- Name: uk_clinical_primary_diagnosis; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_clinical_primary_diagnosis ON public.clinical_examination_diagnoses USING btree (examination_id) WHERE ((type)::text = 'PRIMARY'::text);


--
-- Name: uk_imaging_recon_open_source; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_imaging_recon_open_source ON public.imaging_reconciliation_cases USING btree (COALESCE((operation_id)::text, (event_id)::text), category) WHERE ((state)::text <> 'RESOLVED'::text);


--
-- Name: uk_invoice_items_service_order; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_invoice_items_service_order ON public.invoice_items USING btree (service_order_id) WHERE (service_order_id IS NOT NULL);


--
-- Name: uk_invoices_appointment_source; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_invoices_appointment_source ON public.invoices USING btree (appointment_id) WHERE ((source_type)::text = 'APPOINTMENT'::text);


--
-- Name: uk_invoices_clinical_round_source; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_invoices_clinical_round_source ON public.invoices USING btree (clinical_ordering_round_id) WHERE ((source_type)::text = 'CLINICAL_ROUND'::text);


--
-- Name: uk_invoices_visit_source; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_invoices_visit_source ON public.invoices USING btree (visit_id) WHERE ((source_type)::text = 'VISIT'::text);


--
-- Name: uk_ioe_attempt_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_ioe_attempt_id ON public.imaging_order_executions USING btree (attempt_id) WHERE (attempt_id IS NOT NULL);


--
-- Name: uk_ioe_first_use_event; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_ioe_first_use_event ON public.imaging_order_executions USING btree (first_use_event_id) WHERE (first_use_event_id IS NOT NULL);


--
-- Name: uk_ioe_study_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_ioe_study_ref ON public.imaging_order_executions USING btree (study_ref) WHERE (study_ref IS NOT NULL);


--
-- Name: uk_patient_care_relationship_live; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_patient_care_relationship_live ON public.patient_caregivers USING btree (requester_account_id, dependent_patient_profile_id) WHERE ((state)::text = ANY ((ARRAY['REQUESTED'::character varying, 'CONFIRMED'::character varying])::text[]));


--
-- Name: uk_payment_attempt_active_online; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_payment_attempt_active_online ON public.payment_attempts USING btree (invoice_id) WHERE (((channel)::text = 'ONLINE'::text) AND ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RECONCILIATION_REQUIRED'::character varying])::text[])));


--
-- Name: uk_verification_codes_account_current; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_verification_codes_account_current ON public.verification_codes USING btree (account_id, purpose) WHERE ((consumed_at IS NULL) AND (superseded_at IS NULL) AND ((purpose)::text <> 'CARE_LINK'::text));


--
-- Name: uk_verification_codes_link_current; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_verification_codes_link_current ON public.verification_codes USING btree (link_id, purpose) WHERE (((purpose)::text = 'CARE_LINK'::text) AND (consumed_at IS NULL) AND (superseded_at IS NULL));


--
-- Name: uk_verification_codes_password_reset_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_verification_codes_password_reset_hash ON public.verification_codes USING btree (code_hash) WHERE ((purpose)::text = 'PASSWORD_RESET'::text);


--
-- Name: uq_clinical_active_result_sheet_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_clinical_active_result_sheet_visit ON public.clinical_medical_documents USING btree (visit_id) WHERE (((document_type)::text = 'CLINIC_EXAMINATION_REPORT'::text) AND ((status)::text = 'ACTIVE'::text));


--
-- Name: uq_notifications_dedupe_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notifications_dedupe_key ON public.notifications USING btree (dedupe_key) WHERE (dedupe_key IS NOT NULL);


--
-- Name: uq_queue_entries_exam_number; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_queue_entries_exam_number ON public.queue_entries USING btree (doctor_profile_id, clinic_date, queue_number) WHERE (((stage)::text = 'EXAM'::text) AND (queue_number IS NOT NULL));


--
-- Name: uq_queue_entries_exam_one_in_exam; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_queue_entries_exam_one_in_exam ON public.queue_entries USING btree (doctor_profile_id) WHERE (((stage)::text = 'EXAM'::text) AND ((status)::text = 'IN_EXAMINATION'::text));


--
-- Name: uq_queue_entries_exam_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_queue_entries_exam_visit ON public.queue_entries USING btree (visit_id) WHERE ((stage)::text = 'EXAM'::text);


--
-- Name: uq_queue_entries_reception_active_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_queue_entries_reception_active_visit ON public.queue_entries USING btree (visit_id) WHERE (((stage)::text = 'RECEPTION'::text) AND ((status)::text = 'WAITING'::text));


--
-- Name: appointments ct_appointments_visit_link; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER ct_appointments_visit_link AFTER INSERT OR UPDATE ON public.appointments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.enforce_appointment_visit_link();


--
-- Name: visits ct_clinic_visits_appointment_link; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER ct_clinic_visits_appointment_link AFTER INSERT OR UPDATE ON public.visits DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.enforce_appointment_visit_link();


--
-- Name: patient_caregivers protect_patient_care_relationship_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER protect_patient_care_relationship_history BEFORE DELETE OR UPDATE ON public.patient_caregivers FOR EACH ROW EXECUTE FUNCTION public.protect_patient_care_relationship_history();


--
-- Name: patient_caregivers protect_patient_caregiver_r3_metadata; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER protect_patient_caregiver_r3_metadata BEFORE INSERT OR UPDATE ON public.patient_caregivers FOR EACH ROW EXECUTE FUNCTION public.protect_patient_caregiver_r3_metadata();


--
-- Name: appointments tr_appointments_occupied_until; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tr_appointments_occupied_until BEFORE INSERT OR UPDATE OF end_time, buffer_minutes, occupied_until ON public.appointments FOR EACH ROW EXECUTE FUNCTION public.set_appointment_occupied_until();


--
-- Name: audit_logs tr_audit_logs_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tr_audit_logs_immutable BEFORE DELETE OR UPDATE ON public.audit_logs FOR EACH ROW EXECUTE FUNCTION public.reject_audit_log_mutation();


--
-- Name: audit_logs tr_audit_logs_no_truncate; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tr_audit_logs_no_truncate BEFORE TRUNCATE ON public.audit_logs FOR EACH STATEMENT EXECUTE FUNCTION public.reject_audit_log_mutation();


--
-- Name: diagnostic_reports tr_diagnostic_reports_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tr_diagnostic_reports_immutable BEFORE DELETE OR UPDATE ON public.diagnostic_reports FOR EACH ROW EXECUTE FUNCTION public.prevent_result_mutation();


--
-- Name: queue_entries tr_queue_entries_default_sort_order; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tr_queue_entries_default_sort_order BEFORE INSERT ON public.queue_entries FOR EACH ROW EXECUTE FUNCTION public.queue_entries_default_sort_order();


--
-- Name: queue_entries tr_queue_entries_reception_lifecycle; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tr_queue_entries_reception_lifecycle BEFORE UPDATE ON public.queue_entries FOR EACH ROW EXECUTE FUNCTION public.protect_walk_in_reception_queue_entry_lifecycle();


--
-- Name: queue_entries tr_queue_entries_reception_visit_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tr_queue_entries_reception_visit_guard BEFORE INSERT OR UPDATE OF visit_id, arrived_at, status, entered_at, left_at, exit_reason ON public.queue_entries FOR EACH ROW EXECUTE FUNCTION public.enforce_walk_in_reception_queue_visit();


--
-- Name: visits tr_visits_protect_reception_queue_identity; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tr_visits_protect_reception_queue_identity BEFORE UPDATE OF source, arrived_at ON public.visits FOR EACH ROW EXECUTE FUNCTION public.protect_queued_visit_source_and_arrival();


--
-- Name: appointment_arrivals appointment_arrivals_appointment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_arrivals
    ADD CONSTRAINT appointment_arrivals_appointment_id_fkey FOREIGN KEY (appointment_id) REFERENCES public.appointments(id) ON DELETE CASCADE;


--
-- Name: appointment_arrivals appointment_arrivals_arrival_recorded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_arrivals
    ADD CONSTRAINT appointment_arrivals_arrival_recorded_by_fkey FOREIGN KEY (arrival_recorded_by) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: appointment_arrivals appointment_arrivals_checkin_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_arrivals
    ADD CONSTRAINT appointment_arrivals_checkin_actor_account_id_fkey FOREIGN KEY (checkin_actor_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: appointments appointments_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: appointments appointments_doctor_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_doctor_profile_id_fkey FOREIGN KEY (doctor_profile_id) REFERENCES public.staff_profiles(id) ON DELETE RESTRICT;


--
-- Name: appointments appointments_ordering_doctor_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_ordering_doctor_profile_id_fkey FOREIGN KEY (ordering_doctor_profile_id) REFERENCES public.staff_profiles(id) ON DELETE RESTRICT;


--
-- Name: appointments appointments_patient_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_patient_profile_id_fkey FOREIGN KEY (patient_profile_id) REFERENCES public.patient_profiles(id) ON DELETE RESTRICT;


--
-- Name: appointments appointments_service_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_service_id_fkey FOREIGN KEY (service_id) REFERENCES public.clinic_services(id) ON DELETE RESTRICT;


--
-- Name: sessions auth_sessions_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT auth_sessions_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id);


--
-- Name: visits clinic_visits_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT clinic_visits_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: visits clinic_visits_patient_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT clinic_visits_patient_profile_id_fkey FOREIGN KEY (patient_profile_id) REFERENCES public.patient_profiles(id) ON DELETE RESTRICT;


--
-- Name: clinical_examination_diagnoses clinical_examination_diagnoses_examination_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_examination_diagnoses
    ADD CONSTRAINT clinical_examination_diagnoses_examination_id_fkey FOREIGN KEY (examination_id) REFERENCES public.clinical_examinations(id) ON DELETE CASCADE;


--
-- Name: clinical_medical_document_payloads clinical_medical_document_payloads_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_medical_document_payloads
    ADD CONSTRAINT clinical_medical_document_payloads_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.clinical_medical_documents(id) ON DELETE RESTRICT;


--
-- Name: clinical_ordering_rounds clinical_ordering_rounds_visit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_ordering_rounds
    ADD CONSTRAINT clinical_ordering_rounds_visit_id_fkey FOREIGN KEY (visit_id) REFERENCES public.visits(id) ON DELETE RESTRICT;


--
-- Name: clinical_prescription_items clinical_prescription_items_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_prescription_items
    ADD CONSTRAINT clinical_prescription_items_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.clinical_prescriptions(id) ON DELETE RESTRICT;


--
-- Name: clinical_service_orders clinical_service_orders_doctor_result_assessed_by_account__fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_service_orders
    ADD CONSTRAINT clinical_service_orders_doctor_result_assessed_by_account__fkey FOREIGN KEY (doctor_result_assessed_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: diagnostic_reports diagnostic_reports_lab_confirmed_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT diagnostic_reports_lab_confirmed_by_account_id_fkey FOREIGN KEY (lab_confirmed_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: diagnostic_reports diagnostic_reports_lab_recorded_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT diagnostic_reports_lab_recorded_by_account_id_fkey FOREIGN KEY (lab_recorded_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: doctor_services doctor_service_assignments_service_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_services
    ADD CONSTRAINT doctor_service_assignments_service_id_fkey FOREIGN KEY (service_id) REFERENCES public.clinic_services(id) ON DELETE RESTRICT;


--
-- Name: appointments fk_appointments_doctor_service_assignment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk_appointments_doctor_service_assignment FOREIGN KEY (doctor_profile_id, service_id) REFERENCES public.doctor_services(doctor_profile_id, service_id) ON DELETE RESTRICT;


--
-- Name: appointments fk_appointments_source_appointment_patient; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk_appointments_source_appointment_patient FOREIGN KEY (source_appointment_id, patient_profile_id) REFERENCES public.appointments(id, patient_profile_id) ON DELETE RESTRICT;


--
-- Name: appointments fk_appointments_source_visit_patient; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk_appointments_source_visit_patient FOREIGN KEY (source_visit_id, patient_profile_id) REFERENCES public.visits(id, patient_profile_id) ON DELETE RESTRICT;


--
-- Name: appointments fk_appointments_visit_patient; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk_appointments_visit_patient FOREIGN KEY (visit_id, patient_profile_id) REFERENCES public.visits(id, patient_profile_id) ON DELETE RESTRICT;


--
-- Name: visits fk_clinic_visits_originating_appointment_patient; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT fk_clinic_visits_originating_appointment_patient FOREIGN KEY (originating_appointment_id, patient_profile_id) REFERENCES public.appointments(id, patient_profile_id) ON DELETE RESTRICT;


--
-- Name: visits fk_clinic_visits_prior_appointment_patient; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT fk_clinic_visits_prior_appointment_patient FOREIGN KEY (prior_appointment_id, patient_profile_id) REFERENCES public.appointments(id, patient_profile_id) ON DELETE RESTRICT;


--
-- Name: clinical_service_orders fk_clinical_orders_round; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_service_orders
    ADD CONSTRAINT fk_clinical_orders_round FOREIGN KEY (ordering_round_id) REFERENCES public.clinical_ordering_rounds(id) ON DELETE RESTRICT;


--
-- Name: doctor_services fk_doctor_service_assignments_doctor_profile; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_services
    ADD CONSTRAINT fk_doctor_service_assignments_doctor_profile FOREIGN KEY (doctor_profile_id) REFERENCES public.staff_profiles(id) ON DELETE RESTRICT;


--
-- Name: imaging_studies fk_imaging_studies_service_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_studies
    ADD CONSTRAINT fk_imaging_studies_service_order FOREIGN KEY (service_order_id) REFERENCES public.clinical_service_orders(id) ON DELETE RESTRICT;


--
-- Name: imaging_studies fk_imaging_studies_visit; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_studies
    ADD CONSTRAINT fk_imaging_studies_visit FOREIGN KEY (visit_id) REFERENCES public.visits(id) ON DELETE RESTRICT;


--
-- Name: patient_profiles fk_patient_profiles_medical_history_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_profiles
    ADD CONSTRAINT fk_patient_profiles_medical_history_updated_by FOREIGN KEY (medical_history_updated_by) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: queue_entries fk_queue_entries_doctor_profile; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_entries
    ADD CONSTRAINT fk_queue_entries_doctor_profile FOREIGN KEY (doctor_profile_id) REFERENCES public.staff_profiles(id) ON DELETE RESTRICT;


--
-- Name: queue_entries fk_queue_entries_room; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_entries
    ADD CONSTRAINT fk_queue_entries_room FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE RESTRICT;


--
-- Name: queue_entries fk_queue_entries_service; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_entries
    ADD CONSTRAINT fk_queue_entries_service FOREIGN KEY (service_id) REFERENCES public.clinic_services(id) ON DELETE RESTRICT;


--
-- Name: queue_entries fk_queue_entries_visit; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_entries
    ADD CONSTRAINT fk_queue_entries_visit FOREIGN KEY (visit_id) REFERENCES public.visits(id) ON DELETE RESTRICT;


--
-- Name: diagnostic_reports fk_result_lab_sample; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT fk_result_lab_sample FOREIGN KEY (service_order_id, lab_sample_id) REFERENCES public.laboratory_samples(service_order_id, id) ON DELETE RESTRICT;


--
-- Name: diagnostic_reports fk_results_order_study; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT fk_results_order_study FOREIGN KEY (service_order_id, study_ref) REFERENCES public.imaging_order_executions(service_order_id, study_ref) ON DELETE RESTRICT;


--
-- Name: diagnostic_reports fk_results_order_visit; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_reports
    ADD CONSTRAINT fk_results_order_visit FOREIGN KEY (service_order_id, visit_id) REFERENCES public.clinical_service_orders(id, visit_id) ON DELETE RESTRICT;


--
-- Name: verification_codes fk_verification_codes_account; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_codes
    ADD CONSTRAINT fk_verification_codes_account FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: verification_codes fk_verification_codes_link; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_codes
    ADD CONSTRAINT fk_verification_codes_link FOREIGN KEY (link_id) REFERENCES public.patient_caregivers(id);


--
-- Name: room_reservations fk_visit_room_reservation_assignment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_reservations
    ADD CONSTRAINT fk_visit_room_reservation_assignment FOREIGN KEY (assignment_id, visit_id) REFERENCES public.visit_assignments(id, visit_id) ON DELETE RESTRICT;


--
-- Name: imaging_order_executions imaging_order_executions_service_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_order_executions
    ADD CONSTRAINT imaging_order_executions_service_order_id_fkey FOREIGN KEY (service_order_id) REFERENCES public.clinical_service_orders(id) ON DELETE CASCADE;


--
-- Name: imaging_reconciliation_cases imaging_reconciliation_cases_operation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_reconciliation_cases
    ADD CONSTRAINT imaging_reconciliation_cases_operation_id_fkey FOREIGN KEY (operation_id) REFERENCES public.imaging_studies(id) ON DELETE RESTRICT;


--
-- Name: imaging_study_releases imaging_study_releases_imaging_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_study_releases
    ADD CONSTRAINT imaging_study_releases_imaging_study_id_fkey FOREIGN KEY (imaging_study_id) REFERENCES public.imaging_studies(id) ON DELETE CASCADE;


--
-- Name: imaging_viewer_grants imaging_viewer_grants_study_ref_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imaging_viewer_grants
    ADD CONSTRAINT imaging_viewer_grants_study_ref_fkey FOREIGN KEY (study_ref) REFERENCES public.imaging_studies(study_ref) ON DELETE RESTRICT;


--
-- Name: invoice_items invoice_items_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT invoice_items_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(id) ON DELETE RESTRICT;


--
-- Name: invoice_items invoice_items_payment_attempt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT invoice_items_payment_attempt_id_fkey FOREIGN KEY (payment_attempt_id) REFERENCES public.payment_attempts(id);


--
-- Name: invoice_items invoice_items_refunded_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT invoice_items_refunded_by_account_id_fkey FOREIGN KEY (refunded_by_account_id) REFERENCES public.accounts(id);


--
-- Name: invoice_items invoice_items_service_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT invoice_items_service_id_fkey FOREIGN KEY (service_id) REFERENCES public.clinic_services(id) ON DELETE RESTRICT;


--
-- Name: invoice_items invoice_items_service_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT invoice_items_service_order_id_fkey FOREIGN KEY (service_order_id) REFERENCES public.clinical_service_orders(id) ON DELETE RESTRICT;


--
-- Name: invoices invoices_appointment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_appointment_id_fkey FOREIGN KEY (appointment_id) REFERENCES public.appointments(id) ON DELETE RESTRICT;


--
-- Name: invoices invoices_clinical_ordering_round_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_clinical_ordering_round_id_fkey FOREIGN KEY (clinical_ordering_round_id) REFERENCES public.clinical_ordering_rounds(id) ON DELETE RESTRICT;


--
-- Name: invoices invoices_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: invoices invoices_patient_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_patient_profile_id_fkey FOREIGN KEY (patient_profile_id) REFERENCES public.patient_profiles(id) ON DELETE RESTRICT;


--
-- Name: invoices invoices_visit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_visit_id_fkey FOREIGN KEY (visit_id) REFERENCES public.visits(id) ON DELETE RESTRICT;


--
-- Name: laboratory_samples laboratory_samples_collected_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.laboratory_samples
    ADD CONSTRAINT laboratory_samples_collected_by_account_id_fkey FOREIGN KEY (collected_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: laboratory_samples laboratory_samples_rejected_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.laboratory_samples
    ADD CONSTRAINT laboratory_samples_rejected_by_account_id_fkey FOREIGN KEY (rejected_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: laboratory_samples laboratory_samples_service_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.laboratory_samples
    ADD CONSTRAINT laboratory_samples_service_order_id_fkey FOREIGN KEY (service_order_id) REFERENCES public.clinical_service_orders(id) ON DELETE RESTRICT;


--
-- Name: laboratory_samples laboratory_samples_verified_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.laboratory_samples
    ADD CONSTRAINT laboratory_samples_verified_by_account_id_fkey FOREIGN KEY (verified_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: notifications notifications_recipient_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_recipient_account_id_fkey FOREIGN KEY (recipient_account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: patient_caregivers patient_care_relationships_confirming_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_caregivers
    ADD CONSTRAINT patient_care_relationships_confirming_account_id_fkey FOREIGN KEY (confirming_account_id) REFERENCES public.accounts(id);


--
-- Name: patient_caregivers patient_care_relationships_dependent_patient_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_caregivers
    ADD CONSTRAINT patient_care_relationships_dependent_patient_profile_id_fkey FOREIGN KEY (dependent_patient_profile_id) REFERENCES public.patient_profiles(id);


--
-- Name: patient_caregivers patient_care_relationships_requester_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_caregivers
    ADD CONSTRAINT patient_care_relationships_requester_account_id_fkey FOREIGN KEY (requester_account_id) REFERENCES public.accounts(id);


--
-- Name: patient_caregivers patient_care_relationships_revoked_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_caregivers
    ADD CONSTRAINT patient_care_relationships_revoked_by_account_id_fkey FOREIGN KEY (revoked_by_account_id) REFERENCES public.accounts(id);


--
-- Name: patient_caregivers patient_caregivers_verified_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_caregivers
    ADD CONSTRAINT patient_caregivers_verified_by_account_id_fkey FOREIGN KEY (verified_by_account_id) REFERENCES public.accounts(id);


--
-- Name: patient_profiles patient_profiles_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_profiles
    ADD CONSTRAINT patient_profiles_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id);


--
-- Name: patient_reviews patient_reviews_author_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_reviews
    ADD CONSTRAINT patient_reviews_author_account_id_fkey FOREIGN KEY (author_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: patient_reviews patient_reviews_patient_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_reviews
    ADD CONSTRAINT patient_reviews_patient_profile_id_fkey FOREIGN KEY (patient_profile_id) REFERENCES public.patient_profiles(id) ON DELETE RESTRICT;


--
-- Name: patient_reviews patient_reviews_replied_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_reviews
    ADD CONSTRAINT patient_reviews_replied_by_account_id_fkey FOREIGN KEY (replied_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: patient_reviews patient_reviews_visit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_reviews
    ADD CONSTRAINT patient_reviews_visit_id_fkey FOREIGN KEY (visit_id) REFERENCES public.visits(id) ON DELETE RESTRICT;


--
-- Name: payment_attempts payment_attempts_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts
    ADD CONSTRAINT payment_attempts_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: payment_attempts payment_attempts_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts
    ADD CONSTRAINT payment_attempts_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(id) ON DELETE RESTRICT;


--
-- Name: queue_entries queue_entries_service_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_entries
    ADD CONSTRAINT queue_entries_service_order_id_fkey FOREIGN KEY (service_order_id) REFERENCES public.clinical_service_orders(id);


--
-- Name: room_supported_services room_supported_services_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_supported_services
    ADD CONSTRAINT room_supported_services_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE RESTRICT;


--
-- Name: room_supported_services room_supported_services_service_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_supported_services
    ADD CONSTRAINT room_supported_services_service_id_fkey FOREIGN KEY (service_id) REFERENCES public.clinic_services(id) ON DELETE RESTRICT;


--
-- Name: staff_profiles staff_profiles_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_profiles
    ADD CONSTRAINT staff_profiles_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: visit_assignments visit_care_assignments_doctor_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit_assignments
    ADD CONSTRAINT visit_care_assignments_doctor_profile_id_fkey FOREIGN KEY (doctor_profile_id) REFERENCES public.staff_profiles(id) ON DELETE RESTRICT;


--
-- Name: visit_assignments visit_care_assignments_visit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit_assignments
    ADD CONSTRAINT visit_care_assignments_visit_id_fkey FOREIGN KEY (visit_id) REFERENCES public.visits(id) ON DELETE RESTRICT;


--
-- Name: room_reservations visit_room_reservations_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_reservations
    ADD CONSTRAINT visit_room_reservations_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE RESTRICT;


--
-- Name: website_contents website_contents_cover_image_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_contents
    ADD CONSTRAINT website_contents_cover_image_id_fkey FOREIGN KEY (cover_image_id) REFERENCES public.website_images(id) ON DELETE RESTRICT;


--
-- Name: website_contents website_contents_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_contents
    ADD CONSTRAINT website_contents_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: website_contents website_contents_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_contents
    ADD CONSTRAINT website_contents_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.accounts(id) ON DELETE RESTRICT;


--
-- Name: work_shifts work_shifts_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT work_shifts_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE RESTRICT;


--
-- Name: work_shifts work_shifts_staff_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_shifts
    ADD CONSTRAINT work_shifts_staff_profile_id_fkey FOREIGN KEY (staff_profile_id) REFERENCES public.staff_profiles(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict qAcxlxA6NAjwBUeUbVbbXK3cmjIvGe5vWBHQuMbsY9vzAkITTRBECfiiTFQ9RbB

