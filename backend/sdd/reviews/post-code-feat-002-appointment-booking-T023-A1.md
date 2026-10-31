# T023 Post-Code Review — A1 (Final Approval Pending)

Date: 2026-10-14 (Asia/Bangkok)\
Status: COMPLETE — implementation, independent review, focused tests, PostgreSQL HTTP tests, and local full-suite verification passed; Owner Long approved implementation and trace on 2026-10-14.

## Scope reviewed

T023 implements `GET /api/v1/appointments` and `GET /api/v1/appointments/{id}`. Patient scope resolves the currently linked profile and calls the Patient authorization port; Doctor scope resolves the authenticated account's current Doctor profile through `StaffDirectoryPort`; Receptionist scope is clinic-wide. Admin/Owner/other authenticated roles receive correlated `ACCESS_DENIED` 403. Detail access uses a single SQL query with owner scope in the predicate and returns the same `APPOINTMENT_NOT_FOUND` 404 for missing and out-of-scope records. List filters are conjunctive with actor scope.

## Verification to date

- `./mvnw.cmd '-DskipTests' test-compile`: BUILD SUCCESS, 210 production sources and 53 test sources compile.
- `./mvnw.cmd '-Dtest=GetAppointmentsHandlerT023Test' test`: BUILD SUCCESS, 6 tests, 0 failures/errors/skips (2026-10-14 20:43:12 +07:00).
- First user-run approved `./scripts/test-local.ps1` was pinned to `127.0.0.1:5432/otcms_test_db`; Flyway validated 13 migrations at v013. Suite summary: 186 tests, 0 assertion failures, 2 errors, both in the new T023 HTTP integration tests because PostgreSQL could not infer the SQL type for `java.time.Instant` date-bound parameters. Fixed both bounds to bind `Timestamp.from(instant)`; independent review confirmed this matches existing adapter conventions. Rerun corrected the issue; final verification below.
- User-run approved `./scripts/test-local.ps1` from `backend` completed `BUILD SUCCESS` at 2026-10-14 20:57:05 +07:00. It was pinned to `127.0.0.1:5432/otcms_test_db`; 186 tests, 0 failures/errors/skips. Flyway validated 13 migrations at v013. Aggregated 51 Surefire XML reports confirm 186/0/0/0; newest report timestamp 20:57:05 +07:00.
- `git diff --check` reports only the existing `.gitignore:28` whitespace issue (`test-run.log`); no T023-specific whitespace finding was reported.

## Review findings and disposition

- Independent review found a detail authorization TOCTOU between owner-ID read and full DTO read. Fixed by replacing both calls with one owner-scoped SELECT; list/detail application reads now run under `REPEATABLE_READ` so current identity resolution and appointment scope use one database snapshot. Independent re-review confirmed the race is closed and found no remaining race in this path.
- Independent debug review found an Auth access-status gap. It was initially left pending the Human checkpoint and is now addressed through the approved Auth-owned contract below.

## Approved Auth checkpoint update

Owner Long approved adding the Auth-owned read-only `DoctorAccountAccessPort` on 2026-10-14. Contract `doctor-account-access-read` v1.0.0 is frozen in `shared_context.md` v3.3.8. The Auth persistence adapter returns only an active Doctor boolean; Appointment denies non-ACTIVE Doctor accounts before resolving their Staff profile. A dedicated locked-account HTTP assertion was added. Independent code review, debugger review, and fixture/coverage review found no remaining concrete issues. After fixing the initial PostgreSQL Instant binding error, focused tests pass 6/6 and test-compile passes. Local suite rerun passed. Owner Long approved `APPROVE implementation and trace T023` on 2026-10-14.

## Human checkpoint and final approval


SPEC REQ-BOOK-004 and CONTEXT D07 require account lock to block Doctor access while leaving appointment capacity/state untouched. The approved port resolves this within T023 without exposing Auth persistence. Its contract adds no schema or account mutation behavior.

No existing contract, migration, production/Supabase data, commit, or push was changed; one additive Auth contract was approved and frozen for T023.








## Human disposition

Owner Long approved `APPROVE implementation and trace T023` on 2026-10-14 (Asia/Bangkok). T023 is complete.
