# Requirement trace — FEAT-002 T020 A1

| Requirement | Implementation evidence | Verification/status |
|---|---|---|
| SPEC §4.2 reschedule availability scoped to Patient/Receptionist | `AppointmentAvailabilityController` route; method role gate; `GetAppointmentChangeAvailabilityHandler` | Dedicated PostgreSQL HTTP assertions pass for Patient, Receptionist, unauthorized Patient and Admin |
| REQ-BOOK-019–022 current self-profile ownership and scoped denial | Patient path delegates to `PatientAuthorizationPort`; denial maps to `APPOINTMENT_NOT_FOUND`; authorization precedes state disclosure | Unit test confirms denied Patient does not learn `CHECKED_IN` state |
| REQ-BOOK-023–025 old Patient deadline and new notice, equality inclusive | Handler checks old start against `Clock + patientChangeDeadlineMinutes`; filters generated slots at `Clock + onlineMinimumNoticeMinutes`; Receptionist earliest start is now | Unit deadline rejection and dedicated deadline HTTP assertion pass |
| REQ-BOOK-026/050 same Doctor and stored snapshots; self-overlap | Handler derives all identity/timing from the appointment projection; `SlotServiceContext.snapshot` excludes exactly current appointment | Unit captures snapshot and exclusion; dedicated same-Doctor/self-slot HTTP assertion passes |
| D08 inactive existing service / D05 removed Doctor reschedule exception | Reschedule uses stored service ID and assignment ownership; it does not require active catalog or current active-directory acceptance | Dedicated HTTP fixture sets service inactive and current Doctor outside active directory; assertion passes |
| SPEC §4.2 reassignment availability Receptionist-only, CONFIRMED, start >= now | Controller `ROLE_RECEPTIONIST`; handler enforces status and `BOOKING_TIME_IN_PAST` before generating replacement slots | Dedicated HTTP assertions pass for Receptionist success, Patient denial, past and non-changeable state |
| D06/REQ-BOOK-033–035 replacement eligibility and unchanged appointment context | Active assignment intersected with accepting active-directory Doctors; current Doctor removed; same original timestamp and snapshot slot context | Dedicated HTTP assertion confirms the sole replacement at the original slot |
| SPEC §6 error envelope/codes | `AppointmentAvailabilityExceptionHandler`: `APPOINTMENT_NOT_FOUND`, `APPOINTMENT_CHANGE_TOO_LATE`, `BOOKING_TIME_IN_PAST`, `APPOINTMENT_NOT_CHANGEABLE`, eligibility/window codes | Dedicated route assertions pass for applicable error codes |

## Disposition

Implementation review passed after correcting the two identified error codes and removing the unused catalog dependency. Focused tests pass 5/5. Owner Long approved T020 implementation and trace on 2026-10-14. The subsequent owner-provided local PostgreSQL suite passed 155/155 with `AppointmentChangeAvailabilityT020PostgresIntegrationTest` included and Visit T012 integration coverage 3/3. T020 is complete.
