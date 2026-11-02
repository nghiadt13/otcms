# KAN-24 patient administrative profiles

## Scope and source

Primary source: the user's explicit KAN-24 implementation instruction in this session.
KAN-22 epic content was not available. The ticket supplies matching, create, detail,
update, DTO, authorization, error, and legacy migration behavior directly.

Branch: `feat/hung/KAN-24-patient-profile-admin-fields`; audited base `193fc74`.
Before implementation, `origin/product` contained migrations through V047 and no V048.

## Shadow Plan and Action Record

- Authorization/checkpoint: the user explicitly instructed implementation, including the
  four auth-owned persistence files, V048, Patient APIs, errors, OpenAPI, and tests.
  This records the instruction; it does not invent a reviewer identity or grant agent approval.
- File boundary: Patient code/tests; auth-owned patient profile entity, enum, repository
  and directory; one additive OpenAPI customization; V048; this action record.
- Plan: verify Git/migration tree, add migration/persistence, extend Patient operations,
  add scoped errors/OpenAPI, run focused tests, run full test and clean verify, inspect diff.
- Existing search, reception verification, legacy service overload and create response
  projection remain available. New create requests require address as specified by KAN-24.
- No historical migrations, dependencies, shared ErrorResponse, SecurityConfig,
  appointment, billing, review, notification or visit implementation were changed.
- No commit or push is authorized or performed.

## API behavior

All four KAN-24 operations require `ROLE_RECEPTIONIST` and an active receptionist account.

| Method | Path under `/api/v1/patient-profiles` | Result |
| --- | --- | --- |
| GET | `/matches?phone=&dateOfBirth=&fullName=` | `{items: PatientProfileSummary[]}` |
| POST | root | 201, existing four-field create response |
| GET | `/{id}` | Administrative detail |
| PUT | `/{id}` | Updated administrative detail/version |

Phone uses the existing canonicalizer and exact database equality. Names use trim,
collapsed whitespace and NFC; database equality ignores case but preserves accents.
Full name refines only multiple phone/DOB candidates. Zero matches returns empty items;
more than one remaining candidate returns `PATIENT_MATCH_AMBIGUOUS`.

Create checks canonical phone + DOB + normalized name under the existing coordination
guard, and persists a null account link. National ID is optional, non-unique and at most
20 characters; an ID requires a valid document type. PUT preserves account linkage,
allows legacy null address, compares the submitted version and flushes a managed JPA
entity with `@Version`. It does not assign the client version or add a duplicate rule.

Responses have explicit administrative DTO whitelists; no account entity or clinical data.

## Migration

`V048__align_patient_profile_administrative_fields.sql` widens address to TEXT, renames
citizen_id to national_id and narrows it to VARCHAR(20), adds a non-unique national ID
index, nullable document type with CHECK, and BIGINT version NOT NULL DEFAULT 0.
Legacy document types remain null. A preflight raises a clear exception for any legacy
identifier longer than 20 characters; no truncation or destructive replacement occurs.

## Errors

The existing envelope remains unchanged. Application codes are in `error.code`;
`error.message` contains the corresponding business message. Optional field validation
errors remain at the response root with `code: MSG-17`. Correlation IDs use the existing
request correlation mechanism.

| HTTP | Application code | Message meaning |
| --- | --- | --- |
| 400 | VALIDATION_FAILED | MSG-17 |
| 409 | PATIENT_MATCH_AMBIGUOUS / PATIENT_PROFILE_DUPLICATE | MSG-18 |
| 404 | PATIENT_PROFILE_NOT_FOUND | MSG-19 |
| 403 | ACCESS_DENIED | MSG-19 |
| 409 | STALE_VERSION | MSG-16 |
| 500 | MSG-04 | MSG-04 |

The legacy FORBIDDEN code remains supported for existing internal checks. Expected
coordination failures retain the existing shared database/coordination mapping.

## Verification record

- Focused selection: `PatientProfileServiceTest,PatientProfileControllerTest,PatientProfileReceptionVerificationT046Test,PatientProfileT007PostgresIntegrationTest`.
- Focused final result: 50 tests, zero failures/errors/skips, including nine real PostgreSQL tests.
- Exact requested commands: `.\mvnw.cmd test`, `.\mvnw.cmd clean verify`.
- Initial in-sandbox wrapper bootstrap failed before Maven with `Cannot index into a null array`,
  as previously documented in `docs/uc08-uc09-administrator-accounts.md`. Outside the sandbox,
  the exact requested wrapper commands successfully started Maven and ran the full suites.
- Focused verification uses that document's cached Maven 3.9.16 executable, JDK 21, and
  `-Dmaven.repo.local=C:/Users/ad/.m2/repository`; no dependency/tool version changes.
- Initial database attempts failed because PostgreSQL was stopped and test password
  binding was absent. An isolated local container on port 5433 uses the documented pinned
  PostgreSQL image and a generated credential supplied through environment configuration.
  Existing container credentials/data were not altered; credentials are not recorded here.
- An initial dirty build contained stale compiled V044/V045 resources from earlier
  branches. Maven clean removed these build artifacts; historical source migrations stayed intact.
- Initial HTTP test restubbing error was fixed in the test fixture; no business-rule workaround.
- Exact `.\mvnw.cmd test`: 629 tests, 7 failures, 8 errors, zero skipped; BUILD FAILURE.
- Exact `.\mvnw.cmd clean verify`: 629 tests, 7 failures, 8 errors, zero skipped; BUILD FAILURE.
- All KAN-24 suites pass in both full runs. A redundant cached-Maven rerun was stopped
  after the exact requested commands and baseline comparison had completed.
- Baseline verification used `git archive HEAD backend`, extracted under the OS temporary
  directory without a checkout/branch change or edits to baseline source. The six failing
  suites were run there against an isolated schema: 30 tests, the same 7 failures and
  8 errors. The failing method lists are identical to the full clean-verify run.
- Baseline failing suites: `AppointmentArrivalT053PostgresIntegrationTest` (2 failures),
  `AdministratorAccountDatabaseVerificationTest` (1 error), `T005RealLoginIntegrationTest`
  (1 failure), `T008PasswordRecoveryPostgresIntegrationTest` (1 failure),
  `T032AdminResourceMutationPostgresIntegrationTest` (3 failures), and
  `ClinicServicePostgresIntegrationTest` (7 database authentication/context errors).
- No unrelated baseline implementation or tests were modified to hide these failures.
- Evidence logs: `target/otcms-kan24-focused.log`, `target/otcms-kan24-wrapper-test.log`,
  `target/otcms-kan24-wrapper-verify.log`, `target/otcms-kan24-baseline.log`.
- PostgreSQL tests use isolated schemas; committed concurrency/upgrade fixtures remain
  available for inspection. No existing test or application data is deleted by KAN-24 tests.
- Final scope/whitespace check: `git diff --check` PASS. No tracked migration V001-V047 changes.
- Final Git state: correct branch; 15 modified and 8 new/untracked files; nothing staged;
  no commit/push. `git diff --stat` reports 15 tracked files, 766 insertions and 14 deletions;
  the eight new files are separately listed below and are not included in that tracked-only stat.

## Residual risks

- Full project verification is not green because of the reproduced baseline failures above.
- Deploying V048 to a database containing legacy citizen IDs longer than 20 characters
  intentionally fails and requires data reconciliation; existing identifiers are never truncated.
- Legacy phone values from other writers may be noncanonical. KAN-24 canonicalizes its own
  writes and queries exactly; no unrelated auth data backfill is included.
- PostgreSQL test container `otcms-kan24-postgres-test` remains local on loopback port 5433;
  test schemas and the temporary baseline archive remain for inspection.

## Review

AI Agent Recommendation: PENDING HUMAN REVIEW.
Human Final Review: PENDING; reviewer, decision and timestamp are for the authorized human.
Sync-back: this document records the implemented contract, bounded scope, commands,
verification evidence and residual risks. It does not change project architecture or approval policy.

## File inventory

Paths below are relative to `src/main/java/fu/fa26/sep490/g58/otcms_be` unless specified.

Modified:

- `auth/model/entity/PatientProfileJpaEntity.java`
- `auth/repository/PatientProfileRepository.java`
- `auth/service/PatientProfileDirectory.java`
- `auth/service/impl/PatientProfileDirectoryServiceImpl.java`
- `config/OpenApiConfiguration.java`
- `patient/controller/PatientProfileController.java`
- `patient/exception/PatientProfileException.java`
- `patient/exception/PatientProfileExceptionHandler.java`
- `patient/mapper/PatientProfileMapper.java`
- `patient/model/PatientProfile.java`
- `patient/model/dto/request/CreatePatientProfileRequest.java`
- `patient/service/PatientProfileService.java`
- `patient/service/impl/PatientProfileServiceImpl.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/PatientProfileServiceTest.java` (previously empty)
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/PatientProfileT007PostgresIntegrationTest.java` (previously empty)

Added:

- `auth/model/entity/enums/IdentityDocumentType.java`
- `patient/model/dto/request/UpdatePatientProfileRequest.java`
- `patient/model/dto/response/PatientProfileSummaryResponse.java`
- `patient/model/dto/response/PatientProfileDetailResponse.java`
- `patient/model/dto/response/PatientProfileMatchesResponse.java`
- `src/main/resources/db/migration/V048__align_patient_profile_administrative_fields.sql`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/PatientProfileControllerTest.java`
- `docs/kan24-patient-profile-administrative-fields.md`
