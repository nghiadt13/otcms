# FEAT-002 T031 Material-State Checkpoint — Visit Care Capacity Contract A1

Date: 2026-10-19 (Asia/Bangkok)
Status: APPROVED — Owner Long approved A1 (Visit care-capacity contract/API and provisional V015) and A2 (extension allowed for SCHEDULED and WALK_IN Visits) on 2026-10-19 (Asia/Bangkok). Source implementation is authorized within this boundary.

## Why a separate checkpoint is needed

The T031 policy checkpoint is approved, but repository inspection confirms the approved policy needs an additive owner contract and persistence capability before implementation:

- `VisitScheduledCarePort` covers only scheduled start/complete/cancel. Its command carries the scheduled end and current identity, but has no service-step identity, extension command, or actual completion time.
- `visit_care_assignments` stores Visit, Doctor, `starts_at`, `ends_at`, state and assignment/release times. It cannot distinguish service steps or retain the actual completion instant separately from expected end.
- `AppointmentCapacityPort.checkVisitReservation` can validate a proposed Doctor interval and exact linked-appointment exception. It is read/decision only; it does not persist a reservation. Repository search found no production adapter for this port. T031 must add the Appointment-owned read adapter using Appointment occupancy persistence; Visit must not query Appointment persistence directly.
- `visit_audits` from V014 is append-only but currently constrains action to `WALK_IN_CREATED`; it cannot audit care-step start/extension/completion or capacity conflict signals.
- There is no Room/resource contract, room inventory/suitability source, or room reservation table in the repository. The approved T031 policy excludes room persistence absent a separate Room owner checkpoint.
- Migrations in the repository currently end at V014. The actual local test DB head must still be verified before any migration is applied.

## Original A1 proposal (historical; superseded where noted by approved amendment/current v1.3.0)

### Ownership and transaction

Visit/Reception/Clinical owns care-step assignments, expected/actual intervals, assignment audit and Visit lifecycle. Appointment retains appointment status, schedule snapshots and occupied interval. Visit asks `AppointmentCapacityPort` to validate the candidate interval and exact self-linked appointment. Every writer obtains the shared T014 guard before capacity reads/Visit locks and writes; Appointment status writes continue to call Visit inside their existing transaction. If Visit and Appointment cannot share one datasource transaction, stop for architecture review.

### Duration configuration

Proposed owner configuration is a required map from recognized `stepCode` to positive duration minutes (no default). Example shape only, with no values set:

```yaml
otcms:
  visit:
    care-step-duration-minutes:
      <step-code>: <positive integer>
```

`stepCode` is allowlisted by the Visit owner and mapped to configuration. The request never supplies duration or expected end. Missing/invalid configuration returns `VISIT_CARE_STEP_NOT_CONFIGURED` and writes nothing. Scheduled appointments continue to derive their step estimate from the stored appointment duration/end snapshot; Appointment remains the caller and no client field can replace it. Walk-in and same-visit result-review steps use the configured key. This proposal does not introduce or manage a supporting-service catalog.

### Original Visit HTTP API proposal

All endpoints are Doctor-only; Doctor profile and patient are derived from authenticated account and Visit. They reject SCHEDULED visits because those lifecycle transitions must continue through the Appointment-owned status command, preserving atomic appointment status/audit.

- `POST /api/v1/visits/{visitId}/care-assignments` — body `{ "stepCode": "..." }`. Starts the authenticated Doctor's care step at server `now`; computes expected end from configured duration; writes an IN_PROGRESS assignment and immutable `STARTED` audit atomically after capacity validation.
- `POST /api/v1/visits/{visitId}/care-assignments/{assignmentId}/extensions` — body `{ "stepCode": "..." }`. Treats the configured step duration as an additional segment; proposes new expected end as `max(now, currentExpectedEnd) + configuredDuration`. The original full-interval check is superseded by approved A2: check only the newly added prospective segment, preserve linked appointment buffer, and allow SCHEDULED and WALK_IN Visits.
- `POST /api/v1/visits/{visitId}/care-assignments/{assignmentId}/completion` — empty body. Sets status COMPLETED and server `actualEndedAt`; preserves expected end separately. It does not close the Visit.

No Receptionist care-start/extension endpoint is proposed. Receptionist receives the typed conflict result through existing workflows and coordinates downstream appointment resolution; no incident CRUD or appointment mutation is added here.

## Amendment A2 approved — source eligibility for care extension

The approved A1 wording restricted all three Visit HTTP endpoints to WALK_IN visits. That prevents an active scheduled care assignment from extending through the approved conflict check. Keep `POST .../care-assignments` (start) and `POST .../completion` WALK_IN-only; scheduled start/completion remain atomic through Appointment. Broaden only `POST .../{assignmentId}/extensions` to both SCHEDULED and WALK_IN Visits. It requires the authenticated assigned Doctor and valid Visit/Appointment/Patient/status linkage, acquires the shared guard, and validates only the newly added prospective Doctor segment `[max(now,currentExpectedEnd),newExpectedEnd)`; elapsed history is not reserved again and the linked appointment's `[end_time,occupied_until)` buffer remains protected. It changes only Visit assignment expected-end/audit. It MUST NOT change Appointment status, `start_time`, `end_time`, duration/buffer snapshots or occupied interval. Owner Long approved A2 on 2026-10-19; this semantics amendment is authorized for implementation.

Owner Long approved A2 (“Duyệt A2 (Recommended)”) on 2026-10-19 (Asia/Bangkok). Extension accepts both SCHEDULED and WALK_IN Visits; start and completion API routes remain WALK_IN-only. Extension changes only the Visit care reservation and audit; the Appointment interval/status is immutable.

### Appointment-to-Visit internal contract

Additive `VisitCareStepPort` (or an approved extension of `VisitScheduledCarePort`) exposes `startScheduledCare`, `completeScheduledCare`, and the Visit-owned extension/actual completion semantics needed by scheduled workflows. Commands carry authenticated actor account ID, Visit/Appointment/Patient/Doctor IDs derived by the caller under authorization, step code where applicable, expected interval, and captured `occurredAt`. Visit validates the linked Visit/Patient/Appointment tuple before persistence. No cross-feature repository/entity access.

### Persistence proposal (provisional Flyway V015)

After confirming the local test DB is at V014, add an additive V015 migration; never edit V001–V014:

- Add `step_code`, `expected_duration_minutes`, and nullable `actual_ended_at` to `visit_care_assignments`; preserve `ends_at` as expected end for active capacity checks. New T031 assignments require the snapshot fields; historical rows remain readable.
- Add an immutable `visit_care_assignment_audits` table recording actor account, Visit, nullable assignment for rejected starts, action (`STARTED`, `EXTENDED`, `COMPLETED`, `CAPACITY_CONFLICT`), old/new expected end as relevant, actual event time and correlation ID; reject UPDATE/DELETE/TRUNCATE. Deduplicate conflict signal retries by Visit/actor/correlation. Retain V014 `visit_audits` semantics.
- Add bounded Doctor/time indexes or exclusion enforcement only after confirming the shared guard and query plan. Any exclusion constraint must match exact half-open interval and active statuses; clean/upgrade migration checks are required.

No room/resource table, FK, API, or booking-time room reservation is included. If room capacity must ship in T031, return for a separate Room owner contract/schema checkpoint.

## Proposed errors and response behavior

- `VISIT_CARE_STEP_NOT_CONFIGURED` — 503; server configuration lacks the required step estimate; no write.
- `VISIT_CAPACITY_NOT_AVAILABLE` — 409; active Doctor assignment or Appointment conflict; no assignment/extension mutation, but append the minimal `CAPACITY_CONFLICT` coordination audit. Dependency outage creates no audit.
- `VISIT_LINK_CONFLICT` — 409; linked appointment/Visit/Patient mismatch or scheduled Visit sent to WALK_IN endpoint.
- `VISIT_CARE_NOT_CHANGEABLE` — 409; assignment not active/owned by authenticated Doctor or invalid transition.
- `ACCESS_DENIED` — 403; role/Doctor ownership failure.
- `DEPENDENCY_UNAVAILABLE` — 503; guard, Appointment capacity port, configuration store, or Visit persistence unavailable; fail closed.

Do not expose patient details, other Doctor schedules, or clinical payload in conflict responses. Correlation ID remains in the common error envelope.

## Approval record

Owner Long approved the A1 API/actor/body contract, configuration source and missing-config behavior, additive Java port, provisional V015 columns/audit table, and error mapping on 2026-10-19 (Asia/Bangkok). Owner Long also approved A2, extending only the extension route to scheduled visits. Implementation records `CAPACITY_CONFLICT` as minimal Visit audit evidence to route care back to Receptionist coordination; it creates no incident CRUD or appointment mutation. This authorizes only those specific additive changes within T031. Any Room integration, new catalog, extra role, endpoint, schema element, or transaction architecture outside this checkpoint needs a new material-state checkpoint. T031 remains `[ ]` and production booking remains gated until implementation, PostgreSQL/concurrency tests, review, trace and final closure.

Historical preparation note: this checkpoint was approved before source edits. Approved A2 and the synced Visit v1.3.0 contract supersede the earlier proposal text where explicitly amended above.
