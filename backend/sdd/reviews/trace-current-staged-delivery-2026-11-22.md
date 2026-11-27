# SDD Trace — staged delivery snapshot (2026-11-22)

- Scope: current staged diff from `HEAD 0deb8373`; staged patch fingerprint before adding this report: `33747e6e876548fccd81f7e2d23f65101080d390`.
- Ownership: `team`; execution: `direct` (`backend/sdd/shared_context.md`, v3.3.9).
- Trace mode: read-only manual trace using the `sdd-trace` procedure. No `/sdd-trace` executable is registered in this session. Do not treat this report as an approval or as a completed `/sdd-audit`.
- Canonical feature artifacts inspected: `feat-001-auth-login` SPEC v1.2.0 and `feat-002-appointment-booking` SPEC v0.9.2; current Plans/Tasks/API contract and shared context.

## Requirement traceability and impact

| Scope in staged diff | Canonical requirement / contract | Code and tests | Result |
| --- | --- | --- | --- |
| Appointment DTO, query mapping, reminder candidate boundary and reminder persistence | FEAT-002 SPEC v0.9.2 documents AppointmentResponse at §API (including fields, but not `patientCode`) and appointment workflows. `patientCode` is an API contract addition not in this locked response shape. | `backend/src/main/java/.../appointment/`; `backend/src/main/java/.../notification/`; related appointment/reminder tests | `DRIFT`: additive patientCode response change needs contract owner/feature Spec disposition. Do not claim the current SPEC covers it. |
| Patient display code and migration overflow correction | No canonical patient-profile SPEC/REQ is present in `backend/sdd/features/`; conversational user request is not a persisted REQ. FEAT-002 §5.2 explicitly says receptionist profile search must not assume patient code exists. | V145 adds code; V149 replaces truncating default with sequence-backed function; `PatientProfileT007PostgresIntegrationTest.patientCodeSequenceExpandsPastSixDigitsWithoutTruncation` | `MISSING REQUIREMENT` for product/API semantics; technical regression test is present. |
| Doctor account display name | FEAT-001 SPEC v1.2.0 defines `/api/v1/me` displayName for authenticated patients; doctor account-profile naming from the admin user account is not covered by a current REQ. The admin account-list response uses `staffProfile.displayName`. | `MyAccountQueryServiceImpl` prefers `StaffProfileAccountQueryService`'s name for doctors; `MyAccountServiceImpl` denies doctor self-renaming; `MyAccountQueryServiceImplTest`, `Kan29MyAccountPostgresIntegrationTest` | `MISSING REQUIREMENT`; requested behavior has regression coverage but no canonical doctor/profile requirement. |
| Activity log, billing, clinical examination/ordering, diagnostics, imaging, laboratory, reporting, staff resources, visit workflow, frontend doctor navigation/screens, startup scripts and public site | No corresponding current feature SPEC exists in `backend/sdd/features/` for these changed capabilities. Proposals/screenshots and architecture docs are not canonical business requirements. | Changed paths are discoverable in staged diff; relevant test files exist for a number of backend and frontend behaviors. | `ORPHAN / MISSING REQUIREMENT`: cannot map to UC/REQ without approved feature artifacts. Do not infer UC/BR/MSG identifiers. |
| Idempotency persistence and JPA/layered refactors; split-wide-table migrations | `backend/CONSTITUTION.md` ARCH-01/02, Architecture Profile v1.3.0, and the repository's layered architecture rules establish architectural constraints. Shared-context contract coverage is not a substitute for feature behavior requirements. | `idempotency` service/repository/entity/dto; JPA repository adapters; V137–V148 migrations and migration tests | Architecture alignment can be reviewed; business trace remains incomplete where no current feature SPEC/REQ exists. |
| Imaging DICOM identity migrations | V146/V147 preserve the local migration history placement; database schema behavior is verified by the migration/integration suite. | `V146__persist_imaging_dicom_identity.sql`, `V147__enforce_imaging_dicom_identity.sql`, imaging migration tests | Version placement follows the checked branch history; feature requirement trace is not established by a migration test alone. |

## Stale, drift and orphan checks

- `DRIFT`: FEAT-002 v0.9.2 `AppointmentResponse` does not include `patientCode`; current code adds it. This needs a Human/contract-owner disposition and, if accepted, an approved Spec/API-contract update through the normal route.
- `MISSING REQUIREMENT`: current feature registry only provides FEAT-001 and FEAT-002. Multiple changed product capabilities in this delivery lack canonical SPEC/REQ and cannot be signed off through traceability.
- `MISSING EXECUTION EVIDENCE`: no current `/sdd-audit` command/tool is available in this session. The audit is mandatory for backend source changes under the delivery gate; this manual trace does not replace it.
- Existing T045 restrictions remain applicable; this report does not claim external T045 evidence or a new T045 approval.
- No TASKS checkbox, SPEC, shared contract, or UC/REQ assignment is changed by this report.

## AI recommendation

**PENDING HUMAN REVIEW.** Do not mark this delivery SDD-complete or commit-ready yet. First obtain the required audit evidence and disposition the FEAT-002 response drift and missing canonical feature requirements. A Human/feature owner should decide whether to split this delivery into reviewed feature snapshots or create/approve the missing SDD artifacts. Preserve V145/V146/V147/V148; use a forward migration for subsequent fixes.

## Residual risk

The passing backend suite demonstrates local source, migration and integration behavior against the isolated PostgreSQL test database. It does not authorize applying these migrations to a shared/production database, validate external DICOM/S3 credentials, or resolve missing product requirements and API compatibility decisions.
