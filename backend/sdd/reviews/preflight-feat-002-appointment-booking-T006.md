# Preflight — FEAT-002 T006

- Date: 2026-10-13 (Asia/Bangkok)
- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T006`
- Result: BLOCKED before grant issuance or implementation.
- Governance: canonical `Project Ownership: team`; canonical `Agent Execution: orchestrated`, from `sdd/shared_context.md` v2.0.0. No invocation overrides.
- Runtime: this session exposes Codex collaboration tools, but no Claude Code `Agent` tool. The repository add-execute skill explicitly requires observed Claude Code `Agent` availability for this route and prohibits fallback to direct. Runtime identity/enforcement: UNVERIFIED; no host execution claim observed. No worker launched.

## Dependency and source evidence

- TASKS v0.2.0 Human Final Review is APPROVED by Owner Long; T004 and Phase 0 T001–T005 are marked complete with recorded exit evidence. T006 remains unchecked.
- CONTEXT v0.15.0 defines exactly one account type among PATIENT, RECEPTIONIST, DOCTOR, OWNER and ADMIN; retiring STAFF requires reviewed FEAT-001 compatibility migration.
- PLAN v0.3.0 Phase 1 requires authoritative per-account STAFF inventory/mapping, then admit/backfill, then removal only when zero unresolved rows remain.
- Existing `AccountType.java` contains PATIENT and STAFF; immutable `V001__auth_schema.sql` restricts `account_type` to those two values.
- STAFF consumers include `StaffLoginController`, `PasswordRecoveryController`, `SessionSecurityController`, `JwtAuthenticationFilter`, `DemoAccountSeeder` and auth tests. These are compatibility touchpoints, not authorization to edit them.
- No reviewed per-account STAFF mapping or T006-specific security/schema checkpoint was found in the reviewed FEAT-002 artifacts. Static source inventory cannot establish the number or intended roles of database accounts; no account data was queried.
- Architecture Profile v1.3.0 approves `.\mvnw.cmd test` and `.\mvnw.cmd clean verify` from backend. Neither command ran during preflight.
- Existing worktree modifications were observed and preserved.

## Proposed bounded next step

1. Resolve runtime through an environment exposing the required Claude Code `Agent`, or an explicitly reviewed governance change permitting the intended Codex execution mechanism. Do not silently change the persisted route or assume tool equivalence.
2. Persist a T006 checkpoint identifying exact implementation/test/migration paths, shared auth contract ownership and compatibility semantics, permitted inventory target/query, and authoritative mapping for each legacy STAFF account. If the approved target has no STAFF rows, record actual zero-row evidence rather than assuming it.
3. Preserve V001 bytes; allocate new migration versions only after rechecking inventory. Review how legacy JWT/session values and staff login/recovery/refresh network enforcement behave during transition; no bulk inferred mapping.
4. Resume the named task only after preconditions have been revalidated. Implementation, verification, independent post-code review, trace and sync remain outstanding.

## AI Agent Recommendation

- Status: PENDING HUMAN REVIEW
- Scope: T006 runtime and security/schema preflight.
- Recommendation: Resolve runtime eligibility and approve the bounded T006 checkpoint before execution.
- Evidence: shared_context.md; add-execute/SKILL.md; _shared/ai-review-protocol.md; approved FEAT-002 CONTEXT/SPEC/PLAN/TASKS; static auth source inventory.
- Risks and assumptions: Mapping a generic STAFF account without authority could grant unintended clinical or administrative access; stale JWT/session and portal checks could break compatibility or bypass staff network policy.
- Alternatives considered: Running Codex workers as if they were Claude Code Agent, falling back to direct, or bulk assigning STAFF roles are not authorized by the current workflow.
- Required human decision: Runtime disposition and persisted T006 checkpoint/mapping/file boundary.

## Human Final Review

- Status: PENDING
- Decision:
- Reviewer:
- Reviewed at:
- Follow-up: Resolve the documented blockers, then `/add-execute --feature=feat-002-appointment-booking --task=T006 --resume`.

## Resume addendum — 2026-10-13

- The user stated: “Tôi chưa có tài khoản nào trong database cả” (there are currently no accounts in the database). Treat this as Human-reported evidence only; no database query was run, and the exact target environment was not specified.
- Canonical `Agent Execution` now resolves to `direct` in `sdd/shared_context.md`, so the earlier orchestrated-runtime blocker is cleared for a new resume attempt. The A1 record above remains historical.
- Existing-account role mapping can be empty if the user statement applies to the database targeted by the migration. This does not resolve the separate demo seeder behavior: `DemoAccountSeeder` currently creates a `STAFF` account, and `application-demo.yml` provides staff demo credentials. T006 must use a concrete type for that account or explicitly stop creating it; no choice is inferred.
- Proposed implementation boundary for review: `AccountType.java`; additive Flyway migration after rechecking version/history; auth role handling at `StaffLoginController`, `PasswordRecoveryController`, `SessionSecurityController`, JWT validation/filter; `DemoAccountSeeder` and demo configuration; focused auth contract/integration tests. Preserve V001 and the staff network policy. Exact migration and compatibility behavior require review before edits.
- Result: T006 remains BLOCKED pending confirmation whether the zero-account statement applies to the target database and a decision for the demo staff account role/creation behavior. No source, migration, configuration, or database change was made.

## Human checkpoint — T006 scope decision

- Decision evidence: user confirms there are currently no accounts in the database and selects `RECEPTIONIST` for the demo staff account.
- Scope approved for this execution: replace generic `STAFF` with `PATIENT`, `RECEPTIONIST`, `DOCTOR`, `OWNER`, and `ADMIN`; preserve existing V001 and add a Flyway migration updating the account type constraint; use `RECEPTIONIST` for the demo staff seed; make staff login, password recovery, refresh network policy and JWT/principal handling recognize all four staff account types; update focused auth tests.
- File boundary: `AccountType.java`, auth login/recovery/session/JWT touchpoints and their focused tests, `DemoAccountSeeder.java`, and a new additive migration under `src/main/resources/db/migration/`. `V001__auth_schema.sql` remains immutable. No frontend, appointment module, dependency, or production database edits.
- Data mapping: user reports zero existing accounts; no backfill rows expected. This is user-reported evidence and is not an independent query result. Migration must fail safely if legacy `STAFF` rows exist rather than assign roles automatically.
- Verification: approved `.\mvnw.cmd test` from `backend/`; no production/Supabase database command is authorized.
- Checkpoint: APPROVED for this bounded execution based on the user's direct instruction and persisted choices in this conversation. Reviewer identity is not independently verified by the runtime.
