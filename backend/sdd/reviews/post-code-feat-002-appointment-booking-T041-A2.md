# T041 Follow-up Post-code Review — A2

## Scope

Review the architecture findings raised during the 2026-10-16 T001–T027 source review: Auth data ownership, Appointment persistence dependency direction, service naming, and Auth JPA entity naming. No API/schema change was intended.

## Findings and disposition

| Finding | Disposition | Evidence |
| --- | --- | --- |
| Staff reads Auth repository and `AccountType` directly | Resolved | Staff uses `DoctorAccountTypeQueryPort`; its Auth-owned JPA adapter checks account existence and Doctor type and does not impose active-status checks. |
| Appointment and Staff depend on Auth's JWT principal | Resolved | Shared principal and role now live in `common.security`; Appointment controllers/services use `AccountRole`. |
| Appointment repositories import service-owned persistence ports | Resolved | Appointment repository package has no imports from `appointment.service`; Appointment service implementations inject concrete feature repositories. |
| Business service names use `*UseCase`/`*Handler` | Resolved for business services | Production scan found no remaining `*UseCase` interface or business `*Handler implements *UseCase`; exception handlers/helpers keep responsibility-specific names. |
| Auth JPA entities lack `JpaEntity` suffix | Resolved | All eight Auth entities use `*JpaEntity`; existing `@Table` names are preserved. |

**Superseded by A3 (2026-10-16):** the seven Appointment-owned persistence operation interfaces were removed. Their query and command declarations now live directly on the concrete `Appointment*Repository extends JpaRepository` interfaces; projection/value types live in `appointment.model`. `CurrentPatientProfileQueryPort` remains as the cross-owner Patient-profile boundary. The guarded lock/update/audit/replay methods remain called inside the service transaction boundaries listed in A3 evidence.

Runtime triage also found two existing defects while running the full suite: rate-limit buckets used identity-only map keys, and Visit follow-up evidence mixed positional/named parameters and mapped result columns incorrectly. Both were corrected without changing API/schema behavior. Value equality was added to `CheckedInAppointment` and `PublicRateLimit` so value assertions behave as intended.

## Verification

- `./mvnw.cmd -DskipTests test-compile` — BUILD SUCCESS; production and test sources compile.
- Focused tests — BUILD SUCCESS, 12 tests, 0 failures/errors/skips after runtime triage: `CheckInAppointmentServiceImplT027Test`, `PublicRequestRateLimiterTest`, `AppointmentBookingPropertiesTest`.
- Approved local PostgreSQL test helper — BUILD SUCCESS; 212 tests, 0 failures/errors/skips. Surefire reports refreshed at 2026-10-16 22:44 (+07:00), including T011, T012 and T021 integration coverage.
- Static scans: no Auth repository/entity-enum/JWT-principal imports in Staff or Appointment; no Appointment repository imports from `appointment.service`; no remaining `*UseCase` business contracts or `*Handler` business implementations.
- No remote/Supabase database was accessed; the approved helper used only `127.0.0.1:5432/otcms_test_db`.

## Status

Code review found no blocking issue in the corrected architecture boundaries. Historical status at review time: pending Owner Long disposition; superseded by the Human Final Disposition below.

## Human Final Disposition

- Status: APPROVED — implementation and architecture review; T041 closed.
- Decision: Owner Long approved closure in conversation on 2026-10-18.
- Basis: corrected architecture boundaries, independent review, and local PostgreSQL suite 212/212 with 0 failures/errors/skips as recorded above.

