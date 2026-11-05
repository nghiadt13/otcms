# Doctor active Visit read — Action Record

## Scope, authorization and Shadow Plan

- Authorization: explicit user instruction for a read-only Doctor active-care endpoint and minimal Patient/Visit public read capabilities. No Human Final Review/SDD approval is impersonated. Referenced `.claude/skills/_shared/ai-review-protocol.md` is absent from this checkout.
- Baseline: branch feat/hung/KAN-XX-doctor-workspace-read; HEAD/product/origin-product `0bdb5050c4f5de954894825ca207b2e1e2c902fc`; clean working tree/index, fetch completed. Branch starts exactly at current inspected product and has no upstream configured. No reset/restore/stash/clean/commit/push/merge/rebase performed.
- Shadow Plan: inspect existing Visit/Patient/Staff/appointment APIs and security; add Visit-owned filtered projection/read service and Patient-owned minimal identity query; compose through a thin Clinical read application service/controller; add focused privacy/identity/MVC/query-contract tests; compile/testCompile/regressions, architecture/migration checks and DB availability audit; document FE composition.
- File boundary: new Visit and Patient public read interfaces/implementations, one Visit repository SELECT, Clinical active-read DTO/service/controller, Clinical exception-handler scope, four test classes and this record. No mutation workflow, frontend, Diagnostic/Imaging implementation or migration changes.

## Existing capabilities reused

- AppointmentQueryController GET /api/v1/appointments already accepts DOCTOR. GetAppointmentsServiceImpl resolves active Doctor scope server-side and supplies patientProfileId/patientName/visitId/status/times. No new appointments-today endpoint.
- Existing Clinical examination, completion, advice and UC38 APIs remain unchanged.
- Existing Diagnostic GET /api/v1/visits/{visitId}/imaging-requests and GET /api/v1/imaging-requests/{requestId} supply imaging status. No status copy/aggregation here.
- PatientMedicalHistoryController already accepts DOCTOR for GET/PUT /api/v1/patient-profiles/{id}/medical-history. No medical-history copy here.
- PatientProfileService.detail is Receptionist-authorized administrative access. It is not called or weakened. Doctor identity uses the existing ClinicalDoctorIdentityResolver -> Staff DoctorProfileIdentityQueryService. The public-directory listing flag is not reinterpreted as treatment authorization; JWT/account eligibility remains with the existing authentication filter.

## New endpoint and exact eligibility

GET /api/v1/doctor/visits/active. ROLE_DOCTOR only; AuthenticatedAccount.accountId -> server-resolved doctorProfileId. No Doctor/patient selector parameters or request body. Unknown selector query parameters do not affect scope.

Visit state remains OPEN/CLOSED. Assignment states remain RESERVED/IN_PROGRESS/COMPLETED/RELEASED. Query includes only:

```text
visit.status = OPEN
AND assignment.status = IN_PROGRESS
AND assignment.doctor_profile_id = authenticated Doctor profile
```

RESERVED is capacity, not current treatment. Expected-end expiry alone does not end care; actual IN_PROGRESS remains authoritative. No date filter is invented for care that spans days. One item represents one actual matching assignment; deterministic ordering is starts_at ASC, assignment id ASC.

VisitCareAssignmentRepository adds a typed JPQL projection joining only Visit-owned entities. VisitDoctorActiveCareReadService maps it to immutable records; null internal Doctor ID is rejected. Scheduled links use originatingAppointmentId, never priorAppointmentId. Walk-in links may be null. No entities/rows escape Visit ownership.

Doctor does NOT consume Room Queue.
Room Queue: NOT USED, NOT EXPOSED. No room/reception tables, queue numbers, waiting positions, call/reorder actions or technician worklist.

## Patient privacy and read composition

PatientClinicalIdentityQueryService exposes only patientProfileId/fullName/dateOfBirth/gender. Its implementation uses the existing Auth-owned PatientProfileDirectory public boundary, not PatientProfileService.detail. Missing profiles are omitted from this internal map; no administrative fallback. Empty collection returns immutable empty map; repeated IDs are deduplicated, one directory lookup per distinct ID because that directory currently lacks a bulk read method. Infrastructure errors propagate.

Clinical DoctorActiveVisitReadServiceImpl composes the existing Doctor identity resolver, Visit public read and Patient public query. It has no repository/entity dependencies and owns a read-only REPEATABLE_READ snapshot, following existing appointment-read precedent. Orchestration stays in Clinical rather than adding an actor-named business module.

No active care returns 200 {"items":[]} without patient lookups. Missing/mismatched identity for an active row fails the entire response with 503 DEPENDENCY_UNAVAILABLE; valid items are not silently omitted and partial lists are not returned. Stored allowed identity values are forwarded without guessing missing demographic values. No phone/address/nationalId/identityDocumentType/accountId/guardian details leave Patient's minimal projection.

Success/errors use no-store and shared correlation ID. Anonymous 401; wrong role/missing Doctor profile 403 MSG-40. Existing scoped Clinical handler includes the new controller; no global handler or authentication change.

## Response

```json
{
  "items": [{
    "visitId": "00000000-0000-0000-0000-000000000001",
    "patientProfileId": "00000000-0000-0000-0000-000000000002",
    "patient": {"fullName": "Nguyen Van A", "dateOfBirth": "2000-01-01", "gender": "MALE"},
    "visitSource": "SCHEDULED",
    "linkedAppointmentId": "00000000-0000-0000-0000-000000000003",
    "assignmentId": "00000000-0000-0000-0000-000000000004",
    "stepCode": "SCHEDULED_APPOINTMENT",
    "startedAt": "2026-11-04T14:00:00Z",
    "expectedEnd": "2026-11-04T14:30:00Z"
  }]
}
```

## Intended FE composition

- Scheduled patients: GET /api/v1/appointments?from=<today>&to=<today>.
- Current active Visit: GET /api/v1/doctor/visits/active.
- Visit examination/advice: existing Clinical APIs.
- Imaging request/status: existing Diagnostic APIs.
- Medical history: existing Patient APIs using patientProfileId.
- Navigation target: /doctor/visits/{visitId}; no frontend implementation or Room Queue UI in this checkpoint.

## Verification and limitations

JDK 21.0.9, session-only JAVA_HOME C:\Program Files\Java\jdk-21 and PATH. Cached Maven:
`C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd`.
Every command uses `-Dmaven.repo.local=C:\Users\ad\.m2\repository`.

- `-DskipTests compile`: PASS outside sandbox after known cached-JAR access failure in sandbox; no wrapper/dependency change.
- `-DskipTests test-compile`: PASS, JDK 21; 190 test sources compiled.
- `-Dtest=PatientClinicalIdentityQueryServiceImplTest,VisitDoctorActiveCareReadServiceImplTest,DoctorActiveVisitReadServiceImplTest,DoctorActiveVisitControllerTest test`: PASS, 27 tests (6 Patient + 7 Visit + 6 application + 8 MVC), zero failures/errors/skips. Initial run found one nested-Mockito-stubbing fixture error; fixture corrected, complete focused rerun PASS.
- `-Dtest=VisitClinicalCareContextServiceImplTest,ClinicalExaminationServiceImplTest,AfterVisitAdviceServiceImplTest,ClinicalImagingRequestServiceImplTest,ClinicalImagingRequestControllerTest,ClinicalImagingOrderAdapterTest,GetAppointmentsServiceImplT023Test,MedicalHistoryServiceTest,MedicalHistoryErrorWebTest,DoctorClinicalControllerTest,ClinicalDoctorIdentityResolverTest test`: PASS, 151 tests, zero failures/errors/skips.
- `-Dtest=StaffRoomQueryControllerTest,PatientProfileControllerTest,ReviewControllerTest,AdminClinicServiceControllerTest,T010CommonSecurityContractTest test`: PASS, 42 tests, zero failures/errors/skips.
- Total successful final runs: 220 tests, zero failures/errors/skips. Maven commands above ran outside the filesystem sandbox to use the existing cache; no dependency installation or version changes.
- New Visit unit tests validate mapping, deterministic repository order/immutable output and exact JPQL Doctor/OPEN/IN_PROGRESS predicates excluding RESERVED/COMPLETED/RELEASED/CLOSED. These are static query-contract checks, not a claim of real DB filtering execution.
- New MVC slice uses real read composition and identity resolver with mocked owner public services, exercising security, exact field whitelists, empty/scheduled/walk-in/error cases and ignored client scope selectors.
- Patient query tests populate administrative source fields and assert only four clinical fields survive, with read-only directory interactions.
- PostgreSQL integration: BLOCKED. Read-only TCP probe outside sandbox confirmed 127.0.0.1:5432 unavailable. No real PostgreSQL query execution or full application context claimed; MVC slices did start. No DB install/start/reset performed.
- Architecture/source searches: PASS, no owning repository/entity imports in Clinical read composition/controller; no queue dependencies/response fields in new production sources. Only owner implementations access persistence. Patient administrative authorization unchanged. Migration inventory/diff: no new or changed migration. `git diff --check`: PASS. Index remains empty and baseline HEAD unchanged.

## Sync-back and next checkpoint

No SDD approval, stage/commit/delivery, migration or existing authorization weakening. Next checkpoint after PASS: real Doctor frontend /doctor/today and /doctor/visits/{visitId} using existing APIs, without Room Queue UI.
