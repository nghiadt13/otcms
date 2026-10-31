# Implementation Plan — FEAT-001 Authentication Login/Register

# Version: 1.0.0
# Status: APPROVED
# Feature slug: `feat-001-auth-login`
# Architecture Profile: v1.3.0 — APPROVED
# Specification: v1.1.0 — APPROVED & LOCKED
# Context: v0.6.0 — APPROVED

## Methodology Profile

- **Depth:** FORMAL (inherited)
- **Risk posture:** high
- **Review route:** Product Owner, Tech Lead/Security reviewer and Clinical owner according to SPEC.
- **Planning boundary:** Only locked FEAT-001 behavior; no Google-only local password creation, MFA, employee provisioning, resource authorization or DICOM/MinIO/OHIF implementation.

## Architecture Profile Reference and Verification Evidence

- Java 21, Spring Boot 4.1.1, Spring Web MVC, Spring Security, Spring Data JPA, Flyway and PostgreSQL are approved bindings.
- Modular monolith and `auth` module package direction are approved.
- `src/main/resources/application.yml` supplies environment-based PostgreSQL/Supabase-compatible datasource configuration.
- Human-provided evidence on 2026-09-29: local PostgreSQL `localhost:5432/otcms_db`; `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` for `.\mvnw.cmd clean verify`; JAR repackaged successfully.
- Approved verification commands in Architecture Profile: `.\mvnw.cmd test` and `.\mvnw.cmd clean verify`.
- Gmail, Google OIDC, exact frontend origins, `SameSite`, internal CIDR/VPN and trusted proxy values remain integration configuration/dependency gates.

## Architectural Approach

Use a capability-owned modular monolith with a layered/hexagonal boundary inside `auth`:

```text
HTTP controller + DTO/validation
          ↓
Auth service interface / AuthServiceImpl
          ↓
Domain policy + ports (account, session, identity, OTP, email, clock, audit)
          ↓
JPA/Flyway repositories and external adapters (Google, Gmail)
```

Controllers only translate HTTP and call service interfaces. `AuthServiceImpl` owns orchestration and transaction boundaries. Security filters provide authenticated principal, JWT validation, CSRF and staff network enforcement; patient/staff business rules remain in `auth.service.impl`. External calls are ports with bounded timeout/retry configuration and fake adapters for tests.

## REQ-to-Component Mapping

| Requirement range | Component/port | Responsibility |
| :--- | :--- | :--- |
| REQ-AUTH-001..013 | `RegistrationService`, `RegistrationController`, `RegistrationRequest`, `ConsentPolicy`, `EmailNormalizer`, `PhoneNormalizer`, `AgePolicy` | Validate and normalize registration, age/guardian consent, uniqueness and pending account creation |
| REQ-AUTH-014..020 | `EmailVerificationService`, `OtpChallengeRepository`, `EmailDeliveryPort` | Generate/hash/consume OTP, resend limits, activation and delivery failure behavior |
| REQ-AUTH-021..028 | `CredentialLoginService`, `PatientLoginController`, `StaffLoginController`, `AccountStatusPolicy`, `CredentialFailurePolicy` | Portal/account checks, password authentication, lockout and generic failures |
| REQ-AUTH-029..036 | `GoogleAuthenticationService`, `GoogleOidcPort`, `ExternalIdentityRepository`, `GoogleExchangeController` | PKCE code exchange, claim validation, onboarding and conflict-safe linkage |
| REQ-AUTH-037..047 | `SessionService`, `JwtPort`, `RefreshTokenRepository`, `CsrfService`, `SessionCookiePort`, `SecurityConfig` | JWT/refresh lifecycle, rotation, reuse, session limit, cookie and CSRF |
| REQ-AUTH-048..052 | `AuthenticationAuditPort`, `RateLimitPort`, `SafeIdentifierService`, `StructuredAuthLogger` | Safe audit, HMAC identifier and typed abuse limits |
| REQ-AUTH-053..066 | `PasswordRecoveryService`, `PasswordResetChallengeRepository`, `ForgotPasswordController`, `PasswordController`, `EmailDeliveryPort` | Generic forgot, reset token lifecycle, change password and session revocation |

## Components and Planned Paths

| Layer | Planned paths | Responsibility |
| :--- | :--- | :--- |
| HTTP | `auth/controller/*Controller.java` | Endpoint routing, DTO validation, portal/network annotations; no repository access |
| Request/response | `auth/dto/request/*Request.java`, `auth/dto/response/*Response.java` | Bean Validation and safe contracts; never expose hashes/tokens |
| Mapping | `auth/mapper/AuthMapper.java` | Map persistence/service results to response DTOs |
| Use case | `auth/service/AuthService.java`, `auth/service/impl/AuthServiceImpl.java` | Orchestration, transactions and business rules with `@ears` trace |
| Policy | `auth/service/impl/*Policy.java` | Normalization, age, password, account status, lockout and rate decisions |
| Ports | `auth/service/*Port.java` | Google, email, clock, JWT, cookie, audit and rate-limit abstractions |
| Persistence | `auth/entity/*.java`, `auth/repository/*.java` | Account, profile, identity, consent, OTP, reset challenge, session and audit persistence |
| Security | `security/SecurityConfig.java`, JWT/CSRF/network components | Authentication boundary, principal, cookies, CSRF and trusted-proxy network checks |
| Common/config | `common/exception`, `common/response`, `common/validation`, `config/*` | Error envelope, correlation ID, typed properties and CORS/Jackson configuration |
| Migration | `src/main/resources/db/migration/V001__auth_schema.sql` | Additive auth schema and indexes; exact SQL reviewed before execution |
| Tests | `src/test/java/.../auth`, `.../security`, `.../persistence` | Unit, controller/security integration, PostgreSQL persistence and external partial-failure tests |

Paths are planning targets, not authorization to create files before TASKS and execution checkpoint approval.

## Data Flow

### Registration and OTP

`POST register` → DTO validation/normalization → `RegistrationService` → uniqueness/age/consent policy → account/profile/consent/OTP transaction → `EmailDeliveryPort` → `202 PENDING_VERIFICATION`. Delivery failure keeps pending state and returns safe outcome. Verify endpoint hashes submitted OTP, consumes it atomically, records activation and returns `204`.

### Local login

Portal controller → network/account-type policy → credential failure/rate policy → password encoder → account status → `SessionService` → JWT response + protected refresh cookie → safe audit. Unknown, wrong-password, wrong-portal and inactive states share the generic authentication contract.

### Google login

Frontend authorization code + PKCE → exchange controller → `GoogleOidcPort` → issuer/audience/expiry/email verification → provider-subject lookup → safe email linkage or onboarding transaction → `SessionService`. No clinical-record auto-link.

### Refresh/logout

Cookie + CSRF header → Spring Security CSRF/network checks → hash lookup/rotation/reuse policy → transactionally revoke/insert session → JWT/cookie response. Logout is idempotent and clears cookie.

### Forgot/reset/change password

Forgot controller → portal/network admission → keyed identifier lookup and rate policy → generic `202`; eligible account gets hashed 256-bit reset challenge and adapter email. Reset body token → challenge validation/attempt policy → password hash + all refresh-session revoke in one transaction. Authenticated change verifies current password, applies same policy and performs the same session revocation.

## State-change Classification and Checkpoints

| Flow | Category | Required checkpoint |
| :--- | :--- | :--- |
| Registration/consent | persistence schema + business-data mutation + external email side effect | Human-approved migration/task; transaction and fake-email tests |
| OTP activation | business-data mutation | Approved task; atomic persistence test |
| Login/session | permission/security + persistence mutation | Security review; rotation/reuse/concurrency tests |
| Google linkage | permission/security + persistence + external side effect | Security/identity review; conflict/rollback tests |
| Forgot/reset/change | permission/security + persistence + email side effect | Security review; token/session revocation tests |
| Flyway migration | persistence schema | Human migration checkpoint, rollback plan and PostgreSQL test |
| Runtime dependency/config | dependency/runtime configuration | Architecture/dependency review; secret scan and compatibility evidence |

### T004 Revised Checkpoint — Human `REVISE` Resolution

- **Decision:** `REVISE`, approved by Human Director Long on 2026-09-30 after the A5 post-code review.
- **Retry exception:** One bounded revised execution attempt is authorized after the exhausted A5 budget. No further automatic retries are authorized without a new Human decision.
- **Frozen scope:** T004 registration/consent/OTP boundary only; no unrelated session, migration, dependency or external-adapter expansion.
- **Blocker 1 — distributed rate limit:** replace the process-local registration limiter with the approved PostgreSQL-backed bucket strategy. Preserve typed limits and HTTP 429/error mapping; do not introduce Redis or a new dependency.
- **Blocker 2 — activation consent invariant:** verify required patient consent and conditional guardian consent in the same transaction immediately before activation; missing evidence must fail closed and leave the account pending.
- **Blocker 3 — resend concurrency:** serialize account/challenge state transition and enforce at most one current challenge under concurrent resend requests using the existing PostgreSQL/JPA boundary and constraints/query locking available in scope.
- **Required verification:** add focused tests for all three blockers plus duplicate registration and email partial failure, then run the approved `\.\mvnw.cmd test` command. Post-code review remains mandatory.
- **Human checkpoint status:** APPROVED FOR ONE REVISED ATTEMPT; implementation may start only after a new execution grant references this checkpoint.

### T004 Final Bounded Retry Checkpoint — Human `REVISE`

- **Decision:** Human Final Review `REVISE`, new bounded retry budget approved by Long on 2026-09-30.
- **One-attempt scope:** (1) replace raw-IP/weak-hash rate-limit audit evidence with a safe HMAC identifier and no raw IP persistence; (2) add focused T004 regression, concurrency and partial-email tests; (3) return HTTP 202 for successful registration.
- **Exclusions:** no other T004 behavior, migration, dependency/POM, session/security or external-adapter changes.
- **Gate:** issue one fresh execution grant referencing this checkpoint; if it fails, stop for another Human decision.

### T004 New Checkpoint Proposal — Test Evidence Completion

- **Status:** PROPOSED; not executable until Human approval.
- **Purpose:** close the sole remaining blocker: missing focused T004 tests.
- **Scope:** test sources/fixtures and review evidence only; production behavior is frozen unless a focused test proves a defect.
- **Required coverage:** HTTP 202/no secret leakage; normalization/age/consent/activation; OTP lifecycle; PostgreSQL rate-limit/HMAC/no-raw-IP; duplicate race; concurrent resend uniqueness; registration/resend email partial failure.
- **Verification:** `\.\mvnw.cmd test` with PostgreSQL-compatible locking/constraint semantics as required by the approved profile.
- **Exit gate:** all focused tests pass and post-code reviewer records PASS. Human must approve a bounded test-only retry budget before grant issuance.

- **Human approval:** APPROVED by Long on 2026-09-30 for one bounded test-only retry. Production edits require a demonstrated failing test and new approval.

### T004 Comprehensive Closure Checkpoint — Human Approved

- **Status:** APPROVED by Long on 2026-09-30 for one bounded attempt.
- **Scope:** add complete focused evidence for normalization/age/guardian/activation, OTP lifecycle, PostgreSQL rate-limit/audit persistence, duplicate race, concurrent resend, email partial failure/HTTP 503, and HMAC semantics/persisted audit.
- **Production correction rule:** a directly demonstrated contract defect may be corrected only within T004; unrelated boundaries remain frozen.
- **Gate:** exact `\.\mvnw.cmd test`, independent review, and no automatic retry after this attempt.

### T004 PatientProfile Persistence Fix Checkpoint — Human Approved

- **Status:** APPROVED by Long on 2026-09-30.
- **Scope:** PatientProfile `@MapsId` mapping and registration persistence only, followed by PostgreSQL integration-test rerun.
- **Exclusions:** no unrelated auth behavior, migration, dependency/POM, session/security or external-adapter changes.
- **Gate:** exact `\.\mvnw.cmd test`, focused PostgreSQL evidence, independent review; stop if additional production scope is discovered.

### T004 PostgreSQL Integration-Test Checkpoint — Human Approved

- **Status:** APPROVED by Long on 2026-09-30 for one bounded attempt.
- **Scope:** PostgreSQL integration tests for the remaining T004 gaps: consent/activation, OTP lifecycle, rate-limit/audit persistence, duplicate race, concurrent resend uniqueness, email partial failure and configured-secret HMAC audit evidence.
- **Production rule:** production changes require a demonstrated failing integration test and a new Human approval.
- **Gate:** exact `\.\mvnw.cmd test`, independent review, and no automatic retry after this attempt.

## Shared-contract Impact

- **Auth API contract:** Spec v1.1.0 is producer-owned by `auth.controller`; Patient Portal, Staff Portal and future protected modules consume the success/error envelope. Compatibility decision: additive endpoint contracts; no response hash/token leakage. Sync-back owner: auth module owner.
- **Authenticated principal:** `accountId`, `accountType`, `sessionId` and trusted authentication method are shared inputs to resource modules. Version/claims must be documented before implementation. Sync-back owner: `security` owner.
- **Database schema:** Auth module owns account/session/identity/OTP/reset/consent/audit tables. Other modules consume account ID through service contract, not repositories. Migration compatibility and `/sdd-sync` are required after implementation.
- No existing API consumer or shared contract implementation was found in the repository; compatibility baseline is a new contract.

## Dependency and Delivery Order

1. Confirm Profile command evidence and create approved task checkpoint.
2. Add V001 auth schema and PostgreSQL persistence tests.
3. Implement common error/correlation/typed configuration boundaries.
4. Implement account registration, normalization, consent and OTP adapter.
5. Implement local patient/staff credential login and lockout.
6. Implement session/JWT/cookie/CSRF/network security.
7. Implement Google OIDC adapter and conflict-safe linkage.
8. Implement forgot/reset/change password and all-session revocation.
9. Add controller/security/persistence/external-failure regression coverage.
10. Run approved Maven commands, trace requirements, perform post-code review and sync contracts.

Dependency additions (JWT/OIDC client, Testcontainers or email client) require compatibility, vulnerability, license and Spring Boot BOM review before modifying `pom.xml`.

## Consistency Map

| Requirement | Plan component | Expected task | Exact verification | Evidence/sync | Post-code review |
| :--- | :--- | :--- | :--- | :--- | :--- |
| REQ-AUTH-001..020 | Registration/OTP services, entities, migration | Schema + registration/OTP | `.\mvnw.cmd test` | Unit/persistence/adapter tests; `/sdd-trace` | Required |
| REQ-AUTH-021..036 | Login/Google services, security principal | Credential/OIDC tasks | `.\mvnw.cmd test` | Controller/security/linkage tests; `/sdd-sync` if claims change | Required |
| REQ-AUTH-037..052 | Session/JWT/CSRF/audit/rate ports | Security/session tasks | `.\mvnw.cmd clean verify` | Rotation/reuse/concurrency/log-safety evidence | Required |
| REQ-AUTH-053..066 | Recovery service/reset challenge/email adapter | Recovery tasks | `.\mvnw.cmd test` | Token/privacy/session tests; `/sdd-trace` | Required |
| NFR-AUTH-001..015 | Config/security/persistence/test harness | Verification and hardening tasks | `.\mvnw.cmd clean verify` | Compatibility/security/dependency evidence | Required |

Every implementation method containing business behavior must trace to `REQ-AUTH-XXX` as required by Constitution. Exact commands are the approved Profile entries above.

## Risks and Mitigations

1. **Supabase/local pool or schema drift:** use PostgreSQL-compatible migration tests, `ddl-auto=validate`, Flyway-only changes and environment-bound credentials.
2. **Refresh/reset race:** use row locking/unique constraints and atomic transactions; add concurrent rotation, reset replay and session-revocation tests.
3. **Account enumeration:** generic login/forgot responses, keyed HMAC audit identifiers and contract tests comparing unknown/ineligible/eligible responses.
4. **External Google/Gmail failure:** ports with bounded timeout/retry, fake adapters, no activation/linkage before required state commits and safe pending/retry outcome.
5. **Staff network spoofing:** trusted-proxy allowlist, direct-peer validation and fail-closed configuration tests.
6. **Sensitive logging:** structured redaction filter, captured-log tests and no raw token/OTP/password fixtures.
7. **Dependency vulnerability or Spring incompatibility:** review BOM compatibility, vulnerability/license status and deprecated API policy before dependency approval.
8. **Migration rollback/data loss:** additive V001 only, backup/rollback plan and Human migration checkpoint before execution.

## Questions for Human reviewer

### Q1: OIDC/JWT dependency selection

- **Disposition:** RESOLVED — APPROVED by Long on 2026-09-29.
- **Decision:** Spring Security OAuth2 Client + Resource Server; Nimbus versions managed by Spring Boot BOM.

### Q2: Rate-limit persistence

- **Disposition:** RESOLVED — APPROVED by Long on 2026-09-29.
- **Decision:** PostgreSQL-backed rate-limit buckets; Redis deferred.

### Q3: Testcontainers dependency

- **Disposition:** RESOLVED — APPROVED by Long on 2026-09-29.
- **Decision:** Testcontainers PostgreSQL cho integration tests; local PostgreSQL được phép cho smoke/demo.

### Q4: Password reset email transport

- **Disposition:** RESOLVED — APPROVED by Long on 2026-09-29.
- **Decision:** Connect timeout 5s, read timeout 10s, tối đa 2 attempts; chỉ retry transient SMTP/network failures.

## Plan DoD

- [x] Depth, profile binding, locked scope và verification evidence được ghi.
- [x] REQ-AUTH-001..066 và NFR được map theo range/component/data flow.
- [x] Components, ownership, paths và dependency direction được nêu.
- [x] State-change categories, checkpoint và shared-contract impact được ghi.
- [x] Consistency Map có exact approved commands, trace/test/sync và post-code review.
- [x] Có risks/mitigations và Questions for Human reviewer.
- [ ] Human Final Review cho bản Plan sau Q1–Q4 update chưa `APPROVED`.

## Change Impact Record

- **Changed intent, requirement, architecture, or task assumption:** Chốt OIDC/JWT dependency, PostgreSQL rate-limit buckets, Testcontainers/local PostgreSQL test strategy và Gmail timeout/retry policy.
- **Feature Lock boundary affected:** Không đổi locked business scope; technical binding và verification assumptions được resolve.
- **Material state-change classification:** dependency; persistence schema; runtime configuration; external side effect.
- **Downstream artifacts invalidated:** Plan v1.0.0 review và TASKS v1.0.0 review cần refresh; T001 technical-decision gate được resolve.
- **Required trace, test, and sync actions:** Cập nhật T001/T009/T010 và dependency review; `/sdd-trace` cho affected requirements; `/sdd-sync` nếu claims/schema/contract thay đổi.
- **Review follow-up:** Human review lại Plan và TASKS trước `/add-execute`.

## AI Agent Recommendation

- **Status:** PENDING HUMAN REVIEW
- **Scope:** `.sdd/features/feat-001-auth-login/PLAN.md` v1.0.0
- **Recommendation:** Duyệt Plan theo modular-monolith/auth layered boundary, PostgreSQL/Flyway persistence, ports cho Google/Gmail, Spring Security session boundary và exact Maven commands đã được Profile approve.
- **Evidence:** Spec v1.1.0 `APPROVED & LOCKED`; Context v0.6.0 `APPROVED`; Architecture Profile v1.3.0 `APPROVED`; local PostgreSQL `clean verify` evidence 1/0/0/0 and `BUILD SUCCESS` ngày 2026-09-29.
- **Risks and assumptions:** Q1–Q4 technical decisions are approved; implementation still needs dependency compatibility evidence, Docker availability check and PostgreSQL rate-limit contention tests.
- **Alternatives considered:** Tách auth thành microservice bị loại vì modular-monolith binding; dùng H2 mặc định bị loại vì PostgreSQL semantics; đưa reset/Google-only password ngoài scope bị loại vì Spec lock.
- **Required human decision:** Duyệt updated Plan v1.0.0; sau approval review updated TASKS trước `/add-execute`.

## Human Final Review

- **Status:** APPROVED
- **Decision:** APPROVED FEAT-001 implementation plan v1.0.0 with Q1-Q4 technical decisions
- **Reviewer:** Long
- **Reviewed at:** 2026-09-29T22:48:40.6571753+07:00
- **Follow-up:** Review FEAT-001 TASKS v1.0.0
