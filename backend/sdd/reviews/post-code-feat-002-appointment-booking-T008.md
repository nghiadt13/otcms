# Post-code Review — FEAT-002 T008

- Date: 2026-10-13 (Asia/Bangkok).
- Scope: T008 resumed request/confirm/current authorization slice; independent source review under `ak:code-review`.
- Sources: approved CONTEXT D03, SPEC v0.7.0 §5.1, PLAN v0.3.0 §3.1, T008 Human Checkpoint, and `preflight-feat-002-appointment-booking-T008-resume.md`.
- Historical A2 review result: PARTIAL under the former dependent-booking scope. The review below records the A3 reconciliation to approved self-only D03.

## Scope Revision Notice — 2026-10-13

The user clarified that appointments remain on B's account when A helps B operate it; A's separate account gets no appointment authority over B. Owner Long approved CONTEXT v0.16.0 / SPEC v0.8.0 / PLAN v0.4.0 on 2026-10-14. This A2 source review covers the former dependent-authorization slice only; the A3 review below covers its reconciliation.

## Spec compliance and source findings

| Requirement | Reviewed behavior | Result |
|---|---|---|
| Backend Patient actor and record ownership | Current active Patient account/profile required; confirm restricted to the original confirming account still linked to the dependent profile; requester also rechecked | PASS for delivered slice |
| Current self/dependent authority | Explicit account/target UUIDs; null input denied; self linkage checked; dependent requires current CONFIRMED relation and matching current target account link | PASS for delivered slice |
| No implicit representative authority | No-account dependent denied; guardianConsent/name/phone/staff selection do not grant authority | PASS for delivered slice |
| Shared consistency protocol | Request/confirm use the shared guard before identity/relationship reads and capture injected Clock after guard; same transaction writes relationship | PASS by source review; real PostgreSQL ordering not yet verified |
| Ownership and dependency direction | Pure Java relationship domain; use case controllers; Patient identity output port bridged to existing public Auth account/profile persistence contracts; neutral authorization-denied exception | PASS by source review |
| Historical evidence | Additive V012 UUID/FKs/state/chronology checks, unique live requester-target pair, retained revoked rows; trigger prevents identity rewrite, invalid transitions and history deletion | PASS by source review; migration/trigger runtime unverified |
| HTTP privacy | DTO omits account identifiers/demographics; scoped NOT_FOUND; actor denial ACCESS_DENIED; controller-scoped generic malformed JSON/UUID/validation errors with correlation ID | PASS by source review |
| Revocation commit ordering and authorized actors | Public use case declaration/schema prepare lifecycle; executable revocation withheld pending Human actor decision | MISSING — completion gate |

Review corrections incorporated: explicit-target denial; neutral failure contract; input/output handler naming and identity adapter boundary; correct correlation attribute; scoped transport validation advice; ACCESS_DENIED output. An initial concern about Auth reading profile persistence was withdrawn after checking the existing T007 public directory and approved `auth-persistence` ownership. No unresolved concrete defect was found in the delivered request/confirm/authorization slice.

## Verification

- Final focused command from `backend`: `.\mvnw.cmd '-Dtest=PatientAuthorizationT008Test,CareRelationshipT008Test,CareRelationshipT008WebTest,PatientProfileServiceTest,IntegrationContractArchitectureTest' test`. Exit 0; 17 tests, 0 failures, 0 errors, 0 skipped (T008 lifecycle 4, authorization 5, HTTP 4, existing profile regression 3, architecture 1). Reviewer inspected `backend/target/t008-final-test.log` and Surefire reports; BUILD SUCCESS recorded at 2026-10-13 19:57:51 +07:00. Test source compilation targets Java 21.
- Four PostgreSQL tests are prepared and compiled: clean V001–V012, V011–V012 upgrade/identity retention/trigger protection, request-confirm-current authorization, and no-account/staff denial. They were NOT EXECUTED, so no PostgreSQL pass or skip total is claimed. Full suite and `clean verify` were not run in this continuation.
- Authenticated local PostgreSQL execution is unavailable in this session. V012 clean/upgrade application, constraints/triggers, actual transaction serialization and revocation-vs-command ordering remain unverified.
- Downstream appointment command integration is not part of T008. Those writers must acquire the same guard before current authorization in their transaction; the authorization port does not acquire a competing private guard.
- Request/confirm API does not add notifications, relationship discovery/list UI, legal guardian workflows, delegated proof or Receptionist consent. Human checkpoint remains the scope boundary.

## AI Agent Recommendation

- Status: PARTIAL / PENDING HUMAN REVIEW.
- Preserve T008 unchecked. Resolve authorized revocation actor policy, implement and verify revocation under the shared guard, execute authenticated local PostgreSQL tests, then request implementation/trace review.

## Human Final Review

- Status: PENDING HUMAN REVIEW.
- Decision: Not received for this implementation or trace. Prior checkpoint approval is not completion approval.

## A3 Review — revised D03 self-only authorization — 2026-10-14

- Reviewed changes: appointment authorization now returns SELF only when the authenticated active PATIENT account is currently linked to the target profile; all other patient profile IDs receive the same scoped denial. The relationship request/confirm MVC controller, Spring service handlers and persistence repository are no longer registered as runtime components. `PatientAuthorizationPort.AuthorizationScope` exposes only SELF. V012 remains unchanged and unused by booking authorization.
- Verification: focused Maven command recorded in the T008 trace; 13 tests passed with zero failures/errors/skips. The user-run local `scripts/test-local.ps1` then passed 117 tests, zero failures/errors/skips, BUILD SUCCESS (2026-10-14 00:10:39 +07). T008 PostgreSQL integration class passed 4/4, including clean/upgrade migrations and self-only authorization checks. All 37 test sources compiled.
- Finding: no concrete defect found in the revised source slice reviewed. T008 implementation and verification are complete for approved scope; final Human implementation/trace closure review remains pending.
- Human review: pending implementation/trace completion review; the user's Context/Spec/Plan approval does not itself certify task completion.
