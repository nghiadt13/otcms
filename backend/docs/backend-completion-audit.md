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
