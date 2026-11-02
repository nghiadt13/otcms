# Requirement Traceability — FEAT-002 T008

- Date: 2026-10-14 (Asia/Bangkok).
- Sources: approved CONTEXT v0.16.0 D03; approved SPEC v0.8.0 REQ-BOOK-005, REQ-BOOK-019–022 and §5.1; approved PLAN v0.4.0 §3.1/Phase 1; frozen `patient-appointment-self-authorization` v1.0.0.
- Delivery: Implementation and verification complete for T008's approved self-only appointment authorization slice; implementation/trace closure approved by Owner Long on 2026-10-14.

| Requirement | Source implementation/evidence | Status and limit |
|---|---|---|
| REQ-BOOK-005: linked self-profile authorization | AuthorizePatientProfileHandler, PatientAuthorizationAdapter and neutral PatientAuthorizationPort | Implemented; current Patient account/link required, only SELF scope; other profiles denied |
| REQ-BOOK-019–022: current authority and scoped denial | Current Patient account/profile checks; neutral PatientAuthorizationDeniedException | Implemented prerequisite; downstream appointment query/change must consume port |
| Revised D03 actor attribution | Authenticated account resolves to its own current profile; helping another person use that person's account is outside authorization inference | Implemented at backend identity boundary; Auth policy owns the supported assisted sign-in/session method |
| Dependent relationship endpoint exclusion | CareRelationshipController and lifecycle service/repository adapters are not registered as Spring components | Focused MVC test verifies former request/confirm paths return 404 |
| V012 migration history | V012 remains additive, unchanged and unused by appointment authorization | PostgreSQL clean install and V011 upgrade test passed; no records deleted |
| No-account assisted profile prerequisite | Existing T007 profile contract preserved; no-account target cannot receive dependent authority | Existing Receptionist-assisted path remains separate; appointment booking evidence belongs downstream |
| AC01/AC02/AC17 prerequisite tests | Focused Patient authorization/route tests, existing profile/architecture regression and PostgreSQL migration/security tests | Full user-run local suite passed 117/117; T008 PostgreSQL tests passed 4/4; no full appointment acceptance claim |

## Verification Evidence

Focused command from `backend`: `.\mvnw.cmd '-Dtest=PatientAuthorizationT008Test,CareRelationshipT008Test,CareRelationshipT008WebTest,PatientProfileServiceTest,IntegrationContractArchitectureTest' test`. Exit 0, BUILD SUCCESS, 13 tests with 0 failures/errors/skips. All 37 test sources compiled with Java 21.

User-run `scripts/test-local.ps1` on 2026-10-14 reported BUILD SUCCESS: 117 tests, zero failures/errors/skips, total 01:03 min (finished 00:10:39 +07:00). The T008 Surefire report was inspected locally: `CareRelationshipT008PostgresIntegrationTest` ran 4/4, zero failures/errors/skips. It covers clean V001–V012, V011–V012 upgrade/profile identity preservation, cross-profile denial even with a CONFIRMED legacy relationship row, disabled dependent endpoints and no-account profile denial. No production database access is claimed.

## AI Agent Recommendation

- Status: IMPLEMENTATION AND VERIFICATION COMPLETE / HUMAN APPROVED.
- Scope excludes dependent appointment relationships and family clinical-record/results sharing.

## Human Review

- Status: APPROVED.
- Decision: `Approve bước đóng implementation/trace rồi approve đóng T008` — Owner Long, 2026-10-14.

## Scope Revision Notice — 2026-10-13 / Approval and verification — 2026-10-14

The user clarified that appointments remain on B's account when A helps B operate it; A's separate account gets no appointment authority over B. Owner Long approved CONTEXT v0.16.0, SPEC v0.8.0 and PLAN v0.4.0 on 2026-10-14. T008 A3 implements the self-only port, disables dependent endpoints/beans, and leaves V012 inert. The user-run local suite passed 117/117, including all four T008 PostgreSQL integration tests. Earlier A2 tests/review concern the former scope. Family access to examination data/results remains separate scope.
