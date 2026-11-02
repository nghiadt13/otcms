# KAN-25 small permission and read API changes

## Scope, authorization and action record

- Source: the user's explicit H1-H4 implementation instruction in this session.
- Branch: `feat/hung/KAN-25-small-permission-fixes`; starting HEAD/product `193fc74`, clean tree.
- Human checkpoint: the user explicitly requested implementation of H1, H2, H3 and H4,
  including the audit role correction, service-boundary reuse, tests and this evidence document.
  This records authorization, not an invented reviewer identity or agent self-approval.
- Shadow plan: implement and focus-test H1, then H2, then H3, then H4; run exact full
  test/clean verify; compare failures to untouched HEAD; inspect scope and Git diff.
- Scope: auth password validation/DTO/generation; two appointment method permissions
  and reassignment audit role; additive visit-owned staff room controller; staff-owned
  public doctor detail projection; focused tests/documentation.
- No migration, dependency, SecurityConfig or shared ErrorResponse changes. No KAN-24
  profile changes or KAN-14 implementation changes. No commit or push.
- Login verification remains unchanged, including legacy passwords and provider credentials.

## H1: BR-04

The single production `PasswordPolicy` for new local passwords requires 8-64 Unicode
code points, at most 72 UTF-8 bytes, at least one Unicode letter and at least one Unicode
digit. It never trims or normalizes the password. Uppercase/lowercase/symbols/ASCII-only
are not additional requirements. `abcd1234` is valid; letters-only and digits-only are invalid.

Registration delegates to that policy and retains confirmation, consent and name validation.
Reset/change already delegate to it. DTO password/confirmation `@Size` restrictions were
removed because they count UTF-16 units; `@NotBlank` remains and the service owns the limits.

Temporary passwords still use 32 SecureRandom bytes per candidate, encoded as 43
Base64URL characters. Candidates failing BR-04 are regenerated, not patched with predictable
characters. Existing administrator/staff issuance and reissue flows retain their wiring.

## H2: reassignment

Only these permissions expand from RECEPTIONIST to RECEPTIONIST + ADMIN:

- POST `/api/v1/appointments/{id}/reassign`
- GET `/api/v1/appointments/{appointmentId}/reassignment-availability?from=&to=`

Audit insertion derives actor_type from the actual account row using the bound actor ID,
instead of hard-coding RECEPTIONIST. The existing audit CHECK already permits ADMIN.
Command/service signatures, coordination, same-slot rules, eligibility, notifications and
other appointment mutations are unchanged. ADMIN still cannot cancel, reschedule, read
reschedule availability, change status or check in.

## H3: staff rooms

GET `/api/v1/staff/rooms` allows RECEPTIONIST, DOCTOR, ADMIN, OWNER, LAB_TECHNICIAN and
IMAGING_TECHNICIAN. PATIENT is forbidden and anonymous callers get 401.

It calls `ManageVisitRoomService.listAll()` and reuses `VisitRoomMapper`/`VisitRoomsResponse`.
The response is `{items: VisitRoomResponse[]}`, including active/inactive rows in the existing
roomCode/id order. Empty results return an empty items list. There are no new search,
filter or pagination parameters. The existing Visit API error advice includes the new controller.
Admin room APIs and mutation code are untouched.

## H4: public doctor detail

GET `/api/v1/doctors/{doctorProfileId}` retains the seven existing public fields and adds
`availability: [{date, hasOpenTime}]`. A separate detail DTO keeps GET `/api/v1/doctors`
and its existing DTO unchanged; list queries do not enrich availability.

The detail uses the injected Clock with Asia/Ho_Chi_Minh and returns exactly seven ascending,
consecutive dates from today through today+6. Visible/active doctors not accepting bookings
return seven false values without calling catalog/assignment/availability services. Hidden,
inactive-directory and missing profiles retain the existing bare 404 behavior.

For accepting doctors, the detail reads public-bookable services through the catalog API,
checks active assignments through the existing query service, then calls
`GetAppointmentAvailabilityService.find` once per assigned service with the entire seven-day
range, target doctor and Audience.PUBLIC. It ORs slot-start dates in Vietnam time across
services. No appointment/shift repositories or scheduling algorithm are used by enrichment.
Actual slots, account and internal work contact are not returned.

Dependency failures retain their cause internally and map to the existing doctor
`503 DEPENDENCY_UNAVAILABLE` convention. They are not reported as fully booked days.
The existing booking horizon must accommodate the requested seven-day range.

## Focused verification

JDK 21; Maven Wrapper and the existing pinned PostgreSQL image. PostgreSQL integration uses
the repository's LocalPostgresTest configuration with environment-bound credentials, a dedicated
loopback container `otcms-kan25-postgres-test` on port 5434, and database `otcms_test_db`.
No credentials are recorded here or printed by setup commands.

| Phase | Focused selection | Result |
| --- | --- | --- |
| H1 | PasswordPolicyTest, TemporaryPasswordGeneratorTest, PatientRegistrationT004Test, T008PasswordRecoveryServiceTest, T010PasswordRecoveryControllerTest, UC07CreateAdministratorAccountTest, UC08ManageAdministratorAccountTest, T005CredentialLoginServiceTest | 65 PASS |
| H2 | ReassignAppointmentT026PostgresIntegrationTest, ReassignAppointmentServiceImplT026Test, AppointmentChangeAvailabilityT020PostgresIntegrationTest, GetAppointmentChangeAvailabilityServiceImplTest | 18 PASS |
| H3 | StaffRoomQueryControllerTest, VisitRoomAdminT031PostgresIntegrationTest | 12 PASS |
| H4 | DoctorDirectoryServiceImplTest, DoctorPublicProfileControllerTest, DoctorProfileT011PostgresIntegrationTest, GetAppointmentAvailabilityServiceImplTest | 22 PASS |

Total: 117 tests, zero failures, zero errors, zero skipped in the final focused runs.
Commands used `.\mvnw.cmd '-Dtest=<selection above>' test`; H1 began with `clean test`
to remove stale build resources from prior branches. An initial H4 Mockito unfinished-stubbing
fixture was corrected by resolving a mocked argument before starting doThrow stubbing;
no production behavior was changed to work around a test.

Coverage includes code-point/byte boundaries, supplementary Unicode through all DTO validators,
shared registration/reset/change policy, deterministic generator retry, credential regressions,
role matrices, real audit actor roles, unchanged mutation permissions, actual room ordering,
inactive rooms, exact public DTO whitelists, fixed-clock date boundaries, multi-service OR,
dependency failure behavior, real public availability with PostgreSQL, and OpenAPI schema checks.

## Full verification

Exact requested commands: `.\mvnw.cmd test`, `.\mvnw.cmd clean verify` with JDK 21.

| Command | Tests | Failures | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `.\mvnw.cmd test` | 632 | 7 | 8 | 0 | BUILD FAILURE |
| `.\mvnw.cmd clean verify` | 632 | 7 | 8 | 0 | BUILD FAILURE |
| Untouched product: six failing suites | 30 | 7 | 8 | 0 | Same baseline failures |

Baseline source was exported with `git archive HEAD backend` into the OS temporary directory,
without switching branches or changing baseline files. Representative source/test hashes match
HEAD `193fc74`. Verification ran the six failing suites with JDK 21 and the same local database
environment but a distinct schema. Sorted failing method lists exactly match full clean verify.
All KAN-25-focused suites also pass in the full runs. No unrelated failures were fixed or hidden.

Exact baseline failures/errors:

```text
AppointmentArrivalT053PostgresIntegrationTest.timelyArrivalClaimCommittedBeforeNoShowBlocksNoShowAndLateClaimCannotReopenNoShow
AppointmentArrivalT053PostgresIntegrationTest.timelyQrArrivalHoldingSharedGuardSerializesAheadOfNoShowAfterGrace
AdministratorAccountDatabaseVerificationTest.upgradeFromV019PreservesExistingAccountsAndSessionLinks
T005RealLoginIntegrationTest.wrongPasswordsPersistLockoutAndSuccessResetsCounter
T008PasswordRecoveryPostgresIntegrationTest.changingPasswordInvalidatesPreviouslyIssuedResetLink
T032AdminResourceMutationPostgresIntegrationTest.adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits
T032AdminResourceMutationPostgresIntegrationTest.checkedInAppointmentAlsoBlocksTimeOffCreation
T032AdminResourceMutationPostgresIntegrationTest.mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites
ClinicServicePostgresIntegrationTest.adminCannotSetNegativeServicePrice
ClinicServicePostgresIntegrationTest.adminDeactivationPreservesServiceRowAndWritesAudit
ClinicServicePostgresIntegrationTest.cleanSchemaMigratesThroughV004
ClinicServicePostgresIntegrationTest.dropTemporarySchema
ClinicServicePostgresIntegrationTest.publicCatalogOmitsInactiveAndNonOnlineServices
ClinicServicePostgresIntegrationTest.receptionistCannotManageServices
ClinicServicePostgresIntegrationTest.staffCatalogReturnsEveryActiveServiceAndOmitsInactiveServices
```

Logs under target: `otcms-kan25-h1.log`, `otcms-kan25-h2.log`, `otcms-kan25-h3.log`,
`otcms-kan25-h4.log`, `otcms-kan25-full-test.log`, `otcms-kan25-full-verify.log`,
`otcms-kan25-baseline.log`. These build artifacts are not staged or committed.

## File inventory and regression scope

Main Java paths below are relative to `src/main/java/fu/fa26/sep490/g58/otcms_be/`.

Modified (20 files):

- `auth/service/impl/PasswordPolicy.java`
- `auth/service/impl/RegistrationServiceImpl.java`
- `auth/service/impl/TemporaryPasswordGenerator.java`
- `auth/model/dto/request/RegistrationRequest.java`
- `auth/model/dto/request/ResetPasswordRequest.java`
- `auth/model/dto/request/ChangePasswordRequest.java`
- `appointment/controller/AppointmentBookingController.java`
- `appointment/controller/AppointmentAvailabilityController.java`
- `appointment/repository/AppointmentReassignmentRepository.java`
- `visit/exception/VisitApiExceptionHandler.java`
- `staff/controller/DoctorProfileController.java`
- `staff/service/DoctorDirectoryService.java`
- `staff/service/impl/DoctorDirectoryServiceImpl.java`
- `staff/mapper/DoctorProfileMapper.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/PatientRegistrationT004Test.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/T008PasswordRecoveryServiceTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/appointment/ReassignAppointmentT026PostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/appointment/AppointmentChangeAvailabilityT020PostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/visit/VisitRoomAdminT031PostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/staff/DoctorProfileT011PostgresIntegrationTest.java`

Added (10 files):

- `visit/controller/StaffRoomQueryController.java`
- `staff/model/DoctorPublicDetail.java`
- `staff/model/dto/response/DoctorPublicDetailResponse.java`
- `staff/model/dto/response/DoctorAvailabilityDayResponse.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/PasswordPolicyTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/TemporaryPasswordGeneratorTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/visit/StaffRoomQueryControllerTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/staff/DoctorDirectoryServiceImplTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/staff/DoctorPublicProfileControllerTest.java`
- `docs/kan25-small-permission-fixes.md`

Git scope checks show no changes in migration resources, pom.xml, SecurityConfig, ErrorResponse,
Patient/KAN-24, review or notification implementation. Existing doctor list and room admin
controller/service/DTO source remain unchanged. Only the two targeted appointment method
annotations change; appointment business services and all other method permissions are intact.

Final tracked diff: 20 files, 271 insertions and 36 deletions. New/untracked files are excluded
from Git's tracked-only diff stat. Working tree has 20 modified and 10 new files, nothing staged.
`git diff --check` passes. No commit or push was performed.

## Residual risks

- Full project verification remains red on the reproduced product baseline failures.
- Detail availability requires the existing booking horizon to include today+6, and performs
  one public availability call per eligible service, not one per day or per doctor list item.
- The local test container and temporary baseline/schema artifacts remain for inspection;
  no production/shared database was used and no credentials were recorded in this document.

## Review and sync-back

AI Agent Recommendation: PENDING HUMAN REVIEW.
Human Final Review: PENDING; the authorized human supplies identity, decision and timestamp.
This document records scope, execution, contracts, test evidence and residual issues;
it does not change architecture or approve its own implementation.
