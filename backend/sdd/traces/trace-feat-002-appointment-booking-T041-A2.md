# FEAT-002 T041 Architecture Follow-up Trace — A2

| Requirement / architecture rule | Implementation evidence | Verification |
| --- | --- | --- |
| Cross-feature data owner access goes through the owner contract | Staff provisions Doctor profiles through Auth-owned `DoctorAccountTypeQueryPort`; Appointment and Staff consume `common.security.AuthenticatedAccount` / `AccountRole`. | Static import scan clean; `ProvisionDoctorProfileServiceImplTest` passed 2/2; local T011 PostgreSQL integration passed in full suite. |
| Feature service implementations call their own repositories; repositories do not depend on services | Appointment service implementations inject feature Spring Data repositories; persistence contracts/queries are in `appointment.repository`. | Static repository-to-service scan clean; source and test compile passed. |
| Business services follow `<Action><Entity>Service` and `...ServiceImpl` | Renamed Appointment, Staff, Patient and Service Catalog business service contracts/implementations. | Static scan found no production `*UseCase` contract or business `*Handler` implementation; focused service tests passed. |
| JPA entity classes use the `JpaEntity` suffix without changing table names | Renamed eight Auth entities; existing `@Table` values remain unchanged. | Static scan confirms names and mappings; compilation passed. |
| Appointment-owned persistence operations are declared on Spring Data repositories | Removed seven internal `Appointment*Port` interfaces; queries and guarded commands are declared on the corresponding `Appointment*Repository extends JpaRepository`. `CurrentPatientProfileQueryPort` remains for Patient-owned profile lookup. | Static scan leaves only `CurrentPatientProfileQueryPort` under Appointment repository; `./mvnw.cmd -DskipTests test-compile` passed. Transaction annotations remain on cancel, check-in, reassignment and reschedule service operations; guarded repository methods retain `FOR UPDATE`/atomic update/audit/replay SQL. |

## Verification record

- `./mvnw.cmd -DskipTests test-compile`: BUILD SUCCESS.
- Focused tests: 12 passed, 0 failures/errors/skips after runtime triage.
- Full PostgreSQL suite via `scripts/test-local.ps1`: 212 passed, 0 failures/errors/skips. T012 follow-up evidence and T021 rate-limit integration passed after correcting parameter/result mapping and bucket-key equality. Local database only; no remote database was contacted.
- Independent read-only architecture review: prior repository dependency finding resolved; no blocking issue found in the requested boundaries.

## State

Implementation and verification evidence recorded. T041 is not marked complete pending Owner Long disposition.
