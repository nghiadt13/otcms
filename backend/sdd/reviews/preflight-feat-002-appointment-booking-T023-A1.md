# T023 Preflight — A1

Date: 2026-10-14 (Asia/Bangkok)\
Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T023`\
Status: Preflight persisted; grant G-feat-002-T023-A1 consumed immediately before first source edit. Owner Long approved the Auth access-port checkpoint on 2026-10-14; additive contract `doctor-account-access-read` v1.0.0 is recorded in shared_context v3.3.8.

## Approved scope and inputs

- Task T023 only: scoped `GET /api/v1/appointments` list/pagination and `GET /api/v1/appointments/{id}` detail.
- Requirements: approved `TASKS.md` v0.3.0 T023, `SPEC.md` v0.8.0 §2.2–2.3, §3.4, §4.1–4.3, §6, §8 AC01/AC02; approved `CONTEXT.md` v0.16.0 §§2–3; `PLAN.md` v0.4.0 and Architecture Profile.
- Identity/scope: Patient profile currently linked to account only (revised D03/T008 A3); Doctor appointments where current assigned Doctor profile matches authenticated Doctor; Receptionist whole-clinic. Other roles (including Admin and Owner) get endpoint 403. Missing/out-of-scope detail uses indistinguishable `APPOINTMENT_NOT_FOUND` 404.
- Contract: optional independent clinic-local `from`/`to` dates, inclusive; malformed dates or `from > to` are `VALIDATION_FAILED`; no booking-horizon clamp on history queries. Filters intersect actor scope. `page>=0`, default page 0; `size` default 20 and bounds 1..100; fixed order `startTime DESC, id DESC`; response `Page<T>` with `items,page,size,totalElements,totalPages`. Appointment DTO is the SPEC §4.1 `AppointmentResponse` only.
- Current completed work: T008/T013/T022 and T014–T021 are `[x]`; no list/detail implementation exists. V008/V013 already contain required fields/indexes. T022 currently embeds its response record in the create controller; T023 may extract it into a shared inbound-web DTO without changing its JSON shape.
- Existing Staff-owned frozen `StaffDirectoryPort` is documented to resolve account to Doctor profile. Reuse the public port for Doctor scope; do not query Doctor tables/entities/repositories from Appointment. Existing Patient current-profile query and `PatientAuthorizationPort` similarly remain the public boundary. The approved additive Auth-owned `DoctorAccountAccessPort` v1.0.0 checks that the authenticated Doctor account currently has ACTIVE access; no account status detail crosses the port.

## Shadow plan

1. Define Appointment-owned query input/output records and use-case port/handler; no HTTP/SQL in application/domain models.
2. Resolve actor scope before returning data: Patient through current linked profile plus current Patient authorization; Doctor through Staff's public account-to-profile port; Receptionist clinic-wide. A Patient/Doctor list whose owned profile no longer resolves safely returns an empty page; detail returns scoped 404. Caller filters are always intersected with resolved scope.
3. Implement parameterized Appointment-owned JDBC reads. Detail first reads only minimal patient/Doctor scope IDs, checks scope, then fetches the full DTO. List applies optional inclusive clinic-local date bounds, optional status/Doctor/patient filters, deterministic order, limit/offset, and count; do not join Patient/Staff tables or expose purpose to unauthorized actors.
4. Add authenticated list/detail routes, strict UUID/date/status/page validation, the shared safe correlated error envelope, and the exact response/page DTO shapes.
5. Add unit and local PostgreSQL HTTP tests for scope matrix, current profile-link behavior, filters (from-only/to-only/both/inclusive/reversed/old history), deterministic pagination, detail scoped 404, Admin/Owner 403 and DTO privacy.
6. Run focused tests and the approved local test suite, obtain independent implementation/test review, update trace and wait for Owner Long final approval. No migration, contract change, production access, commit or push.

## File boundary

- `backend/src/main/java/.../appointment/application/` — list/detail query input, result, port and handler.
- `backend/src/main/java/.../appointment/adapter/in/web/` — shared Appointment response DTO extraction if needed, query controller and scoped query error mapping.
- `backend/src/main/java/.../appointment/adapter/out/persistence/` — Appointment-owned parameterized list/count/scoped-detail query adapter only.
- Approved checkpoint extension: `backend/src/main/java/.../integration/contract/DoctorAccountAccessPort.java`; Auth-owned read-only adapter under `backend/src/main/java/.../auth/adapter/out/persistence/`; `shared_context.md` v3.3.8 and focused contract integration test. No Auth status writes or account data projection.
- `backend/src/test/java/.../appointment/` — T023 handler tests and `AppointmentQueryT023PostgresIntegrationTest`.
- `backend/sdd/reviews/`, `backend/sdd/traces/`, and FEAT-002 `TASKS.md` — T023 evidence and record.
- No Patient/Staff/Service/Visit persistence access, no shared contract changes beyond the specifically approved `doctor-account-access-read` addition, no migration edits, SecurityConfig change unless route security evidence requires it, production/Supabase access, or unrelated workflow commands.

## Exact verification and grant gate

- Focused unit: from `backend`, `./mvnw.cmd '-Dtest=GetAppointmentsHandlerT023Test' test`.
- PostgreSQL HTTP/full suite: only `./scripts/test-local.ps1` from `backend`, fixed to `127.0.0.1:5432/otcms_test_db`; do not inspect/output credentials.
- Governance: `shared_context.md` v3.3.7 resolves `Project Ownership: team`, `Agent Execution: direct`; approved Auth contract addendum is v3.3.8. Host/runtime replay enforcement remains UNVERIFIED.
- Grant: `G-feat-002-T023-A1`, attempt 1, consumer `codex-direct-session-T023-A1`, cooperative reference only. Persist DISPATCHED/UNCONSUMED before implementation; mark CONSUMED immediately before the first source edit. No source edit/test has occurred at this preflight point.
- Checkpoint: T023 API/scope/date/pagination requirements are approved. Owner Long approved the additive Auth-owned read-only Doctor access port on 2026-10-14 after review identified that REQ-BOOK-004/D07 lock enforcement was unavailable through the existing Staff contract. Scope extension is limited to the port, Auth query adapter, shared contract record and tests; no schema, account mutation, or other API change.
