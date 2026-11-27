# Post-code Review — current staged delivery (2026-11-22)

- Review type: independent static review plus verification evidence; implementation remains pending Human disposition.
- Repository ownership: `team`; current `HEAD`: `0deb8373`.
- Reviewed code/test diff fingerprint (staged diff excluding the two SDD evidence files): `1af879eb2fea2c1ffc8d0543f8952f3805016d91`.
- Scope: 169 staged paths across backend layered persistence and service changes, Flyway migrations, tests, frontend doctor/account/imaging/laboratory flows, architecture/startup documentation and local startup script. Full path list is in `git diff --cached --name-only` at the current snapshot.
- Current trace report: `backend/sdd/reviews/trace-current-staged-delivery-2026-11-22.md`.
- Canonical artifacts checked: `feat-001-auth-login` SPEC v1.2.0, `feat-002-appointment-booking` SPEC v0.9.2, shared context v3.3.9, Architecture Profile v1.3.0, Constitution v2.0.0 and repository architecture instructions.

## Independent code review

- Result: no new blocking correctness finding in the latest independent static follow-up. The earlier P2 name-source finding was corrected: doctor profile now prioritizes `staffProfile.displayName`, and both unit and PostgreSQL integration tests cover differing admin/profile and account values.
- Latest reviewer observed staged tree: `0144f3ca51f258b8933f6896f41e97e29c922e12`; reviewed source/test patch fingerprint (excluding these SDD reports): `aae72d64f9af7f70d1529c7b58ec0f330863e2b3`.
- Latest follow-up reviewed staged tree: `f3dd465e0b44d84d0ec132575736e6bd7bbe7989`; reviewer confirmed the final `MedicalHistoryR3PostgresIntegrationTest` doctor-name expectation matches `MyAccountQueryServiceImpl` and found no further concrete issue in that change. Latest source/test patch fingerprint (excluding these SDD reports): `1af879eb2fea2c1ffc8d0543f8952f3805016d91`.
- Review scope includes the staged delivery's broad prior review and latest account/migration/persistence follow-up; review is static and does not replace the full test suite or requirements/architecture audit.

## Changes since previous Human approval

- A prior in-session `APPROVED` covered staged snapshot fingerprint `8692c99c5d28013452c5a63fa6a0a6ea3115ffb3`.
- This snapshot includes required fixes after that approval: V149 corrects the six-digit patient-code overflow; doctor profile reads now prefer `staffProfile.displayName` shown in the admin account list and doctor self-renaming is denied; regression tests cover these behaviors. The prior approval is recorded as stale for the current staged snapshot.
- **Human Final Review: PENDING HUMAN REVIEW** for the current snapshot. No Human approval is inferred from the earlier snapshot.

## Requirement and boundary coverage

- FEAT-002 SPEC v0.9.2 defines `AppointmentResponse` fields but does not include the new `patientCode` field: `DRIFT`, see trace report. Feature contract owner disposition is required.
- FEAT-001 SPEC v1.2.0 covers patient `/api/v1/me` display-name behavior; doctor account-profile name sourced from the admin account's full name has no current canonical REQ.
- The current registry has no canonical feature SPEC/REQ for several changed clinical, diagnostic, imaging, laboratory, reporting, staff, idempotency, activity-log, billing and doctor screen-flow changes. Tests provide behavior evidence but do not establish product requirements.
- Architecture changes were checked against `backend/CONSTITUTION.md` ARCH-01/02 and the user-provided modular-monolith/layered rules; no architecture approval is inferred for new business behavior.
- No SPEC, PLAN, TASKS, shared contract, UC, BR or MSG was modified or marked approved.

## Verification

- Approved command: `.\mvnw.cmd clean verify` (Architecture Profile v1.3.0).
- Final run: **PASS** — `2026-11-23`, `mvnw.cmd clean verify`: 2,280 tests, 0 failures, 0 errors, 7 skipped; `BUILD SUCCESS` in 10m52s. The test profile used the isolated local PostgreSQL database `otcms_test_db_20261122` on port 5433; credentials were loaded into the process only and were not written to the report.
- A preceding complete clean run ended with 2,281 tests, 0 failures, 4 errors, 7 skipped. All four errors were the same Spring context startup failure in `VisitEndOfDayPostgresIntegrationTest`: `ClinicalVisitCompletionEvidenceServiceImpl` reported no `ServiceOrderRepository` bean. The class then passed independently (3 tests, 0 failures/errors/skips), with TRACE confirming Spring Data registered `ServiceOrderRepository`; a second complete clean run passed without source changes. Record the first-run issue as transient and not reproduced, rather than a confirmed code fix.
- Focused current-fix runs: 55 tests, 0 failures/errors, 0 skipped (`Kan29MyAccountPostgresIntegrationTest` + `MyAccountQueryServiceImplTest`); 61 tests, 0 failures/errors, 1 skipped (`MedicalHistoryR3PostgresIntegrationTest`).
- Backend `clean verify` includes compile and Maven verification. Frontend lint, typecheck, tests, and build were not run.
- Review does not claim frontend lint/build/test execution.

## SDD validation state

- `/sdd-trace`: **N/A (formal command unavailable)**; a manual read-only trace is recorded in the linked report. It identifies the contract drift and missing SPEC/REQ; this manual report is not formal trace evidence.
- `/sdd-audit`: **N/A (formal command unavailable)**. This gate applies to backend source changes, and no equivalent approved audit artifact is present; a static code review is not a substitute.
- `/sdd-lint`: N/A; no `SPEC.md` changed.
- `/sdd-sync`: N/A; feature registry/shared context were not changed.
- `git diff --cached --check`: PASS. Staged secret/path scan: PASS (no matching credential patterns or local output paths); untracked `output/` remains unstaged. Backend test/build verification: PASS as above. Frontend validation: NOT RUN.

## Delivery validation summary

- `GIT VALIDATION: BLOCKED` for commit scope; ownership is `team`, source is the staged diff on `feat/long/laboratory`.
- Repository integrity: PASS (`git diff --cached --check`; staged diff is non-empty; no active Git operation observed). Secret and forbidden-file scan: PASS (0 credential-pattern matches, 0 forbidden staged paths). Constitution/RFC: N/A (Constitution unchanged). Backend tests/build: PASS (`mvnw.cmd clean verify`, exact result above). Frontend lint/test/typecheck/build: not run.
- SDD audit and formal trace evidence: unavailable in this runtime and required for changed backend source/behavior. Post-code Human Final Review: stale for this snapshot. Contract/requirement drift and missing canonical feature requirements remain unresolved.
- Required remediation: run `/sdd-audit` and `/sdd-trace --feature=<slug> --diff` in an environment that provides them; obtain feature-owner disposition for the `AppointmentResponse.patientCode` contract drift and missing requirements; have a Human review and approve this exact staged diff; run applicable frontend quality commands before delivery.

## Recommendation and residual risk

**PENDING HUMAN REVIEW.** Backend verification is green, but delivery is not SDD-complete or validated READY. Required blockers: obtain formal `/sdd-audit` and `/sdd-trace` evidence (unavailable in this runtime), resolve FEAT-002 API contract drift and missing feature requirements, run frontend quality checks, and record Human Final Review for this exact snapshot. The previous user approval applied to an older diff fingerprint and is stale. Do not commit while the repository delivery gate remains blocked.

The migration/test evidence uses the isolated local PostgreSQL test database only. Production/shared-database migration rollout and external imaging/object-storage verification are outside this evidence.
