# Backend completion audit

## UC01–UC69 coverage matrix

Status describes source-backed backend capability, not deployment approval or database verification. PARTIAL means existing behavior remains usable but the stated missing portion is not complete. Each blocked extension below is bounded; it does not erase existing implemented behavior. Evidence names resolve under `src/main/java/fu/fa26/sep490/g58/otcms_be/<feature>` and `src/test/java/.../<feature>`. Legacy test/UC filenames are not the authority for numbering.

| UC | Name | Backend status | Evidence | Missing behavior | Can implement now? | Dependency/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| UC01 | Sign In | IMPLEMENTED | PatientLogin/StaffLogin/GoogleExchange → session services; active-role/network checks, persisted sessions and JWT; Credential/StaffLogin/SessionSecurity tests | — | No new work | External provider runtime verification remains separate |
| UC02 | Reset Password | IMPLEMENTED | PasswordRecoveryController/service; expiring single-use identity evidence and session revocation; PasswordRecoveryFlow/T008 tests | — | No new work | SMTP runtime configuration |
| UC03 | View and Update Account Details | IMPLEMENTED | MyAccountController/services; current identity only, permitted fields/avatar validation and persisted account writes; Kan29/MyAccount tests | — | No new work | DB suites blocked |
| UC04 | Change Password | IMPLEMENTED | PasswordRecoveryController change-password path; identity/current-password policy, session security; recovery/security tests | — | No new work | DB suites blocked |
| UC05 | Register Patient Account | IMPLEMENTED | PatientRegistrationController/service; pending/OTP/consent/activation state and persisted identity; PatientRegistrationT004 tests | — | No new work | SMTP/DB verification |
| UC06 | Create Employee Account | IMPLEMENTED | StaffAccountAdmin + OwnerAdministrator controllers/services; fixed roles and administrator ownership split; Kan27/UC07 tests (legacy numbering) | — | No new work | No new role assignment model |
| UC07 | Activate or Deactivate Account | IMPLEMENTED | PatientAccountAdmin/StaffAccountAdmin/OwnerAdministrator; own permitted account types, transition/session checks; account administration tests | — | No new work | DB suites blocked |
| UC08 | View User Accounts | IMPLEMENTED | Admin Patient/staff and Owner administrator query surfaces; role-scoped search/detail and pagination; account query/controller tests | — | No new work | DB suites blocked |
| UC09 | Create Walk-in Patient Profile | IMPLEMENTED | PatientProfileController/service; active Receptionist, duplicate/identity checks and profile persistence; PatientProfile tests | — | No new work | Detailed UC10 filename uses earlier numbering |
| UC10 | Update Patient Details | IMPLEMENTED | MyPatientProfile + PatientProfileController; Patient own and Receptionist permitted fields/version checks; Kan29/PatientProfile tests | — | No new work | No broad identity-field changes |
| UC11 | View Patient Details | IMPLEMENTED | Own/dependent profile APIs plus Receptionist profile reads and DoctorActiveVisit current-care minimal demographics; access/Doctor current-care tests | — | No new work | Doctor arbitrary-Patient lookup remains forbidden |
| UC12 | Record Patient Medical History | PARTIAL | PatientMedicalHistoryController/service supports active Patient authorized profiles and current-care Doctor; version CAS; MedicalHistoryService/Error tests | Receptionist basic-background field/write scope is undefined | BLOCKED for Receptionist extension | UC12 says where allowed; no approved field boundary for Receptionist |
| UC13 | Add Dependent Profile | IMPLEMENTED | CareRelationship/DependentAddOtp endpoints; identity/OTP/counter confirmation and persisted PENDING/CONFIRMED relationship; relationship/dependent tests | — | No new work | No implicit authorization from unconfirmed relationship |
| UC14 | Update Dependent Profile | IMPLEMENTED | CareRelationshipController + DependentProfileService; current CONFIRMED authority, allowed fields and concurrency; DependentProfileServiceTest | — | No new work | DB suites blocked |
| UC15 | Remove Dependent Profile | IMPLEMENTED | CareRelationshipController revoke; transactional relationship revocation preserves profile/records; dependent/relationship tests | — | No new work | No deletion of history |
| UC16 | View Orthopedic Doctors | IMPLEMENTED | DoctorProfileController/DoctorDirectoryService; public visibility/active-directory filtering and detail; DoctorProfile handler/controller tests | — | No new work | Availability source decision noted under UC17 |
| UC17 | View Open Appointment Times | PARTIAL | AppointmentAvailabilityController + real capacity/availability services; booking windows/buffers/versioned availability tests | Team must confirm availability source/maintenance ownership | BLOCKED for source-policy changes | UC_description.html decisions section; do not invent workforce scheduling |
| UC18 | Book Appointment | IMPLEMENTED | AppointmentBooking/FollowUp controllers; Patient/dependent, Receptionist and responsible Doctor follow-up authorization; atomic slot/capacity/idempotency; booking/follow-up tests | — | No new work | Configured availability and DB concurrency require runtime verification |
| UC19 | Change Appointment Time | IMPLEMENTED | Reschedule endpoint/service; authorized mutable appointment, patient/service retained, notice and availability checks; Reschedule T024 tests | — | No new work | DB race verification blocked |
| UC20 | Cancel Appointment | IMPLEMENTED | Cancel endpoint/service; actor/ownership/state policy, slot release and audit; Cancel T025 tests | — | No new work | Deferred Diagnostic no-show owner protocol remains separate |
| UC21 | View Appointments | IMPLEMENTED | AppointmentQuery/GetAppointmentsService; Patient authorized scope, Receptionist/Doctor responsibility filters; pagination/history; T023 tests | — | No new work | No arbitrary patient filter bypass |
| UC22 | Check In Patient | PARTIAL | Booked check-in and walk-in reception have real transactions, actor checks and tests; walk-in enters V023 FIFO | Scheduled check-in deliberately has no reception FIFO under approved D11; latest overview says first waiting list | BLOCKED for unified waiting-list extension | Resolve overview versus approved reception contract; no suitable room/Doctor queue |
| UC23 | View Reception Queue | PARTIAL | GET /reception-queue; active Receptionist, day-bounded stable WALK_IN FIFO/position, history-preserving exit; T045/T049 DB tests compile | Booked-appointment waiting list not represented | BLOCKED for expansion | V023 queue explicitly WALK_IN; no generic queue contract |
| UC24 | Assign or Change Patient Room | PARTIAL | Doctor care-start/extension coordinates existing room/Doctor capacity; audited reservations, T031 tests | Receptionist next-service assignment/change and waiting-list transitions absent | BLOCKED | Needs approved Room/service queue contract; no speculative generalized queue |
| UC25 | View Room Queue | BLOCKED | No room waiting-list entity/repository/API; room reservations and care assignments represent occupancy, not a queue | Production room/Doctor queue model and actor-scoped read contract | No | Queue inventory V017/V023 and existing blocker record |
| UC26 | Change Queue Order | BLOCKED | V023 reception FIFO ordered arrivedAt/id, immutable/history-preserving exits; no reorder position/reason command | Approved reordering semantics and history contract | No | Cannot fabricate positions or mutate arrival evidence |
| UC27 | View Visit Progress | PARTIAL | Existing endpoint now supports Patient self/dependent, active Receptionist and responsible current Doctor; stage/current room/reception position; VisitProgress authorization/HTTP tests | Technician scope, canonical completed-service projection and missing room queue | Implemented safe staff read; remaining extensions BLOCKED | Technician responsibility/service lifecycle and queue owner contracts missing |
| UC28 | Close Patient Visit | PARTIAL | Receptionist-only lifecycle/controller, active account, OPEN walk-in/scheduled Visit, locked Clinical completion composition; VisitClosure and ClinicalClosureCoordination tests; additive V102 | Required imaging findings completion cannot be proven by an existing public Diagnostic query | Safe non-imaging closure implemented; imaging fails closed | Diagnostic owner must expose trusted per-required-order completed/released findings evidence; RESULTS_AVAILABLE/REVIEWED alone is insufficient |
| UC29 | Record Orthopedic Examination | IMPLEMENTED | ClinicalExaminationController/service; active responsible Doctor/current Visit, DRAFT/COMPLETED validation and diagnosis owner persistence; Examination/DoctorCore tests | — | No new work | Completion is not UC34 Patient release |
| UC30 | Upload Medical Document | IMPLEMENTED | ClinicalMedicalDocument multipart API/service; Doctor/Receptionist scope, patient derived from Visit, file validation and atomic payload write; MedicalDocument tests | — | No new work | V100 DB suites blocked |
| UC31 | Update Medical Document | IMPLEMENTED | Doctor/version-CAS metadata/payload replacement; released records immutable; MedicalDocument service/controller tests | — | No new work | No payload overwrite after release |
| UC32 | View Medical Document | IMPLEMENTED | Patient-capable list /visits/{id}/medical-documents, metadata /medical-documents/{id}, content /content; owner self/dependent + releasedAt filters, no-store/nosniff attachment; tests | — | No new work | No unrestricted document binary endpoint |
| UC33 | View Past Medical Records | IMPLEMENTED | Current Visit authorization → CLOSED historical Visit owner query → completed Clinical/issued Rx/document metadata/released Diagnostic; batch owner ports; UC33 service/controller/architecture/semantic tests | — | No new work | Different historical Doctor preserved; DB integration blocked |
| UC34 | View Examination Results | BLOCKED | No Patient examination-result controller; Examination has DRAFT/COMPLETED only; document releasedAt is separate; BR-39 requires clinic availability | Approved explicit Examination publication/availability signal | No | Team decision pending: do not infer release from COMPLETED/CLOSED or a different document |
| UC35 | Record After-Visit Advice | IMPLEMENTED | AfterVisitAdviceController/service; responsible Doctor current-care checks; persisted advice under examination/version policy; Advice tests | — | No new work | No automatic long-term treatment workflow |
| UC36 | Write e-Prescription | IMPLEMENTED | ClinicalPrescriptionController/service; current-care Doctor, DRAFT CAS, ISSUED immutable/replay-key semantics; owner items/history; Prescription tests | — | No new work | V099 DB races blocked |
| UC37 | View e-Prescriptions | IMPLEMENTED | New PatientPrescriptionController/service; self/confirmed dependent ISSUED list/detail, canonical Visit linkage, batch names/items, stable DB pagination; 15 new tests | — | Completed in c30a064 | No schema or frontend changes; nullable unavailable source fields documented |
| UC38 | Request X-ray, CT, or MRI | IMPLEMENTED | One Clinical POST mapping, Doctor current care and catalog checks; transactional Clinical order → Diagnostic registration, idempotency; UC38/registration/wiring tests | — | No new work | No duplicate Imaging POST added |
| UC39 | View Imaging Requests | PARTIAL | Technician metadata and new /imaging-requests/{id}/clinical-details use real active current room shift authorization; Diagnostic-owned reason/body area/Patient/Doctor IDs plus Clinical public instructions; security/service tests | Runtime request-to-room assignment/capacity lifecycle for unassigned UC38 requests is incomplete | Current assignment and authorized detail reads implemented; remaining BLOCKED_BY_DOMAIN_CONTRACT | UC38 registers unassigned requests under V098; RoomReservationPort capacity/version/assignment owner remains required |
| UC40 | Upload and Send Imaging Study | OWNED BY EXISTING EXTERNAL FEATURE | Diagnostic evidence/state/outbox contracts exist; real study ingest/matching/archive adapter is absent here | Imaging-owned ingest/archive plus missing Doctor return queue | No | Long Imaging integration and separate queue contract; branch not merged/copied |
| UC41 | View DICOM Images | PARTIAL | Real VisitDiagnosticPort supports current Doctor review linkage, findings review mutations tested | Study-scoped grant/DICOMweb/archive runtime absent | BLOCKED | Secure viewer owned by Imaging integration; no public Orthanc URL |
| UC42 | Record Imaging Findings | PARTIAL | Existing review/draft/report/completion endpoints preserve Doctor/version/study/idempotency/audit/outbox; real V101 Visit acknowledgment; focused tests | Real ImagingStudyPort and ReportStoragePort runtime adapters absent | BLOCKED for runtime end-to-end | Owner merge required, no no-op port |
| UC43 | View Diagnostic Images | PARTIAL | Existing imaging-history endpoint now has production Patient self/dependent adapter; released validated findings/report metadata only; 70-test compatibility run | Secure image streaming | BLOCKED for viewer | Imaging-owner integration; metadata does not claim viewer completion |
| UC44 | View Imaging Request Status | PARTIAL | Existing Doctor/Receptionist Visit status and technician current assigned-room metadata now have real production authorization; Staff/Diagnostic tests | Technician projection is upload-stage PENDING/IN_PROGRESS only; completed-findings status/room-assignment lifecycle incomplete | Current assignment read implemented; remaining BLOCKED_BY_DOMAIN_CONTRACT | REVIEWED does not distinguish finalized findings; UC44 lists staff actors, so Patient extension is not authorized by current requirements |
| UC45 | Create Invoice | IMPLEMENTED | InvoiceController/InvoiceService; Receptionist closed-Visit source, catalog prices snapshotted, unique source/fingerprint replay handling; V042 owner persistence | — | No new work | BillingInvoicePostgresIntegrationTest compiled but DB execution blocked |
| UC46 | View Invoice | IMPLEMENTED | Invoice detail and /me/invoices use existing Patient Billing authorization/Receptionist permissions; confirmed totals and immutable invoice items | — | No new work | Billing DB regression blocked |
| UC47 | Record Payment at Clinic | IMPLEMENTED | PaymentController/service; Receptionist only, payable invoice, partial/full bounds, locked invoice and actor idempotency, confirmed receipt persistence | — | No new work | DB transaction/idempotency verification blocked |
| UC48 | Pay Invoice Online | IMPLEMENTED | Payment-attempt/PayOS verified webhook/reconciliation flow; Patient invoice authority, outstanding amount, provider confirmation controls paid state | — | No new work | Gateway credentials/network runtime and DB verification remain external |
| UC49 | View Payment History | IMPLEMENTED | New GET /payments uses authorized Patient scope or clinic Billing staff roles; confirmed-only page, date range, stable order; BillingRead service/HTTP/JPQL tests | — | Completed in d590957 | Existing PayOS attempt mutations unchanged |
| UC50 | View Unpaid Invoices | IMPLEMENTED | New GET /invoices/unpaid: Receptionist/Admin/Owner, OPEN with positive persisted-snapshot balance, confirmed payments only, DB pagination | — | Completed in d590957 | Not the frontend mock pre-invoice unpaid-services concept |
| UC51 | Print Invoice and Export Receipt | IMPLEMENTED | New Receptionist-only /invoices/{id}/print and /payments/{id}/receipt; private UTF-8 text attachments, exact confirmed partial payment amount; BillingRead tests | — | Completed in d590957 | Branded/PDF layout is not required or fabricated; no financial writes |
| UC52 | Rate and Review Clinic | IMPLEMENTED | PatientReviewController/ReviewService: own CLOSED eligible Visit, one review per Visit, validation and persistence; ReviewService/Controller tests | — | No new work | DB uniqueness verification blocked |
| UC53 | View Public Reviews | IMPLEMENTED | PublicReviewController; PUBLISHED-only query, stable pagination/sort, clinic replies; review tests | — | No new work | No hidden/pending review projection |
| UC54 | Approve or Hide Patient Reviews | IMPLEMENTED | AdminReviewController/service; role boundary, versioned state transitions PENDING/PUBLISHED/HIDDEN and audit; review tests | — | No new work | DB suites blocked |
| UC55 | Reply to Patient Review | IMPLEMENTED | Admin-only reply endpoint/service, text validation/version/audit; ReviewTextValidationHttpTest and review tests | — | No new work | No public mutation |
| UC56 | View Notifications | IMPLEMENTED | MyNotificationController/service: recipient-account pagination/count/owned-row read and read-all, related record authority rechecked; Notification tests | — | No new work | External delivery channels depend on configured providers |
| UC57 | View Rooms and Availability | PARTIAL | StaffRoomQueryController exposes real ordered rooms/type/capabilities/services/active flag to permitted clinic roles; StaffRoomQueryControllerTest | Occupancy/time-based availability projection is absent | BLOCKED for full availability semantics | Need agreed instant/interval and service/capacity definition; active is not an empty room |
| UC58 | Add Clinic Room | IMPLEMENTED | VisitRoomAdminController/ManageVisitRoomService; Admin role, catalog capability checks, coordination guard and audit; RoomCapacity/Admin tests | — | No new work | DB constraints blocked |
| UC59 | Update Room Details | IMPLEMENTED | Admin room update with capability/service validation and existing reservations/coordination guard; room service tests | — | No new work | No unrelated room assignment work |
| UC60 | Mark Room Available or Unavailable | PARTIAL | Admin activation/deactivation exists with resource impact checks/audit | Staff assigned-responsibility authorization/temporary availability contract absent | BLOCKED | Do not equate persistent activation with every actor's temporary room availability |
| UC61 | Add Clinic Service | IMPLEMENTED | ClinicServiceController/owner service; Admin role, validated service/type/price and persisted audit; ClinicServiceHandler/Admin controller tests | — | No new work | LABORATORY enum is catalog only |
| UC62 | Update Service Details and Price | IMPLEMENTED | Admin service update/activation and catalog snapshots; existing booking/invoice prices preserved; catalog/coordination tests | — | No new work | No medicine inventory |
| UC63 | View Services and Prices | IMPLEMENTED | Public clinic-services API filters offered services; staff/admin projections authorized; catalog handler/controller tests | — | No new work | No frontend edits |
| UC64 | Add or Edit Clinic Website Content | IMPLEMENTED | AdminWebsiteContentController/ClinicContentService; persisted validated content/version/audit and image validation; Content service/controller/architecture tests | — | No new work | DB suites blocked |
| UC65 | Post Clinic Announcement | IMPLEMENTED | AdminAnnouncementController/service; validated audience/channels, persisted publication and notification owner integration; content/notification tests | — | No new work | Configured delivery channels only |
| UC66 | View Clinic Information and Announcements | IMPLEMENTED | PublicClinicContentController/public owner queries; published/public-only pages/articles/announcements, no draft leakage; content tests | — | No new work | No assumptions from mock site content |
| UC67 | View System Activity Logs | IMPLEMENTED | Admin/Owner list/detail with date/category/actor/record filters, stable pagination; V103 composes recorded account/appointment/Visit/care/queue/Diagnostic actions plus existing staff/room/service/content; ActivityLog tests | No missing recorded-action read contract; event producers with no audit evidence cannot be reconstructed | Completed safe read composition | PostgreSQL V103 verification BLOCKED; no invented Clinical/payment history or new event producer |
| UC68 | View Clinic Dashboard | PARTIAL | ReportingController Admin/Owner; real appointments/Visits/services and prior-period metrics; mapper explicitly NO_SOURCE for other areas; Reporting tests | Imaging/payment/unpaid/feedback metrics and queue source | BLOCKED for unspecified metric/queue portions | Agree time basis/current versus historical unpaid balances; owner summary contracts, queue model |
| UC69 | Create Clinic Reports | PARTIAL | ReportingService subjects only APPOINTMENTS/VISITS/SERVICES; date validation/export; repository count queries and Reporting tests | Imaging/payment/unpaid/feedback report subjects and metrics | BLOCKED for undefined report contract | Owner metrics/public reporting services and required metric/time-basis decision |


## Shadow plan and action record

Scope: UC01–UC69 backend audit and source-supported missing backend reads. No frontend, remote delivery, queue schema, Laboratory workflow, or Imaging-owner implementation changes.

Baseline: `feat/hung/KAN-XX-backend-completion`, clean at `4fdfda2`. `git fetch origin` succeeded. Maven 3.9.16 runs with Java 21.0.9. V099/V100/V101 belong to the committed parent stack.

Plan: inspect requirements and actual controller/service/persistence/security/tests; implement and verify independent confirmed gaps; record unresolved requirement and owner dependencies explicitly; run relevant broad regression before any completion claim.

Current checkpoint: independent source-supported implementations verified; whole-backend completion remains BLOCKED by the decisions and owner dependencies below. No completion or delivery approval inferred.

UC34 release decision: BR-39 requires clinic availability in addition to completed examination. `ClinicalExaminationJpaEntity` currently stores only DRAFT/COMPLETED; Medical Documents stores its own independent `releasedAt`. A team policy decision is pending; completion alone will not be treated as release.

Verification commands: `mvnw.cmd -DskipTests compile`, `mvnw.cmd test-compile`, focused `mvnw.cmd -Dtest=... test`, broad non-database regression, `git diff --check`. PostgreSQL tests require separate availability evidence.

## UC37 action record

Source: UC_description.html UC37; BR-43; existing V099 Clinical issuance aggregate. Added Patient-only `GET /api/v1/prescriptions` and `GET /api/v1/prescriptions/{id}`. The service checks Patient principal and active self/CONFIRMED dependent scope; patientId only narrows that scope. Only ISSUED records are projected; detail revalidates canonical Visit/Patient linkage and does not require the issuing Doctor to remain assigned. No draft examination diagnoses are returned.

List uses database pagination ordered issuedAt DESC, id DESC; page size 1–100; one batch each for items, authorized profile details and historical Doctor names. Staff public-directory visibility is not required to retain the issuing Doctor's identity. No foreign repository imports in Clinical, no new persistence, no migration, no frontend edits.

Frontend compatibility limits: relationship is SELF/DEPENDENT (the authorization evidence, not an invented family label); patientCode is absent because the profile owner has no such field; diagnosis contains stored ICD codes rather than fabricated labels; clinicName is the published Clinic Content value or null. Additional prescription lineNumber is retained.

Validation: Java 21 compile PASS; test-compile PASS; focused/compatibility selector `PatientPrescription*Test,ClinicalPrescription*Test,ClinicalMedicalDocument*Test,ClinicalPastMedicalRecords*Test,DoctorCoreProductionWiringTest,!*PostgresIntegrationTest`: **155 tests, 0 failures, 0 errors**. Includes 15 new service/HTTP/architecture tests. Initial new-test fixture stubbing errors were corrected. An initial overbroad selector attempted 11 PostgreSQL cases, all failed to initialize because 127.0.0.1:5432 refused connections; these are BLOCKED, not passed. TCP probe independently confirmed unavailable.

Precommit validation: explicit file scope reviewed; `git diff --check` PASS; no frontend/unrelated files in scope. UC37 checkpoint READY for local commit; whole-backend completion remains in progress.

## Patient imaging history action record

Gap proven: `DiagnosticHistoryServiceImpl` already exposes `GET /api/v1/imaging-history?patientId=...` or `?visitId=...`, but its PatientDiagnosticHistoryPort had no production adapter. Added Patient-owned adapter delegating to the existing active-Patient/self/CONFIRMED-dependent access service. Visit lookup remains Visit-owned. Repository doubles in DoctorCoreProductionWiringTest now exercise the actual production authorization composition for self, dependent, unrelated profile, revoked relationship and inactive Patient.

History response is no-store. It returns operational request metadata plus study reference/findings/report ID only for completed findings matched to an explicitly released, validated report; canceled requests cannot expose those result fields. No storage key, binary content, raw DICOM URL, or viewer grant is returned. UC33's dedicated current-care/different-historical-Doctor path remains unchanged. Secure image streaming is BLOCKED by Imaging-owner integration; this is partial UC43 support, not full viewer completion.

Validation: compile PASS, test-compile PASS; selector `DoctorCoreProductionWiringTest,DiagnosticHistoryT015Test,DiagnosticControllersT016Test,DiagnosticStatusT015Test,DiagnosticPatientHistoryQueryServiceImplTest,ClinicalPastMedicalRecords*Test,DoctorImagingRegistrationTest,VisitDiagnostic*Test,PatientProfileAccess*Test`: **70 tests, 0 failures, 0 errors**. No migration. Local commit scope: this record, PatientDiagnosticHistoryAdapter, DiagnosticHistoryServiceImpl, DiagnosticHistoryController, DiagnosticHistoryT015Test, DoctorCoreProductionWiringTest. Precommit checkpoint READY after diff validation.

## Billing reads/export action record — UC49/50/51

Evidence: BR-55/56/57 and V042 invoice/item/payment tables exist; production controllers previously offered invoice detail/Patient invoice list/payment-attempt detail but no completed-history list, unpaid-invoice list or printable export. Implemented:

- `GET /api/v1/payments?patientId&from&to&page&size`: confirmed payments only; active Patient scope comes from the existing Billing authorization service (self/confirmed dependent). Receptionist/Admin/Owner use their existing clinic Billing read roles. Optional patientId narrows scope. Date filters use clinic-local inclusive dates converted to an exclusive upper instant.
- `GET /api/v1/invoices/unpaid?page&size`: Receptionist/Admin/Owner only; OPEN invoices with positive outstanding balance. Totals use immutable invoice line price/quantity snapshots, paid amount uses CONFIRMED payments only. VOID, PAID and fully covered invoices do not enter the query. Oldest invoice first, ID tie-breaker.
- `GET /api/v1/invoices/{invoiceId}/print` and `GET /api/v1/payments/{paymentId}/receipt`: Receptionist-only UTF-8 text attachments suitable for printing/export. Receipt identifies exactly one confirmed payment and its actual partial/full amount, never substitutes the invoice total. Invoice export uses the existing authorized owner service and stored line snapshots. No HTML/scripts, gateway URLs, invented receipt numbers or financial mutations.

Read pages run at REPEATABLE_READ, use bounded database pagination and a count query without per-row secondary queries. Existing payment idempotency/webhooks/gateway remain unchanged. Frontend mock unpaid-service/pre-invoice data is not treated as invoice authority; no speculative pre-invoice billing or V102 migration was added.

Validation: compile PASS, test-compile PASS; `BillingRead*Test,PatientPrescription*Test,DoctorCoreProductionWiringTest,Notification*Test,Reporting*Test,!*PostgresIntegrationTest`: **75 tests, 0 failures, 0 errors**. Includes 14 Billing service/HTTP/Hibernate-query checks. Hibernate validates both list/count JPQL against real entities without a database; SQL execution remains PostgreSQL BLOCKED. Prior fixture/DB failures are recorded above and not counted as passes.

Precommit checkpoint READY: only this audit, new BillingRead controller/service/repository/transport handler and three tests, plus the compatible BillingExceptionHandler status additions. No existing payment mutation tests were removed or weakened. Billing DB integration regression remains BLOCKED by unavailable PostgreSQL.

## UC27/current-care read checkpoint

Extended the existing `GET /api/v1/visits/{visitId}/progress` instead of creating a parallel API. Patient self/dependent authorization is preserved. Active Receptionists may follow clinic operational progress; active Doctors resolve accountId to Doctor profile and must match current Visit care, clinicalWorkAllowed and canonical Patient linkage. Wrong Doctor, inactive/profileless Doctor, closed/nonclinical Visit and mismatched projection fail closed. No client Doctor/Patient target is accepted. Response is no-store and contains only existing operational fields. Technician authorization remains blocked by the absent assigned-room owner adapter; no queue, room assignment, completed-service state, Clinical/Billing/Prescription mutation was invented.

The existing Patient-owned clinical identity query previously called the profile owner once per distinct Patient on Doctor active-Visit reads. It now uses the real batch profile directory method. The same four identity fields, null/empty handling, deduplication, immutable map, missing-profile behavior and dependency-error propagation remain tested; no additional PII is projected.

Validation: compile PASS, test-compile PASS; `VisitProgress*Test,PatientClinicalIdentityQueryServiceImplTest,DoctorActiveVisitRead*Test,VisitClinicalCareContextServiceImplTest,DoctorCurrentCareAuthorizationTest,DoctorCoreProductionWiringTest,ClinicalPastMedicalRecords*Test,VisitPatientHistoryQueryServiceImplTest,VisitApiExceptionHandlerTest,PatientPrescription*Test`: **103 tests, 0 failures, 0 errors**. Includes eight new progress service cases and three HTTP/security cases. Existing six identity-query tests retain content/privacy assertions and now verify one batch instead of N per-ID owner calls.

Precommit scope: this audit, VisitReceptionReadController/Service/Impl, two VisitProgress tests, PatientClinicalIdentityQueryServiceImpl and its test. Diff validation READY; no schema or frontend changes.

## Broad regression evidence so far

Initial broad selector `*Test,*Tests,!*Postgres*,!*Database*,!OtcmsBeApplicationTests,!T005RealLoginIntegrationTest,!T006A8EvidenceTest`: 1098 tests, 0 failures, 2 errors. The two errors are ContentAuditMigrationOrderTest, which uses direct JDBC/Flyway and requires local database credentials despite not having Postgres in its name. 1096 tests passed; the two DB cases are BLOCKED. Do not describe this initial Maven invocation as passing. The final non-database selector must explicitly exclude that DB suite as well. No secret file was read or database credentials invented.

## Final verification — 2026-11-05

- JDK: 21.0.9, Maven Wrapper 3.9.16.
- `mvnw.cmd -DskipTests compile`: PASS; log `target/backend-completion-final-compile.log`.
- `mvnw.cmd test-compile`: PASS, including database test sources; log `target/backend-completion-final-test-compile.log`.
- `mvnw.cmd "-Dtest=*Test,*Tests,!*Postgres*,!*Database*,!OtcmsBeApplicationTests,!T005RealLoginIntegrationTest,!T006A8EvidenceTest,!ContentAuditMigrationOrderTest" test`: **1107 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Log: `target/backend-completion-final-regression.log`. This includes unit, controller, architecture, Hibernate semantic, Doctor/Clinical/Diagnostic/current-care/UC33/UC38/UC42 compatibility tests. Excluded DB suites were not made passing or silently skipped by editing tests.
- `POSTGRESQL = BLOCKED`: loopback 127.0.0.1:5432 unavailable on final TCP probe; earlier real Maven database context attempts reported connection refused. SQL execution, Flyway application, DB concurrency/rollback and complete production application startup are not verified.
- `git diff --check`: PASS; unresolved index entries: NONE; Java source/test conflict markers: NONE (rg exit 1 means no match).
- Frontend and migration diffs against 4fdfda2: EMPTY. V099 Prescription, V100 Medical Documents and V101 Visit/Diagnostic receipts remain tracked and unchanged. No new migration/version reserved. Fetched origin/product currently ends at V099; no remote branch was merged or copied.
- Working tree was clean after each implementation commit. No unrelated/untracked user file was staged, discarded or deleted. No push, PR, merge, rebase or reset was performed.

Counts across exactly 69 rows: **49 IMPLEMENTED, 16 PARTIAL, 3 BLOCKED, 1 OWNED BY EXISTING EXTERNAL FEATURE, 0 MISSING, 0 OUT OF SCOPE**. PARTIAL does not mean complete: its remaining portion is explicitly blocked or requires the bounded decision stated in its row. These are source/logic coverage classifications, not database/deployment approval.

## Blocking evidence and minimum decisions

1. **UC34 / BR-39 clinic availability.** Approve the owner and explicit availability signal for a completed Examination, including Advice and post-publication corrections. No Examination releasedAt/publication fact currently exists. A document's release is independent; AfterVisitAdviceServiceImpl can still update advice during allowed current care. Do not silently equate COMPLETED, CLOSED, prescription issuance, or a released PDF with release of the structured examination. Decide whether to adopt an explicitly released report as a signal or approve a separate Examination publication contract. Until then the proposed Patient list/detail/navigation endpoints are not implemented and cannot expose drafts.
2. **UC28 / BR-37 closure.** Actor and scheduled-Visit policy are resolved by the phase 2 instruction and implemented in 38c2611. Active Receptionist, OPEN Visit, completed Clinical evidence, reservations/audit/review notification are enforced under the shared guard. Required imaging fails closed until Diagnostic supplies a current-Visit per-order completed/released findings contract.
3. **Queue/room model.** `Doctor queue return = BLOCKED by missing production queue model/contract`. V023 stores walk-in reception entries/history with FIFO arrival ordering and history-preserving exits. Visit assignments/room reservations are care/occupancy facts, not waiting-list positions. UC24/25/26, scheduled first waiting-list semantics, remaining UC27 queue/completed-service projection, and full UC57/60 availability require approved destination, ordering, actor responsibility, service completion, temporal availability and history semantics. Minimal future contract: a Visit-owned return command triggered by authoritative result readiness, deriving requesting Doctor from backend identities, atomically claiming the existing V101 event receipt with one logical queue transition/history write; exact multi-request return policy and queue representation must first be approved. No queue/room table or fake transition was created.
4. **Imaging owner.** Phase 2 supplies RoomStatusAccessPort from actual V040 staff-shift responsibility. RoomReservationPort (capacity/modality/reservation/version), ImagingStudyPort and ReportStoragePort remain absent. UC38 requests are created unassigned by V098; no assignment is fabricated. `Secure DICOM viewer runtime integration = BLOCKED BY IMAGING OWNER MERGE`. No Long branch copy/merge or fake gateway.
5. **Patient status scope.** UC44 explicitly lists Receptionists, Orthopedic Doctors and Diagnostic Imaging Technicians. The phase 2 task conditions Patient status implementation on authoritative support; none exists. No Patient status surface is added. Existing Patient imaging history and released findings remain available under their separate authorization contract.
6. **Reporting and audit.** ReportingServiceImpl defines APPOINTMENTS/VISITS/SERVICES only. Imaging/payment/unpaid/feedback metrics need event/time bases and owner summaries; unpaid period semantics remain undefined. UC67 recorded-action reads are completed through additive V103; absent historical Clinical/payment actor events are not fabricated and are not prerequisites for reading the recorded actions required by UC67.
7. **Patient history fields.** UC12 Receptionist basic-background authority is not defined at field level; choose the permitted read/write fields before adding that actor to medical history. Doctor and authorized Patient paths remain implemented.
8. **Availability source.** UC_description.html decisions section explicitly leaves Doctor availability ownership/source open. Existing scheduling/capacity code is preserved; no workforce scheduling scope was invented to resolve that decision.
9. **Laboratory / Blood Test.** `LABORATORY IMPLEMENTATION BLOCKED BY REQUIREMENT GAP`. Catalog LABORATORY/legacy role constants are not an approved order/specimen/result/release workflow. No Laboratory UC/BR number, schema, endpoint or fake result block was introduced.
10. **Database/infrastructure.** Restore/provision approved PostgreSQL and run the compiled DB suites and migration/concurrency checks. Real SMTP, PayOS and Imaging integration runtime verification remains environment/owner work; no fake success is reported.

## Review and sync-back

AI Agent Recommendation: **PENDING HUMAN REVIEW** for the unresolved Examination release, imaging-completion, queue/room and reporting contracts above. Phase 2 implements the explicitly requested UC28 actor decision and source-supported recorded-action reads; it does not approve unresolved contracts.

Human Final Review: **PENDING HUMAN REVIEW**; authorized reviewer identity, decision and timestamp not supplied. The implementation task authorizes the tested independent checkpoints; it does not approve these unresolved proposals. Persist the approved decisions before dependent implementation. No proposal is marked approved by the agent.

Implementation-ready: YES for the verified source-supported checkpoints; NO for the blocked extensions. Whole backend complete: NO. **READY FOR FINAL BACKEND PUSH: NO**. Source-supported reads are verified locally, but the stated authorization/state and integration gaps must remain visible for the team.

Local implementation commits:

- `c30a064` — Patient e-prescriptions UC37.
- `8045737` — Patient-owned imaging history authorization/released-report checks.
- `d590957` — Billing completed payment history, unpaid invoice balances and printable exports UC49/50/51.
- `9346470` — Authorized staff Visit progress UC27 and batched current-care identity lookup.

Manual delivery only. Existing parent stack remains intact. No further independent production change is claimed without the missing requirement/owner contracts.


## Exact changed-file boundary

All paths below are backend-only and relative to the repository root. They are already in the local implementation commits, except this record's final verification update. No blanket staging was used. Final documentation staging boundary is only `backend/docs/backend-completion-audit.md`; after its local commit, no files remain to stage.

```text
backend/docs/backend-completion-audit.md
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/PatientProfileDirectory.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/PatientProfileDirectoryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/billing/controller/BillingReadController.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/billing/exception/BillingExceptionHandler.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/billing/exception/BillingReadApiExceptionHandler.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/billing/repository/BillingReadRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/billing/service/BillingReadService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/billing/service/impl/BillingReadServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/controller/PatientPrescriptionController.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/exception/ClinicalApiExceptionHandler.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalPrescriptionRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/PatientPrescriptionQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/PatientPrescriptionQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/controller/DiagnosticHistoryController.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/impl/DiagnosticHistoryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/patient/integration/diagnostic/PatientDiagnosticHistoryAdapter.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/PatientClinicalIdentityQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/staff/service/DoctorProfileIdentityQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/staff/service/impl/DoctorProfileIdentityQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/controller/VisitReceptionReadController.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/VisitReceptionReadService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/VisitReceptionReadServiceImpl.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/billing/BillingReadControllerTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/billing/BillingReadQuerySemanticTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/billing/BillingReadServiceTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorCoreProductionWiringTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/PatientPrescriptionArchitectureTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/PatientPrescriptionControllerTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/PatientPrescriptionQueryServiceTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/DiagnosticHistoryT015Test.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/patient/PatientClinicalIdentityQueryServiceImplTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/visit/VisitProgressAuthorizationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/visit/VisitProgressControllerTest.java
```

## Phase 2 - UC28 checkpoint

The explicit UC28 actor decision in the continuation task supersedes the earlier actor question. Closure now requires active Receptionist, OPEN Visit, no IN_PROGRESS care, existing COMPLETED examination, zero outstanding orders and all non-cancelled order results. Required imaging fails closed: none of the existing Diagnostic public APIs proves per-order completed/released findings for a current Visit. No payment gate, invoice creation, draft release, prescription issuance or clinical-history mutation is added.

Existing transaction guard ? Visit row lock ? Clinical owner read ? closure/release/audit/notification all share the transaction. Examination writes, generic order submission and UC38 acquire the same guard before their first Visit read; the order writer also rechecks current care under the guard. This includes the UC38 replay transaction and avoids an early cached Visit read winning after closure. See backend-completion-phase2-action-record.md. New V102 is required for the database queue actor and scheduled closure audit; applied V027 is unchanged.

Focused UC28/Clinical/Diagnostic/Billing/notification/review compatibility: 580 PASS after updating test constructor/wiring fixtures and expected eligibility recheck count. Initial fixture-only failures were corrected; no specification workaround. PostgreSQL migration and race execution remain unverified pending DB availability.

## Phase 2 - reassessment of all original 16 PARTIAL UCs

Classification concerns the outstanding behavior, not already working endpoints. Bounded safe work can be completed even when the full remaining UC is blocked. No requirement or owner-runtime dependency is treated as complete.

| UC | Classification | Evidence and disposition |
| --- | --- | --- |
| UC12 | BLOCKED_BY_REQUIREMENT | UC12 says Receptionist basic background "where allowed"; no field whitelist. Patient/Doctor medical-history writes preserved. |
| UC17 | BLOCKED_BY_REQUIREMENT | Existing availability/capacity works; overview decisions still leave ownership and maintenance source open. No workforce policy invented. |
| UC22 | BLOCKED_BY_REQUIREMENT | Current overview requires first waiting list; approved appointment D11 deliberately excludes scheduled check-in from reception FIFO. No unified queue policy assumed. |
| UC23 | BLOCKED_BY_DOMAIN_CONTRACT | V023 represents WALK_IN FIFO only; scheduled waiting-list identity/ordering/history contract absent. |
| UC24 | BLOCKED_BY_DOMAIN_CONTRACT | Care reservations are occupancy, not a next-service waiting-list/room-transfer model. RoomReservationPort and queue composition remain undefined. |
| UC27 | BLOCKED_BY_DOMAIN_CONTRACT | No canonical room queue or completed-service progress projection. Current Doctor/Receptionist/Patient reads preserved. Staff shift room identity does not establish whole-Visit clinical visibility. |
| UC39 | IMPLEMENTABLE_NOW | Implemented Staff-owned current-shift RoomStatusAccessPort and UC39 clinical-details read with canonical Clinical instructions. Remaining BLOCKED_BY_DOMAIN_CONTRACT: UC38 request-to-room assignment/capacity lifecycle. |
| UC41 | BLOCKED_BY_EXTERNAL_OWNER | Study-scoped grant, secure DICOMweb/archive runtime belong to Imaging. No branch copy/merge. |
| UC42 | BLOCKED_BY_EXTERNAL_OWNER | Existing findings lifecycle needs actual ImagingStudyPort and ReportStoragePort runtime adapters; metadata/state tests do not replace them. |
| UC43 | BLOCKED_BY_EXTERNAL_OWNER | Released findings/history authorization works; secure image streaming still Imaging-owned. |
| UC44 | IMPLEMENTABLE_NOW | Real technician assignment scope implemented from V040. Remaining BLOCKED_BY_DOMAIN_CONTRACT: existing upload-only status projection cannot prove findings completion; unassigned request lifecycle remains unresolved. Patient status has no current requirement authorization. |
| UC57 | BLOCKED_BY_REQUIREMENT | Existing roles can read rooms/type/services/active. Occupancy query hasBlockingUse supports mutation protection, but no agreed work/service interval or complete availability read definition; active is not full patient-ready availability. |
| UC60 | BLOCKED_BY_DOMAIN_CONTRACT | V040 proves some staff shift assignments, but no temporary unavailability state/lifecycle or complete all-role responsibility contract. Persistent Admin activation is not silently reused as temporary availability. |
| UC67 | IMPLEMENTABLE_NOW | V103 exposes existing recorded actor/time/action/target evidence through the established safe Admin/Owner read API; source-table event producer retrofit is outside UC67 read scope. |
| UC68 | BLOCKED_BY_REQUIREMENT | Existing appointment/Visit/service formulas retained. Queue definition, imaging time basis, received-payment attribution, historical/current unpaid amount and feedback population undefined. No business KPI invented. |
| UC69 | BLOCKED_BY_REQUIREMENT | Only APPOINTMENTS/VISITS/SERVICES report subjects and count/time definitions exist. Additional subject formulas/period semantics/owner summaries undefined. |

UC34 remains BLOCKED: COMPLETED examination is not publication, and an independently released Medical Document does not release the Examination aggregate. No new authoritative relationship or persisted examination release signal was found.

UC28 moved from BLOCKED to PARTIAL: safely closable non-imaging Visits now follow UC28, while imaging completion remains an explicit missing Diagnostic owner contract. Updated matrix total: 49 IMPLEMENTED / 16 PARTIAL / 3 BLOCKED / 1 EXTERNAL (69 UCs).

Read checkpoint logs: target/phase2-reads-{compile,test-compile,focused}.log. Final compile PASS, test-compile PASS, 115 focused/compatibility tests PASS (0 failures/errors/skips), including Clinical/Diagnostic production wiring, UC39 HTTP authorization, Staff shift composition and existing room/status/ActivityLog coverage. PostgreSQL remains BLOCKED; database fixtures compile but are not claimed as executed.

## Phase 2 - final verification and delivery state

- Java 21.0.9; Maven Wrapper 3.9.16; branch feat/hung/KAN-XX-backend-completion unchanged.
- Compile PASS and test-compile PASS: target/phase2-uc28-{compile,test-compile}.log and target/phase2-reads-{compile,test-compile}.log. Database test sources are included in compilation.
- UC28/Clinical/Diagnostic/Billing/notification/review compatibility: 580 tests PASS, no failure/error/skip; target/phase2-uc28-focused.log.
- UC67/Staff/Diagnostic/room reads and actual production wiring: 115 tests PASS, no failure/error/skip; target/phase2-reads-focused.log.
- Broad exact selector: `mvnw.cmd "-Dtest=*Test,*Tests,!*Postgres*,!*Database*,!OtcmsBeApplicationTests,!T005RealLoginIntegrationTest,!T006A8EvidenceTest,!ContentAuditMigrationOrderTest" test`: **1163 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** at 2026-11-05T13:26:26+07:00; target/phase2-final-regression.log.
- PostgreSQL TCP probe 127.0.0.1:5432 unavailable: **POSTGRESQL = BLOCKED**. V102/V103 SQL execution, real lock/race/rollback behavior and new DB fixture cases are not claimed as passed. No service/database startup, reset or new credential access.
- V102/V103 are additive; previously applied migrations unchanged. No external Imaging branch copied or merged. No frontend edits, new branch, push or remote delivery.
- Local precommit checks: scoped backend-only file review and git diff --check PASS. Final working-tree/commit evidence is recorded in the final response; no delivery approval inferred.

Phase 2 implementation commits: 38c2611 (UC28), 085dc9f (UC67), 387cb9f (assigned-technician status authorization and UC39 clinical-details read). A final documentation commit records this completed audit. UC67 now fulfills reading recorded account/medical/operational actions. This does not claim complete future audit publication for every operation. Other remaining partials and the three blocked UCs retain their exact missing contract in the matrix.

READY FOR FINAL BACKEND PUSH: **NO**. Doctor return queue, Laboratory workflow, secure Imaging viewer and PostgreSQL verification remain blocked. Required-imaging Visit closure intentionally fails closed, with no fabricated completion/release.

Final test-source compilation after the DB fixture review: PASS, 231 test source files, Java 21; log target/phase2-final-test-compile.log. This fixture-only change does not alter the passing non-database regression. Final source status is unchanged from the 1163-test green run. Final docs-only staging boundary: backend/docs/backend-completion-audit.md and backend/docs/backend-completion-phase2-action-record.md.
