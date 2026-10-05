# Appointment rules proposal

Status: **MVP RULE APPROVED — SPEC/TASK SYNC REQUIRED**. This proposal records the booking decisions supplied for the orthopedic clinic. It is not yet an amendment to the locked authentication SPEC and does not authorize a migration or endpoint implementation.

## 1. Booking flow

```text
Patient selects service
        ↓
Doctor or “Any available doctor”
        ↓
Date
        ↓
Available slots
        ↓
Confirm booking
```

Only services with `clinic_services.online_bookable = true` are shown to patients. A doctor is eligible only when an active `doctor_services` row exists for the selected service. “Any available doctor” is a search mode; the persisted appointment still stores the selected doctor so ownership, notification and audit remain deterministic.

## 2. Slot calculation

The occupied interval is:

```text
occupied_until = starts_at + duration_minutes + buffer_minutes
```

An available slot must satisfy all of these conditions:

- The doctor has an active service assignment.
- The interval is inside an assigned doctor shift.
- The interval does not overlap active `doctor_time_off`.
- The interval does not overlap an active appointment.
- `starts_at >= now + minimum_booking_notice_minutes`.
- `starts_at <= now + booking_window_days`.
- The service is active and online bookable.

The application generates candidate starts from a configured slot step. The initial proposal uses a 30-minute slot step, while service duration and buffer determine occupancy. This step is an implementation/configuration choice and should not be confused with service duration.

The database exclusion constraint remains the final concurrent double-booking guard. Availability results are advisory until the booking transaction commits.

## 3. Initial policy values

These are application policy values, not medical rules and not admin-editable in the first release:

| Key | Initial value | Meaning |
|---|---:|---|
| `BOOKING_WINDOW_DAYS` | 30 | Maximum days ahead for online booking |
| `MINIMUM_BOOKING_NOTICE_MINUTES` | 120 | Minimum notice before appointment start |
| `RESCHEDULE_NOTICE_HOURS` | 24 | Self-service reschedule cutoff |

The values are stored in the proposal's `booking_policies` table so the first release can use typed server configuration or a controlled seed. Adding an admin screen for editing them is out of scope.

## 4. Reschedule and cancellation

- Self-service reschedule is allowed only when the appointment is at least 24 hours away.
- Self-service cancellation follows the same 24-hour cutoff in the initial scope.
- Requests to cancel or reschedule inside 24 hours are rejected with a safe policy message and the patient is instructed to contact the clinic.
- Every reschedule stores old and new time, actor, time and reason in `appointment_time_changes`. The appointment row is not overwritten without history.
- The appointment status becomes `RESCHEDULE_REQUIRED` when a clinic schedule or doctor time-off change conflicts with an existing appointment. The system does not move the appointment automatically.
- `RESCHEDULE_REQUIRED` keeps the old start/end timestamps for patient-facing history, but does not occupy any slot. Availability checks must filter by occupied status, never by timestamp alone.
- A `RESCHEDULE_REQUIRED` appointment must eventually be resolved by a successful reschedule back to `CONFIRMED` or by an authorized cancellation. It must not remain unresolved indefinitely.

## 5. Doctor schedule changes

When a shift is shortened/removed or doctor time-off is added:

1. Find active appointments whose occupied interval intersects the unavailable period.
2. Mark each affected appointment `RESCHEDULE_REQUIRED` with the appropriate reason and release its old slot.
3. Keep the original appointment time and patient/doctor/service identity.
4. Notify the patient and clinic staff when notification delivery is available.
5. Let the patient or authorized staff choose a new available slot.
6. Record the change as a clinic reschedule event.

This operation needs one transaction for conflict detection and state update. Notification delivery is an external side effect; it must not cause the appointment state update to roll back. A retryable notification/outbox design needs a separate approved notification SPEC.

## 6. State rules

| State | Can appear in availability occupancy? | Patient self-service |
|---|---|---|
| `CONFIRMED` | Yes | View, reschedule/cancel if policy allows |
| `CHECKED_IN` | Yes | No reschedule/cancel |
| `IN_PROGRESS` | Yes | No reschedule/cancel |
| `COMPLETED` | No | View history |
| `CANCELLED` | No | View history |
| `RESCHEDULE_REQUIRED` | No | View and choose replacement, or cancel through an authorized workflow |
| `NO_SHOW` | No | View history |

The service layer must validate legal transitions and actor authorization. A SQL CHECK constraint alone cannot determine whether the caller is the patient, an approved dependent manager, a receptionist or the responsible doctor.

## 7. Implementation acceptance criteria

- `GET availability` supports a concrete doctor and `ANY_AVAILABLE_DOCTOR`.
- Availability excludes shifts, time-off, active appointments and service buffer.
- `POST appointment` revalidates all conditions inside a transaction and returns a generic “slot unavailable” result on exclusion conflict.
- Two concurrent requests for the same doctor and overlapping occupied time create at most one active appointment.
- A booking outside the 30-day window or inside the two-hour notice period is rejected.
- A patient can book their own patient record or a confirmed dependent relationship only.
- A non-bookable service or inactive doctor-service assignment cannot be booked online.
- Reschedule keeps an append-only time-change record.
- Schedule/time-off conflict changes create `RESCHEDULE_REQUIRED` and never silently move a patient.
- `RESCHEDULE_REQUIRED` preserves the old timestamps for display/audit but never blocks availability.
- A `RESCHEDULE_REQUIRED` appointment can end only through successful rescheduling to `CONFIRMED` or authorized cancellation.
- Cancel and reschedule honor the 24-hour policy and legal status transitions.

## 8. Approved MVP decision

The MVP uses the simple cancellation policy:

- self-service cancellation requires at least 24 hours before appointment start;
- self-service rescheduling requires at least 24 hours before appointment start;
- requests inside the cutoff are rejected and do not change the appointment;
- clinic staff may handle exceptional cases through an authorized operational workflow, which requires its own API authorization rule and audit record.

The `LATE_CANCELLATION` state is removed from the MVP state machine. A later feature may add it through a new reviewed SPEC and migration.

```text
HUMAN DECISION: APPROVED
SCOPE: Appointment MVP cancellation and rescheduling policy
DECISION: Both self-service actions require at least 24 hours' notice.
NEXT STEP: Sync appointment SPEC/TASKS, then create reviewed Flyway/API implementation tasks.
```
