# Post-Code Review — FEAT-001 T004

## Review status

- **AI Agent Recommendation:** REVISE_REQUIRED
- **Execution status:** BLOCKED — retry budget exhausted at A5/5
- **Human Final Review:** REVISE
- **Human reviewer:** Long (Human Director)
- **Decision recorded:** REVISE — revise plan and obtain an explicit retry-budget exception before implementation.
- **Reviewer scope:** T004 registration, normalization, consent and OTP services
- **Review attempt:** A5

## Verification evidence

- Exact approved command: `.\mvnw.cmd test`.
- Result: `BUILD SUCCESS`; 1 test, 0 failures, 0 errors, 0 skipped.
- Flyway validated V001 and Spring initialized eight JPA repositories.
- Test inventory contains only `OtcmsBeApplicationTests.contextLoads()`; no focused T004 behavior test exists.

## Passing implementation evidence

- Register, verify-email and resend-OTP endpoints exist with safe error envelopes and HTTP 429 mapping for rate-limit errors.
- Resend uses the typed `AuthProperties.resend()` policy.
- Concurrent duplicate-email persistence errors map to `EMAIL_ALREADY_REGISTERED`.
- Registration and resend delivery failures retain pending state and persist sanitized authentication-audit outcomes.
- OTP values are generated with `SecureRandom` and stored as password-encoder hashes rather than plaintext.

## Blocking findings

1. **HIGH — Rate-limit persistence:** `RegistrationServiceImpl` uses a process-local `ConcurrentHashMap` for registration throttling. This conflicts with the approved PostgreSQL-backed bucket decision in `PLAN.md` and can be bypassed by restart or multi-node routing.
2. **HIGH — Activation invariant:** `verifyEmail` activates the account after OTP validation without querying required patient/guardian consent evidence. The mandatory-consent-before-activation invariant is therefore not enforced at activation time.
3. **HIGH — Concurrent resend:** resend count, supersede and challenge insertion are not serialized. The schema has no uniqueness constraint for one current challenge, so concurrent requests can leave multiple valid current OTP challenges.
4. **MEDIUM — Verification coverage:** no tests exercise registration validation/normalization, age and guardian consent, duplicate races, OTP expiry/attempt/replay, resend concurrency/rate limit, activation, or email partial failure.

## Required human decision

AI RECOMMENDATION: PENDING HUMAN REVIEW

HUMAN DECISION REQUIRED: Approve a revised T004 plan and explicit retry-budget exception to implement PostgreSQL-backed rate limiting, enforce consent during activation, serialize resend/current-challenge creation, and add focused tests; or explicitly accept/defer each named limitation.

NEXT STEP: Human Director records APPROVED, REVISE, or REJECTED. No A6 implementation is authorized until that decision is persisted.

## Human Final Review

- **Status:** REVISE
- **Decision:** Revise T004 before any further execution. The next approved slice must implement PostgreSQL-backed registration rate limiting, enforce consent at activation, serialize resend/current-challenge creation, and add focused behavioral tests. A new execution attempt requires an explicit retry-budget exception and updated execution record.
- **Reviewer:** Long (Human Director)
- **Reviewed at:** 2026-09-30
- **Follow-up:** Revised plan/checkpoint and one-attempt retry-budget exception are approved. Issue a new task-specific grant referencing the revised checkpoint; do not perform further automatic retries after that bounded attempt.

## Revised Attempt R1 Review

- **Status:** REVISE_REQUIRED / BLOCKED
- **Verification:** `.\mvnw.cmd test` passed with 1 context test only.
- **Passing corrections:** activation re-checks patient/guardian consent; resend locks account and relies on the current-challenge uniqueness index; process-local registration limiter was removed.
- **Remaining blockers:** PostgreSQL rate-limit evidence stores raw client IP and uses `Integer.hashCode` rather than the required safe HMAC identifier; focused T004 tests are still absent; registration success remains HTTP 201 instead of the SPEC-required HTTP 202.
- **Decision boundary:** the one approved revised attempt is consumed. Further changes require a new Human checkpoint and retry-budget decision.

## Human Final Review — New Bounded Retry Budget

- **Status:** REVISE
- **Decision:** Approve a new bounded retry budget for the three remaining blockers.
- **Reviewer:** Long (Human Director)
- **Reviewed at:** 2026-09-30
- **Bounded scope:** safe HMAC rate-limit identifier with no raw IP persistence; focused T004 regression/concurrency/partial-email tests; registration success response changed to HTTP 202 per SPEC.
- **Execution rule:** one new task-specific grant must reference this checkpoint. No automatic retries beyond the new bounded attempt.

## Final Bounded Attempt Review

- **Status:** REVISE_REQUIRED / BLOCKED
- **Verification:** `.\mvnw.cmd test` passed with 1 context test only.
- **Passing corrections:** HMAC-SHA256 rate-limit identifier, no raw IP persistence, HTTP 202 registration response; earlier consent and resend concurrency corrections retained.
- **Remaining blocker:** focused tests required by the checkpoint were not added. There is no evidence for HMAC/no-raw-IP, 202 contract, registration/consent/OTP lifecycle, concurrency/race or partial-email behavior.
- **Decision boundary:** final bounded retry budget is consumed. Further changes require a new Human Review and checkpoint.

## Authoritative Review Rewrite — T004 Current State

- **Status:** `REVISE_REQUIRED` / `BLOCKED`
- **Supersedes:** earlier A5, R1 and Final Bounded Attempt summaries above; those entries remain historical evidence.
- **Implementation accepted by inspection:** PostgreSQL advisory-lock rate limiting with HMAC-SHA256 identifier and no raw-IP persistence; consent re-check before activation; resend account locking; HTTP 202 registration response.
- **Verification evidence:** `\.\mvnw.cmd test` → `BUILD SUCCESS`; 1 test, 0 failures/errors/skips. This is context-startup evidence only.
- **Single remaining delivery blocker:** the approved focused T004 test set is absent. No tests prove HMAC/no-raw-IP, 202 response, registration validation/consent, OTP lifecycle, resend concurrency/rate limiting, duplicate race, or email partial failure.

## New Checkpoint Proposal — T004 Test Evidence Completion

- **Purpose:** close the remaining verification gap without changing production behavior already reviewed.
- **Allowed files:** `src/test/java/.../auth/**`, test fixtures/resources, and the T004 post-code/trace evidence files. Production changes are out of scope unless a failing focused test demonstrates a defect in the reviewed behavior.
- **Required tests:**
  1. registration success returns 202 and never exposes password/OTP;
  2. normalization, age/guardian consent and activation fail-closed behavior;
  3. OTP hash, expiry, five-attempt cap, consume-once and resend supersession;
  4. PostgreSQL rate-limit boundary, HMAC identifier and absence of raw IP in audit;
  5. duplicate registration race maps to the generic contract;
  6. concurrent resend leaves exactly one current challenge;
  7. registration/resend email failure retains pending state and records safe outcome.
- **Verification command:** exact `\.\mvnw.cmd test`; focused tests must run against approved PostgreSQL-compatible infrastructure where locking/unique-index semantics are exercised.
- **Pass criteria:** all focused tests pass, no public-contract regression, and reviewer confirms no raw IP/secret/OTP leakage.
- **Human gate:** this is a proposal only. Human Director must record `APPROVED` and an explicit bounded test-only retry budget before execution.

## Test-Only Attempt Review

- **Status:** `REVISE_REQUIRED` / `BLOCKED`
- **Verification:** `.\mvnw.cmd test` passed; 4 tests total, 0 failures/errors/skips.
- **Added evidence:** HTTP 202 registration, response redaction, safe error envelope and basic derived-identifier checks.
- **Missing required evidence:** normalization/age/guardian/activation; OTP lifecycle; PostgreSQL rate-limit boundary and persisted no-raw-IP audit; duplicate race; concurrent resend uniqueness; email partial failure and HTTP 503 mapping.
- **HMAC limitation:** current test checks deterministic fixed-length derivation but not configured-secret semantics or persisted audit record contents.
- **Decision boundary:** bounded test-only retry is consumed. Further work requires a new Human Review and checkpoint.

## Human Final Review — Comprehensive T004 Closure

- **Status:** `APPROVED`
- **Decision:** Approve a new bounded retry to add all missing T004 evidence and correct any directly demonstrated contract defect.
- **Scope:** normalization, age/guardian consent and activation; full OTP lifecycle; PostgreSQL rate-limit/audit persistence; duplicate registration race; concurrent resend; email partial failure with HTTP 503; HMAC semantics and persisted audit evidence.
- **Reviewer:** Long (Human Director)
- **Reviewed at:** 2026-09-30
- **Execution rule:** one fresh grant only. No unrelated migration/POM/session/security expansion. Stop and escalate if a required production change exceeds this scope.

## Comprehensive Closure Attempt Review

- **Status:** `REVISE_REQUIRED` / `BLOCKED`
- **Verification:** `.\mvnw.cmd test` passed; 7 tests, 0 failures/errors/skips.
- **Passing changes:** HTTP 503 mapping for email delivery failure; unit evidence for normalization, 202/redaction, basic HMAC, resend supersession/locking and sanitized audit failure.
- **Required evidence still missing:** age/guardian activation fail-closed; OTP hash/expiry/attempt/replay/consume lifecycle; PostgreSQL rate-limit/audit persistence; duplicate registration race; actual concurrent resend uniqueness; registration email partial-failure pending-state persistence; configured-secret HMAC and persisted audit contents.
- **Review conclusion:** Mockito/unit tests do not prove PostgreSQL locking, unique partial-index or transaction semantics. T004 cannot be marked PASS.
- **Decision boundary:** comprehensive bounded retry is consumed. New work requires another Human Review and checkpoint.

## Human Final Review — PostgreSQL Integration-Test Attempt

- **Status:** `REVISE`
- **Decision:** Approve one bounded PostgreSQL integration-test attempt for the remaining T004 gaps.
- **Scope:** Integration tests only; production changes require a demonstrated failing test and new approval.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30

## PatientProfile Fix Verification Review

- **Status:** `PASS — PENDING HUMAN FINAL REVIEW`
- **Fix evidence:** removing explicit `profile.accountId` assignment resolved the `@MapsId` null-identifier defect without expanding scope.
- **Test evidence:** guardian consent assertion corrected to expect patient + guardian (2 rows); exact `.\mvnw.cmd test` passed with 12 tests, 0 failures/errors/skips against PostgreSQL 18.1/Flyway V001.
- **Review conclusion:** production fix and PostgreSQL rerun pass the approved checkpoint. Human final review remains required before T004 closure and trace/sync.

## Human Final Review — T004 Closure

- **Status:** `APPROVED`
- **Decision:** Approve T004 PatientProfile persistence fix and PostgreSQL verification.
- **Evidence:** `.\mvnw.cmd test` — 12 tests, 0 failures, 0 errors, 0 skipped.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30

## PostgreSQL Integration-Test Attempt Review

- **Status:** `REVISE_REQUIRED` / `BLOCKED`
- **Verification:** `.\mvnw.cmd test` → `BUILD FAILURE`; 12 tests, 0 failures, 2 errors, 0 skipped.
- **Passing evidence:** PostgreSQL connection and Flyway V001 validation; unit-focused tests pass; current-OTP uniqueness/audit tests that do not create a profile pass.
- **Blocking defect:** guardian registration persistence and duplicate-registration integration tests both fail at `PatientProfile` save with `org.hibernate.AssertionFailure: null identifier (PatientProfile)`, implicating the production `@MapsId`/registration persistence flow.
- **Scope decision:** no production change was made under the test-only checkpoint.
- **Human decision required:** approve a narrowly bounded production fix for the demonstrated `PatientProfile` persistence defect, then rerun PostgreSQL integration tests.

## Human Final Review — PatientProfile Persistence Fix

- **Status:** `APPROVED`
- **Decision:** Approve one bounded production fix for PatientProfile `@MapsId`/registration persistence defect, followed by PostgreSQL integration-test rerun.
- **Scope:** PatientProfile mapping and registration persistence only.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30

## Human Final Review — Test Evidence Completion

- **Status:** `APPROVED`
- **Decision:** Approve one bounded test-only retry budget for T004 Test Evidence Completion.
- **Scope:** Add focused T004 tests only; production changes require a demonstrated failing test and new approval.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30
