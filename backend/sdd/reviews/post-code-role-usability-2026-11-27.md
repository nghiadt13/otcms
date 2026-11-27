# Post-code Review — Role Usability and Reception Desk

- Review type: independent static backend/frontend review.
- Project ownership: `team` (`backend/sdd/shared_context.md`, v3.3.9).
- Base: `caaa8140` (`origin/product`).
- Original staged patch fingerprint: `ca208f1e26d0a95f48891f125f3e56de088634a7`.
- Current staged patch fingerprint: `739eaea899202e92b2cc52cb5683c007b0265184`.
- Current scope: 38 staged backend/frontend source and test files, including `V152__add_reception_desks.sql`; no docs, config, test fixtures, or generated outputs staged.
- Human Final Review: `PENDING` for the current fingerprint. The approval below applies to the original fingerprint only.
- Reviewer identity: current Human collaborator in this conversation; account name is not exposed to the agent.
- Decision recorded: 2026-11-27 01:32 +07:00, in response to the exact staged fingerprint above.

## Scope reviewed

- Reception desk room type, no-service creation, room management, and receptionist shift assignment.
- Doctor appointment history/detail, shift schedule, and today's patient list usability.
- Doctor account/profile usability and compact service-order identifiers.
- Imaging findings draft restoration when reopening the visit.
- Constructor update for `ClinicalPastMedicalRecordsServiceImplTest`.

## Independent review findings

- The backend reviewer found that omitted room type and service IDs would now reject room creation. The staged correction restores the previous default behavior; the reviewer confirmed the correction and found no new concrete backend issue.
- The frontend reviewer found that booked patients without a queue number displayed `—` in the `STT` column. The staged correction now shows the displayed row ordinal and separately labels a positive queue number as `Hàng đợi N`; the reviewer confirmed the fix.
- No other concrete correctness or security finding was reported in the reviewed scope.
- Residual performance concern: doctor imaging history performs a per-order draft lookup and, when absent, a completed-report lookup. The maximum page size is 100, so this can add up to one repository lookup per item. Consider batching if latency becomes material.

## Verification

- `git diff --cached --check`: PASS.
- `npm run lint`: PASS, 0 errors and 5 warnings. Warnings are in `doctorExamLiveVisits.ts`, `useDoctorData.ts`, `TechnicianImagingUploadScreen.tsx`, and `PatientNotificationBell.test.tsx`.
- Targeted ESLint for the changed appointment and today's-patient screens: PASS.
- `.\mvnw.cmd clean -DskipTests compile`: PASS; compiled 1,116 backend sources.
- Targeted Maven tests after adding regression coverage: `.\mvnw.cmd '-Dtest=DiagnosticHistoryT015Test,ManageVisitRoomServiceImplTest' test` — PASS, 11 tests, 0 failures/errors/skips.
- Full approved `.\mvnw.cmd test` attempt: BLOCKED by local integration-test setup. Flyway reports migration V137 checksum mismatch in `127.0.0.1:5433/otcms_test_db`; `ActivityLogPostgresIntegrationTest` also requires `OTCMS_TEST_DB_PASSWORD`. The run was interrupted after these failures; no database repair was attempted.

## SDD and requirement trace

- `feat-002-appointment-booking` SPEC v0.9.2 and its Plan cover appointment workflows and include staff shifts/time-off among owned components. They do not specify the reception-desk room type or the no-service rule introduced here.
- The checked `docs/requirements/` tree contains only UC01, UC02, UC06, and UC10 requirement files. It has no canonical requirement file for the receptionist desk or the imaging-draft restoration behavior. Do not infer UC/BR identifiers for those behaviors.
- Doctor-facing appointment/schedule/patient-list changes are UI improvements over existing APIs; no backend behavior was changed for those screens.
- No matching current `/sdd-audit` evidence was available in this session. The older `post-code-current-staged-delivery-2026-11-22.md` is stale and has a different scope/fingerprint.

## Recommendation

**PENDING HUMAN REVIEW for current patch; delivery validation remains BLOCKED.** The Human confirmed that both new behaviors should remain and that the group will record their canonical requirements. The current patch adds focused regression tests, which pass, but the full Maven suite is blocked by local database/test configuration. Canonical requirement records and formal `/sdd-audit` evidence are still missing. Do not treat this report as `GIT VALIDATION: READY`.
