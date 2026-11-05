# Backend completion audit

## Shadow plan and action record

Scope: UC01–UC69 backend audit and source-supported missing backend reads. No frontend, remote delivery, queue schema, Laboratory workflow, or Imaging-owner implementation changes.

Baseline: `feat/hung/KAN-XX-backend-completion`, clean at `4fdfda2`. `git fetch origin` succeeded. Maven 3.9.16 runs with Java 21.0.9. V099/V100/V101 belong to the committed parent stack.

Plan: inspect requirements and actual controller/service/persistence/security/tests; implement and verify independent confirmed gaps; record unresolved requirement and owner dependencies explicitly; run relevant broad regression before any completion claim.

Current checkpoint: audit in progress. No completion or delivery approval inferred.

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
