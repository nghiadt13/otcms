-- Online booking holds the slot while the patient pays (see plans/261108-2017-booking-payment-hold-awaiting-payment).
--   AWAITING_PAYMENT : technical, short-lived hold (payment_deadline) - occupies the doctor slot.
--   PAYMENT_EXPIRED  : technical terminal state of a released hold - frees the slot, hidden from every list/report.
-- NOTE: appointments.status and appointment_audits.old_status/new_status are VARCHAR(16);
-- 'AWAITING_PAYMENT' is exactly 16 characters. Do not add a longer status without widening those columns first.

ALTER TABLE appointments ADD COLUMN payment_deadline TIMESTAMPTZ;

ALTER TABLE appointments DROP CONSTRAINT ck_appointments_status;
ALTER TABLE appointments ADD CONSTRAINT ck_appointments_status CHECK (status IN (
    'AWAITING_PAYMENT', 'PAYMENT_EXPIRED',
    'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'
));

-- A held / expired-hold appointment never has a visit yet.
ALTER TABLE appointments DROP CONSTRAINT ck_appointments_visit_status_link;
ALTER TABLE appointments ADD CONSTRAINT ck_appointments_visit_status_link CHECK (
    (status NOT IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') OR visit_id IS NOT NULL)
    AND (status NOT IN ('AWAITING_PAYMENT', 'PAYMENT_EXPIRED', 'CONFIRMED', 'NO_SHOW') OR visit_id IS NULL)
);

ALTER TABLE appointments ADD CONSTRAINT ck_appointments_payment_deadline CHECK (
    status <> 'AWAITING_PAYMENT' OR payment_deadline IS NOT NULL
);

-- AWAITING_PAYMENT occupies the slot; PAYMENT_EXPIRED (like CANCELLED) releases it.
ALTER TABLE appointments DROP CONSTRAINT ex_appointments_doctor_occupied_range;
ALTER TABLE appointments
    ADD CONSTRAINT ex_appointments_doctor_occupied_range
        EXCLUDE USING gist (
            doctor_profile_id WITH =,
            tstzrange(start_time, occupied_until, '[)') WITH &&
        )
        WHERE (status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED'));

-- Sweeper: expired holds, oldest deadline first.
CREATE INDEX ix_appointments_awaiting_deadline ON appointments (payment_deadline, id)
    WHERE status = 'AWAITING_PAYMENT';

ALTER TABLE appointment_audits DROP CONSTRAINT ck_appointment_audits_actor;
ALTER TABLE appointment_audits ADD CONSTRAINT ck_appointment_audits_actor
    CHECK (actor_type IN ('PATIENT', 'RECEPTIONIST', 'DOCTOR', 'ADMIN', 'SYSTEM'));

ALTER TABLE appointment_audits DROP CONSTRAINT ck_appointment_audits_status;
ALTER TABLE appointment_audits ADD CONSTRAINT ck_appointment_audits_status CHECK (
    old_status IS NULL OR old_status IN (
        'AWAITING_PAYMENT', 'PAYMENT_EXPIRED',
        'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'
    )
);

ALTER TABLE appointment_audits DROP CONSTRAINT ck_appointment_audits_new_status;
ALTER TABLE appointment_audits ADD CONSTRAINT ck_appointment_audits_new_status CHECK (
    new_status IN (
        'AWAITING_PAYMENT', 'PAYMENT_EXPIRED',
        'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'
    )
);

-- Legacy rows (decision 09/11): online appointments that were confirmed without payment and still have
-- a full payment window before they start become holds that expire in 15 minutes; the sweeper then
-- settles them (asks PayOS first, voids the open invoice, frees the slot). Rows that start sooner stay
-- CONFIRMED and are paid at the counter as before. Idempotent: only CONFIRMED rows are touched.
WITH moved AS (
    UPDATE appointments a
    SET status = 'AWAITING_PAYMENT',
        payment_deadline = now() + interval '15 minutes',
        updated_at = now(),
        version = a.version + 1
    WHERE a.booking_source = 'PATIENT_ONLINE'
      AND a.status = 'CONFIRMED'
      AND a.visit_id IS NULL
      AND a.start_time > now() + interval '15 minutes'
      AND EXISTS (SELECT 1 FROM clinic_services s WHERE s.id = a.service_id AND s.price > 0)
      AND NOT EXISTS (
          SELECT 1 FROM invoices i
          WHERE i.appointment_id = a.id
            AND (i.status = 'PAID' OR EXISTS (
                SELECT 1 FROM payment_attempts p
                WHERE p.invoice_id = i.id AND p.status IN ('CONFIRMED', 'RECONCILIATION_REQUIRED')
            ))
      )
    RETURNING a.id, a.created_by_account_id, a.doctor_profile_id, a.start_time, a.end_time
)
INSERT INTO appointment_audits (
    appointment_id, actor_account_id, actor_type, action,
    old_status, new_status,
    old_doctor_profile_id, new_doctor_profile_id,
    old_start_time, new_start_time, old_end_time, new_end_time,
    reason, occurred_at, correlation_id
)
SELECT m.id, m.created_by_account_id, 'SYSTEM', 'STATUS_CHANGED',
       'CONFIRMED', 'AWAITING_PAYMENT',
       m.doctor_profile_id, m.doctor_profile_id,
       m.start_time, m.start_time, m.end_time, m.end_time,
       'Chuyen lich online chua thanh toan sang giu cho cho thanh toan (V106)', now(), 'V106-awaiting-payment'
FROM moved m;
