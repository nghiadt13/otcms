# UC36 Doctor e-Prescription - Action Record

## Authorization and Shadow Plan

Explicit user scope: complete Doctor-only Clinical prescription backend, migration, security/version/retry/history boundaries and tests. No frontend, inventory, dispensing, Billing, Doctor Room Queue, Patient UC37 HTTP, commit/push/merge/rebase. Baseline: clean feat/hung/KAN-XX-doctor-prescription, HEAD and fetched origin/product 08c8770833cca743470ea8d81a20cbed88036822. Product did not advance. Core integration repair and V098 are present.

Shadow Plan: audit current requirements/Clinical/security/mocks and migration inventory; implement one visit aggregate plus ordered medicine items; add current-care Doctor HTTP and trusted issued-history query; verify atomic expected-version writes and durable issuance retry; add unit/MVC/wiring/architecture and deferred real PostgreSQL race tests; run compile/testCompile and regressions; record exact evidence and blockers. This record does not impersonate Human review/approval.

## Requirement and existing-code evidence

- Current UC_description.html UC36: electronic prescription for a visit, medicines/how to use them; no medicine stock or dispensing. BR-43 limits issuance to Orthopedic Doctor. Current-care responsibility uses existing ClinicalDoctorIdentityResolver and real VisitClinicalContextPort (OPEN + IN_PROGRESS-backed clinicalWorkAllowed).
- UC29/BR-38 examination, UC35/BR-42 advice and UC33/BR-41 past records were checked. None makes completed examination a prescription prerequisite. No pharmaceutical engine/catalog, interactions, stock, quantities, pricing or dispensing state is inferred.
- MSG-39/40/41/42 are current invalid/permission/unavailable/state messages; MSG-44 includes successful prescription save. Existing Clinical HTTP envelope/error, no-store and correlation conventions are reused; no new success envelope is introduced.
- Search audit: no prescription/medicine/medication/drug production backend or tests in src. Backend hits are architecture/schema proposal/action-record documentation, not applied prescription schema. Frontend prescriptions types/API/hooks/screens/tests use USE_MOCK=true, with Patient document/display/diagnosis assumptions; they are contract evidence only, untouched. Requirements are authoritative; mock Patient HTTP, completed-Visit and display-name assumptions are not copied.
- Local/product/all fetched remote migration inventories have V098 as highest relevant version and no V099. No older migration will change.

## Design decisions

- One Clinical prescription aggregate per Visit, multiple ordered lines. UC36 describes a visit-linked prescription, with no requirement for multiple independent aggregates; singular requested API and UNIQUE(visit_id) encode this checkpoint's explicit cardinality choice.
- State: none -> DRAFT; DRAFT -> DRAFT; DRAFT -> ISSUED. No amendment/cancellation/dispensing lifecycle. Issued content and server linkage are immutable through all commands.
- Version 0 means absent/create-only (existing Patient medical-history convention); first stored draft is version 1. Every draft replacement/issuance atomically compares and increments the header version. @Version documents the JPA field; owner repository CAS writes enforce exact expected-version/state conditions, including item-only changes and fixed clocks. Affected-row count zero means MSG-42. DB unique Visit protects concurrent first creation.
- Issuance body is only {"version":<current version>}; medicines come from the saved draft. Required Idempotency-Key is nonblank/max128, normalized by trim. Issued aggregate durably stores the winning key and supplied draft version. Same Doctor/Visit/key/version replays the identical issued view; same key/different version is IDEMPOTENCY_CONFLICT (409); another key against issued data is MSG-42. No separate replay table/hash is needed because issuance accepts no other material payload and the issued aggregate never changes.
- Draft replacement owns header + full item set in one REQUIRED transaction. Issuance CAS, timestamp and replay metadata are one REQUIRED transaction. No REQUIRES_NEW or rollback-only recovery. Header CAS serializes competing updates; loser touches no items. A losing identical issuance reloads the winning issued header after the failed conditional update (not after a constraint exception).
- Doctor GET/write/issue all require real current care. Missing Visit MSG-41; wrong current Doctor MSG-40; clinicalWorkAllowed=false MSG-42; patient must exist in authoritative context and match persisted linkage. The responsible Doctor is resolved afresh: draft edits record the current Doctor, issuance freezes the actual issuing Doctor. A former draft author does not supersede Visit responsibility. An issued prescription cannot be reissued or relabelled by a subsequent responsible Doctor; issuance replay is scoped to the issuing Doctor. No historical authorization weakening. Clinical never reads another feature's repositories/entities.
- No diagnosis in this aggregate/view, so no fabricated ICD labels or mutable diagnosis snapshot. No names/PII snapshots are needed by this API. Immutable patientProfileId/doctorProfileId/visitId linkage is retained.
- Trusted ClinicalPrescriptionHistoryQueryService returns issued summaries only, deterministically ordered and immutable. Future UC33 composition must authorize the actor before selecting patient/Visit IDs; this owner query is not a Patient/Doctor historical HTTP endpoint.
- Prescription is optional unless Doctor decides to prescribe. ClinicalVisitCompletionEvidenceService remains unchanged; absence does not block Visit close.
- Storage/transport limits: medicine/dose/frequency 255, usage 2000 characters; nonblank text and at least one line. These are free-text safeguards, not pharmaceutical rules. No arbitrary medicine-list count limit.

## Persistence and HTTP contract

V099__create_clinical_prescriptions.sql creates clinical_prescriptions and clinical_prescription_items. Header owns UUID Visit/patient/Doctor linkage, status, timestamps, positive version and issuance key/expected version. Like V073, external Visit UUID existence/eligibility is established through the real public Visit port, not by a cross-feature repository query. UNIQUE(visit_id), status/issuance consistency checks, item-to-header FK, positive/unique line numbers and nonblank bounded free text protect persistence. No older migration changes.

| Method | Path | Operation |
| --- | --- | --- |
| GET | /api/v1/visits/{visitId}/prescription | Current responsible Doctor read; absent record MSG-41 |
| PUT | /api/v1/visits/{visitId}/prescription | Create-only version0 or replace a matching-version draft |
| POST | /api/v1/visits/{visitId}/prescription/issuance | Required Idempotency-Key, issue saved draft |

All three require ROLE_DOCTOR and server accountId -> ClinicalDoctorIdentityResolver -> doctorProfileId -> VisitClinicalContextPort. No client-selected identity/status/timestamp. As with existing Clinical DTOs, unknown input fields are ignored and cannot affect authorization or state. Every successful operation returns 200 resource JSON, Cache-Control: no-store and shared X-Correlation-ID. MSG-44 is the existing FE success message, not an invented response envelope. Transport validation follows existing VALIDATION_FAILED/400; domain MSG-39/40/41/42 map to 400/403/404/409, IDEMPOTENCY_CONFLICT to409, dependency to503 and unexpected to sanitized500.

Draft request:

```json
{"items":[{"medicine":"Paracetamol 500mg","dose":"1 viên","frequency":"2 lần/ngày","usage":"Uống sau ăn"}],"version":0}
```

First draft response (illustrative server UUIDs):

```json
{
  "id":"00000000-0000-0000-0000-000000000001",
  "visitId":"00000000-0000-0000-0000-000000000002",
  "patientProfileId":"00000000-0000-0000-0000-000000000003",
  "status":"DRAFT","issuedAt":null,"version":1,
  "items":[{"lineNumber":1,"medicine":"Paracetamol 500mg","dose":"1 viên","frequency":"2 lần/ngày","usage":"Uống sau ăn"}]
}
```

Issuance sends {"version":1} and Idempotency-Key. Successful response keeps IDs/items and becomes ISSUED with server issuedAt/version2. Later draft saves must send the returned positive version; version0 never means overwrite. Neither public JSON nor trusted history exposes item JPA IDs, account IDs or issuance metadata. No Patient UC37/document HTTP is registered.

Writes explicitly use REQUIRED/READ_COMMITTED. Conditional header writes hold their row lock to transaction completion before line mutation; a failed conditional write changes no lines and does not create a rollback-only recovery problem. Reads use read-only REPEATABLE_READ for coherent header/line snapshots. The complete item list is defensively copied at the application command boundary and output lists are immutable. Responsible Doctor handover while DRAFT remains Visit-authoritative; issuance freezes the actual issuer. No issued content can be overwritten by PUT or another issuance key.

History API: findIssuedByPatient(UUID) / findIssuedByVisit(UUID), returning immutable IssuedPrescriptionSummary records (IDs, issuing Doctor profile ID, issuedAt, version, ordered lines). Only ISSUED data, order issuedAt DESC/id DESC, lineNumber ASC. No historical authorization is embedded; a future trusted UC33 application service must authorize actor/care access. No direct Patient/Visit/Staff/Diagnostic/Billing persistence imports, names/phone/address/nationalId snapshots or pharmacy/queue dependencies.

## Verification

JDK 21.0.9, session-only JAVA_HOME C:\Program Files\Java\jdk-21. Cached Maven:
`C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd`.
Every Maven invocation used `-Dmaven.repo.local=C:\Users\ad\.m2\repository` outside the filesystem sandbox for the existing cache. No new dependency or wrapper change.

- `-DskipTests compile`: PASS. Final regression recompiled all 856 production sources after final authorization/isolation review.
- `-DskipTests test-compile`: PASS. Final regression also compiled all added PostgreSQL test sources.
- `-Dtest=ClinicalPrescriptionServiceImplTest,ClinicalPrescriptionControllerTest,ClinicalPrescriptionArchitectureTest,DoctorCoreProductionWiringTest test`: PASS, 66 tests at the focused checkpoint.
- Final regression (`target/prescription-regression.log`): **558 tests, 0 failures, 0 errors, 0 skips**. All non-PostgreSQL Clinical/Diagnostic/Visit/Patient/Appointment tests plus selected common security, Review and catalog controller suites.

| Scope | Tests |
| --- | ---: |
| New Prescription service/history/CAS races | 19 |
| New Prescription MVC/security | 36 |
| New Prescription architecture/completion/transactions | 3 |
| All Clinical (includes those 58 and core wiring/mapping) | 256 |
| Diagnostic | 37 |
| Visit | 50 |
| Patient | 118 |
| Appointment | 72 |
| Auth common security | 4 |
| Review controller | 18 |
| Catalog controller | 3 |
| Final total (no double counting) | 558 |

Service tests cover draft create/read/update, ordering, null/blank/length/version validation, absent/wrong/disallowed Visit/Doctor/patient, server timestamps, issued immutability, replay/conflict, Doctor handover, deterministic issued-only history and three CAS-control-flow races. MVC uses actual security/identity resolver and exact JSON whitelists, excludes identity/state spoofing and medical/private metadata, checks no-store/correlation/CSRF, strict integer version parsing and sanitized errors. Core real MVC/application wiring includes production Prescription controller/services with persistence doubles. Architecture guards verify owner-only persistence, no inventory/dispensing/Billing/room dependency and unchanged completion evidence.

PostgreSQL integration is **BLOCKED**: outside-sandbox read-only TCP probe found 127.0.0.1:5432 unavailable. Five real PostgreSQL tests were added and compiled for initial-create race, two draft updates, two identical issuances, issuance vs draft update and durable replay/history across transactions. No real SQL concurrency, migration application, SQL rollback or full database-backed boot is claimed PASS. No DB start/change/reset/install/migration command was run.

`git diff --check` and untracked-file whitespace checks: PASS. Frontend, V098/earlier migrations and Visit completion evidence remain unchanged. An unrelated formatting-only edit to OtcmsBeApplication.java appeared after the initial clean baseline; it was preserved and is excluded from this checkpoint's staging boundary. HEAD/origin-product remain 08c8770833cca743470ea8d81a20cbed88036822 and the index is empty.

## Sync-back and remaining scope

Prescription implementation is ready to stage for review, not a claim of Human approval or production DB verification. No commit/push/merge/rebase. Remaining Doctor BE gaps: UC30-32 Medical Documents, UC33 Past Medical Records, UC24 Doctor initial room/capacity integration, UC60 Doctor availability mutation (existing room read/admin APIs do not supply this Doctor command), UC41 DICOM Study/View and inherited Diagnostic Study/Report/Billing/result-coordination production ports. Patient UC37 HTTP/document rendering remains deliberately separate.

## Exact final regression arguments

```text
-Dmaven.repo.local=C:\Users\ad\.m2\repository -Dtest=AdminClinicServiceControllerTest,AfterVisitAdviceServiceImplTest,AppointmentBookingControllerValidationTest,AppointmentRequestValidationTest,CancelAppointmentServiceImplT025Test,CareErrorMessageWebTest,CareRelationshipT008Test,CareRelationshipT008WebTest,CheckInAppointmentServiceImplT027Test,CheckInAppointmentServiceImplTest,CheckVisitReservationCapacityServiceT031Test,ClinicalDoctorIdentityResolverTest,ClinicalExaminationServiceImplTest,ClinicalImagingOrderAdapterTest,ClinicalImagingRequestConcurrencyTest,ClinicalImagingRequestControllerTest,ClinicalImagingRequestServiceImplTest,ClinicalOrderingServiceImplTest,ClinicalPrescriptionArchitectureTest,ClinicalPrescriptionControllerTest,ClinicalPrescriptionServiceImplTest,ClinicalVisitCompletionEvidenceServiceImplTest,CreateAppointmentServiceImplTest,CreateFollowUpAppointmentT030Test,CreateWalkInVisitT043Test,DependentAddOtpServiceTest,DependentEmailMaskingTest,DependentProfileServiceTest,DiagnosticControllersT016Test,DiagnosticHistoryT015Test,DiagnosticStatusT015Test,DiagnosticTransitionPolicyTest,DoctorActiveVisitControllerTest,DoctorActiveVisitReadServiceImplTest,DoctorClinicalControllerTest,DoctorCoreArchitectureTest,DoctorCoreMvcMappingTest,DoctorCoreProductionWiringTest,DoctorCurrentCareAuthorizationTest,DoctorImagingRegistrationTest,GetAppointmentAvailabilityServiceImplTest,GetAppointmentChangeAvailabilityServiceImplTest,GetAppointmentsServiceImplT023Test,Kan29MyPatientProfileServiceTest,MarkAppointmentNoShowT028Test,MedicalHistoryErrorWebTest,MedicalHistoryServiceTest,PatientAuthorizationT008Test,PatientClinicalIdentityQueryServiceImplTest,PatientProfileAccessServiceTest,PatientProfileControllerTest,PatientProfileReceptionVerificationT046Test,PatientProfileServiceTest,PublicRequestRateLimiterTest,ReassignAppointmentServiceImplT026Test,RescheduleAppointmentServiceImplT024Test,ReviewControllerTest,ServiceCatalogClinicalAdapterTest,SlotAvailabilityEngineTest,StaffRoomQueryControllerTest,T010CommonSecurityContractTest,TrustedProxyIpResolverTest,UpdateAppointmentStatusT029Test,VisitApiExceptionHandlerTest,VisitCareCapacityT031Test,VisitClinicalCareContextServiceImplTest,VisitClinicalContextAdapterTest,VisitClinicalContextProductionWiringTest,VisitClinicalEvidenceRepairTest,VisitDoctorActiveCareReadServiceImplTest,VisitRoomCapacityT031Test test
```

## Exact files for prescription review/staging

```text
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/exception/ClinicalApiExceptionHandler.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorCoreProductionWiringTest.java
backend/docs/doctor-e-prescription-uc36-action-record.md
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/controller/ClinicalPrescriptionController.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/ClinicalPrescriptionStatus.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/dto/PrescriptionDtos.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/entity/ClinicalPrescriptionItemJpaEntity.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/entity/ClinicalPrescriptionJpaEntity.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalPrescriptionItemRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalPrescriptionRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/ClinicalPrescriptionHistoryQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/ClinicalPrescriptionService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalPrescriptionHistoryQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalPrescriptionServiceImpl.java
backend/src/main/resources/db/migration/V099__create_clinical_prescriptions.sql
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalPrescriptionArchitectureTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalPrescriptionControllerTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalPrescriptionPostgresIntegrationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalPrescriptionServiceImplTest.java
```
