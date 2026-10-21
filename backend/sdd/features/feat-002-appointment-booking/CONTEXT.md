# Feature Context — FEAT-002 Appointment Booking

# Version: 0.16.0
# Status: APPROVED — D03 REVISION
# Feature slug: `feat-002-appointment-booking`
# Repository authority: `AGENTS.md`, `backend/AGENTS.md`, and `docs/architecture.md`

---

## Intent Packet

- **WHAT:** Cung cấp luồng công khai xem dịch vụ/availability và các luồng có xác thực để đặt, xem, đổi, hủy, check-in, hoàn tất và xử lý xung đột lịch hẹn.
- **WHY:** Phòng khám cần lịch hẹn đúng service/Doctor/thời gian, chống double booking và giữ đúng authorization khi lịch làm việc thay đổi.
- **Definition of Done:** Availability và booking dùng cùng rule; booking atomic; patient self-service tuân thủ policy; staff intervention được audit; sự cố được xử lý bằng reschedule/reassign/cancel; test và traceability đầy đủ.
- **Boundaries:** Service catalog phục vụ booking, Doctor eligibility, shift/time off, availability, appointment lifecycle, follow-up, manual reassignment, schedule-conflict handling và audit.
- **Exclusions:** Billing/payment, medical records/results, insurance, family/clinical-record sharing, notifications delivery, wait queue và automatic reassignment.
- **Decision owner:** Human Product Owner phối hợp Tech Lead, Database và Security reviewer.

## 1. Metadata

- Feature ID: `FEAT-002`
- Owner: Human Director / OTCMS Team
- Official SRS baseline (D01): `FA26_SEP490_G58_Report3_Software_Requirement_Specification (2).docx`, supplied by the user. Booking: `UC21–UC25`; shift integration: `UC26–UC29`; check-in: `UC30`. Relevant rules include `BR-18–BR-19`, `BR-21`, `BR-23–BR-31`, `BR-65`, `BR-67`.
- Baseline archive: `docs/requirements/baselines/report3-2026-10-12/BASELINE.md` contains source hash, text extraction and UC mapping.
- Source roles: Report 1 defines product scope; Report 2 defines delivery/review process; meeting minutes supply change requests and unresolved business questions. Repository HTML is a historical alternate mapping; the baseline manifest records FEAT-002 correspondence and variances.
- Dependencies: `feat-001-auth-login`; patient-module self-profile authorization (revised D03); visit/clinical public follow-up authorization contract (D09); visit/reception public atomic check-in contract (D10).
- Architecture: Spring Boot modular monolith, PostgreSQL, Flyway
- Clinic model: one clinic in MVP

## 2. Actors and authority

| Actor | Authority in FEAT-002 |
|---|---|
| Public user | View active online-bookable services and bounded availability. |
| Patient | Book, view, reschedule and cancel appointments only for the profile linked to the authenticated Patient account. |
| Receptionist | Search, view, create, reschedule and cancel every appointment in the single clinic through approved operational workflows; check in a confirmed appointment through the atomic visit/reception workflow under D10; inside-deadline changes require reason and audit. Reassign an affected `CONFIRMED` appointment to another Doctor at the same slot under D06, with mandatory reason and audit. |
| Doctor | View appointments whose `doctor_id` matches the authenticated Doctor profile; progress their statuses and create follow-up appointments from a source visit in which they were assigned to care and have completed their examination part (D09). |
| Clinic Owner | Operational oversight; no general appointment-read, maintenance or reassignment authority is granted by D02/D06. |
| Administrator | Manage clinic services, Doctor profiles/publication/directory status, permitted account statuses and work shifts under the SRS baseline; inspect Doctor availability for scheduling. Account lock does not grant appointment-change authority or imply clinical unavailability (D07). Admin also manages Doctor-service assignments and operational Doctor time off. General appointment intervention/read scope is not implied; FEAT-002 grants Admin availability and mutation-impact evidence only, not general appointment list/detail or change commands. |

Account types are `PATIENT`, `RECEPTIONIST`, `DOCTOR`, `OWNER`, and `ADMIN`. One account has exactly one account type in the MVP; multi-role accounts are outside scope. Retiring the current generic `STAFF` type requires an approved FEAT-001 compatibility migration across persistence, JWT claims, login portal checks and authorization tests.

## 3. Patient identity boundary

- Public browsing does not require an account. Creating an online appointment requires registration or login with a `PATIENT` account.
- `account` is authentication identity. `patient_profile` is stable patient identity and demographic/contact data. Medical encounters, diagnoses, results, images, prescriptions and invoices are separate records outside this feature.
- `patient_profiles` requires an independent UUID primary key and an optional unique `account_id` link. Registration creates the account holder's initial profile. Appointment foreign keys reference `patient_profile_id`, never `account_id`.
- FEAT-002 Patient appointment authorization is limited to the profile currently linked to the authenticated Patient account (revised D03). Booking, query and changes derive the actor from that authenticated account and re-check the current account/profile link. If A helps B operate B's account, the booking remains attributed to B; A's separate account does not gain appointment authority over B. The approved sign-in or assisted-operation method for that help remains to be specified by Auth/product policy.
- Family access to B's clinical information and test/imaging results requires a separate, scoped decision covering B's capacity/consent, the representative's verified identity, permitted records, revocation and audit. That access is outside FEAT-002 and must not be inferred from an appointment relationship or registration `guardianConsent`. Receptionist-assisted booking remains its separately authorized staff workflow.
- Under D11, Receptionist first searches for an existing patient profile and creates one through the patient module only when no suitable profile is identified. A walk-in profile need not have an account (BR-18/BR-19); matching name/phone alone does not authorize automatic profile merging. Later account linkage is a separate identity workflow.
- The existing FEAT-001 shared primary key `patient_profiles.account_id` is incompatible and must be migrated additively before appointment persistence is introduced.

## 4. Booking and availability decisions

### 4.1 Service and Doctor eligibility

- Public service catalog exposes only active services with `onlineBookable = true`.
- Receptionist and Doctor use an authenticated staff service catalog that exposes every active service permitted for their workflow, including services not offered for online self-service.
- Doctor follow-up may select an active service only when at least one active, eligible Doctor can provide it.
- Every new appointment selects exactly one Doctor accepting new bookings with an active Doctor-service assignment for the selected service. Existing-appointment eligibility is evaluated separately under D07; account status is not a single booking-eligibility flag.
- Any-Doctor availability returns Doctor-specific slots; appointment creation never defers Doctor selection.
- Duration and buffer belong to the service and apply equally to all eligible Doctors. Doctor-specific overrides are outside scope.

### 4.1.1 Doctor state and workflow eligibility — confirmed D07

- Treat public visibility, acceptance of new bookings, account access status and eligibility to serve an existing appointment as separate concepts; a single `active` flag must not conflate them.
- Admin manages profile publication/directory state and permitted account status through the owning staff/account module. Appointment consumes its public contracts and does not modify those states directly.
- Hiding a Doctor's public profile removes that Doctor from the public directory and public new-booking selection. Staff may still select the Doctor when acceptance of new bookings and service/slot eligibility permit. Hiding alone never changes existing appointments or their occupancy.
- Removing a Doctor from the active directory under UC20/BR-23 stops all new-booking selection, including follow-up and selection as a replacement under D06. Existing appointments and history are preserved.
- An authorized Patient or Receptionist may reschedule an existing appointment with the same removed Doctor under D05/UC23/BR-23 when the preserved service and new slot remain eligible. This exception does not permit creating a new appointment or reassignment to a removed Doctor.
- Availability for that existing-appointment change must support the same-Doctor exception in an authorized appointment context. Public new-booking availability must not expose the removed Doctor as a selectable provider; viewing/managing existing appointment details remains subject to actor scope.
- Account lock blocks the Doctor's authentication/operations according to the account security policy. It does not by itself stop new booking acceptance, cancel/reassign appointments, release occupancy or change appointment status; unlocking does not change appointments either.
- Eligibility to serve an existing appointment depends on service assignment, shift coverage, time off and occupied-slot rules, with consultation/buffer snapshots preserved. Being hidden or removed from new selection is not evidence that an existing appointment cannot be served.
- Actual inability to serve must be established separately and handled through its approved workflow: D04/D06 for shift-related resolution. Admin time-off and assignment mutations must follow the protected-booking rules in Section 5.5; D07 does not authorize automatic appointment transitions.

### 4.1.2 Service deactivation and existing bookings — confirmed D08

- Admin service deactivation under BR-67 stops new bookings and removes the service from active public/staff booking catalogs. Patient-online, Receptionist-assisted and Doctor-follow-up creation all require an active service; follow-up is a new appointment, not an existing-booking exception.
- Existing bookings retain their service identity, status, Doctor, times, duration/buffer snapshots and occupied intervals. Deactivation alone never cancels an appointment, releases its slot or changes appointment status.
- Existing bookings remain serviceable. Authorized ordinary reschedule under D05 may retain the inactive service and same Doctor when all other status, authorization, deadline and service/slot eligibility rules pass.
- Receptionist reassignment under D06 may retain the inactive service and same slot when the replacement Doctor is eligible and available. D07 still forbids selecting a Doctor removed from new selection as a replacement.
- Existing-appointment availability uses the stored duration/buffer snapshots and permits the inactive-service exception only within an authorized appointment-change context. An inactive service must not become selectable for new booking through this exception.
- Changes to service duration/buffer apply to new appointments only. Existing appointments, including after reschedule/reassignment, retain their stored snapshots; occupancy and shift/time-off checks use those snapshots.
- Service name/description changes preserve service identity and historical references. The exact historical-name display contract belongs to SPEC.
- If the clinic cannot honor existing bookings, handle those appointments explicitly through an authorized change/cancellation workflow with required reason and audit. Service deactivation does not authorize silent appointment changes or override D04/D06 and the separate conflict policies.

### 4.2 Booking policy configuration

Policy is deployment-managed typed application configuration. There is no booking-policy table or runtime policy UI in the MVP.

```yaml
appointment:
  booking:
    max-horizon-days: 30
    online-minimum-notice-minutes: 120
    patient-change-deadline-minutes: 1440
    slot-interval-minutes: 15
    clinic-time-zone: Asia/Ho_Chi_Minh
```

Business services use one injected `Clock`; direct calls to current-time APIs are not a business-time source.

### 4.3 Time semantics

- Persisted timestamps use PostgreSQL `timestamptz` and represent UTC instants.
- Business dates use `Asia/Ho_Chi_Minh` from configuration.
- Availability `from` and `to` are inclusive clinic-local dates and map to a half-open UTC persistence interval `[fromStart, dayAfterToStart)`.
- Latest bookable date is `clinicToday.plusDays(maxHorizonDays)`. A 30-day horizon may include 31 dates when both endpoints are included.
- Online patient booking accepts a slot exactly at `now + 120 minutes`.
- Patient self-service change accepts an appointment exactly at `now + 1440 minutes`; inside that boundary it is rejected.
- Every create/reschedule rejects a slot before the injected Clock current instant. Equality at now is permitted for notice-exempt workflows if all slot rules pass.
- Patient reschedule checks both the old appointment change deadline and the new slot online minimum notice. Receptionist create/reschedule and Doctor follow-up bypass online notice but still enforce horizon, non-past start and availability.

### 4.4 Slot generation and overlap

- Slot starts use the clinic-wide 15-minute grid: minute marks `00`, `15`, `30`, `45`.
- Duration plus buffer must fit completely inside an active shift and avoid Doctor time off. New bookings use the current service values; existing-appointment reschedule/reassignment uses the stored snapshots under D08.
- Appointment stores `start_time`, consultation `end_time`, and snapshots `duration_minutes` plus `buffer_minutes`.
- Occupied interval is `[start_time, end_time + buffer_minutes)` for occupied statuses.
- Occupied statuses are `CONFIRMED`, `CHECKED_IN`, `IN_PROGRESS` and `COMPLETED`. A completed appointment retains its stored `[start_time, end_time + buffer_minutes)` range; elapsed ranges do not block future slots. Completion does not truncate that range or rewrite start/end snapshots.
- `CANCELLED` and `NO_SHOW` do not block availability.
- Availability and command paths use the same occupied predicate. Booking re-checks inside the transaction and database protection prevents concurrent overlap.

Example: shift `08:00–12:00`, duration 60 minutes and buffer 15 minutes allows `10:45–11:45` because the buffer ends at `12:00`; `11:00–12:00` is invalid.

## 5. Appointment lifecycle

### 5.1 Statuses and transitions

```text
CONFIRMED -> CHECKED_IN
CONFIRMED -> CANCELLED
CONFIRMED -> NO_SHOW
CHECKED_IN -> IN_PROGRESS
CHECKED_IN -> CANCELLED
IN_PROGRESS -> COMPLETED
```

`COMPLETED`, `CANCELLED`, and `NO_SHOW` are terminal. Reschedule/cancellation is not allowed from `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, or `NO_SHOW`.

| Transition | Authorized actor |
|---|---|
| `CONFIRMED -> CHECKED_IN` | Receptionist through atomic appointment/visit/reception check-in (D10). |
| `CONFIRMED -> CANCELLED` | Authorized Patient (linked self profile) or Receptionist |
| `CONFIRMED -> NO_SHOW` | Receptionist |
| `CHECKED_IN -> IN_PROGRESS` | Assigned Doctor |
| `CHECKED_IN -> CANCELLED` | Receptionist with mandatory reason |
| `IN_PROGRESS -> COMPLETED` | Assigned Doctor |

Patient commands require current linked self-profile authorization. The current status vocabulary is exactly `CONFIRMED`, `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, and `NO_SHOW`; every unlisted transition/status is rejected.

`COMPLETED` means the scheduled Doctor service has finished, not that the entire visit is closed. Visit completion, pending orders and billing remain under the owning modules.

Cancellation after check-in is allowed only before clinical activity has begun. Appointment cancellation, ending initial reception and cancelling/ending the linked visit must commit or roll back together through the visit/reception contract, with required reason/audit. If clinical activity has begun or the linked visit cannot be ended consistently, reject cancellation; do not leave an active visit behind a cancelled appointment.

### 5.1.1 Appointment check-in and reception — confirmed D10

- Only Receptionist may check in a booked appointment. A first check-in requires `CONFIRMED`; patient identity is derived from the appointment and cannot be replaced by client input.
- A successful check-in transitions the appointment to `CHECKED_IN`, creates or links its one corresponding visit, records the initial reception step and writes the required audit evidence as one atomic operation.
- Appointment owns appointment status and its visit reference; visit/reception owns visit creation/linkage and reception records. Cross-module orchestration uses public application contracts, not another module's repositories/entities.
- All mandatory changes commit or roll back together. If visit creation/linkage, initial reception or required audit fails, a first check-in leaves the appointment `CONFIRMED` and creates no partial visit/reception records.
- One appointment may produce at most one check-in visit and initial reception record. Concurrent requests and retries must not create duplicate visits, reception records or success audit events.
- An authorized retry of the same successful request returns the existing check-in result without repeating the transition or creating records. It must not reset an appointment that has subsequently progressed. SPEC must define the retry identity, response and uniqueness/locking contract.
- `CHECKED_IN` continues to occupy the Doctor slot. Audit includes Receptionist, check-in timestamp, appointment, linked visit and correlation ID.
- Queue is a separate integration awaiting scope confirmation because Report 3 UC30/BR-31 and Report 1 queue exclusions conflict. D10 requires a visit and initial reception step, not a queue/waiting-list entry. Reconcile this variance before final SRS approval; do not silently implement queue within appointment.
- Check-in timing (early/late allowance) must be defined and reviewed in SPEC before execution. `NO_SHOW` remains terminal; late arrival is handled by a separate reception workflow rather than restoring the appointment. Any restoration would require an explicit policy change. D10 grants no automatic restoration of `NO_SHOW` or other unsupported states.
- Walk-in arrival without an appointment is a separate confirmed reception/visit workflow under D11; this appointment check-in contract does not require or authorize a synthetic appointment.

### 5.2 Patient and Receptionist changes

- Patient reschedule/cancellation is allowed at least 1440 minutes before start and requires current linked self-profile authorization.
- Patient cancellation reason is optional.
- Receptionist may reschedule or cancel inside that deadline for an operational reason. `reason` is mandatory.
- Patient reschedule additionally enforces online notice on the new slot; Receptionist bypasses it but cannot select a past slot.
- Ordinary reschedule atomically replaces the slot and preserves patient, Doctor, service and purpose (D05, UC23/BR-27). Changing Doctor is a separate Receptionist reassignment workflow under D06; it is not an option in ordinary reschedule.
- Reschedule of an existing booking may retain a deactivated service under D08; it preserves the stored duration/buffer snapshots and must satisfy every other change/slot rule.
- Successful reschedule records immutable old/new times, unchanged Doctor, actor, reason and timestamp.
- Cancellation changes status to `CANCELLED`, releases the slot and records actor, reason and timestamp.

### 5.3 Doctor follow-up — confirmed D09

- Source is an actual visit. The authenticated Doctor must have been assigned to care for its patient, completed their examination part and recorded a follow-up indication. Knowing a patient/visit ID alone grants no authority.
- The visit/clinical module owns care assignment and examination-completion evidence. Appointment verifies the Doctor's authority through its public application contract; it does not access clinical repositories/entities or copy clinical records.
- The source visit need not be closed; pending payment or other visit steps do not by themselves block follow-up booking. A source appointment is not required and its `COMPLETED` status is not a prerequisite.
- Patient identity for Doctor follow-up is derived from the authorized source visit. The client cannot replace the patient. Doctor care-based authorization is separate from Patient self-profile authorization under revised D03.
- Persist `source_visit_id` on the new appointment. If the visit originated from an appointment, an additional source-appointment link may be retained after resolving it through the visit contract; it is not the authority or required source for follow-up.
- The target service must be active. The selected provider Doctor must accept new bookings and be eligible/available for that service under D07/D08. The ordering Doctor need not be the provider.
- Follow-up bypasses the online minimum notice but remains within the configured booking horizon. It must not start before the injected Clock's current instant.
- Reason is required and sensitive; it must not be written to application logs. Creation audit includes the ordering Doctor, source visit, provider, required reason and normal booking evidence.
- Booking source is `DOCTOR_FOLLOW_UP`; this creates a new `CONFIRMED` appointment subject to normal slot re-check/overlap protection. Existing-booking exceptions under D07/D08 do not permit follow-up with an inactive service or a Doctor removed from new selection.
- The former appointment-scoped endpoint is superseded as a Context design assumption. SPEC must define the visit-based follow-up endpoint/request and errors using the source-visit contract.
- Return to read results within the same visit is a subsequent visit service step under D12, not a new follow-up appointment. Supporting service orders belong to the appropriate visit/clinical/service module, not to this Doctor-appointment contract.

### 5.4 Manual Doctor reassignment — confirmed D06

- Only Receptionist may manually reassign when the current Doctor cannot serve the confirmed appointment; arbitrary Doctor replacement through ordinary reschedule is forbidden.
- Only `CONFIRMED` appointments are eligible. Reassignment from `CHECKED_IN`, `IN_PROGRESS` or any terminal state is forbidden.
- The replacement must be a different eligible Doctor for the same service, accepting replacement selection under D07. The service may be inactive for an existing booking under D08; the Doctor-service assignment must still permit serving it. Their shift must cover the existing consultation plus buffer snapshots, with no time-off or occupied-appointment conflict.
- Preserve patient, service, purpose, start/end times and duration/buffer snapshots. Atomically change only the assigned Doctor, associated update metadata and audit evidence; status remains `CONFIRMED`.
- Reason is mandatory. Immutable audit records the actor, reason, old/new Doctor, unchanged times, status, timestamp and correlation ID.
- An ineligible or unavailable replacement fails with `DOCTOR_NOT_ELIGIBLE` or `SLOT_UNAVAILABLE`. A rejected command leaves all appointment data, occupancy and audit history unchanged; it never changes appointment status.
- Receptionist uses existing clinic appointment list/detail authority. D06 grants no additional appointment-read or command authority to Admin or Owner.
- After success, the previous Doctor loses normal assignment-based operational access; the replacement receives assigned-Doctor scope.
- Successful reassignment counts as resolving an affected booking under D04 only when that booking no longer depends on the shift being changed/removed. Admin must still validate every remaining booking before accepting the shift mutation.
- This is a confirmed extension to Report 3 BR-30, which originally lists reschedule/cancellation only. Synchronize this extension into the SRS before downstream approval.

### 5.5 Schedule conflict

- Admin shift change/removal must be rejected if any booked appointment would lose shift coverage (D04, UC28/UC29, BR-30). Affected bookings must first be rescheduled, cancelled or successfully reassigned to an eligible replacement Doctor at the same slot through an authorized workflow. Reassignment satisfies this prerequisite only if the booking no longer depends on the affected shift (D06 extension to BR-30).
- A successful shift mutation must not leave any booking uncovered. Coverage validation and mutation must remain consistent with concurrent appointment commands; implementation must define and test their synchronization.
- Sudden Doctor unavailability is recorded as an operational incident with Receptionist, reason, timestamp and affected appointment references; recording it does not create a new appointment status or release occupancy. Receptionist resolves affected appointments through authorized reschedule, same-slot reassignment or cancellation with reason/audit. Until a command succeeds, the original appointment status and occupancy remain unchanged.
- Incident tracking belongs to the operational/reception workflow; FEAT-002 writes resolution audit and exposes its authorized appointment contracts. Do not implement an implicit waiting-for-reschedule lifecycle.
- A resource mutation remains blocked until every affected booking is validly resolved; merely recording an incident does not satisfy D04/D06.
- Service deactivation alone never triggers appointment conflict transitions or releases occupancy (D08).
- Account lock alone never triggers appointment conflict transitions (D07).
- Admin manages Doctor-service assignments and operational time off (not HR leave/payroll). Reject creating/extending time off or removing eligibility if it would invalidate booked or active care. Resolve affected confirmed bookings through authorized reschedule/cancellation/reassignment first; checked-in/in-progress care cannot be silently changed or invalidated.
- Validation and resource mutation must stay consistent with concurrent booking/care commands. Detailed lock strategy belongs to PLAN; mutation error contracts belong to SPEC.
- `CHECKED_IN` and `IN_PROGRESS` appointments must not be silently rewritten by schedule maintenance.
- Notification delivery remains outside this feature; retain an integration seam.

## 6. Booking sources and audit

Booking sources are `PATIENT_ONLINE`, `RECEPTIONIST_ASSISTED`, and `DOCTOR_FOLLOW_UP`. `WALK_IN` is a visit reception source under D11, not an appointment booking source. Walk-in patients do not require registration/login or a synthetic appointment. A later booking assisted by Receptionist uses `RECEPTIONIST_ASSISTED`.

Immutable appointment audit covers creation, reschedule, cancellation, status transition, Doctor reassignment and operational conflict resolution. Evidence includes appointment, actor account/type, action, old/new state, old/new Doctor/time when applicable, reason when required, timestamp and correlation ID. Logs must not contain purpose, follow-up reason or unrelated patient data.

Audit has no public Patient or Doctor API. FEAT-002 writes audit evidence but does not expose an audit-query API. A future audit feature may grant read access only to `ADMIN`. Retention follows a future system policy; FEAT-002 does not automatically delete audit records.

### 6.1 Walk-in reception boundary — confirmed D11

- Only Receptionist performs walk-in reception, identifying an existing patient profile or creating one through the patient module's approved workflow. A login account is optional and is not created implicitly by reception.
- Reception/visit creates a visit with source `WALK_IN`, linked patient profile, arrival timestamp and Receptionist identity, and records initial reception and required audit. No appointment or appointment status is fabricated.
- Visit creation and initial reception/audit commit or roll back together within reception/visit. The separate patient-profile creation workflow is not implicitly included in this transaction; its ownership/contract remains with the patient module.
- Retries of the same authorized request return the existing result; concurrent/repeated requests must not create duplicate walk-in visits or initial reception records. Retry identity must distinguish separate legitimate visits by the same patient; a patient-wide one-visit-ever constraint is forbidden.
- Walk-in reception alone does not reserve an appointment occupied interval. Doctor/room assignment belongs to reception/visit and must coordinate with existing bookings through public appointment contracts. D12 requires coordinated Doctor and room capacity checks before service starts; lack of a synthetic appointment is not permission to double-assign a Doctor. Expected-duration/overrun rules and the synchronization contract remain to be specified.
- Queue remains a separate integration pending scope confirmation under D10. This decision does not add a waiting-list implementation to FEAT-002.
- A Doctor may later create follow-up from an eligible walk-in visit under D09, because a source appointment is not required.

### 6.2 Doctor capacity, rooms and service steps — confirmed D12

- Appointment reserves Doctor time only. It does not select or reserve a room, imaging device or other resource. Confirmation guarantees the reserved Doctor interval under booking rules, not immediate service start when a suitable room is unavailable.
- Reception/visit coordinates Doctor and suitable-room assignment when care is delivered. It must prevent concurrent service assignments to the same Doctor or occupied room and coordinate with appointment occupied intervals through public application contracts.
- Walk-in or return-for-results service may start only when the Doctor is free and its expected occupied interval does not overlap another active service assignment or another appointment's occupied interval. The visit's own linked appointment is not treated as a competing patient; its mapping and capacity checks must be defined in the integration contract.
- Checking only whether the Doctor is free now is insufficient. Reception/visit must define expected duration and overrun handling so unscheduled work does not silently displace, reschedule or alter confirmed appointments. Exact duration estimation, overrun response and concurrent booking/assignment synchronization remain explicit business/integration decisions before execution; D12 does not invent numeric thresholds.
- Supporting services within approved product scope (for example imaging) are service orders/steps within a visit. Their owning modules coordinate providers, rooms/devices and results. Do not convert every supporting order into an appointment requiring one Doctor.
- Returning to a Doctor to read results within the same visit is another visit service step, not a new appointment. Doctor capacity still applies; waiting/calling behavior depends on the separately approved queue scope.
- A later, separate examination uses a new appointment when scheduled in advance (Doctor-recommended follow-up under D09) or a `WALK_IN` visit when received without a booking (D11).
- A visit tracks pending/completed service orders. The Doctor decides whether available results permit continued assessment or require waiting; the system does not make clinical conclusions or silently close pending work.
- D12 does not approve laboratory workflows, external laboratory-result ingestion or external diagnostic-service management. The final supporting-service catalog and laboratory/external-results scope need a separate decision reflecting the council feedback and Report 1 exclusions.
- Room/resource scheduling, service-order execution/results and queue implementation belong to their owning features. FEAT-002 owns Doctor booking capacity and the public coordination boundary only.

## 7. Public data exposure and abuse controls

- Public availability returns only bookable slots and the Doctor identifier/display data needed for selection.
- Public responses never expose appointments, patients, staff contact details or reasons that a Doctor is unavailable.
- One availability request covers at most `max-horizon-days + 1` inclusive clinic-local dates within the configured horizon (31 dates at the default).
- Public catalog and availability endpoints require rate limiting at the API or gateway boundary.

## 8. Scope

### Delivery and module ownership

FEAT-002 delivers backend/API, persistence, authorization, audit and public integration contracts; frontend screens are not part of this delivery.

| Owner | Responsibility |
|---|---|
| Appointment | Catalog/availability projections, booking/query/change/lifecycle/reassignment/follow-up commands, appointment schema/audit and capacity/conflict ports. |
| Service/catalog | Admin service CRUD/deactivation and Doctor-service assignments; must enforce appointment impact policy through public contracts. |
| Staff/account | Doctor profile/publication/account state, Admin shifts and operational time off; must enforce protected-booking mutation policy. |
| Patient | Independent profile identity, search/create and current linked self-profile authorization for appointment consumers. |
| Visit/reception/clinical | Atomic reception/visit lifecycle, care assignment/completion, walk-in, room capacity, service steps and follow-up evidence. |

These owning-module contracts are prerequisites/integration slices, not permission for appointment to access their repositories or implement all their CRUD inside appointment. PLAN must enumerate exact prerequisite deliveries and owners. Queue implementation is outside FEAT-002; its optional integration remains pending separate product-scope approval and is not required for this Context approval.

### In scope

- Public online-bookable service discovery and bounded availability.
- Appointment-side eligibility/capacity checks and protected-mutation integration with Admin-managed services, Doctor profiles, shifts, time off and assignments; owning-module CRUD is distinguished below.
- Patient online booking for the authenticated account's linked profile, and Receptionist-assisted booking for an existing profile.
- Appointment list/detail, lifecycle transitions, reschedule and cancellation; atomic, duplicate-safe appointment check-in integrated with visit/reception (D10).
- Doctor follow-up from an authorized source visit after the ordering Doctor completes their examination part (D09). Receptionist same-slot reassignment is confirmed under D06.
- Block shift/time-off/assignment mutations that invalidate booked or active care until authorized resolution is complete. Resolve operational conflicts via authorized reschedule/reassignment/cancellation without adding a pending-resolution appointment status.
- Independent patient-profile identity compatibility migration.
- Explicit account-type compatibility migration.
- Typed policy, UTC/clinic-time semantics, overlap protection and immutable audit.

### Out of scope

- Family-management and clinical-record/results sharing workflows; no cross-account appointment authority is granted.
- Billing, payment including cash, insurance and invoices.
- Encounters, diagnoses, test/imaging results, prescriptions and medical documents.
- Queue implementation, waiting-time estimation and patient calling; queue integration scope remains pending. Walk-in reception/visit implementation belongs to its owning feature under D11; FEAT-002 supplies the appointment integration boundary.
- Notification delivery, recurring appointments and automatic Doctor reassignment.
- Multi-role accounts, multi-clinic tenancy and Doctor-specific duration/buffer.
- Supporting-service orders, room/device reservations and results management (owned by visit/clinical/service features under D12); laboratory/external-results scope requires a separate product decision.

## 9. Dependencies and review state

- D01–D12 were confirmed by the user in the conversation on 2026-10-12; D03 is revised by the user's 2026-10-13 clarification and requires new review. D06 also extends the booking-resolution options in D04.
- Patient-module current linked self-profile authorization is a prerequisite for revised D03.
- D09 requires the visit/clinical module to expose source patient identity, Doctor care assignment, examination completion and follow-up indication through a public contract. Define that contract and its consistency/verification evidence before implementing follow-up; do not replace it with an appointment-status shortcut.
- D10 requires a visit/reception public contract for atomic visit creation/linkage and initial reception, plus retry/uniqueness guarantees. Queue is outside FEAT-002. SPEC must obtain/record check-in timing/NO_SHOW rules before the affected commands execute; no queue dependency is presumed.
- D11 requires patient-module profile lookup/create contracts and reception/visit retry/atomicity guarantees. Doctor/room capacity coordination follows D12; walk-in implementation remains outside appointment.
- D12 requires a public capacity-coordination contract between appointment and reception/visit, including atomic/concurrent checks, own-appointment mapping, expected duration and overrun rules. Supporting-service catalog and laboratory/external-results scope remain separate decisions.
- Root `AGENTS.md` now binds FEAT-002 to the archived Report 3 baseline and confirmed appointment decisions; the old HTML is historical for this feature. Broader HR/payroll/inventory scope is not restored.
- Existing SPEC v0.4.0 and PLAN/TASKS v0.5.0 still describe the former contract and must not be used to execute the revised decisions. Synchronize them after the Context decisions and review are complete.

### Decision register

| ID | Status | Recorded decision / remaining boundary |
|---|---|---|
| D01 | CONFIRMED | Supplied Report 3 is the official SRS baseline; use its UC numbering. |
| D02 | CONFIRMED | Admin manages services, Doctor profiles and shifts; Owner provides oversight. Reassignment belongs to Receptionist under D06; the v0.13.0 supplementary policy assigns operational time-off and Doctor-service assignment management to Admin. |
| D03 | REVISION PENDING REVIEW | Patient appointment actions and queries use only the patient profile currently linked to the authenticated Patient account. Helping another patient use that patient's account creates no authority for the helper's own account. Separate family clinical-record/results sharing requires its own scope and review. This supersedes the former confirmed-dependent booking rule when this Context revision is approved. |
| D04 | CONFIRMED | Reject shift change/removal until uncovered bookings are rescheduled/cancelled or successfully reassigned so they no longer depend on the affected shift; other conflict triggers require separate policy. |
| D05 | CONFIRMED | Ordinary reschedule preserves patient, Doctor, service and purpose; reassignment is separate. |
| D06 | CONFIRMED | Receptionist reassigns only `CONFIRMED` appointments to a different eligible, available Doctor at the same slot; reason/audit required, other booking fields preserved. Success resolves D04 shift dependency; failure is immutable. |
| D07 | CONFIRMED | Separate public visibility, new-booking acceptance, account access and existing-appointment eligibility. Account lock never automatically changes appointments; directory removal blocks new selection but allows authorized same-Doctor reschedule of eligible existing appointments. |
| D08 | CONFIRMED | Deactivation stops new bookings only; honor existing bookings and allow authorized reschedule/reassignment with stored duration/buffer snapshots when other eligibility rules pass. No automatic status change or occupancy release. |
| D09 | CONFIRMED | Follow-up derives patient from a source visit; ordering Doctor must have assigned care, completed their examination part and recorded a follow-up indication. Source appointment `COMPLETED` and visit closure are not required; persist `source_visit_id` and verify through the visit/clinical public contract. |
| D10 | CONFIRMED | Receptionist check-in atomically changes a `CONFIRMED` appointment, creates/links one visit and records initial reception/audit; rollback and concurrent/retry duplicate prevention required. Queue is a separate integration awaiting scope confirmation. |
| D11 | CONFIRMED | Receptionist identifies/creates a patient profile without requiring an account; reception/visit atomically creates a `WALK_IN` visit and initial reception/audit with retry/concurrency duplicate prevention. No synthetic appointment; capacity coordination follows D12. |
| D12 | CONFIRMED | Appointment reserves Doctor time only; reception/visit coordinates Doctor/room capacity. Supporting orders and same-visit return-for-results are visit steps; later examinations use new appointment/walk-in. Room readiness, expected duration/overruns and laboratory/external-results limits are recorded in Section 6.2. |

D01–D02 and D04–D12 remain confirmed; revised D03 awaits review. Supplementary v0.13.0 policies define completion versus visit closure, cancellation-after-check-in consistency, Admin operational time-off/assignment ownership, non-past booking and Patient reschedule notice. The user ultimately chose to exclude the pending-reschedule status from this version, superseding the earlier retention choice. Completed appointments preserve occupancy through stored `end_time + buffer_minutes`; resolution commands and incident evidence follow Section 5.5. The archived Report 3 source remains unchanged; proposed D03 records a user scope change and does not rewrite the baseline.

### Approval boundary versus downstream details

- CONTEXT approval fixes backend/API scope, actor/resource authority, business lifecycle, capacity/ownership and exclusions; it does not approve implementation execution.
- Queue implementation remains outside FEAT-002. Supporting-service catalog/laboratory scope belongs to separate product decisions and does not silently enter appointment.
- SPEC defines DTOs, errors, retry identity/response, check-in early/late and NO_SHOW policy with Human review; no restoration is implied by the current terminal-state rule.
- SPEC/PLAN and owning-feature contracts define duration estimation/overrun response, locks, concurrency/uniqueness, transaction orchestration, dependency order and acceptance tests before relevant execution. Public-contract design must avoid module dependency cycles.
- Technical details above do not need to be fully designed in CONTEXT; their required outcomes and ownership are binding.

- FEAT-001 must approve and implement compatibility changes for explicit account types, JWT/portal checks and independent patient-profile identity before FEAT-002 persistence can execute.
- Existing migration `V001__auth_schema.sql` is applied history and must not be edited; all schema changes are additive Flyway migrations.
- Exact API/error/schema contracts belong to `SPEC.md`; implementation order belongs to `PLAN.md`; execution evidence belongs to `TASKS.md`.

## AI Agent Recommendation

- Status: PENDING HUMAN REVIEW
- Recommendation: Review Context v0.16.0 revised D03 self-only appointment authority against the user's 2026-10-13 clarification. The partial T008 dependent relationship code and V012 cannot be treated as valid FEAT-002 delivery under this revision. Review SPEC/PLAN/TASKS changes and reconcile that code before implementation continues.
- Required human decision: `APPROVED`, `REVISE`, or `REJECTED` before implementation.

## Human Final Review — v0.16.0

- Status: COMPLETED.
- Decision: APPROVED.
- Reviewer: Owner Long (explicit user approval).
- Reviewed at: 2026-10-14 (Asia/Bangkok).
- Approved version: 0.16.0.
- Evidence: User explicitly instructed, "Approve luôn cho cả CONTEXT, SPEC và PLAN sửa lại luôn T008" on 2026-10-14; revised D03 states that appointments remain attributed to B's authenticated account when A helps, and A's separate account gains no authority over B.

## Historical Human Final Review — v0.15.0

- Status: COMPLETED
- Decision: APPROVED
- Reviewer: Owner Long
- Reviewed at: 2026-10-12
- Approval evidence: Explicit user instruction in this conversation to approve CONTEXT under Owner Long.
- Approved version: 0.15.0 (historical; D03 changed in proposed v0.16.0)
- Follow-up at that time: Use CONTEXT v0.15.0 as the approved source to revise SPEC, then PLAN/TASKS. This historical follow-up is superseded for D03 by proposed v0.16.0; downstream artifacts and implementation need new review.

## Historical Human Final Review — v0.4.0

- Status: COMPLETED
- Decision: APPROVED
- Reviewer: Owner Long
- Reviewed at: 2026-10-09
- Follow-up: Historical approval applies only to Context v0.4.0 and does not authorize this revised contract.
