# Doctor core integration repair - Action Record

## Authorization, scope and Shadow Plan

Explicit user authorization: backend-only repair of UC38/UC44 integration, current-care medical-history access, follow-up/cancellation evidence and production MVC wiring. No frontend, Doctor Room Queue, new document/prescription/history feature, commit, push, merge or rebase. Baseline is clean `feat/hung/KAN-XX-doctor-core-integration` at `cbf955f` (product); origin fetched. No human review is impersonated.

Shadow Plan: reproduce mapping collision before changes; remove competing Diagnostic HTTP handler; add public Diagnostic registration with one shared local transaction and service-order idempotency; provide real Visit/Staff/catalog reads; replace temporary Patient authorization; expose Clinical follow-up/examination evidence and remove legacy Visit SQL; test real MVC registration/production application wiring with persistence-only doubles, security and regressions; record DB limitations and exact safe scope.

## Before-change evidence

`DoctorCoreMvcMappingTest.capturesBaselineAmbiguousUc38Mapping` started an AnnotationConfigWebApplicationContext with @EnableWebMvc and both real controllers. Result: PASS (1 test asserting context failure), log `target/doctor-core-mapping-before.log`. Spring reported Ambiguous mapping between ClinicalImagingRequestController.submit and DiagnosticRequestController.create for POST /api/v1/visits/{visitId}/imaging-requests. Not standalone MockMvc. Service doubles were used only to isolate handler registration in this reproduction.

## Decisions

- Public UC38 owner: Clinical. Existing orders[] body, JWT Doctor identity, mandatory idempotency key and price/replay semantics stay intact. Diagnostic create is an internal Java application boundary; no alternative public POST.
- Same PostgreSQL application database: registration must join the Clinical transaction (MANDATORY), including round, orders and replay. No REQUIRES_NEW, best-effort or swallowed error. Replay repairs missing operational work idempotently by serviceOrderId; partial unique index V097 remains authoritative.
- Clinical uses doctorProfileId. Diagnostic existing persistence/policies use ordering Doctor **accountId**. Resolve through Staff's explicit profile/account public boundary; never compare accountId with profileId. Do not reinterpret existing persisted UUIDs.
- UC38/BR-45 requires type/body area/reason; UC24 owns room assignment separately (current UC_description.html). Model A: PENDING requests may be unassigned. No invented room ID/capacity reservation; acquisition/worklists require assignment. Existing room/technician commands retain their real dependency requirements.
- Read-only Visit evidence uses OPEN + current IN_PROGRESS responsibility; RESERVED is not current care. Separate Diagnostic read capability from unavailable Visit result/coordination commands; no no-op production commands.
- Medical history: replace all-active-Doctors shortcut with active account + Staff profile + Visit-owned current OPEN/IN_PROGRESS/patient relationship. Patient self/dependent access unchanged.
- Current Clinical schema has followUpRecommendation text only. Explicit optional followUpIndicated is required to honor existing follow-up booking precondition (BR-26 recommended follow-up, UC29 further care/follow-up). Add nullable Boolean on examination, populated through additive advice input. Null is unknown/not indicated for booking; never infer from text or backfill it. Omitted flag preserves stored indication.
- Visit cancellation checks real Clinical examination existence via public service after Visit lock; no Clinical repository import, legacy dual write or change to UC28 close ownership.
- Migration V098 is unused in local/product/all fetched remote migration inventories (highest observed V097). New migration only; V073/V074/V097 untouched.

## Implemented boundaries and compatibility

`ClinicalImagingRequestTransactionService.submit` owns the REQUIRED transaction. Its REQUIRED Clinical writer, MANDATORY DiagnosticImagingRequestRegistrationImpl, and replay save all join that transaction. The outer ClinicalImagingRequestServiceImpl remains NOT_SUPPORTED and handles only the existing named uniqueness races after rollback; round-number allocation retains its bounded retry and unique constraint. Registration exceptions propagate and are sanitized by the Clinical handler; no cross-feature repository/entity access.

Each immutable registration command carries persisted serviceOrderId, visitId, patientId, orderingDoctorProfileId, serviceId, bodyArea and clinicalReason. Instructions remain in the Clinical service order; no price is passed to Diagnostic. Staff resolves profile -> account; Diagnostic ordering_doctor_id retains its established account semantics (existing creation and policies use actor.accountId). No UUID backfill or silent reinterpretation. Diagnostic stores catalog modality rather than deriving it from a service name or body area.

Diagnostic registration looks up serviceOrderId first, verifies immutable linkage/content and account identity, and returns the original request regardless of later operational status. New work uses PostgreSQL INSERT ON CONFLICT against V097's live-service-order partial unique index, then verifies the persisted row. Same-key UC38 replay reconstructs the same Clinical round and idempotently ensures its Diagnostic work. Existing open pre-integration rounds can therefore be repaired by retry without a second Clinical round. Existing unlinked orders that already have results/cancellation need explicit owner reconciliation; the repair never invents a PENDING request for a non-open order.

The public UC38 JSON and idempotency/error/status conventions are preserved (first 201, replay 200). DiagnosticRequestController no longer has a create handler; its GET status routes remain. New Diagnostic registration errors map to current Clinical MSG-39/40/41/42 or sanitized DEPENDENCY_UNAVAILABLE. Doctor status list/detail use real active account -> Staff profile -> OPEN/IN_PROGRESS Visit evidence. Receptionist/Technician responsibility is not guessed by this Doctor-specific adapter.

PENDING requests have nullable roomId in status responses. No room is selected/reserved by UC38. Technician work projections skip unassigned requests, first use rejects them as ROOM_NOT_READY, and DB constraint forbids an unassigned request from progressing past PENDING (except cancellation). Cancellation/replay snapshots tolerate unassigned room state; assignment/transfer still requires the owning real capacity capability. Doctor Room Queue is NOT USED / NOT EXPOSED. No queue/Visit lifecycle command or synthetic state was introduced.

V098 additionally widens Diagnostic body_area to 255 and removes its old 2000-character reason ceiling, retaining nonblank checks: these align with the existing Clinical UC38 transport/persistence contract instead of narrowing it. No existing migration was edited.

Advice adds optional Boolean followUpIndicated to input/output/domain view. True/false must be explicitly supplied; omission preserves the previous value. The existing at-least-one-nonblank-advice-field rule remains. ClinicalFollowUpEvidenceQueryService reports examination existence, COMPLETED status and explicit true indication; null is not indication. Visit composes this public query with Visit-owned actual started/completed care responsibility. Follow-up appointment validation remains in its existing application service. Cancellation locks its Visit, rejects any real Clinical examination (including draft), then uses the original Visit cancellable-state guard. No UC28 normal-close implementation or legacy dual writes.

Patient current-care authorization now uses VisitDoctorCareRelationAdapter -> VisitDoctorCareRelationQueryService -> Visit-owned OPEN/IN_PROGRESS/patient/Doctor query, with active Doctor account and explicit account/profile conversion. The temporary R3 shortcut is retired (no component annotation, fails closed for legacy Java callers). Patient self/dependent authorization is unchanged. Both UC12 reads/writes remain current-care only for Doctors; UC33 historical access is separate.

## Diagnostic ObjectProvider audit

Every distinct production port provider is listed below. The new current-care read capability is segregated from the old compound command/history contract; no fake implementations or default no-op commands were added.

| Port | Production implementation | Classification/status |
| --- | --- | --- |
| VisitDiagnosticReadPort | VisitDiagnosticReadAdapter | REQUIRED UC38/Doctor UC44; RESOLVED |
| ImagingServiceCatalogPort | ImagingServiceCatalogAdapter | REQUIRED UC38 registration; RESOLVED |
| ClinicalImagingOrderPort | existing ClinicalImagingOrderAdapter | REQUIRED Clinical linkage; RESOLVED, frozen methods unchanged |
| RoomReservationPort | absent | Separate room assignment/transfer and assigned-request cancellation; not required for unassigned UC38/Doctor status |
| RoomStatusAccessPort | absent | Technician assigned-work/status concern, not Doctor UC44 |
| VisitDiagnosticPort | absent | Visit result/release/no-show coordination and historical authorization; REQUIRED later UC40/41/42/history workflows, not hidden by the read adapter |
| FirstUseEvidencePort | absent | Trusted acquisition/contrast evidence, later operational imaging |
| ImagingStudyPort | absent | Production DICOM study verification/access, UC40/41 |
| ReportStoragePort | absent | Findings/report storage/release, UC42 |
| ImagingBillingPort | absent | First-use obligation/outbox Billing handoff; not a UC38 payment blocker |
| PatientDiagnosticHistoryPort | absent | Patient/dependent imaging-history authorization; other concern |

Consequently this patch does not claim the whole Diagnostic bounded context is production-ready. UC38 create and responsible-Doctor UC44 read dependencies are wired; later acquisition, room assignment, result coordination, findings release and historical authorization remain explicit gaps. There is no production Doctor medical-document or e-prescription service, and no completed UC33 general past-record read model.

## Verification

JDK 21 and cached Maven 3.9.16; all Maven invocations use `-Dmaven.repo.local=C:\Users\ad\.m2\repository`. Final results, exact commands and remaining production ports will be recorded after implementation. No database reset/start/install authorized.

- `-DskipTests compile`: PASS; final regression also recompiled all 845 production sources.
- `-DskipTests test-compile`: PASS; final regression compiled the extended test sources too.
- Before-change mapping reproduction: PASS, 1 assertion of actual Spring ambiguous-mapping failure, before removing the handler.
- Focused repair run: PASS, 41 tests at that checkpoint. Initial test import/fixture defects were corrected; no speculative production workaround. Subsequently added cases were included in the final broad run.
- Final broad regression (`target/doctor-core-regression.log`): PASS, **500 tests, zero failures/errors/skips**. Includes 28 cases in the six new unit/MVC/wiring classes, two additional advice cases, all Clinical and Diagnostic unit tests, Doctor active Visit tests, Patient medical-history/self/dependent regressions, appointment follow-up/cancellation, Visit unit tests and selected security/controller regressions.

| Feature | Tests |
| --- | ---: |
| Clinical | 198 |
| Diagnostic | 37 |
| Patient | 118 |
| Visit | 50 |
| Appointment | 72 |
| Auth common security | 4 |
| Review controller | 18 |
| Service catalog controller | 3 |
| Total | 500 |

DoctorCoreMvcMappingTest now asserts exactly one real registered POST handler. DoctorCoreProductionWiringTest starts a real AnnotationConfigWebApplicationContext with @EnableWebMvc and transaction interception: real Clinical/Diagnostic controllers, services, Visit/Patient/Staff/catalog adapters and owner read implementations; only repositories are mocked, with a recording transaction-manager test implementation. No service/port mock conceals a missing production adapter. It verifies actual shared Spring transaction entry/rollback, multi-order linkage, replay, legacy-open-round reconciliation, existing payload lengths and UC44 account/profile authorization. It is the narrow realistic MVC/application-wiring alternative requested when DB prevents full boot; not a full database-backed application boot or proof of SQL rollback.

The PostgreSQL integration suite now additionally verifies linked Diagnostic counts under same-key/different-key races and full rollback on registration failure, using real registration and both modules' persistence (external owner reads isolated). Its actual execution is **BLOCKED**: read-only TCP probe outside sandbox confirmed 127.0.0.1:5432 unavailable. V098 application/constraints, actual PostgreSQL concurrency, rollback and query execution are NOT claimed PASS. No DB start/install/reset/migration command was run.

Architecture tests PASS: Patient has no Visit repository/entity import; Visit has no Clinical repository/entity import; Clinical has no Diagnostic repository/entity import; Diagnostic has no Clinical/Visit repository/entity import. Production Java `visit_examinations` references: zero. Existing legacy table/migrations are untouched. No frontend, Doctor Room Queue or normal Visit-close changes. `git diff --check` PASS; index empty, HEAD unchanged.

## Sync-back / residual review

Ready to stage for code review, with DB verification outstanding. No human approval, SDD lock, deployment readiness or delivery is impersonated. Owner review should cover V098 and nullable-room consumers, explicit advice follow-up intent and the Visit evidence/cancellation boundary. Existing unlinked completed/cancelled Clinical orders need an evidence-based reconciliation decision; only open rounds can be safely repaired automatically. Remaining Doctor backend scope: UC30-32 Documents, UC33 past records, UC36-37 e-Prescription, UC40/41 production study/view and UC42 release dependencies listed above. Room/capacity integration is separate Visit/Imaging work, not Doctor Room Queue.

## Exact final regression arguments

Cached Maven binary above, with session-only JAVA_HOME/PATH set to JDK 21:

```text
-Dmaven.repo.local=C:\Users\ad\.m2\repository -Dtest=AdminClinicServiceControllerTest,AfterVisitAdviceServiceImplTest,AppointmentBookingControllerValidationTest,AppointmentRequestValidationTest,CancelAppointmentServiceImplT025Test,CareErrorMessageWebTest,CareRelationshipT008Test,CareRelationshipT008WebTest,CheckInAppointmentServiceImplT027Test,CheckInAppointmentServiceImplTest,CheckVisitReservationCapacityServiceT031Test,ClinicalDoctorIdentityResolverTest,ClinicalExaminationServiceImplTest,ClinicalImagingOrderAdapterTest,ClinicalImagingRequestConcurrencyTest,ClinicalImagingRequestControllerTest,ClinicalImagingRequestServiceImplTest,ClinicalOrderingServiceImplTest,ClinicalVisitCompletionEvidenceServiceImplTest,CreateAppointmentServiceImplTest,CreateFollowUpAppointmentT030Test,CreateWalkInVisitT043Test,DependentAddOtpServiceTest,DependentEmailMaskingTest,DependentProfileServiceTest,DiagnosticControllersT016Test,DiagnosticHistoryT015Test,DiagnosticStatusT015Test,DiagnosticTransitionPolicyTest,DoctorActiveVisitControllerTest,DoctorActiveVisitReadServiceImplTest,DoctorClinicalControllerTest,DoctorCoreArchitectureTest,DoctorCoreMvcMappingTest,DoctorCoreProductionWiringTest,DoctorCurrentCareAuthorizationTest,DoctorImagingRegistrationTest,GetAppointmentAvailabilityServiceImplTest,GetAppointmentChangeAvailabilityServiceImplTest,GetAppointmentsServiceImplT023Test,Kan29MyPatientProfileServiceTest,MarkAppointmentNoShowT028Test,MedicalHistoryErrorWebTest,MedicalHistoryServiceTest,PatientAuthorizationT008Test,PatientClinicalIdentityQueryServiceImplTest,PatientProfileAccessServiceTest,PatientProfileControllerTest,PatientProfileReceptionVerificationT046Test,PatientProfileServiceTest,PublicRequestRateLimiterTest,ReassignAppointmentServiceImplT026Test,RescheduleAppointmentServiceImplT024Test,ReviewControllerTest,ServiceCatalogClinicalAdapterTest,SlotAvailabilityEngineTest,StaffRoomQueryControllerTest,T010CommonSecurityContractTest,TrustedProxyIpResolverTest,UpdateAppointmentStatusT029Test,VisitApiExceptionHandlerTest,VisitCareCapacityT031Test,VisitClinicalCareContextServiceImplTest,VisitClinicalContextAdapterTest,VisitClinicalContextProductionWiringTest,VisitClinicalEvidenceRepairTest,VisitDoctorActiveCareReadServiceImplTest,VisitRoomCapacityT031Test test
```

## Exact review/staging file boundary

```text
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/exception/ClinicalApiExceptionHandler.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/mapper/ClinicalApiMapper.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/dto/ClinicalDtos.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/entity/ClinicalExaminationJpaEntity.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/AfterVisitAdviceService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/AfterVisitAdviceServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalImagingRequestTransactionService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/controller/DiagnosticRequestController.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/integration/VisitDiagnosticPort.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/model/entity/DiagnosticRequestJpaEntity.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/repository/DiagnosticRequestJpaRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/impl/DiagnosticEvidenceServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/impl/DiagnosticRequestServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/impl/DiagnosticStatusServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/TemporaryR3DoctorCareRelationAdapter.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/staff/service/DoctorProfileIdentityQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/staff/service/impl/DoctorProfileIdentityQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/repository/VisitCareAssignmentRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/repository/VisitWorkflowRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/VisitWorkflowServiceImpl.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/AfterVisitAdviceServiceImplTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalImagingRequestConcurrencyTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalImagingRequestPostgresIntegrationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalImagingRequestServiceImplTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorClinicalControllerTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/diagnostic/controller/DiagnosticControllersT016Test.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/patient/MedicalHistoryServiceTest.java
backend/docs/doctor-core-integration-repair-action-record.md
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/ClinicalFollowUpEvidenceQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalFollowUpEvidenceQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/integration/VisitDiagnosticReadPort.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/DiagnosticImagingRequestRegistration.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/impl/DiagnosticImagingRequestRegistrationImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/patient/integration/visit/VisitDoctorCareRelationAdapter.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/servicecatalog/integration/diagnostic/ImagingServiceCatalogAdapter.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/integration/diagnostic/VisitDiagnosticReadAdapter.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/VisitDoctorCareRelationQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/VisitDoctorCareRelationQueryServiceImpl.java
backend/src/main/resources/db/migration/V098__repair_doctor_core_integration.sql
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorCoreArchitectureTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorCoreMvcMappingTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorCoreProductionWiringTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/DoctorImagingRegistrationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/patient/DoctorCurrentCareAuthorizationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/visit/VisitClinicalEvidenceRepairTest.java
```
