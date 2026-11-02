# T008 Resume — Shadow Plan and Action Record

- Date: 2026-10-13 (Asia/Bangkok).
- User instruction: `Tiếp tục T008 cho tôi`.
- Sources: approved FEAT-002 PLAN v0.3.0, SPEC v0.7.0, CONTEXT D03, T008 Human Checkpoint and `patient-dependent-authorization` v1.1.0.
- Prerequisites: T006/T007 and T014 completion are recorded in TASKS.md. T014 now provides the shared PostgreSQL transaction guard; the previous guard sequencing blocker is resolved.
- Governance: `team` / `direct` from shared_context.md. Delegated implementation/testing/review are cooperative tools in this session; host-level task-grant enforcement is not claimed.

## Shadow Plan

1. Add Patient-owned relationship persistence using the next additive migration V012; preserve V001–V011 and historical profile/appointment data.
2. Implement authenticated Patient request/confirm lifecycle, current self/dependent authorization and scoped denial through public input/output ports.
3. Integrate all relationship mutations with T014 guard before owner reads/writes and capture the command instant after acquiring the guard.
4. Implement revocation after the missing authorized-actor decision is resolved. Confirmation remains restricted to the account linked to the dependent profile. Unlinked profiles never receive dependent authority.
5. Verify role/ownership, lifecycle, migration, current-state authorization and guard/revocation commit ordering; independent code review; update task and trace evidence without marking Human approval.

## Action Record

- Execution reference: E-feat-002-appointment-booking-T008-A2.
- Cooperative implementation grant: G-feat-002-T008-A2, bound to the already approved T008 checkpoint and current user continuation; no authorization for unresolved revocation policy or expanded representative workflows.
- File boundary: Patient domain/application/adapters; additive V012 migration; Patient tests and migration-head regression assertions; T008 review/trace/TASKS/shared-context evidence and relevant architecture documentation.
- Exclusions: no legal-guardian, delegated, OTP/document-proof, receptionist-created or no-account dependent flow; no appointment workflow implementation; no dependency changes or production/Supabase access.
- Verification: backend `.\mvnw.cmd test`; focused Maven test selections may isolate database-independent tests. Full local PostgreSQL verification uses `scripts/test-local.ps1` against `127.0.0.1:5432/otcms_test_db`.
- Environment limitation: test subprocess has no OTCMS_TEST database credentials. PostgreSQL test execution cannot be claimed without authenticated local test evidence.
- Pending material decision: checkpoint does not specify who can revoke; a user question is pending. Independent request/confirm/persistence work can proceed, but dependent revocation policy must not be inferred.
- Historical A2 verification: `.\mvnw.cmd '-Dtest=PatientAuthorizationT008Test,CareRelationshipT008Test,CareRelationshipT008WebTest,PatientProfileServiceTest,IntegrationContractArchitectureTest' test` exited 0; 17 tests, zero failures/errors/skips. It verified the former dependent-authorization behavior, not revised D03.
- Historical A2 disposition: superseded by Owner Long's approval of revised D03 and subsequent A3 reconciliation.

## A3 scope reconciliation — 2026-10-14

- User approved CONTEXT v0.16.0, SPEC v0.8.0 and PLAN v0.4.0. T008 now authorizes only a PATIENT account's current linked profile; dependent appointment authorization and HTTP endpoints are disabled.
- A3 verification: `.\mvnw.cmd '-Dtest=PatientAuthorizationT008Test,CareRelationshipT008Test,CareRelationshipT008WebTest,PatientProfileServiceTest,IntegrationContractArchitectureTest' test` exited 0; 13 tests, zero failures/errors/skips; all 37 test sources compiled on Java 21.
- User-run `scripts/test-local.ps1` passed: 117 tests, zero failures/errors/skips, BUILD SUCCESS (2026-10-14 00:10:39 +07); T008 PostgreSQL integration tests passed 4/4. T008 remains unchecked pending final Human implementation/trace closure review. No commit, deployment or production database access.
