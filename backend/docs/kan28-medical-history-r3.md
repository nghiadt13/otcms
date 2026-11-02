# KAN-28 — Medical history R3

## Shadow Plan / Action Record

- Source and Human checkpoint: explicit H1–H20 implementation instruction in this session.
- Branch: feat/hung/KAN-28-medical-history, intentionally stacked on KAN-26 commit 6e12253.
- Preflight: clean tree; V049 present, V050/V051 absent, highest V072. V050 remains reserved.
- Approved boundary: new Patient controller/service/repository/entity/DTO, temporary doctor
  port/adapter, scoped error advice, V051, focused tests and this document. Existing Patient
  package conventions are retained as requested. No unrelated production changes.
- Plan: migration/conditional persistence; actor/profile authorization; HTTP contract;
  PostgreSQL concurrency/security/regressions; full test and clean verify; baseline comparison.
- Exact authorized verification: focused Maven tests, .\mvnw.cmd test, .\mvnw.cmd clean verify,
  git diff --check, git status, git diff --stat; JDK 21 and dedicated local PostgreSQL.
- No rebase, historical migration edits, dependency additions, formatter, commit or push.
- Results and sync-back: recorded below after execution. PENDING HUMAN REVIEW; this record
  does not assert Human approval of the resulting implementation.

## Stacked dependency and migration

KAN-28 remains stacked on origin/feat/hung/KAN-26-dependents-r3 (6e12253); it has not been
rebased. KAN-27 is not a technical dependency. Recheck the migration tree before future
rebasing/deployment. V049 is unchanged; V050 remains reserved for KAN-27.

V051__create_patient_medical_histories.sql adds one table only. patient_profile_id is the
PK and FK to patient_profiles, with RESTRICT deletion; updated_by_account_id is an accounts
FK with RESTRICT deletion and an index. Five VARCHAR(2000) texts are NOT NULL/default empty.
updated_at is required. Persisted version defaults to 1 and is constrained >=1. No id,
created_at, JSON, soft deletion or event table is added. Highest repository migration is V072.
The existing Flyway out-of-order setting allows V051 after V072. Fresh and upgrade tests
also verify existing V049 history checksum and accountless profile data are retained.

## API contract

GET and PUT /api/v1/patient-profiles/{id}/medical-history require active PATIENT or DOCTOR.
Both return Cache-Control: no-store. The old frontend /patients URL is not exposed as an alias.

The response contains exactly conditions, allergies, procedures, injuries, orthopedicHistory,
updatedAt, updatedBy and version. No row returns five empty strings, explicit null updatedAt
and updatedBy, and version 0. GET never inserts. No profile/account entity or role/email is
serialized. updatedAt is an ISO timestamp with clinic offset +07:00. updatedBy is the current
display name resolved through the public MyAccountQueryService for the stored editor UUID;
it is not a UUID or account object and no name snapshot is stored. Missing/blank display
name resolves to null; an unexpected lookup failure uses the sanitized shared error contract.

PUT replaces all five texts and requires an integer version >=0. Every text must be a JSON
string, present and non-null. Scoped Jackson deserializers reject number/boolean/container
coercion and decimal/string/overflow version coercion without altering shared JSON behavior.
Texts are trimmed with String.trim(), then limited to 2000 UTF-16 code units; empty strings
are allowed. Internal whitespace, newlines, accents and decomposed characters are retained.
No truncation or clinical-content normalization is performed. PostgreSQL VARCHAR counts
Unicode characters; application validation enforces the stricter Java/JS UTF-16 boundary.

## Authorization and privacy

PATIENT requires actual active PATIENT account validation, then calls KAN-26
PatientProfileAccessService.canAccess before profile/history lookup. False gives identical
404 PATIENT_PROFILE_NOT_FOUND for unrelated, missing, pending OTP/counter and revoked targets.
The helper remains unchanged, including missing-self fail-closed and accountless confirmed
dependent access. KAN-28 production code does not query patient_caregivers.

DOCTOR requires the principal role and actual active DOCTOR account. The public Patient
DoctorCareRelationPort is then checked before profile existence. Its temporary R3/KAN-28
adapter explicitly permits any active doctor without workspace/visit responsibility lookup,
and rejects null, inactive and non-doctor accounts. Replace this adapter when a responsible
doctor/workspace relationship exists. A future false relation returns 403 ACCESS_DENIED.
No DoctorProfile or workspace assignment is required by the temporary policy.

BR-12: RECEPTIONIST never receives this data. ADMIN, OWNER and both technician roles are
also denied GET/PUT with ACCESS_DENIED. Anonymous/CSRF behavior is unchanged. BR-13: the
future main doctor/workspace flow should consume this API; that flow is not modified here.

## Version, transactions and concurrency

No row exposes version 0. Version-0 PUT uses INSERT ... ON CONFLICT(patient_profile_id)
DO NOTHING, storing version 1. Affected count 0 means STALE_VERSION, including concurrent
first-create loss. Positive versions use UPDATE ... WHERE patient_profile_id=:id AND
version=:expected, explicitly incrementing version and setting actor/time. Count 0 means
STALE_VERSION; a positive version cannot create a missing row. Unchanged text still increments.

There is no JPA @Version or save/merge write path. Both statements flush/clear the persistence
context, so OpenEntityManagerInView cannot expose or flush stale managed history state.
Each successful write is read back in the same transaction. The short common coordination
guard encloses target authorization and PUT mutation, serializing with existing KAN-26 revoke.
Only local DB work happens while held. GET/doctor reads do not acquire the guard. A committed
revoke prevents subsequent patient reads/writes, without deleting profile or medical history.

## Errors

The scoped advice keeps shared ErrorResponse unchanged: nested error.code/message/timestamp/
correlationId and optional root fieldErrors. It does not add messageCode or change sibling APIs.

| HTTP | Code | Semantics |
| --- | --- | --- |
| 400 | VALIDATION_FAILED | MSG-20; field errors also use MSG-20 |
| 403 | ACCESS_DENIED | MSG-19; wrong role, inactive actor, denied doctor relation |
| 404 | PATIENT_PROFILE_NOT_FOUND | MSG-19; inaccessible/missing patient target or missing doctor target |
| 409 | STALE_VERSION | MSG-16; create conflict or conditional update miss |
| 500 | MSG-04 | Unexpected; no internal error details |
| 503 | DEPENDENCY_UNAVAILABLE | Existing guard/DB timeout/deadlock convention |

## Frontend follow-ups (not implemented)

- Change mock URL /api/v1/patients/{id}/medical-history to the R3 patient-profiles URL.
- Detect stale using backend error.code == STALE_VERSION (ApiError.errorCode), instead of
  ProblemDetail detail/message == STALE.
- Replace old MSG-17 / MSG-40 / MSG-41 expectations with the R3 application-code/error mapping.
- Mock currently validates raw length; the backend limit is after trim. Keep five string
  fields, display-name updatedBy, explicit null metadata for no row, and version 0 -> 1.
- No frontend, doctor workspace, visit, appointment or examination implementation is changed.

## Manual checks

1. Apply Flyway to a local database, including out-of-order V051 after V072 if applicable.
2. Authenticate an active patient with own profile and obtain the existing CSRF token.
3. GET medical-history: empty strings/null metadata/version 0; confirm no row was created.
4. PUT all five strings and version 0; expect version 1, trimmed content and server actor/time.
5. Repeat with version 1, including unchanged content; expect version 2. Resubmit 1 -> 409.
6. Save through active doctor credentials without a workspace assignment; verify editor name.
7. For a confirmed accountless dependent, repeat GET/PUT. Revoke via existing KAN-26 API;
   subsequent patient GET/PUT must be 404; persisted data and profile must remain.
8. Verify pending links, unrelated targets and receptionist/other roles cannot read/write.
9. Send null/missing fields, >2000 after trim, fractional/string/negative versions: expect 400.
10. Inspect /v3/api-docs; keep CSRF and sensitive responses no-store. Do not log clinical data.

## Verification evidence

- JDK 21 and existing dedicated PostgreSQL 18.6 test containers on loopback 5433/5432,
  isolated per-run schemas, no production credentials. Logs live outside target at
  %TEMP%/otcms-kan28-evidence/ so clean cannot erase evidence.
- Incremental KAN-28 command: .\mvnw.cmd -Dtest=MedicalHistoryServiceTest,MedicalHistoryErrorWebTest,MedicalHistoryR3PostgresIntegrationTest test:
  68 tests PASS before the final additional metadata/missing-self assertions.
- Final complete focused selection: the three MedicalHistory suites plus the unchanged
  16-suite KAN-26/KAN-24/UC06 selection. Result: **232 tests, 0 failures, 0 errors, 0 skipped;
  BUILD SUCCESS**. KAN-28: **70 new tests PASS** (58 real PostgreSQL, 11 service, 1 error HTTP).
- PostgreSQL coverage includes exact empty/existing GET, all five field boundaries/types,
  strict versions, supplementary Unicode, metadata/time/rename, real access helper, all roles,
  active-account checks, anti-enumeration, CSRF, concurrent first-create/update HTTP (200/409),
  independent repository insert transactions (1/0), revoke/PUT race and post-revoke denial,
  fresh schema constraints and out-of-order V051 with unchanged V049 history/legacy profile.
- Focused KAN-26 suite includes real SMTP booking isolation/attempt preservation, confirmed
  dependent booking, pending/revoked denial and revoke concurrency; all PASS. KAN-24 and
  UC06 regressions also PASS. No existing production files were modified.
- Exact .\mvnw.cmd test: **865 tests, 7 failures, 1 error, 0 skipped; BUILD FAILURE**.
- The seven selected suites ran on a fresh git archive of untouched stacked base
  **6e12253** in the OS temporary directory, with no branch switch/rebase. Selection:
  AppointmentArrivalT053PostgresIntegrationTest, AdministratorAccountDatabaseVerificationTest,
  T005RealLoginIntegrationTest, T008PasswordRecoveryPostgresIntegrationTest,
  T032AdminResourceMutationPostgresIntegrationTest, ActivityLogPostgresIntegrationTest,
  ClinicServicePostgresIntegrationTest. Result: **33 tests, 7 failures, 1 error**. ActivityLog
  and ClinicService pass. All eight suite/method/kind entries exactly equal the feature full run.
- Archive integrity: 828 source/pom files match the exact 6e12253 archive bytes after baseline
  verification; no baseline source was patched. Logs: stacked-baseline.log, baseline-failures.json,
  full-test.log, full-test-failures.json. Generated credentials/proofs are not copied here.

| Suite | Method | Kind |
| --- | --- | --- |
| AppointmentArrivalT053PostgresIntegrationTest | timelyArrivalClaimCommittedBeforeNoShowBlocksNoShowAndLateClaimCannotReopenNoShow | failure |
| AppointmentArrivalT053PostgresIntegrationTest | timelyQrArrivalHoldingSharedGuardSerializesAheadOfNoShowAfterGrace | failure |
| AdministratorAccountDatabaseVerificationTest | upgradeFromV019PreservesExistingAccountsAndSessionLinks | error |
| T005RealLoginIntegrationTest | wrongPasswordsPersistLockoutAndSuccessResetsCounter | failure |
| T008PasswordRecoveryPostgresIntegrationTest | changingPasswordInvalidatesPreviouslyIssuedResetLink | failure |
| T032AdminResourceMutationPostgresIntegrationTest | mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites | failure |
| T032AdminResourceMutationPostgresIntegrationTest | adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits | failure |
| T032AdminResourceMutationPostgresIntegrationTest | checkedInAppointmentAlsoBlocksTimeOffCreation | failure |

- Exact .\mvnw.cmd clean verify: **865 tests, 7 failures, 1 error, 0 skipped; BUILD FAILURE**.
  All eight suite/method/kind entries again exactly match untouched stacked base 6e12253.
  Evidence: clean-verify.log, clean-verify-failures.json, clean-verify-results.json. The build
  stops in the test phase; full-project verification is not green. No failure was suppressed.
- KAN-28 final clean results: MedicalHistoryR3PostgresIntegrationTest 58/0/0/0,
  MedicalHistoryServiceTest 11/0/0/0, MedicalHistoryErrorWebTest 1/0/0/0: all 70 new tests PASS.
- KAN-26 real helper/dependents/revoke/booking and KAN-24/UC06 regression suites PASS.
  KAN-27, appointment, visit, billing, review, notification, auth/login/password, SecurityConfig,
  shared ErrorResponse, all historical migrations and all pre-existing production files are
  unchanged. No KAN-26 implementation was edited. V051 is the only added migration; V050 absent.
- Final Git: 15 new untracked files, zero modified tracked files, nothing staged. Consequently
  git diff --stat is empty for tracked changes. git diff --check PASS; additional whitespace/
  final-newline checks include every new file. HEAD remains 6e12253 on the KAN-28 branch.
- Dedicated test containers are stopped after verification. Archive and evidence logs remain
  available for review. No rebase, formatter, dependency additions, commit or push.

## File inventory

All files below are new; no existing file is modified.

- src/main/resources/db/migration/V051__create_patient_medical_histories.sql
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/controller/PatientMedicalHistoryController.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/exception/MedicalHistoryException.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/exception/MedicalHistoryExceptionHandler.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/model/dto/MedicalHistoryDtos.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/model/entity/PatientMedicalHistoryJpaEntity.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/repository/PatientMedicalHistoryRepository.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/DoctorCareRelationPort.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/PatientMedicalHistoryService.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/PatientMedicalHistoryServiceImpl.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/TemporaryR3DoctorCareRelationAdapter.java
- src/test/java/fu/fa26/sep490/g58/otcms_be/patient/MedicalHistoryErrorWebTest.java
- src/test/java/fu/fa26/sep490/g58/otcms_be/patient/MedicalHistoryR3PostgresIntegrationTest.java
- src/test/java/fu/fa26/sep490/g58/otcms_be/patient/MedicalHistoryServiceTest.java
- docs/kan28-medical-history-r3.md

AI Agent Recommendation: PENDING HUMAN REVIEW.
Human Final Review: pending; identity, decision and timestamp are for the authorized human.
