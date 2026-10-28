 # Implementation Tasks — FEAT-002 Appointment Booking

## 1. Metadata và execution boundary

- Feature ID: FEAT-002
- Version: 0.6.3
- Status: APPROVED — alignment with SPEC v0.9.2 and PLAN v0.5.3
- Sources: CONTEXT.md v0.16.0 — approved; SPEC.md v0.9.2 — APPROVED & LOCKED and PLAN.md v0.5.3 — APPROVED by Owner Long on 2026-10-27.
- Artifact root: backend/sdd/features/feat-002-appointment-booking; application root: otcms/backend.
- Toolchain mục tiêu: Java 21, Maven Wrapper, Spring Boot modular monolith/layered architecture theo Constitution ARCH-01 và PostgreSQL/Flyway.
- Bản này thay toàn bộ task contracts v0.1.0. Task IDs được định nghĩa lại; không chuyển checkbox/evidence cũ sang bản này.
- Owner trong bảng là module chịu trách nhiệm giao contract/code/evidence, không phải quyền actor của API. CRUD giữ tại owning module; appointment dùng public ports/DTOs.
- TASKS v0.2.0/v0.3.0 approvals are historical for their stated scopes; v0.6.0 proposed Visit WALK_IN API delivery. This v0.6.3 aligns task sources to Spec v0.9.2 and Plan v0.5.3; v0.6.2 synchronized approved T044–T053 task sources to Spec v0.9.1 and Plan v0.5.2; v0.6.1 clarifies cross-phase execution order and gates downstream care/follow-up/capacity verification on T041–T043. The v0.6.0 review recommendation remains historical; Owner Long approved v0.6.1 on 2026-10-18.
- Không bật booking production trước khi T031–T033, T043 và verification T034–T040 pass. Không dùng production stub để vượt prerequisite gate.

## 2. Quy tắc thực thi và evidence

- Phase chỉ nhóm task theo workstream, không quy định thứ tự thực thi. Thứ tự thực hiện được xác định bởi cột `Depends on`; dependency có thể đi giữa các Phase và task ở Phase số cao hơn có thể phải hoàn tất trước task ở Phase số thấp hơn.
- Task chỉ bắt đầu khi TASKS được Human review và các dependencies của task đạt exit evidence. T001–T005 là baseline/execution checkpoints, không yêu cầu code có sẵn để review tài liệu này.
- Mỗi task lưu Shadow Plan, scope/file boundary, dependency evidence, exact command nếu có, checkpoint, result, residual blockers và sync-back decision trong Action Record.
- Checkbox chỉ được đánh dấu sau khi deliverable và verification evidence đạt. Interface hoặc green build với required DB tests bị skip không chứng minh production readiness.
- Các task schema/security/contract tuân thủ owning review checkpoints của PLAN; thay đổi business/API/architecture ngoài approved baseline cần sync-back và review trước khi tiếp tục.
- Queue, laboratory/external results và incident CRUD không tự mở rộng vào scope. Visit owner chốt duration/overrun để bảo vệ upcoming bookings trong T031.

## 3. Phase 0 — Baseline và execution checkpoints

| Task | Owner | Depends on | Deliverable / exit evidence |
|---|---|---|---|
| [x] T001 | Integration | Human review TASKS | Ghi version/hash/approval của ba inputs, source tree và file boundary; xác nhận baseline Report3/UC mapping, không dùng task contracts cũ. Evidence trong Phase 0 execution record. |
| [x] T002 | Persistence | T001 | Inventory Flyway file/runtime head, giữ checksum V001; cấp migration version cho schema task kế tiếp; chứng minh target PostgreSQL cho phép btree_gist/exclusion và migration permissions. Local test DB evidence ghi trong Phase 0 execution record: PostgreSQL 18.1, extension available/installed, role có CREATE, Flyway V001 success và exclusion DDL canary thành công. Version tiếp theo được xác nhận lại lúc tạo migration để tránh collision. Supabase production không được truy cập trong checkpoint này. |
| [x] T003 | Build/docs | T001 | Xác minh JDK21/wrapper/test profile và commands; reconcile hướng dẫn Java17 cũ theo quyết định Java21; ghi environment và approved command binding, không tự nâng dependency. Evidence trong Phase 0 execution record; không chạy build/test. |
| [x] T004 | Integration + owning modules | T001 | Contract register Auth/Patient/Service/Staff/Visit/Reception: owner, public DTO/port, authority, consistency/error semantics, schema order, readiness evidence và review checkpoints; neutral composition boundary không có import cycle. Register và contract gaps trong Phase 0 execution record. |
| [x] T005 | Integration/persistence | T002–T004 | Review guard protocol/shared datasource/transaction manager, READ_COMMITTED, lock order, bounded lock/statement timeouts và error/retry binding; không silently retry command sang state mới. Owner Long chốt transaction-local `lock_timeout=2s`, `statement_timeout=5s`; SQLSTATE `55P03`/`57014`/`40P01` -> rollback + `503 DEPENDENCY_UNAVAILABLE`, không tự retry; `23P01` -> rollback + `409 SLOT_UNAVAILABLE`. Hikari connection timeout 10s là giới hạn chờ lấy connection riêng. T035 còn phải kiểm thử timeout/deadlock trên PostgreSQL thật. |

Phase exit: T001–T005 pass và checkpoint evidence persisted trước các schema/command tasks liên quan.

### Phase 0 execution record — 2026-10-13 (Asia/Bangkok)

- Baseline `report3-2026-10-12`, manifest v1.0.0. `Report3_SRS.docx` SHA-256 `de3feb8cd8b2abf2917cb0aaa90e88821f60536f630632030af02e1bb5e8be3d`; extracted text SHA-256 `e3936905f7ad63bf7b09ba74c87f51a9318d830df34d08ecd55ff59abd0bd873`. `BASELINE.md` maps Report 3 UC21–UC25 and UC30 to FEAT-002; D01–D12 remain in CONTEXT.
- Approved inputs: CONTEXT v0.15.0 APPROVED, SHA-256 `784c495117729d67abe295959b308e8fda27e4a47b52d5ba77a44496b91c876c`; SPEC v0.7.0 APPROVED, SHA-256 `22527ad13e4441d7a5c3dd0732999cb2e836ad7209ab860558fc50e5998b9b6e`; PLAN v0.3.0 APPROVED, SHA-256 `fce4afa4e6ccf5878fa18b42e901b8c55d08b6c0ad7b2b19d886dd597c0885d9`; TASKS v0.2.0 APPROVED by Owner Long.
- Repository snapshot at git revision `50db869739873af99417c76df56f95f0b0d38d77`; artifact root `backend/sdd/features/feat-002-appointment-booking`, implementation root `backend`, runtime migration inventory `backend/src/main/resources/db/migration`. FEAT-002 artifacts/baseline are untracked in this worktree snapshot; file hashes above identify the reviewed bytes. Scope for this phase: Phase 0 evidence plus Java version guidance correction.
- Database environment decision from Owner Long: Supabase is the real/production DB; local PostgreSQL is the test DB. Supabase was not connected to or changed. At the initial inspection, local service `postgresql-x64-18` was Running and TCP `127.0.0.1:5432` was reachable; a read-only capability query against `otcms_db` could not authenticate (`fe_sendauth: no password supplied`). That initial attempt did not inspect credentials or change data. The later authenticated check for the dedicated `otcms_test_db` is recorded in the test execution addendum below. Do not run feature migrations against Supabase as part of this test checkpoint.
- Flyway static inventory: only `V001__auth_schema.sql`, SHA-256 `9a616dcb1d8ffbf007d6af3e8fd1fa3f1eb97c60294e1d5d5fe7ba37802d35a6`; it creates auth/account/patient-profile tables. Runtime Flyway history and next migration allocation remain unverified. Test isolation needs correction before any suite is run: `T006A8EvidenceTest`, `T007GoogleOnboardingPostgresIntegrationTest` and `T008PasswordRecoveryPostgresIntegrationTest` use Testcontainers `postgres:16-alpine` with `disabledWithoutDocker=true` (skip without Docker); `T004PostgresIntegrationTest`, `T005RealLoginIntegrationTest` and `T006PostgresIntegrationTest` use the default Spring datasource without test override. Since `application.yml` imports optional `.env` and configures Supabase datasource variables, those tests could target the real DB if the environment resolves to Supabase. They must be made to use local test PostgreSQL and fail closed when that test datasource is absent. No `.env`/credential was read and no test or migration was attempted.
- Toolchain evidence: `java -version` → OpenJDK 21.0.7; `.\mvnw.cmd -version` → Maven 3.9.16, Java 21.0.7, Windows 11 amd64. `pom.xml` declares `java.version=21`; Maven includes PostgreSQL, Flyway PostgreSQL and Testcontainers PostgreSQL dependencies. Architecture Profile v1.3.0 records `.\mvnw.cmd test` and `.\mvnw.cmd clean verify` as approved commands. Neither test nor build ran in this checkpoint. Corrected `otcms/AGENTS.md` from Java 17 to Java 21 to match approved profile and pom.
- Existing implementation inventory: `auth` module and V001 exist; current `patient_profiles` schema is account-linked (`account_id` PK), so it does not yet satisfy the approved independent patient profile model. No appointment/service/staff/visit modules or their public contracts were found under backend source. This is planned prerequisite work, not evidence to create appointment-side substitutes.
- Test datasource isolation implemented for the requested local-only workflow: the normal application `application.yml` and `.env` behavior are preserved; test profile fixes datasource URL to `127.0.0.1:5432/otcms_test_db`, uses separate `OTCMS_TEST_DB_*` credentials and requires Flyway/JPA validation. Every Spring context test opts into this profile and a pre-context initializer rejects remote/non-test URLs. Three previous Testcontainers suites now use the same local profile; Docker skip behavior and Testcontainers dependencies were removed. `docs/local-testing.md` and `scripts/test-local.ps1` document/setup a secure local prompt, inspect the local PostgreSQL/extension capability, install the extension only in `otcms_test_db` if permitted, and then invoke the approved Maven test command. `.env` may load through base configuration, but the test datasource URL is fixed locally and cannot be overridden by `.env` or a process variable.
- T004 contract register: Auth owns authentication/principal/session; role mapping and compatibility evidence belong to T006. Patient owns independent identity/search/create and current self/dependent authorization (T007–T008; schema before appointment FK). Service/catalog owns service/deactivation and Doctor-service assignment (T010). Staff owns Doctor profile/publication/acceptance/account state and shifts/time-off (T011). Visit/reception/clinical owns arrival/reception/WALK_IN/care evidence and visit links (T012). Appointment owns appointment/audit/replay and consumes public owner contracts (T013 onward). Neutral integration owns shared transaction/guard orchestration (T009, T014). All contracts are NOT IMPLEMENTED; DTO/port names and concrete error mapping are to be finalized by the owning tasks before consumers integrate. SPEC/PLAN define the authority, current-state check, atomicity, fail-closed arrival and lock-order rules; owner-specific schemas precede appointment references.
- T005 review/decision — APPROVED by Owner Long (2026-10-13): PLAN §3–4 defines one PostgreSQL datasource/transaction boundary, READ_COMMITTED fresh reads, guard-first lock order, captured Clock after guard, no network under guard, and no silent command retry. Bind transaction-local `lock_timeout=2s` and `statement_timeout=5s` for guarded booking mutations. SQLSTATE 55P03 (lock timeout), 57014 (statement timeout), and 40P01 (deadlock) roll back the whole command and map to 503 DEPENDENCY_UNAVAILABLE; do not automatically retry. SQLSTATE 23P01 (exclusion conflict) rolls back and maps to 409 SLOT_UNAVAILABLE. Hikari connection timeout 10s remains the separate pool-acquisition limit. Configuration implementation and HTTP/error mapping are owned by implementation tasks; T035 must prove timeout/deadlock handling and rollback on real PostgreSQL.
- Document drift found: approved SPEC v0.7.0 metadata still says PLAN/TASKS are not synced/implementation-ready, now stale after their approvals. Recorded for documentation sync-back review; SPEC was not altered in Phase 0.
- Test execution addendum (2026-10-13 14:51:30 Asia/Bangkok, user-reported): Maven `test` completed with BUILD SUCCESS, 69 tests, 0 failures, 0 errors and 0 skipped in 39.772 s. `OtcmsBeApplicationTests` passed (1); `LocalPostgresTestInitializerTest` passed (5). The authenticated helper capability output reported PostgreSQL 18.1, `btree_gist available=t`, initially `installed=f`, and current role `can CREATE=t`; the helper then successfully installed `btree_gist` in dedicated local `otcms_test_db`. No credentials were recorded.
- T002 DB verification (2026-10-13, user-reported): `flyway_schema_history` on `otcms_test_db` contains rank 1, version `001`, description `auth schema`, success `t`. A transaction-local canary `CREATE TEMP TABLE` with `EXCLUDE USING gist (doctor_id WITH =, slot WITH &&)` succeeded and was rolled back. Together with the helper output, this verifies current local PostgreSQL/extension capability and V001 runtime head; next migration version must be rechecked when the feature migration is created. T002 is complete for the local test DB; Supabase production was not tested and remains subject to its own deployment capability gate.
- Phase 0 status: T001–T005 complete; Phase 0 exit criteria are met. Real database target is Supabase; test target is local PostgreSQL per Owner Long's clarification. The test suite passed after isolation changes, as recorded above. T035 retains runtime concurrency/timeout/deadlock verification as an implementation acceptance gate.

## 4. Phase 1 — Auth/patient và contract foundation

| Task | Owner | Depends on | Deliverable / exit evidence |
|---|---|---|---|
| [x] T006 | Auth/security | T004 | Inventory STAFF và authoritative per-account role mapping được review; admit/backfill rồi loại legacy khi zero unresolved; JWT/principal/session/portal/seeder compatibility, auth regression. Không bulk suy role từ UI. |
| [x] T007 | Patient | T002, T004 | Independent patient_profiles UUID/backfill, optional unique account linkage; search/create public contract, preserve registration/history, clean/upgrade migration evidence; không auto merge theo name/phone. |
| [x] T008 | Patient + auth | T006, T007, T014 | Revised D03: current linked self-profile authorization and scoped denial for every other Patient profile; preserve Receptionist no-account workflow. Reconciled dependent relationship code, V012 and tests after revised CONTEXT/SPEC/PLAN approval; implementation, verification and trace closure approved by Owner Long on 2026-10-14. |
| [x] T009 | Integration | T004, T005 | Neutral coordination/transaction DTOs/contracts and provider/service/visit/reception/capacity calls through owning features' public services or contracts; architecture evidence confirms feature-owned `model` types, service interfaces with implementations under `service.impl`, and no cross-feature JPA/entity/repository imports or dependency cycles. Logical contract foundation approved; owning feature services/contracts bind in T010–T012 and T014. |

Phase exit: identity/auth regression và prerequisite interfaces được review; interfaces chưa thay thế implementations ở phase 2.

### T008 Preflight — 2026-10-13

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T008`.
- Dependencies: T006 and T007 are complete; T007 local database/test/review/trace/sync evidence is recorded above.
- Source/spec check: CONTEXT D03 assigns confirmed-dependent relationship ownership and authorization to Patient; SPEC §5.1 requires input `(accountId, targetProfileId)`, current relationship re-check, scoped denial and a defined consistent commit order against revocation. PLAN §3.1 requires dependent revocation to participate in the same guard-first consistency protocol used by appointment mutations.
- Repository check: T007 supplies independent patient profile identity and Receptionist search/create only. No care/dependent relationship schema, lifecycle, authorization port, or Patient-owner persistence contract exists in source/shared_context.
- Classification: security/privacy and schema/contract state change. Execution grant not issued and no implementation/source migration changes made.
- Status: BLOCKED pending Human checkpoint defining relationship proof/lifecycle, authorized actors, persistence shape and current authorization/revocation consistency protocol. Do not infer relationship from name, phone, guardian fields, or receptionist selection.
- Proposed checkpoint options to resolve: (A) require a separately confirmed care relationship created/confirmed through an explicit relationship workflow before authorization; (B) approve an existing authoritative relationship source and identify its schema/owner/API; or (C) revise T008 to deliver only an authorization port/fail-closed behavior until relationship lifecycle is separately approved. For any option, confirm who may initiate/confirm/revoke, what proof/consent is required, and whether revocation and appointment commands share the PLAN coordination guard/commit ordering.
- Exact verification command after checkpoint: from `backend`, ` .\mvnw.cmd test`; local DB only `127.0.0.1:5432/otcms_test_db`. Do not connect to Supabase/production.

### T008 Human Checkpoint — 2026-10-13

- Decision: APPROVED by Owner Long. A care relationship may be confirmed only by the patient account linked to the dependent profile. `guardianConsent` is registration consent only and grants no representative authority. A dependent profile without an account link cannot receive dependent authorization in this task.
- Revocation consistency: dependent relationship revocation participates in the same coordination transaction guard and commit ordering defined in PLAN §3.1; earlier committed revocation denies a later appointment command, while a command committed first was authorized at that commit order. Appointment/profile history is retained.
- Scope implication: no legal-guardian, delegated, receptionist-created, OTP, document-proof, or no-account dependent flow is added. Do not infer or manufacture a confirmed relationship.
- Architecture dependency: the shared coordination guard is owned by T014 and is not implemented yet. T008 may define the Patient-owned lifecycle/authorization contract and schema, but any operation whose correctness requires guard ordering must remain fail-closed until T014 supplies and wires the shared guard. T008 must not add a private competing guard.
- Grant status: checkpoint approved; T008 implementation grant is not issued until scope is bound to the T014 guard dependency and exact executable behavior is established.
- Shared sync: approved checkpoint semantics were recorded as `patient-dependent-authorization` v1.1.0 in `backend/sdd/shared_context.md` v2.4.0; relationship schema/endpoints are not implemented.
- Execution disposition: BLOCKED before implementation. Do not issue or consume a T008 source-change grant until T014 provides the single shared guard port/adapter and transaction wiring; T008 then consumes that boundary rather than adding a competing lock. This is a sequencing prerequisite, not a request to broaden T008 into T014.

### T008 Resume — E-feat-002-appointment-booking-T008-A2

- User instruction: `Tiếp tục T008 cho tôi` (2026-10-13). Approved PLAN and T008 checkpoint are reused; T014 shared guard is now complete. The earlier guard sequencing blocker is historical and resolved.
- Shadow Plan / Action Record: `sdd/reviews/preflight-feat-002-appointment-booking-T008-resume.md`; cooperative grant `G-feat-002-T008-A2`, bound to the existing checkpoint and requested continuation. Host-level task-grant enforcement is not claimed.
- Implemented slice: additive V012 Patient-owned care relationship history; Patient-owned service interfaces and `service.impl` implementations for request/confirmation; Patient-only HTTP mutations; current self/confirmed-dependent authorization binding through the Patient-owned authorization contract. Identity is obtained through owning public services/contracts; no demographic/guardian-consent inference. All implemented lifecycle mutations acquire T014 guard before owner reads and capture Clock after acquisition.
- Scope limitation: revocation actor policy was not defined by the checkpoint. User clarification was requested, explained with requester A/dependent B, and remains pending. No revocation handler/API is exposed before that decision; do not claim revocation consistency delivered.
- Verification: `.\mvnw.cmd '-Dtest=PatientAuthorizationT008Test,CareRelationshipT008Test,CareRelationshipT008WebTest,PatientProfileServiceTest,IntegrationContractArchitectureTest' test` passed 17 tests, zero failures/errors/skips (13 new lifecycle/authorization/HTTP tests plus 4 existing profile/architecture regressions). All 37 test sources compile on Java 21. Four PostgreSQL integration tests are prepared but not executed because this subprocess has no local test credentials; compilation must not be treated as database or concurrency evidence.
- Status: SUPERSEDED SCOPE / BLOCKED for revised D03; keep T008 unchecked. Implementation/trace Human review is PENDING; T022/T023 may not consume `CONFIRMED_DEPENDENT` authority. The preceding execution evidence remains historical and does not validate the newly requested behavior.

### T008 Scope Revision — 2026-10-13

- User clarification: A may help B operate B's account for appointments; A's separate account must not gain appointment authority over B. Possible access by A to B's examination information and test results is a separate family/clinical-data sharing decision, outside FEAT-002.
- Approved artifacts: CONTEXT v0.16.0, SPEC v0.8.0 and PLAN v0.4.0 were explicitly approved by Owner Long on 2026-10-14. Prior approvals apply to their historical versions only.
- Source impact: the T008 request/confirm API, `CONFIRMED_DEPENDENT` authorization path and V012 relationship schema from A2 implemented the former scope. A3 removes them from appointment authorization and disables their Spring/API registration. V012 remains in migration history but is inert; no applied migration is rewritten or relationship data deleted.
- Verification: self authorization, denial of another Patient profile even when a CONFIRMED relationship row exists, and absence of dependent HTTP endpoints are covered by focused tests. Authenticated local PostgreSQL verification passed 4/4; final implementation/trace closure approved by Owner Long on 2026-10-14.

### T008 Resume — E-feat-002-appointment-booking-T008-A3

- User instruction: approve revised CONTEXT/SPEC/PLAN and fix T008 (2026-10-14).
- Approved boundary: appointment actor remains the authenticated Patient account; Patient authorization returns SELF only for the currently linked profile. A helping B operates through B's account/session as defined by Auth policy; A's account gains no authority over B. Family clinical-record/results access is out of FEAT-002.
- Source changes: `AuthorizePatientProfileHandler` no longer checks relationship state and denies every target other than current linked self; `PatientAuthorizationPort.AuthorizationScope` is SELF only. Care relationship request/confirm controllers, handlers and persistence repository are no longer Spring MVC/service/repository beans. V012 remains immutable and unused; no destructive cleanup migration was added.
- Verification: user-run `scripts/test-local.ps1` passed on 2026-10-14: 117 tests, zero failures/errors/skips, BUILD SUCCESS (01:03 min; finished 00:10:39 +07). `CareRelationshipT008PostgresIntegrationTest` passed 4/4, covering clean V001–V012, V011–V012 upgrade/profile identity retention, other-profile denial despite a confirmed legacy relationship row, and disabled dependent endpoints/no-account behavior. Focused 13-test T008/profile/architecture suite also passed.
- Human decision: `Approve bước đóng implementation/trace rồi approve đóng T008` — Owner Long, 2026-10-14.
- Status: COMPLETE `[x]`. Implementation, verification and requirement trace approved for the revised self-only scope. Does not include family clinical-record/results sharing.

### T009 Execution Record — E-feat-002-appointment-booking-T009-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T009`.
- Dependencies: T004 and T005 completed in Phase 0; local PostgreSQL/guard capability evidence and approved lock order/timeouts recorded above. T006/T007 are complete. T008 checkpoint is approved but implementation waits for T014 guard wiring.
- Governance: `team` / `direct`, resolved from `backend/sdd/shared_context.md` v2.5.0; host execution identity/enforcement UNVERIFIED.
- Classification: additive architecture/interface foundation; no schema or runtime DB mutation.
- Grant: `G-feat-002-T009-A1`, dispatched and consumed before edits; consumer `codex-direct-session-T009-A1` (cooperative reference; no host claim).
- Boundary: new technology-neutral interfaces/DTO records under `integration/contract`, architecture test, architecture documentation and T009 evidence only. No owner repository/entity access, no no-op adapters, no production DB, no new dependency.
- Implemented foundation: seven pure-Java ports for Patient authorization; Staff doctor identity, directory, shift and time-off projection; Service catalog/assignment snapshot; Visit follow-up evidence; Reception arrival and atomic check-in; Appointment capacity decision; and neutral CoordinationGuard.
- Owner/API bindings: not implemented because owning use cases for T010–T012 do not yet exist and T014 owns the guard adapter/transaction wiring. Shared record `feat-002-integration-ports` v1.0.0 is FROZEN as logical foundation; owning-module bindings remain pending; Patient checkpoint/authorization contract stays fail-closed until T014 guard is wired.
- Verification: focused integration architecture test passed 1/1. Full-suite PostgreSQL verification remained pending the authenticated local helper run; runtime owner adapters and the CoordinationGuard were not claimed delivered.
- Human decision received: `APPROVE T009 contract foundation v1.0.0` by Owner Long (2026-10-13); logical contracts are approved, owner adapters bind in T010–T012, and T014 implements CoordinationGuardPort in the shared transaction.
- Disposition: T009 foundation COMPLETED and `[x]`; owner bindings remain assigned to T010–T012, shared guard to T014. This completion does not claim runtime adapter or guard readiness. Full-suite PostgreSQL verification remains pending the authenticated local helper run; focused architecture test passed 1/1.

## 5. Phase 2 — Owning schemas, config và concurrency

| Task | Owner | Depends on | Deliverable / exit evidence |
|---|---|---|---|
| [x] T010 | Service/catalog | T002, T006, T009 | **Foundation exit (v0.3.0):** V004/catalog persistence, public/staff catalog and snapshot/eligibility owner contract; Admin authorization and historical-reference policy; Admin service create/update/activate and assignment foundation. Contract shape must be approved and stable for T013/T017. Service deactivation or assignment removal that could make a protected booking unserviceable remains unavailable or fails closed until T032. T010 closes on this foundation; T032 owns enabling the impact-aware operations. |
| [x] T011 | Staff/account | T002, T006, T009 | **Foundation exit (v0.3.0):** Doctor profile/provisioning, directory/acceptance/shift/time-off read contracts and schema; separate new-booking eligibility from existing-booking serviceability; Admin authorization. Booking-impacting publication/acceptance/account/schedule/time-off mutations remain unavailable or fail closed until their T014/T032 guard and impact checks are wired. T011 closes on this foundation; T032 owns the protected mutation integration. |
| [x] T012 | Visit/reception/clinical | T002, T007, T009 | **Foundation exit (v0.3.0):** visit/arrival/reception/clinical evidence schemas and owner read contracts; WALK_IN semantics, recorded arrival and link/uniqueness constraints. Arrival/check-in writers remain T027 work; care reservation/overrun enforcement remains T031 work. T012 closes on the owner foundation and its verified persistence contract, not those downstream workflows. |
| [x] T013 | Appointment/persistence | T010–T012 | Additive appointment/source_visit_id/FKs/indexes, snapshots, immutable audit và replay schema sau owner schemas; non-cascading history, check-in one appointment/one visit/initial reception; replay unique actorAccountId/appointmentId/command/key, lifetime retention. |
| [x] T014 | Integration + all writers | T005, T009–T013 | Dedicated neutral guard row and shared transaction wiring; current participating writers acquire it before owner reads/writes. Future appointment, arrival, authority-changing identity-link and maintenance writers must acquire the same guard when implemented; UUID lock ordering/Clock capture follow the PLAN in those workflows. No network under guard. |
| [x] T015 | Appointment/persistence | T013, T014 | Doctor + half-open tstzrange exclusion cho CONFIRMED/CHECKED_IN/IN_PROGRESS/COMPLETED; occupied_until=end+buffer snapshot, consistent duration; không now-dependent predicate/cleanup; exclusion rollback -> SLOT_UNAVAILABLE. Real PostgreSQL overlap/adjacency/COMPLETED evidence. |
| [x] T016 | Appointment/config | T003, T009 | Bind chín typed keys theo SPEC: horizon/notice/deadline/grid/zone, early30/late15, rate requests/window; injected Clock; defaults/overrides/zero values hợp lệ và startup rejection cho config sai. |
| [x] T017 | Persistence + owning modules | T006–T016 | **Foundation checkpoint (v0.3.0):** clean DB and upgrade from applied V001 with preserved existing data; checksums/mapping/FK/constraints; approved stable T010–T012 owner contracts and schema readiness. Review transaction/atomicity boundaries and ordered-lock plan across current writers; record admission rules for future guarded writers. Do not require T027/T031/T032 runtime workflows before T017. Runtime atomicity/race evidence remains T033/T035. |

### T010 Execution Record — E-feat-002-appointment-booking-T010-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T010`.
- Dependencies: T002, T006 and T009 complete. V001–V003 remain immutable; user-provided T007 evidence confirms the local DB was at V003. Static migration inventory confirms V004 is the next source version.
- Governance: `team` / `direct`, resolved from `backend/sdd/shared_context.md` v2.6.0; host execution identity/enforcement UNVERIFIED.
- Classification: Service-owned persistence/API and authorization state change.
- Grant: `G-feat-002-T010-A1`, dispatched and consumed before implementation; consumer `codex-direct-session-T010-A1` (cooperative reference; no host claim).
- Implemented boundary: additive `V004__service_catalog_and_doctor_assignments.sql`; `servicecatalog` domain/application/persistence/web adapters; public and staff catalog; Admin create/update/activate/soft-deactivate API; public GET security exception; `ServiceCatalogPort` binding; focused handler/PostgreSQL tests; architecture/shared-contract evidence.
- Safety/limitations: no hard delete; deactivation retains service identity and scheduling values. No assignment write endpoint is exposed until T011 can validate Doctor profiles and T032 protected-removal policy is wired. Assignment table references Doctor profile UUID without FK because Staff schema is not yet present. No sample service data seeded.
- Verification: user-run `scripts/test-local.ps1` against local `127.0.0.1:5432/otcms_test_db` passed: 88 tests, 0 failures/errors/skips (BUILD SUCCESS, 2026-10-13 17:56:40 +07). `ClinicServicePostgresIntegrationTest` passed 4/4. No production/Supabase access.
- Review: Owner Long approved implementation and requirement trace on 2026-10-13; evidence recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T010.md` and `sdd/reviews/trace-feat-002-appointment-booking-T010.md`.
- Contract: `clinic-service-catalog` v1.0.0 remains a draft pending explicit contract-shape approval. Doctor-service assignment validation and T032 protected-removal integration also remain outstanding.
- Status: PARTIAL. Keep T010 unchecked; do not claim Doctor-service assignment CRUD or protected removal complete.

### T011 Execution Record — E-feat-002-appointment-booking-T011-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T011`.
- Dependencies: T002, T006, T009 complete. V004 is the current prior schema; V001–V004 are unchanged.
- Governance: `team` / `direct` per `shared_context.md`; execution stayed in this session.
- Grant: `G-feat-002-T011-A1`, consumed by `codex-direct-session-T011-A1` as a cooperative execution reference; host-level grant enforcement is not claimed.
- Implemented boundary: additive V005 Doctor-profile/shift/operational-time-off schema and V006 Doctor-service assignment FK; Staff-owned `staff` feature with profile domain, account-linked provisioning use case, Admin authorization, public published-directory read boundary, own/Admin shift reads (range bounded to 90 days), `StaffDirectoryPort` query binding and PostgreSQL-backed test coverage.
- Safety/limitations: profile provisioning only links an existing account whose application type is DOCTOR (SRS UC18/BR-22); it does not create credentials or a generic Staff-management workflow. New profiles are internal, not accepting bookings and not public by default. Account status is deliberately not collapsed into new-booking acceptance (D07); public DTO excludes account ID, work contact and eligibility flags. Schedule/profile state writes that affect booking eligibility, shift/time-off maintenance, and protected-booking impact checks are withheld until T014 shared guard and T032 impact integration. Assignment FK migration fails closed if pre-existing assignments reference missing Doctor profiles.
- Verification: final user-run `scripts/test-local.ps1` passed: 93 tests, 0 failures/errors/skips (BUILD SUCCESS, 2026-10-13 18:16:06 +07). `DoctorProfileT011PostgresIntegrationTest` passed 3/3. An earlier test assertion failure was corrected before this passing run. No production/Supabase access.
- Review: Owner Long approved implementation and requirement trace on 2026-10-13; evidence recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T011.md` and `sdd/reviews/trace-feat-002-appointment-booking-T011.md`.
- Contract: Owner Long approved `staff-doctor-directory` v1.0.0 on 2026-10-13; the frozen contract is recorded in `shared_context.md` v3.0.0.
- Historical A1 disposition under the then-current task exit: PARTIAL / GUARD-DEPENDENT MAINTENANCE PENDING. This status is superseded by the T011 A2 foundation closeout under approved TASKS v0.3.0 below; publication/acceptance mutations and shift/time-off maintenance remain deferred and are not claimed complete.

### T012 Execution Record — E-feat-002-appointment-booking-T012-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T012`.
- Dependencies: T002, T007, T009 complete. V006 was the source schema head; V001–V006 remain unchanged.
- Governance: `team` / `direct` per `shared_context.md`; execution stayed in this session.
- Grant: `G-feat-002-T012-A1`, consumed by `codex-direct-session-T012-A1` as a cooperative execution reference; host-level grant enforcement is not claimed.
- Implemented boundary: additive V007 schema for visits, recorded appointment arrivals, initial reception, walk-in request replay, care assignments and minimal clinical examination/follow-up evidence; actual `VisitWorkflowPort` read binding verifies source visit, same-Doctor care assignment, completed exam and indication without returning clinical content.
- Safety/limitations: no appointment is fabricated for `WALK_IN`; originating appointment and prior appointment identifiers are stored separately. T013 supplies the same-patient `priorAppointmentId` FK/validation; acceptance in an operational workflow remains subject to the guarded T014 writer. Reception arrival/check-in writers are not bound before T014 shared guard; care-reservation capacity coordination and expected-duration/overrun rules belong to T031. At the T012 historical baseline no reception/clinical public HTTP mutation API was exposed; the current proposed v1.2 amendment adds one through T043.
- Verification: user-run `scripts/test-local.ps1` against local `127.0.0.1:5432/otcms_test_db` passed: 95 tests, 0 failures/errors/skips (BUILD SUCCESS, 2026-10-13 18:37:14 +07). `VisitT012PostgresIntegrationTest` passed 2/2. No production/Supabase access.
- Review: Owner Long approved T012 implementation and requirement trace on 2026-10-13; evidence recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T012.md` and `sdd/reviews/trace-feat-002-appointment-booking-T012.md`.
- Contract: Owner Long approved `visit-reception-clinical` v1.0.0 on 2026-10-13; frozen in `shared_context.md` v3.2.0.
- Status: PARTIAL / T014/T031 INTEGRATION PENDING. T013 persistence is complete; do not claim atomic reception writers or care-capacity enforcement before those tasks.

### T013 Execution Record — E-feat-002-appointment-booking-T013-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T013`.
- Dependencies: T010–T012 owner schema contracts are present; V007 is the prior Flyway head. T012 review/trace and `visit-reception-clinical` v1.0.0 approval are recorded above.
- Governance: `team` / `direct` per `shared_context.md`; execution stayed in this session. Host-level execution enforcement is not claimed.
- Grant: `G-feat-002-T013-A1`, consumed by `codex-direct-session-T013-A1` as a cooperative execution reference; no host claim.
- Implemented boundary: additive V008 appointment, audit and command-replay persistence; V009 correction for appointment-only trigger fields; V010 deferred protection against deleting/moving required check-in reception evidence; restrictive owner references, same-patient composite FKs, lifecycle/source/snapshot constraints, indexes and append-only history.
- Explicit exclusions: no booking HTTP/use case, coordination guard, check-in writer, current slot exclusion/occupied-until enforcement, schedule policy, or care-capacity coordination. Those remain assigned to T014–T016/T027 and T031.
- Verification: `mvnw.cmd -DskipTests test` compiled 129 production and 31 test sources (BUILD SUCCESS, 2026-10-13 19:07:37 +07). User-run `scripts/test-local.ps1` against local `127.0.0.1:5432/otcms_test_db` passed on 2026-10-13 19:11:15 +07: 97 tests, 0 failures/errors/skips; `AppointmentT013PostgresIntegrationTest` passed 2/2. Clean migration reached V010; upgrade from V007 applied V008–V010; Hibernate validation initialized and public schema migrated to V010. Regression coverage rejects deleting or moving the required reception row. No production/Supabase access.
- Required next step: none for T013. T014 owns shared transaction guard/writer wiring; T015 owns Doctor/time-range exclusion and occupied-until enforcement.
- Review: Owner Long approved implementation and requirement trace on 2026-10-13; evidence recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T013.md` and `sdd/reviews/trace-feat-002-appointment-booking-T013.md`.
- Status: COMPLETE for T013 persistence foundation, verification, review and trace. T014/T015 and later workflow/slot enforcement remain separate tasks.

### T014 Execution Record — E-feat-002-appointment-booking-T014-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T014`.
- Dependencies: T005, T009–T013 complete. T005 binds PostgreSQL transaction-local `lock_timeout=2s`, `statement_timeout=5s`; SQLSTATE `55P03`/`57014`/`40P01` -> rollback and `503 DEPENDENCY_UNAVAILABLE` without retry; `23P01` -> rollback and `409 SLOT_UNAVAILABLE`.
- Governance: `team` / `direct` per `shared_context.md`; work remained in this session. Host-level task-grant enforcement is not claimed.
- Implemented boundary: additive V011 singleton `integration_coordination_guard` row protected from update/delete/truncate; `PostgresCoordinationGuardAdapter` implements `CoordinationGuardPort.acquire()` with `Propagation.MANDATORY`, transaction-local bounded timeouts, READ COMMITTED validation, and a `FOR UPDATE` row lock retained through caller commit/rollback. Shared HTTP error mapping classifies only the approved SQLSTATEs and exposes the common safe error envelope.
- Current guarded writers: service catalog create/update/activate/deactivate, Doctor profile provisioning, and Receptionist patient-profile creation acquire the guard before owner reads/locks/writes. Read-only queries remain unguarded. Unit tests assert guard-before-repository ordering.
- Explicit limitations: appointment command handlers, reception arrival/check-in writers, dependent-relationship revocation and staff schedule/assignment maintenance writers do not yet exist or are withheld by their owning tasks. They must acquire this port first when implemented; T014 does not claim these workflows delivered. No network call occurs inside the current guarded handlers.
- Verification so far: focused `mvnw.cmd -Dtest=ClinicServiceHandlerTest,ProvisionDoctorProfileHandlerTest,PatientProfileServiceTest,CoordinationFailureExceptionHandlerTest test` passed 12 tests (0 failures/errors/skips, BUILD SUCCESS, 2026-10-13 19:24:11 +07); Maven compiled 132 production and 33 test sources. `CoordinationGuardT014PostgresIntegrationTest` passed 3/3 in the full local PostgreSQL run, covering V011 clean/upgrade, UPDATE/DELETE/TRUNCATE singleton immutability, transaction requirement, bounded timeout/isolation settings and PostgreSQL-confirmed separate-transaction lock waiting. After updating the stale T013 migration-count assertions to 4/11 for additive V011, the full `scripts/test-local.ps1` suite passed against `127.0.0.1:5432/otcms_test_db`: 104 tests, 0 failures/errors/skips, BUILD SUCCESS, 2026-10-13 19:37:57 +07. Flyway V011 clean/upgrade and PostgreSQL guard-lock serialization passed; no production/Supabase access.
- Review: code review found no concrete production correctness defect in the implemented guard/current guarded handlers; deterministic PostgreSQL lock-wait and UPDATE/TRUNCATE assertions were added in response to review. Green local PostgreSQL suite and code review are complete. Human decision received: `APPROVE implementation and trace T014 — Owner Long` (2026-10-13).
- Status: COMPLETE. Local PostgreSQL verification, code review, and implementation/requirement-trace approval by Owner Long passed. T014 is complete for the shared coordination guard and currently existing guarded writers only; future appointment, check-in, dependent-revocation and staff-maintenance writers remain responsible for acquiring this guard when implemented.

### T015 Execution Record — E-feat-002-appointment-booking-T015-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T015`.
- Dependencies: T013 persistence and T014 coordination/error mapping are recorded COMPLETE above. Source migration inventory was V012 before this task; V013 is the next additive migration. No applied migration was edited.
- Implemented boundary: `V013__enforce_appointment_doctor_occupancy.sql` adds persisted `occupied_until = end_time + buffer_minutes`, maintained by a trigger on insert and relevant updates, and a GiST exclusion constraint on Doctor plus `[start_time, occupied_until)` for CONFIRMED/CHECKED_IN/IN_PROGRESS/COMPLETED. The predicate has no current-time dependency. Existing shared SQLSTATE `23P01` mapping returns `409 SLOT_UNAVAILABLE`.
- Verification added: `AppointmentT015PostgresIntegrationTest` covers overlap within the buffer, exact adjacency, cancelled-row exclusion, CHECKED_IN/IN_PROGRESS/COMPLETED occupancy, generated end, and transaction rollback after an exclusion failure.
- Verification result: `./mvnw.cmd -DskipTests test` compiled all 151 production and 38 test sources successfully. The first local run exposed SQLSTATE `42P17` (`generation expression is not immutable`); V013 was corrected to maintain the persisted column with a trigger. Follow-up user-run `backend/scripts/test-local.ps1` passed at 2026-10-14 00:24:40 +07: 120 tests, 0 failures/errors/skips; `AppointmentT015PostgresIntegrationTest` passed 3/3 against local PostgreSQL, with migration and Hibernate validation successful. No production/Supabase database was contacted.
- Review artifacts: `sdd/reviews/post-code-feat-002-appointment-booking-T015.md` and `sdd/reviews/trace-feat-002-appointment-booking-T015.md`; technical review found no concrete defect. Owner approval is pending.
- Residual deployment gate: verify migration compatibility with the actual production PostgreSQL role and `btree_gist` capability before deployment. Existing T002 capability evidence covers only the local test database.
- Human decision: `APPROVE implementation and trace T015` received from Owner Long on 2026-10-14 (Asia/Bangkok).
- Status: COMPLETE. Implementation, local PostgreSQL verification, post-code review and requirement trace approved. Production/Supabase migration capability remains a deployment check; no production database was accessed for this task.

## 6. Phase 3 — Catalog và availability

| Task | Owner | Depends on | Deliverable / exit evidence |
|---|---|---|---|
| [x] T018 | Appointment | T017 | Shared slot engine UTC/clinic-local dates, configured grid với seconds/fraction=0, reject không round; bounded shift/time-off/appointment/care queries; snapshot/current-service contexts và self exclusion. |
| [x] T019 | Appointment/web | T018 | Public và staff availability: visibility/acceptance/service rules, notice/horizon/non-past/actor scope; catalog {items:[]} và slot {slots:[]} empty200; Admin inspect only, public không PII/reasons. |
| [x] T020 | Appointment | T018 | Reschedule availability CONFIRMED same Doctor/service/patient/purpose/snapshots; removed Doctor/inactive service exception; Patient old deadline + new notice, Receptionist bypass notice vẫn horizon/availability; reassignment availability same slot/different eligible accepting Doctor, Receptionist và start>=now. |
| [x] T021 | Appointment/web | T016, T019 | Trusted proxy IP resolver, independent application fixed-window limiter buckets, Clock reset/ceil Retry-After và bounded cleanup; verify privacy/error/correlation; deployment one instance theo PLAN. |

## 7. Phase 4 — Booking, query và changes

| Task | Owner | Depends on | Deliverable / exit evidence |
|---|---|---|---|
| [x] T022 | Appointment | T008, T014–T020 | Atomic create online/assisted, actor-derived source, final current authorization/availability/capacity recheck và audit; no-account profile; all create/follow-up/reschedule reject past. |
| [x] T023 | Appointment/web | T008, T013 | Scoped detail/list/pagination; clinic-local startDate filters from/to independently optional, inclusive, invalid range rejects, no history horizon; scoped404/endpoint403/DTO/error contracts. |
| [x] T024 | Appointment | T020, T022 | Reschedule updates same row/self exclusion, keeps snapshots/identity/purpose; Patient deadline/new notice, staff exception; failure leaves reservation/row/audit unchanged. |
| [x] T025 | Appointment | T022 | CONFIRMED cancellation authorization/deadline/reason/audit/occupancy rules; CHECKED_IN branch chỉ expose khi T029 pass, không silently expose incomplete workflow. |
| [x] T026 | Appointment | T020, T022 | Receptionist reassignment CONFIRMED, start>=now equality accepted, same slot/snapshots/different Doctor, reason/audit; past -> BOOKING_TIME_IN_PAST, failure immutable, old Doctor scope revoked; resolves affected booking theo D04/D06. |

## 8. Phase 5 — Visit/reception lifecycle và follow-up

| Task | Owner | Depends on | Deliverable / exit evidence |
|---|---|---|---|
| [x] T027 | Integration + appointment/visit | T012–T017, T022 | Atomic check-in/visit/reception/audit/replay, inclusive early/late window; valid success replay reauthorized trước current state/window checks trả stored response; different-key uniqueness; invalid linkage VISIT_LINK_CONFLICT. Mandatory failure rollback all writes/replay. |
| [x] T028 | Appointment + reception | T012, T014, T016, T027, T041–T043 | Manual Appointment-owned NO_SHOW strictly after grace, xác minh no timely arrival, actor/time/audit/reason optional; unavailable arrival port fail-closed 503; serialize arrival/check-in/no-show. Depends on Visit WALK_IN API T043 so late reception preserves same-patient `priorAppointmentId`/`arrivedAt` without changing old appointment status/occupancy. No WALK_IN endpoint or Visit write in Appointment. Exact focused command: `.\mvnw.cmd '-Dtest=MarkAppointmentNoShowT028Test,MarkAppointmentNoShowT028PostgresIntegrationTest' test`; local PostgreSQL route remains the approved backend integration script, revalidated at preflight. |
| [x] T029 | Integration + clinical/appointment | T025, T027, T041, T042 | Assigned Doctor start/complete scheduled-care step; COMPLETED giữ end+buffer, visit có thể còn mở. CHECKED_IN cancel chỉ khi chưa clinical activity, kết thúc visit/reception atomic; cancel/start race đúng commit order. Reception/visit capacity check trước bắt đầu khám. Focused and full PostgreSQL suite passed; execution evidence in the T029 Execution Record and `sdd/reviews/trace-feat-002-appointment-booking-T029-A1.md`. |
| [x] T030 | Appointment + clinical | T012, T022, T029, T043 | Follow-up từ actual source visit với assigned Doctor đã hoàn tất phần khám/indication, patient derived, source_visit_id mandatory; không bắt source appointment COMPLETED/visit đóng; active service/accepting provider/current snapshot và reason/audit. |

### T030 Checkpoint — approved 2026-10-19

- Owner Long approved all five decisions in `sdd/reviews/preflight-feat-002-appointment-booking-T030.md`: new-booking eligibility applies; reason is required; patient/ordering Doctor derive from Visit/authenticated Doctor; invalid or unavailable evidence fails closed; scope is follow-up appointment plus audit only.
- Implementation boundary, file scope, exclusions, verification cases and stop conditions are recorded in the preflight. T030 remains unchecked until implementation, local PostgreSQL verification, independent review, trace/sync-back and final Human approval pass.

### T030 Execution Record — E-feat-002-appointment-booking-T030-A1 (in progress)

- Added Doctor-only `POST /api/v1/visits/{visitId}/follow-up-appointments`, Appointment orchestration and DTO-only response. Under the shared guard, the service resolves the authenticated ordering Doctor, checks Visit-owned care/exam/indication evidence, derives patient, applies active service/accepting target Doctor/assignment/current slot and booking-window checks, then writes `DOCTOR_FOLLOW_UP` appointment plus immutable `CREATED` audit with source Visit and required reason.
- Existing V008 schema supports source Visit, optional originating appointment, ordering Doctor and audit reason; no migration or clinical/Visit mutation. Visit not-found evidence now subclasses the shared `VisitWorkflowException`; the public contract shape is unchanged.
- Added focused unit and PostgreSQL HTTP tests. `./mvnw.cmd clean -DskipTests test-compile` passed (315 production/71 test sources); `./mvnw.cmd '-Dtest=CreateFollowUpAppointmentT030Test' test` passed 5/5.
- Initial direct PostgreSQL attempt failed during test-context startup because local test credentials were unavailable; the profile is pinned to `127.0.0.1:5432/otcms_test_db`. No Supabase/production access.
- Owner-run `scripts/test-local.ps1` first reported only the T030 audit assertion failing within 250 tests. Root cause: fixture correlation `t030-<UUID>` was rejected and replaced by `CorrelationIdFilter`; the fixture now sends a valid UUID. Follow-up Owner run reports BUILD SUCCESS, 250 tests, 0 failures/errors/skips (2026-10-19 16:58:05 +07); Surefire report confirms T030 PostgreSQL HTTP integration 2/2.
- Focused unit coverage (5/5) includes derived identities, assigned-source requirements, missing indication, required reason and fail-closed Visit dependency behavior. Draft AC trace is at `sdd/traces/trace-feat-002-appointment-booking-T030-A1.md`.
- Independent review: `sdd/reviews/post-code-feat-002-appointment-booking-T030-A1.md`; no remaining findings after removing Patient online minimum-notice enforcement (Doctor follow-up exemption) and mapping SQLSTATE 23P01 to `SLOT_UNAVAILABLE`. Reviewer withdrew audit redundancy concern after confirming durable appointment ordering-Doctor data and immutable audit linkage.
- Final verification: owner-run `backend/scripts/test-local.ps1` passed BUILD SUCCESS, 250 tests, 0 failures/errors/skips at 2026-10-19 17:19:03 +07, after review corrections. T030 unit tests pass 5/5; PostgreSQL HTTP integration passed 2/2. Requirement trace and post-code review are approved by Owner Long on 2026-10-19 (Asia/Bangkok).
- State: IN PROGRESS. Owner Long approved implementation and trace on 2026-10-19 (Asia/Bangkok). T030 is COMPLETE and `[x]`.

## 9. Phase 6 — Capacity và protected mutations

| Task | Owner | Depends on | Deliverable / exit evidence |
|---|---|---|---|
### T031 Checkpoint — approved 2026-10-19

- Dependency audit: T012, T014, T029 and T043 are `[x]`; evidence is recorded in TASKS and the T029 review/trace. T031 remains `[ ]`.
- D12 deliberately leaves expected-duration estimation, overrun response, suitable-room coordination, and concurrent reservation mechanics to the owning Visit SPEC/PLAN and public contract. Existing capacity ports are read/validation boundaries; no room-reservation contract/persistence was found.
- Owner Long approved all eight policy groups in `sdd/reviews/preflight-feat-002-appointment-booking-T031.md` on 2026-10-19: configured service/step estimate with no numeric fallback; conflict-safe overrun and Receptionist coordination; interval/state rules; exact own-link; room owner scope; guarded concurrency; fail-closed authority/errors; and separate checkpoint for schema/contracts.
- T031 remains `[ ]`. The policy checkpoint authorizes implementation only after a material-state checkpoint for new Visit API/schema/contract; that checkpoint was subsequently approved as A1/A2 below.
- Owner Long approved the separate material-state checkpoint `sdd/reviews/preflight-material-feat-002-T031-visit-capacity-contract-A1.md` on 2026-10-19: additive Visit care-step API/port, estimate config, actual completion/audit persistence (provisional V015), error mapping; no room schema/API. Owner Long approved A2 on 2026-10-19: extension route accepts SCHEDULED and WALK_IN, while start/complete remain Visit WALK_IN-only; source implementation is authorized within the checkpoint.
- Room prerequisite disposition: Owner Long approved all eight decisions in `sdd/reviews/preflight-material-feat-002-T031-room-capacity.md` and all eight Admin API decisions on 2026-10-19. V017 room allocation/reservation and V018 Admin catalog/API are implemented. The 278/278 PostgreSQL run verified the implementation before repository consolidation. Final persistence now uses `VisitRoomJpaRepository`; its test-compile passed. Owner Long explicitly approved the trace to close T031 on 2026-10-19 with that verification boundary recorded. The wider room-start-vs-Appointment-booking race remains in T033/T035 and does not block T031's scope. AC15 remains open until T033–T035 complete.
- T031 Action Record — E-feat-002-appointment-booking-T031-A1: add V015 care-step snapshots/actual completion/immutable audit; validated Visit config map; Appointment-owned `AppointmentCapacityPort` contract/adapter; Doctor-only Visit WALK_IN start/complete and scheduled-or-WALK_IN extension API. Extension checks only the new prospective interval `[max(now,currentExpectedEnd),newExpectedEnd)` and preserves Appointment state/snapshots. Capacity conflicts append a minimal Visit audit signal for Receptionist coordination without mutating appointment/care occupancy; a separate Doctor route records coordination intent. If Appointment capacity evidence is unavailable or a completed appointment lacks completion audit evidence, fail closed with typed `DEPENDENCY_UNAVAILABLE` (503). Use shared guard + one datasource transaction; no room catalog/schema/assignment. Runtime correction: V015 action width was corrected additively in V016; T029 cleanup, T043 fixture scoping, and T017 historical target were corrected. Owner Long's local PostgreSQL run on 2026-10-19 20:11:18 +07 passed 267/267 tests including affected T031/T043/T029/T017 tests and clean V001→V016 migration. Later concurrency review found scheduled-start capacity rejection rolled back its required audit; that path now preserves the Visit audit while leaving Appointment/care state unchanged. Latest source/test compile passed and `UpdateAppointmentStatusT029Test` passed 4/4, but the PostgreSQL integration regression needs a fresh run. Concurrency analysis and proposed separate Room owner checkpoint are at `sdd/reviews/concurrency-review-feat-002-appointment-booking-T031-A1.md` and `sdd/reviews/preflight-material-feat-002-T031-room-capacity.md`. Trace: `sdd/traces/trace-feat-002-appointment-booking-T031-A1.md`.
- PostgreSQL correction and closure evidence: the 21:39:53 run's single failure was caused by the Doctor-conflict fixture being moved before the tested interval while adding a separate stale-room fixture; restored it to `[NOW,NOW+15m)`. Owner Long's rerun at 2026-10-19 21:49:21 +07 reports BUILD SUCCESS, 274 tests, 0 failures/errors/skips, with `VisitT012PostgresIntegrationTest` 4/4. This verifies V001→V017 clean install, V016→V017 preserving Visit/care data, T031 room lifecycle and same-room concurrent starts. T031 still awaits operational room provisioning disposition, broader room-vs-booking race evidence in T033/T035, and final trace approval.
- Owner Long approved all eight decision groups in the separate Admin room-management API checkpoint on 2026-10-19. Admin-only catalog endpoints, class DTOs, Visit service and additive V018 immutable management audit are implemented. Owner Long's local PostgreSQL run at 2026-10-19 22:19:56 +07 reports BUILD SUCCESS, 278 tests, 0 failures/errors/skips; `VisitT012PostgresIntegrationTest` passed 4/4. It verified Admin authorization/lifecycle/audit, active-use protection, Admin-vs-care-start serialization, V001→V018 and V017→V018 before the repository consolidation. Final persistence uses `VisitRoomJpaEntity` + `VisitRoomJpaRepository`; test-compile passed. Owner Long then approved the trace to close T031 on 2026-10-19. The wider T033/T035 room-start-vs-booking race remains separate.

| [x] T031 | Visit/reception/clinical | T012, T014, T029, T043 | Visit contract v1.5.0 plus A1/A2 and approved Admin room checkpoint; configured care-step durations, Doctor and suitable-room active capacity reservations/extensions for SCHEDULED/WALK_IN without changing Appointment snapshots/status; protect upcoming booking/completed buffer with exact validated own-link exception; Admin-managed room catalog; guarded atomic writes, typed conflicts, fail-closed outage. V017/V018 are additive. PostgreSQL 278/278 verifies clean V001→V018, V017→V018 preservation, API/audit/lifecycle, active-use checks and Admin-vs-care-start serialization for the implementation before repository consolidation. Final `VisitRoomJpaRepository` consolidation passes test-compile; no PostgreSQL run after this consolidation is recorded. Owner Long approved the trace to close T031 on 2026-10-19. Wider room-start-vs-booking integration races remain in T033/T035; AC15 remains incomplete until those tasks pass. Supporting services/result-reading are Visit steps; queue/lab scope is separate. |
| [x] T032 | Service/staff + integration | T010, T011, T024–T026, T031 | Consume completed T010/T011 foundation contracts; deliver the minimal Admin mutation-impact projection and guarded owner APIs for Service Catalog/Staff mutations. Apply D04/D06 blocking to shift changes/removal only when an affected booking would lose coverage; apply the approved protected-booking policy to time-off creation/extension and Doctor-service assignment removal when they would invalidate a confirmed booking or active care. Reject the mutation until each affected confirmed booking is validly resolved through Receptionist reschedule, cancellation, or successful same-slot reassignment under D06; never silently invalidate checked-in/in-progress care. Service deactivation (D08), profile hide/directory removal and account lock (D07) preserve existing appointment state, snapshots, and occupancy and do not by themselves count as inability to serve; do not auto-transition or release bookings. Any profile/account mutation that actually changes existing-care serviceability must use an explicitly approved policy and the same impact check; do not infer clinical unavailability from public visibility or account lock. Admin projection is purpose-limited impact evidence only (affected resource, whether protected booking/active care blocks the proposed mutation, and whether resolution is complete); it must not expose general appointment list/detail, patient data, or grant appointment command authority. Incident tracking/API/CRUD is outside this task; successful appointment resolution uses the existing appointment audit contract, with no new status or automatic resolution. All participating mutation and booking/care writers acquire the T014 coordination guard before policy reads/locks/writes in the shared transaction; impact-read outage or stale/incomplete evidence fails closed. Exit evidence: owner/contract and HTTP authorization tests for each mutation class; rejection tests prove no resource, appointment/care state, occupancy, or audit mutation on failure; success tests prove preserved snapshots/history and only the authorized resolution audit; PostgreSQL separate-transaction tests prove booking/care versus protected mutation serialize without an uncovered committed booking or invalidated active care. Keep protected endpoints unavailable/fail-closed until the complete check is wired. T032 is downstream of T010/T011 and T024–T026/T031, not a prerequisite for their foundation exits or T017. |
| [x] T033 | Integration | T022–T032, T043 | Separate-transaction PostgreSQL races across appointment create/change, active care/room, protected Doctor/service mutations, arrival/no-show/WALK_IN and patient identity authority; no double assignments or invalid committed booking/care; no room/device reservation during booking. Startup-evaluated production gate verifies deployment-mounted JSON SHA-256 + Ed25519 signature, release/build + Flyway version, Owner Long approval, expiry, DB constraint/room/config prerequisites and signed phase7 evidence. Gate appointment commands, WALK_IN, Doctor care start/extend/complete, follow-up creation, Admin Doctor/service/assignment/room writes; keep reads, impact previews, auth and health open. Closed gate returns 503 `PRODUCTION_READINESS_GATE_CLOSED`; fail closed on missing/invalid/expired/mismatched evidence. Verify on dedicated local PostgreSQL only; production access excluded. |

T032 policy and material API/schema/audit checkpoint approved by Owner Long on 2026-10-19; see `sdd/reviews/preflight-feat-002-appointment-booking-T032.md` and `sdd/reviews/preflight-material-feat-002-T032-admin-mutations.md`. Removing a Doctor-service assignment is blocked whenever that Doctor has any active Visit care (`RESERVED` or `IN_PROGRESS`), regardless of service or step. No `service_id` is added to Visit. T032 completed after implementation, independent review/re-review, trace update and Owner Long's 2026-10-20 09:37:59 +07 full local PostgreSQL suite: 288/288 passed; `T032AdminResourceMutationPostgresIntegrationTest` 6/6 passed, including CHECKED_IN protection. Trace: `sdd/traces/trace-feat-002-appointment-booking-T032-A1.md`.

T032 implementation record — Admin APIs cover shift/time-off create, update and delete, Doctor directory-state update, Doctor-service assignment create/remove, D08 soft deactivation and privacy-limited impact reads. Shift removals/replacements reject only when protected booking occupancy or active care would lose all covering shifts; time-off create/extension and assignment removal reject affected protected bookings, and assignment removal always rejects any active Visit care per approved policy. Every guarded mutation acquires T014 before impact reads and writes owner-local rows/audits in the same transaction. Appointment and Visit expose feature-owned public impact query services; no foreign repository/entity access or Visit `service_id` field was added. Conflicts use `409 RESOURCE_MUTATION_BLOCKED` with only resource type/id, booking count and active-care flag; impact-evidence failures fail closed as `503 DEPENDENCY_UNAVAILABLE`. Service activation validates Admin actor and writes V019 audit. Independent review found and verified fixes for time-off deletion incorrectly blocking existing work and read-only impact preview trying to lock the guard; reviewer re-review reported no remaining issues. Owner Long's local PostgreSQL run finished 2026-10-20 09:30:10 +07 with BUILD SUCCESS, 287 tests, 0 failures/errors/skips; its T032 integration suite passed 5/5. An explicit CHECKED_IN booking protection case was added and compiled successfully at 09:33:27 +07. Owner Long's subsequent full local PostgreSQL run finished 2026-10-20 09:37:59 +07 with BUILD SUCCESS: 288 tests, 0 failures/errors/skips; `T032AdminResourceMutationPostgresIntegrationTest` passed 6/6. This verifies migration V001→V019 and V018→V019 preservation, time-off preview/deletion, audit/authorization, D07/D08 preservation, active-care assignment protection, checked-in booking protection and guarded booking/care races. T032 is complete; see trace `sdd/traces/trace-feat-002-appointment-booking-T032-A1.md`.

## 10. Phase 7 — Verification và closure

| Task | Owner | Depends on | Deliverable / exit evidence |
|---|---|---|---|
| [x] T034 | QA + owning modules | T018–T033, T040–T043 | Domain/contract/HTTP security/projection/config tests theo AC matrix bên dưới; exact timezone/grid/now/grace/deadline boundaries, unauthorized transitions và failure immutability. |
| [x] T035 | QA/persistence | T017, T033, T040–T043 | Real PostgreSQL separate transactions/thread barriers; overlap/COMPLETED buffer, guard ordering/deadlock/timeout, arrival/check-in/no-show/WALK_IN/cancel-start/identity-link authority races; failure injection sau visit/reception/audit/replay rollback. Clean/upgrade V001 và checksum evidence. |
| [x] T036 | Architecture/security | T034, T035, T040–T043 | Independent code review, layered dependency boundaries/no cycles; privacy/log/correlation/impact projection, schema/security review và residual blockers resolved. |
| [x] T037 | Appointment/docs | T034–T036, T040–T043 | OpenAPI/DTO/errors/config/owning contracts, source baseline UC mapping và AC01–AC24 test references; TASKS sync-back sau implementation, mọi change cần review tương ứng. Owner Long approved T037 on 2026-10-20 with A3's runtime/static response and schema differences retained as documented limitations; 306/306 tests passed. |
| [x] T038 | Build/QA | T003, T034–T037, T040–T043 | Từ otcms/backend chạy .\mvnw.cmd test và .\mvnw.cmd clean verify; lưu JDK/profile/environment/exit codes/totals/failures/skipped; required PostgreSQL/Flyway tests thực sự chạy, skipped không đủ full verification. **PASS** — full test output 2026-10-20 17:35:43 +07 and clean verify output 2026-10-20 20:55:51 +07 each report 306 tests, 0 failures/errors/skips; Java 21.0.7, profile `test`, PostgreSQL 18.1 at `127.0.0.1:5432/otcms_test_db`; Flyway migrations and upgrade tests executed. Maven reports BUILD SUCCESS; literal shell header and wrapper version were not included in the pasted evidence. See T038-A1 review. |
| [x] T040 | Appointment + architecture | T017, T022–T027 | Migrate Appointment to the repository's current feature-first layered convention: `controller`, `exception`, `mapper`, `model/dto/{request,response}`, `model/entity/enums`, `repository`, `service`, `service/impl`; use feature-specific `config`/`web` where needed and place internal value-model classes directly in `model`. Keep JPA entities only for persisted tables and use Java classes rather than `record`. Preserve routes, behavior, persistence and frozen cross-feature contracts; remove obsolete package paths and review dependency direction. No schema/contract/API behavior change. **COMPLETE** — implementation and trace approved by Owner Long 2026-10-14. |
| [x] T041 | FEAT-002 + architecture | T040 | Align Patient, Service Catalog, Staff, Visit and Integration packages with the current feature-first convention in root `AGENTS.md` and ARCH-01: `controller`, `exception`, `mapper`, `model/dto/{request,response}`, `model/entity/enums`, `repository`, `service`, `service/impl`; add feature-specific packages only when needed. Keep internal value-model classes directly under `model`, use `@Entity` only for persisted table mappings, and use Java classes rather than `record`. Preserve API behavior, schema, ownership, and contract semantics. |
| [x] T042 | Backend architecture + persistence | T041 | Standardize production persistence on Spring Data JPA across all feature packages. Replace direct JDBC persistence in Appointment, Auth, Patient, Visit and Integration with JPA repositories/entities/EntityManager queries; retain PostgreSQL/Flyway constraints and transaction, locking, audit/replay semantics. No production `JdbcTemplate`, `NamedParameterJdbcTemplate` or `JdbcClient` use. |
| [x] T039 | Owner Long + reviewers | T033–T038, T040–T043 | **CLOSED for review/verification** — Owner Long approved on 2026-10-20: “Approve đóng T039 theo đề nghị; giữ production gate đóng.” T033–T038 and T040–T043 evidence/reviews are recorded. FEAT-002 runtime/static OpenAPI alignment was completed and verified in T037-A4; release-specific phase7 readiness remains a pre-release condition. Production enablement is a separate gate: no release-bound signed phase7 evidence or production prerequisite verification is recorded, so protected production commands remain closed. |
| [x] T043 | Visit/reception | T012, T014, T042 | Add Receptionist-only `POST /api/v1/visits/walk-ins` using Visit controller → service interface → service.impl → Visit JPA repository. Request requires `patientProfileId` and `Idempotency-Key`, accepts optional `priorAppointmentId`; actor and `arrivedAt` are server-derived. Set `checked_in_at = arrived_at` from the same server Clock instant. Validate prior appointment belongs to patient; atomically persist WALK_IN visit, initial reception, append-only audit and replay under shared guard; exact replay returns stored creation response; conflicting reuse fails. Add a new additive Flyway migration for a Visit audit table (`actor_account_id`, `visit_id`, `action`, `occurred_at`, `correlation_id`); enforce append-only semantics and never edit applied migrations. No Appointment status/occupancy mutation, cross-feature entity/repository access, or port/adapter wrapper around Visit JPA. Pre-edit material-state Human checkpoint covers schema/version allocation, database route/capability, append-only enforcement and guard transaction. Exact focused command: `.\mvnw.cmd '-Dtest=CreateWalkInVisitT043Test,CreateWalkInVisitT043PostgresIntegrationTest' test`; local PostgreSQL route remains the approved backend integration script, revalidated at preflight. Material-state Human checkpoint must be approved before edits. |

### T039 Review Record — R-feat-002-appointment-booking-T039-A1
- Review artifact: `sdd/reviews/post-code-feat-002-appointment-booking-T039-A1.md`.
- Evidence: T033–T038 and T040–T043 traces/reviews inspected; dependency tasks are recorded complete. T037 was approved with documented response/schema differences; follow-up T037-A4 now reconciles the FEAT-002 subset and verifies parity. T038 records both full 306-test runs passing with no failures/errors/skips.
- Production gate: implementation is fail-closed by default; no release-bound signed phase7 evidence, production migration/capability verification, or production owner enablement decision was present. Local test evidence does not satisfy that release gate.
- Outcome: **Owner Long approved closing T039 for review/verification on 2026-10-20** with production gate explicitly held closed. FEAT-002 runtime/static OpenAPI alignment is now complete; release-bound phase7 readiness remains a pre-release follow-up. Production enablement is not approved and no signed release artifact was created or validated.

Exact verification commands (PowerShell, working directory `otcms/backend`, sau T003 checkpoint):

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

## 11. Acceptance traceability

Các task implementation và verification cùng chịu trách nhiệm AC; T034–T038 lưu test IDs/results cho từng AC riêng biệt.

| SPEC AC | Tasks | Evidence bắt buộc |
|---|---|---|
| AC01, AC02, AC17 | T006–T008, T022, T034–T035 | Roles/portal, profile upgrade, current linked self/other-profile denial/no-account Receptionist flow, scoped denial. |
| AC03, AC04 | T015, T025, T027–T029, T034–T035 | Authorized/unauthorized lifecycle, terminal rules, completed buffer và interval adjacency/history. |
| AC05, AC06, AC22 | T016, T018–T020, T024, T026, T034 | Four availability contexts, eligibility exceptions, config overrides/startup, grid seconds/fraction/offset, notice/horizon/deadline/now boundaries. |
| AC07, AC08, AC21 | T024–T026, T034–T035 | Snapshot/self-overlap reschedule, cancellation, reassignment trước/bằng/sau now; immutable failure. |
| AC09 | T030, T034 | Visit-source exam/indication, implicit patient, open/walk-in source và eligibility. |
| AC10, AC11 | T027, T029, T034–T035 | Replay/single visit, full rollback, progression và cancel-start atomicity. |
| AC12, AC13, AC14 | T032–T035 | Blocked mutation, unchanged lock/hide/deactivate history, incident no auto-release, private impact evidence. |
| AC15 | T031, T033–T035 | Doctor active-care capacity, exact own-link with consultation buffer protected, upcoming booking/completed buffer, no double Doctor allocation. Room contract/persistence and Admin catalog API are approved/implemented; do not claim AC15 fully satisfied until PostgreSQL verification and integrated T033–T035 concurrency evidence exist. |
| AC16, AC23 | T019, T021, T023, T034, T036 | Limiter/proxy/reset/errors/privacy; empty catalog, independent inclusive filters/date boundaries/history/pagination. |
| AC18, AC19, AC20 | T016, T027–T028, T034–T035, T043 | 30/15 override/zero, inclusive/strict/midnight, timely arrival delayed processing, late WALK_IN reference/API and serialized fail-closed evidence. |
| AC24 | T043, T034–T035 | Visit API authorization, request validation, prior appointment same-patient check, idempotency/replay, server arrival time, atomic visit/reception/audit/replay, unchanged referenced appointment. |

## 12. Completion rule và review record

Feature complete khi T001–T043 có exit evidence, mọi AC01–AC24 có verified results, required reviews và Human closure đạt. Pending/blocking prerequisite không được che bằng checkbox hoặc mock. TASKS approval không xác nhận implementation đã hoàn thành.

### T040 Execution Record — E-feat-002-appointment-booking-T040-A1

> Historical A1 snapshot: references to the `domain` package and `*UseCase` service names below describe the intermediate migration state and are superseded by the T041-A2 architecture follow-up. Current conventions are feature `model` types, service interfaces, and implementations under `service.impl`.

- Invocation: User requested: “Tạo task và triển khai migration FEAT-002 sang layered architecture”, 2026-10-14 (Asia/Bangkok).
- Governance: Project `team` / execution `direct` from `backend/sdd/shared_context.md` v3.3.8; host execution enforcement not claimed. Architecture authority: `backend/CONSTITUTION.md` ARCH-01 and approved Architecture Profile v1.3.0; RFC-001 already approves Spring layered architecture.
- Grant: `G-feat-002-T040-A1`, direct cooperative reference; runtime replay enforcement unverified. Consumed before package edits.
- Scope: Appointment package migration and related architecture docs only; preserve HTTP contracts, database schema, frozen integration contracts and business behavior. Existing unrelated dirty worktree changes were preserved.
- Implementation: moved Appointment HTTP classes to `controller`, API responses to `dto/response`, conversion to `mapper`, persistence implementations and persistence contracts to `repository`, service contracts/commands/search types to `service`, orchestration to `service/impl`, and result/value types to `domain`. Added `GetAppointmentsUseCase` so its controller depends on a service interface and returns service-owned result types rather than repository records. Check-in persistence returns a domain result; controller maps it to the API response DTO. Request JSON parsing remains unchanged to preserve strict field/type behavior. No migration or public route/schema change.
- Documentation: PLAN v0.5.0 and TASKS v0.4.0 align to ARCH-01; root AGENTS and `docs/architecture.md` were corrected to describe layered architecture. No Constitution change was needed because the approved Constitution already defines the target.
- Layout cleanup after Owner Long's follow-up: removed empty legacy `appointment/adapter` and `appointment/application` directories plus an empty accidentally nested `otcms_be/fu/fa26/sep490/g58/otcms_be/appointment` directory chain. Verified these paths contained zero files before cleanup; retained the active `fu.fa26.sep490.g58.otcms_be.appointment` source package and its code. Active Appointment Java package/path check remains 78 files, 0 mismatches.
- Static review: production Appointment package declarations match their source paths; no `appointment.application` or `appointment.adapter` Java package declarations/imports remain. Controllers have no Appointment repository imports or persistence exception handling; Appointment repositories have no Appointment service imports. The documentation tree and example flow were reconciled with layered architecture after independent review.
- Verification: initial user-run `./mvnw.cmd test` and `./mvnw.cmd clean verify` failed compilation because the check-in replay branch returned the repository replay record. Fixed by mapping to `CheckedInAppointment`. Focused post-fix rerun `./mvnw.cmd '-Dtest=CheckInAppointmentHandlerT027Test,GetAppointmentsHandlerT023Test' test` passed 10/10; user then reported full local suite BUILD SUCCESS, 211 tests, 0 failures/errors/skips (2026-10-14 23:06:01 +07:00). After review moved check-in persistence exception translation to a non-transactional service facade around a separate `@Transactional` worker, `./mvnw.cmd '-Dtest=CheckInAppointmentHandlerT027Test,CheckInAppointmentHandlerTest' test` passed 5/5 (T027 handler 4/4 and facade mapping 1/1). The successful 211-test user run predates this final service-boundary adjustment.
- Independent review: first pass identified controller persistence-exception handling; fixed. Re-review confirmed service/controller/repository direction and that the separate transactional proxy lets the facade catch commit-time `DataAccessException` and map it to `DEPENDENCY_UNAVAILABLE`/503. No blocking findings remain. Limits: no test deliberately fails a deferred constraint during commit; existing PostgreSQL rollback coverage exercises an immediate SQL failure. Service contracts retain `*UseCase` names while current root naming guidance gives `*Service` examples; recorded as a naming follow-up, not an architecture blocker.
- Human decision: Owner Long requested “hoàn tất independent review và phê duyệt implementation/trace T040” on 2026-10-14 (Asia/Bangkok); approval and trace closure recorded.
- State: COMPLETE — Appointment layered migration, verification evidence, independent review, trace and Human approval recorded. T040 is `[x]`. No schema, route, or frozen cross-feature contract change.
- Superseding runtime evidence — 2026-10-16: the approved local PostgreSQL test helper completed with BUILD SUCCESS; 212 tests, 0 failures, 0 errors and 0 skipped, after the T041/T042 follow-up changes. See `sdd/reviews/post-code-feat-002-appointment-booking-T041-A2.md`; this supersedes the earlier 211-test run noted above. No remote/Supabase database was accessed.

### T041 Execution Record — E-feat-002-appointment-booking-T041-A1

> Historical A1 snapshot: the `use-case`, persistence adapter, and entity layout terms below record the intermediate package migration. The subsequent T041-A2 follow-up supersedes those layout details and records the current architecture review and runtime evidence.

- Invocation: Owner Long follow-up that Patient, Service Catalog, Staff, Visit and Integration still used folders inconsistent with Appointment after T040; 2026-10-14 (Asia/Bangkok).
- Authority/scope: Constitution ARCH-01 and approved Architecture Profile; T041 follows approved layered target. Preserve public routes/request-response behavior, schema/Flyway history, frozen integration contract semantics/FQNs, ownership, transactions and security. No business logic change.
- Implementation: relocated 60 Java files across `patient`, `servicecatalog`, `staff`, `visit`, and `integration`. HTTP endpoints use `controller`; request/response DTOs and entity mapping are in `dto`/`mapper`; use-case contracts are injected into controllers while implementations are in `service/impl`; persistence contracts/adapters use `repository`; JPA entities use `entity`. `integration.contract` remains the shared contract boundary. Tests were moved/updated with source packages. Removed verified empty legacy `application`/`adapter` directories.
- Review-driven correction: Patient Profile and Doctor directory/schedule controllers now inject use-case interfaces, with Spring implementations under `service/impl`. Extracted Patient, Staff and Service Catalog API DTOs and mappers. Moved external Patient Auth adapter to `patient/service/impl`; moved exceptions used at persistence/cross-feature boundaries to their owning domain/contract packages.
- Documentation: updated `docs/architecture.md` with DTO/mapper boundaries and current feature package layout.
- Verification: `./mvnw.cmd -DskipTests test-compile` — BUILD SUCCESS; 258 production and 62 test Java sources compiled (2026-10-14 23:39:26 +07:00). Focused `-Dtest=PatientProfileServiceTest,CareRelationshipT008Test,PatientAuthorizationT008Test,CareRelationshipT008WebTest,ClinicServiceHandlerTest,ProvisionDoctorProfileHandlerTest,CoordinationFailureExceptionHandlerTest,IntegrationContractArchitectureTest test` passed 19/19 (0 failures/errors/skips; 2026-10-14 23:39:51 +07:00). No full local PostgreSQL suite run in this turn.
- Static inspection: validate package declarations against paths, absence of scoped legacy `application`/`adapter` imports/packages and empty legacy folders, and repository/service dependency direction before final review.
- Static inspection result: 0 scoped package/path mismatches; 0 legacy package/import references; 0 repository imports from `service`/`controller`; no empty `application`/`adapter` or unused Visit/Integration service folders. All cross-feature `integration.contract` FQNs remain in place.
- Final handoff recheck — 2026-10-16: current Visit/Patient/Service Catalog/Staff/Integration Java declarations match their paths (excluding the root `OtcmsBeApplication` class, which correctly has no package subfolder); the package migration removed the former `application` and `adapter` directories. T041 cannot be closed from this check: `staff/repository/DoctorProfilePersistenceAdapter` still imports Auth's `AccountRepository` and `AccountType` directly to implement `isDoctorAccount`, crossing the Auth ownership boundary. Replacing it with a public Auth contract would need a reviewed contract that preserves the existing provisioning rule; the available `DoctorAccountAccessPort` only answers active-account access and is not equivalent. No source behavior was changed. Focused tests were not rerun because the prior 19/19 result is already recorded and this pass was static-only. Independent review and Owner Long disposition remain outstanding; T041 stays `[ ]`.
- Owner Long follow-up implementation — 2026-10-16: replaced Staff direct Auth repository/type access with Auth-owned `DoctorAccountTypeQueryPort` preserving type-only provisioning semantics; moved Appointment actor identity/role to `common.security`; changed Appointment services to inject Spring Data repositories directly and removed repository-to-service imports; renamed business services and all eight Auth JPA entities (`JpaEntity`) without changing table mappings. Runtime triage corrected rate-limit bucket equality and Visit follow-up SQL/result mapping. Independent review and runtime evidence are recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T041-A2.md` and `sdd/traces/trace-feat-002-appointment-booking-T041-A2.md`.
- State: implementation, independent review and approved local PostgreSQL runtime suite passed. Owner Long approved closure on 2026-10-18; T041 is complete.
- Superseding runtime evidence — 2026-10-16: the approved local PostgreSQL test helper completed with BUILD SUCCESS; 212 tests, 0 failures, 0 errors and 0 skipped after runtime triage. Independent architecture findings and static scan results are in `sdd/reviews/post-code-feat-002-appointment-booking-T041-A2.md`; the full suite result is also recorded there. No remote/Supabase database was accessed.
- Historical handoff state before A2 follow-up: Auth ownership-boundary issue and independent review/Owner Long disposition were open; superseded by the follow-up record above.

### T042 Execution Record — E-backend-jpa-standardization-T042-A1

> Historical A1 verification state: the PostgreSQL/full-suite limitation recorded below was accurate when this entry was written and is superseded by the runtime evidence addendum below.

- Invocation: Owner Long instruction “Dùng JPA cho toàn backend”; 2026-10-15 (Asia/Bangkok).
- Authority/scope: approved Architecture Profile already binds persistence to Spring Data JPA, Flyway and PostgreSQL. Standardize production data access; preserve public APIs, Flyway history/schema, ownership, READ COMMITTED guarded transaction order/timeouts, row locks, atomic lifecycle/audit/replay writes, and SQL constraint/error behavior. No migration or test fixture change.
- Inventory: 16 production classes directly used `JdbcTemplate`/`NamedParameterJdbcTemplate`; Service Catalog and Staff were already using JPA. Auth JDBC lookups were in service classes; Appointment, Patient, Visit and Integration had JDBC adapters.
- Implementation: migrated Auth profile/account lookup to Spring Data repositories; converted Appointment creation/audit to JPA entities and Spring Data repositories; routed remaining Appointment PostgreSQL-specific projection/locking/atomic queries through JPA `EntityManager`; converted Patient care-relationship persistence to JPA mapping without re-registering the retired T008 workflow; converted Visit evidence/workflow and Integration guard adapters to `EntityManager`. Renamed Appointment/Auth persistence adapters to reflect JPA. Removed the direct production `spring-boot-starter-jdbc` dependency; JDBC test support remains for integration fixtures.
- Documentation: added T042 plan and task, updated `docs/architecture.md` to state JPA production persistence and the test-fixture exception.
- Verification: `.\mvnw.cmd package -DskipTests` — BUILD SUCCESS; 264 main and 62 test sources compiled, package repackaged after the final persistence/dependency changes (2026-10-15 12:30:14 +07:00). No tests were executed. An initial build found and corrected compile issues before the successful reruns.
- Static inspection: no `JdbcTemplate`, `NamedParameterJdbcTemplate`, or `JdbcClient` references remain in `backend/src/main/java`; test sources still use `JdbcTemplate` for direct PostgreSQL setup/assertions. No Flyway migration was added or edited.
- Independent review: `sdd/reviews/post-code-backend-jpa-standardization-T042-A1.md` — no blocking static finding. Runtime binding/behavior and full-suite regressions remain unverified.
- Historical A1 state: IMPLEMENTED — at that time, PostgreSQL/full-suite behavior verification and Owner Long disposition were pending; superseded by the current disposition below. T042 remains `[ ]` pending Owner Long disposition.
- Superseding runtime evidence — 2026-10-16: the approved local PostgreSQL test helper completed with BUILD SUCCESS; 212 tests, 0 failures, 0 errors and 0 skipped, as recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T041-A2.md`. This run followed the T042 implementation and included PostgreSQL integration coverage; it supersedes the verification limitation above. No remote/Supabase database was accessed.
- Current disposition: runtime/full-suite verification is recorded. Owner Long approved closure on 2026-10-18; T042 is complete.

### T029 Execution Record — E-feat-002-appointment-booking-T029-A1

- Authority: Owner Long approved preflight checkpoint `sdd/reviews/preflight-feat-002-appointment-booking-T029.md` on 2026-10-19. Scope includes assigned Doctor care start/completion, checked-in Receptionist cancellation before clinical activity, shared guard/transaction and immediate active-care capacity. No migration.
- Implementation: Added additive `VisitScheduledCarePort` and Staff `DoctorProfileIdentityQueryPort`; kept frozen `VisitWorkflowPort` unchanged. Appointment Doctor status flow resolves account to Staff profile under the coordination guard, locks Appointment, invokes Visit-owned start/complete, then stores status/audit in the same transaction. Visit creates/completes its care assignment and rejects overlapping active Doctor care. CHECKED_IN cancellation is Receptionist-only, closes the Visit only without examination or started/completed care, retains immutable initial reception evidence, and stores CANCELLED status/audit (including reason) atomically. Visit row is locked before cancellation evidence is checked. No room/device or overrun policy was added.
- Documentation: synchronized the additive owner contract and future clinical writer lock requirement in `backend/sdd/shared_context.md`.
- Tests added: `UpdateAppointmentStatusT029Test` covers assigned profile, wrong profile, capacity rejection, transition sequencing and completion. `AppointmentLifecycleT029PostgresIntegrationTest` covers actual transitions/snapshot retention, cancellation, clinical activity rejection, active-care capacity rejection, audit failure rollback and both sequential plus concurrent start/cancel orderings.
- Verification so far: `.\mvnw.cmd -DskipTests test-compile` BUILD SUCCESS (311 main and 69 test sources, 2026-10-19 11:31:40 +07); focused unit/regression command `.\mvnw.cmd '-Dtest=UpdateAppointmentStatusT029Test,MarkAppointmentNoShowT028Test,CheckInAppointmentServiceImplT027Test,VisitApiExceptionHandlerTest' test` BUILD SUCCESS, 12 tests, 0 failures/errors/skips (2026-10-19 11:32:06 +07).
- Initial PostgreSQL run: Owner Long's local helper run exposed T029 error mapping/cleanup defects and fixture leakage into T043 assertions. Corrected the checked-in cleanup ordering, added scoped stale T029 fixture cleanup, and aligned cancellation mappings; focused unit regressions passed afterward.
- PostgreSQL/full-suite verification: Owner Long reported the approved `scripts/test-local.ps1` run completed `BUILD SUCCESS` on 2026-10-19 14:55:16 +07:00; 243 tests, 0 failures, 0 errors, 0 skipped. This includes `AppointmentLifecycleT029PostgresIntegrationTest` and `CreateWalkInVisitT043PostgresIntegrationTest`. Exact final totals and task trace are recorded in `sdd/reviews/trace-feat-002-appointment-booking-T029-A1.md`.
- Independent review: first pass requested PostgreSQL rollback/race coverage and an explicit clinical writer lock protocol. Added those tests and contract requirements; final independent re-review passed with no remaining code blocker. Owner Long's full local PostgreSQL suite supplies the required runtime evidence; T029 is complete.

### Action Record — Regenerate TASKS

- Authorized scope: user yêu cầu sửa TASKS theo review findings và PLAN approved.
- Shadow Plan: inspect approved inputs/rules -> phase/owner/dependency decomposition -> AC mapping -> document verification.
- File boundary: TASKS.md only.
- Result: TASKS v0.2.0 thay backlog cũ, không thực thi task hay thay approval các nguồn.
- Verification: documentation-only consistency check of phase/task dependencies, deliverables and acceptance mappings; no code or tests were run for this TASKS-only regeneration.
- Residual gate: execution evidence của tất cả tasks vẫn pending; approval này không đánh dấu implementation hoàn thành.

### Historical AI Agent Recommendation — TASKS v0.2.0

- Status: APPROVED
- Recommendation: TASKS v0.2.0 was approved by Owner Long for sequential execution; no task implementation was then confirmed complete. This recommendation is historical and is superseded by the v0.3.0 review below.

### Historical Human Final Review — TASKS v0.2.0

- Status: APPROVED
- Decision: APPROVE
- Reviewer: Owner Long
- Reviewed at: 2026-10-13 (Asia/Bangkok)
- Follow-up: Approval này chỉ áp dụng cho TASKS v0.2.0 theo PLAN v0.3.0. TASKS v0.3.0 dưới đây là đề xuất chưa được phê duyệt; production booking chỉ bật sau các gate T031–T039 đạt.

### Human Review — TASKS v0.3.0 (dependency-cycle resolution)

- Change requested by Owner Long: resolve the apparent T010↔T032 delivery loop so owner foundations can be completed before protected-mutation integration.
- Version/status: v0.3.0 APPROVED. The v0.2.0 approval above remains historical; this approval governs future task preflights under the revised exit criteria.
- Scope: T010/T011/T012 foundation exits; T017 foundation checkpoint; T032 as the downstream protected-mutation integration consumer. Task IDs/order and business requirements are otherwise retained.
- Approved dependency behavior: T010/T011 close after their reviewed schema/read contracts and fail-closed protected-write boundaries are complete. T012 closes its schema/read-contract foundation while check-in and active-care writers remain T027/T031 work. T017 verifies clean/upgrade data preservation and owner schema/contract readiness without waiting for T027/T031/T032. T032 consumes the closed T010/T011 foundations and enables only impact-checked protected operations after T024–T026/T031.
- Safety invariant: until T032 passes, no T010/T011 API may commit a service, assignment, publication, acceptance, account, shift or time-off mutation that makes a protected booking unserviceable. Rejected/withheld capability is not represented as a production success stub.
- No completion change: T010–T012 and T017 checkboxes remain unchanged. This approval does not approve any implementation, contract shape, migration, endpoint, or task completion.
- Review artifact: `sdd/reviews/task-contract-review-feat-002-appointment-booking-TASKS-v0.3.0.md`.
- Human decision: `APPROVE TASKS v0.3.0 dependency and exit-criteria revision`.
- Reviewer: Owner Long.
- Reviewed at: 2026-10-14 (Asia/Bangkok; decision received in conversation).

### Historical AI Agent Recommendation — TASKS v0.3.0

- Status: APPROVED.
- Recommendation: the explicit foundation exits and downstream protected-mutation gate are approved for future task preflights.
- Validation: dependency references were inspected for T010–T018 and T024–T035; T032 remains downstream through T024–T026 and T031, with no reciprocal T032 prerequisite in the proposed T010/T011/T012/T017 exits.
- Human decision received: `APPROVE TASKS v0.3.0 dependency and exit-criteria revision`.

### Historical Human Final Review — TASKS v0.3.0

- Status: APPROVED.
- Decision: APPROVE TASKS v0.3.0 dependency and exit-criteria revision.
- Reviewer: Owner Long.
- Reviewed at: 2026-10-14 (Asia/Bangkok; decision received in conversation).
- Follow-up: re-preflight named T010/T011/T012 foundations, then T017 and T018. Prior BLOCKED attempts remain historical; approval alone does not complete or execute tasks.

## Change Impact Record
- Changed intent, requirement, architecture, or task assumption: Added T043 for the Visit WALK_IN API and made Appointment T028 depend on it; existing D11 intent remains unchanged. T043 also requires the separately Human-approved additive append-only Visit audit migration and `checked_in_at = arrived_at` timestamp rule.
- Feature Lock boundary affected: Add Visit-owned HTTP endpoint and JPA service/repository write; T028 remains NO_SHOW-only in Appointment. No cross-feature persistence access or Appointment-owned WALK_IN route.
- Material state-change classification: public API/shared contract; business-data mutation/audit/replay; transaction/coordination guard.
- Downstream artifacts invalidated: SPEC v0.9.0, PLAN v0.7.0, Visit contract v1.2.0, T028/T043 preflight, API/OpenAPI, tests and AC trace.
- Required trace, test, and sync actions: Verify T043 after T042 against the task-bound focused command and approved local PostgreSQL integration route; prove API security, replay, same-patient reference, exact returned response, audit migration/append-only enforcement, `checked_in_at = arrived_at`, rollback and no appointment mutation. T034/T035 include T043/AC24; update shared contract and trace after delivery.
- Review follow-up: Human review SPEC v0.9.0, PLAN v0.7.0, TASKS v0.6.0 and Visit contract v1.2.0. Persist the T043 material-state checkpoint before source edits; execution grants remain separate.

## Historical AI Agent Recommendation — TASKS v0.6.0

- Status: PENDING HUMAN REVIEW — alignment with SPEC v0.9.2 and PLAN v0.5.3
- Scope: FEAT-002 TASKS v0.6.0 — T043 Visit WALK_IN API and T028 dependency
- Recommendation: Approve TASKS v0.6.0 with separate Visit API delivery T043 and Appointment NO_SHOW T028 dependent on T043. Visit owns the new HTTP write and its JPA persistence; Appointment owns only NO_SHOW and arrival evidence checks. No Appointment service writes Visit data.
- Evidence: Human approved the proposed Visit API shape in the current conversation; SPEC v0.9.0 adds REQ-BOOK-061/AC24; PLAN v0.7.0 places the endpoint in Visit and requires JPA owner repositories; T042 is the persistence-standardization prerequisite; T027 is complete and T028 remains unchecked.
- Risks and assumptions: Exact idempotent replay returns the original 201 creation response. T043 requires a new additive Flyway migration because V007/V008 have no dedicated Visit audit table; preflight must allocate the migration version and verify the local database route/capability, append-only enforcement, replay schema and coordination wiring. WALK_IN sets `checked_in_at = arrived_at` using the same server Clock instant. New endpoint is a public API/business-data mutation and requires a persisted Human checkpoint before source edits.
- Alternatives considered: Fold the Visit API into Appointment T028 (rejected: mixed ownership and no-show/WALK_IN have separate authorization/state semantics); implement a Visit HTTP API as T043, then run T028 (recommended); leave late WALK_IN without API (rejected after Human approved the API scope).
- Required human decision: Approve TASKS v0.6.0 and its T043→T028 prerequisite after reviewing SPEC v0.9.0/PLAN v0.7.0 and contract v1.2.0. Approval does not itself grant either task execution.

## Historical Human Final Review — TASKS v0.6.0

- Status: PENDING
- Decision:
- Reviewer:
- Reviewed at:
- Follow-up:
- Review boundary: TASKS v0.6.0, including T043 additive migration/checkpoint and T028 dependency.

## Current Handoff State

### Execution Record — E-feat-002-appointment-booking-T018-A2

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T018`; resumed after T017 completion and final approval, 2026-10-14 (Asia/Bangkok).
- Governance: canonical `Project Ownership: team`, `Agent Execution: direct`, `shared_context.md` v3.3.3; runtime identity/enforcement UNVERIFIED.
- Feature/selection/state: T018 only; dependencies T006–T017 complete; implementation, trace, contract, and test run approved by Owner Long; initial Spring constructor-selection failure fixed with `@Autowired` and passed follow-up review; post-fix suite passed 135/135; T018 is `[x]`.
- Prior attempt: A1 BLOCKED before grant, implementation, command, or DB action. No previous grant is reused.
- Grant: `G-feat-002-T018-A2`, attempt 2, direct route, consumer `codex-direct-session-T018-A2` (cooperative reference; no host execution claim); consumed before source edits.
- Approved inputs: TASKS v0.3.0, CONTEXT v0.16.0, SPEC v0.8.0, PLAN v0.4.0; frozen T010/T011/T012 contracts and T017 completion evidence.
- Shadow plan: add a pure-Java slot engine for clinic-local date ranges/grid alignment and duration+buffer fit; compose current-service and appointment-snapshot contexts; define bounded neutral read ports and owner adapters for shifts/time off, occupied appointments, and active care reservations; exclude only the current appointment from its own occupancy query; add focused unit/PostgreSQL tests; document the additive integration contract; get independent code review before asking for test approval; run through the approved local helper; finalize trace and close after Human approval.
- File boundary: `appointment/**` new slot engine/query use case/persistence; additive neutral query contracts and owning Staff/Visit query adapters; focused T018 unit/PostgreSQL tests; `shared_context.md` additive port contract record; architecture docs and T018 review/trace/record. No schema migration, API endpoint, security workflow, dependency upgrade, or production/Supabase access.
- Checkpoint: any PostgreSQL verification must use only `127.0.0.1:5432/otcms_test_db` via `scripts/test-local.ps1`; test fixtures use unique IDs and clean up their own rows. No `.env` or credential inspection.
- Exact approved commands: from `backend/`, `./mvnw test` via `scripts/test-local.ps1`; Architecture Profile also approves `./mvnw clean verify` if required. Initial run exposed Spring constructor-selection failure; production constructor was annotated `@Autowired` and independently reviewed. Post-fix user run on 2026-10-14 passed 135 tests, 0 failures, 0 errors, 0 skipped in 1:12; `VisitT012PostgresIntegrationTest` passed 3/3.

### Execution Record — E-feat-002-appointment-booking-T019-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T019`; 2026-10-14 (Asia/Bangkok).
- Governance: canonical `Project Ownership: team`, `Agent Execution: direct`, `shared_context.md` v3.3.6; runtime identity/enforcement UNVERIFIED.
- Feature/selection/state: T019 only; T018 complete; COMPLETE after Owner Long approved implementation, trace, and frozen provider-query contract on 2026-10-14.
- Grant: `G-feat-002-T019-A1`, attempt 1, direct route, consumer `codex-direct-session-T019-A1` (cooperative reference; no host execution claim); consumed before source edits.
- Approved inputs: TASKS v0.3.0; CONTEXT v0.16.0; SPEC v0.8.0; PLAN v0.4.0; frozen T010 clinic catalog, T011 staff directory, and T018 slot read contracts.
- Shadow plan: reuse T010 catalog and T018 slot engine; add a minimal Staff-owned projection of accepting active-directory Doctors and a Service/Catalog-owned active-assignment ID query; compose public/staff eligibility in Appointment; enforce required date range, configured clinic horizon, public notice/staff non-past filters, and actor permissions; return slots/items envelopes without PII or unavailability reasons; add unit and local PostgreSQL HTTP coverage; independent review before test approval; run approved local helper only after approval.
- File boundary: new Appointment availability application/use case/web DTO/controller/error mapping and focused tests; additive Staff/Service assignment read ports and owner adapters; public GET security matcher; update `shared_context.md`, `docs/architecture.md`, T019 review/trace and this record. No schema migration, public rate limiter (T021), reschedule/reassignment (T020), booking command, owner write, or production database access.
- Checkpoint: shared provider-query ports v1.0.0 approved and frozen by Owner Long; public API outputs follow SPEC §4.1–4.2; no active service/eligible Doctor returns HTTP 200 with `{slots: []}`; explicit ineligible service/Doctor returns stable eligibility error.
- Exact approved command: from `backend/`, `./scripts/test-local.ps1` → `./mvnw.cmd test`, using only `127.0.0.1:5432/otcms_test_db`. Owner-provided run on 2026-10-14: 146 tests, 0 failures, 0 errors, 0 skipped; 1:13 total, including Visit T012 integration test 3/3. Earlier failures and fixes are recorded in the T019 review. Owner Long approved implementation, trace, and froze the provider-query contract on 2026-10-14; T019 is `[x]`.

### Execution Record — E-feat-002-appointment-booking-T010-A3

- Coordinator: current Codex Agent, `/add-execute` resumed after Owner Long checkpoint, 2026-10-14 (Asia/Bangkok).
- Project ownership: team.
- Agent execution: direct.
- Governance resolution: canonical `backend/sdd/shared_context.md` v3.3.2; invocation axis overrides: none.
- Feature/selection/state: `feat-002-appointment-booking`, T010 only; COMPLETED after Owner Long approved implementation and trace on 2026-10-14.
- Classification: sequential-handoff; Service/Catalog owner contract and its API enforcement change require a serialized update.
- Prior attempts: A1 PARTIAL with consumed grant; A2 BLOCKED before grant issuance. Neither grant is reused.
- Task grant: `G-feat-002-T010-A3`; attempt 3, direct route, state CONSUMED; consumer `codex-direct-session-T010-A3` (cooperative reference; no host enforcement claim).
- Grant transition: matching A3 grant was persisted DISPATCHED/UNCONSUMED and consumed by this direct session before any T010 contract or source edit. A1 remains consumed/retired; A2 had no grant.
- Runtime identity/enforcement: UNVERIFIED; no host-controlled execution claim is available in this session.
- Dependencies/contract: T002, T006 and T009 complete; TASKS v0.3.0 APPROVED. Owner Long explicitly approved `clinic-service-catalog` v1.0.0 shape and the T010 checkpoint on 2026-10-14. `staff-doctor-directory` v1.0.0 is frozen; T032 impact-aware mutation integration remains downstream.
- Profile/checkpoint/commands: Architecture Profile v1.3.0; `backend/.\\mvnw.cmd test` and `backend/.\\mvnw.cmd clean verify` approved. Checkpoint: service deactivate must fail closed before any success absent impact verification; assignment removal stays withheld until T032. No production/Supabase access.
- SHADOW PLAN: (1) persist approval and freeze `clinic-service-catalog` v1.0.0; (2) make the existing Admin deactivate use case reject safely until T032, preserving the service row/state; (3) add unit and local PostgreSQL HTTP regression coverage; (4) run focused test then full local suite through the secure helper; (5) refresh T010 trace/post-code review. No assignment mutation endpoint will be introduced.
- Owned file boundary: `backend/sdd/shared_context.md`; servicecatalog deactivate handler and exception mapping; `ClinicServiceHandlerTest`; `ClinicServicePostgresIntegrationTest`; T010 TASKS/review evidence. No migration, schema, unrelated owner module, or frontend changes.
- Implementation: `DeactivateClinicServiceHandler` validates ID, acquires the coordination guard, verifies service existence, and fails closed with `SERVICE_DEACTIVATION_REQUIRES_IMPACT_CHECK` without saving. Error mapping returns HTTP 409. Existing identity/history/snapshots are preserved; no assignment mutation endpoint was added. Contract `clinic-service-catalog` v1.0.0 is frozen under the explicit checkpoint above.
- Focused verification: from `backend`, ` .\mvnw.cmd '-Dtest=ClinicServiceHandlerTest' test` completed BUILD SUCCESS; 4 tests, 0 failures/errors/skips (2026-10-14 01:10:51 +07:00).
- Independent code review: `/root/t010_code_review` found no correctness issues; the initially identified catalog test gap was closed by adding inactive online-bookable exclusion to the public catalog integration test and an authenticated Receptionist staff catalog test for active online/staff-only inclusion and inactive exclusion. Follow-up review found no findings. Reviewer did not run tests.
- Full local validation attempt: initial .\scripts\test-local.ps1` invocation in this non-interactive process could not receive secure credential prompts and was interrupted before DB inspection/Maven; no credential was read or echoed. Subsequently, user-provided terminal output (2026-10-14 01:14:11 +07:00) reports `BUILD SUCCESS`, 124 tests, 0 failures, 0 errors, 0 skipped; `VisitT012PostgresIntegrationTest` passed 2/2. This is recorded as user-reported full-suite evidence; the excerpt does not separately identify `ClinicServicePostgresIntegrationTest`.
- Disposition: `COMPLETED` — fail-closed service deactivation implementation, independent code review, focused unit verification (4/4), user-reported full-suite validation (124/124), and Owner Long approval of implementation and trace on 2026-10-14. T010 is `[x]`. T032 remains responsible for enabling impact-aware deactivation and assignment removal; no assignment mutation endpoint is exposed meanwhile.
- Human review evidence: Owner Long decision `APPROVE implementation and trace T010` received 2026-10-14 and recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T010-A3.md` and `sdd/reviews/trace-feat-002-appointment-booking-T010-A3.md`.

### T011 Foundation Closeout — E-feat-002-appointment-booking-T011-A2

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T011`; scope evaluated under the Owner Long-approved TASKS v0.3.0 foundation exit, 2026-10-14 (Asia/Bangkok).
- Governance: `team` / `direct`, resolved from `backend/sdd/shared_context.md` v3.3.2. Runtime identity/enforcement UNVERIFIED; no host-controlled execution claim.
- State: T011 foundation COMPLETED and `[x]`. No implementation or migration changes were required; A1 code remains the implementation under review.
- Dependencies: T002, T006 and T009 complete. T014 shared coordination guard is complete. Contract `staff-doctor-directory` v1.0.0 is frozen; T032 remains the downstream protected-mutation integration task.
- Approved exit: Doctor profile/provisioning, directory and eligibility read contracts, shifts/time-off schema and interval projections, Admin authorization, and separate new-booking eligibility from existing-booking serviceability. Booking-impacting publication/acceptance/account/schedule/time-off writes remain unavailable pending required T014/T032 wiring and impact checks.
- Scope review: V005/V006 and Staff feature implementation match the approved exit. Doctor provisioning links only an existing DOCTOR account; new profiles default unpublished and not accepting bookings. Public directory omits account/work-contact/eligibility details. Doctor self/Admin bounded shift reads are authorized. Assignment FK is restrictive. The existing contract/projection keeps public visibility, booking acceptance, directory status, shift coverage and time-off distinct.
- Protected-write safety: no publication, acceptance, directory-state, shift, time-off, or account-status mutation endpoint is exposed. Provisioning creates a new profile with visibility/acceptance disabled and does not create assignment/appointment links. V008 requires an existing Doctor-service assignment for appointment references, so provisioning alone cannot change serviceability of a protected appointment.
- Verification evidence: prior T011 local PostgreSQL execution passed 93/93 tests, including `DoctorProfileT011PostgresIntegrationTest` 3/3 with clean V001→V006 migrations. The subsequent user-provided full-suite output on 2026-10-14 01:14:11 +07 reports 124 tests, 0 failures/errors/skips (BUILD SUCCESS). No new test run was requested or performed for this documentation-only closeout.
- Independent review: fresh read-only v0.3.0 alignment review found no concrete defect. The initial provisioning concern was withdrawn after reviewer verified defaults and V006/V008 constraints; no files or tests were changed by reviewer.
- Human approval basis: Owner Long approved T011 implementation and trace on 2026-10-13 (A1 review artifacts); Owner Long approved TASKS v0.3.0 foundation exit on 2026-10-14. Implementation files are unchanged; the revised task contract explicitly defers impact-sensitive mutations to T032.
- Disposition: COMPLETED for the v0.3.0 foundation exit. T032 still owns protected mutation enablement; this closeout does not claim those writes are implemented or safe to enable.
- Review evidence: `sdd/reviews/post-code-feat-002-appointment-booking-T011.md` and `sdd/reviews/trace-feat-002-appointment-booking-T011.md` include the A2 closeout addendum.

### T012 Execution Record — E-feat-002-appointment-booking-T012-A2

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T012`, 2026-10-14 (Asia/Bangkok).
- Governance: `team` / `direct`, resolved from `backend/sdd/shared_context.md` v3.3.2. Runtime identity/enforcement UNVERIFIED.
- Feature/selection/state: T012 only; BLOCKED during preflight pending a shared-port ownership/shape decision. T012 remains unchecked.
- Dependencies: T002, T007 and T009 complete; T010, T011 and T013 complete; T014 shared guard complete. `visit-reception-clinical` v1.0.0 is frozen and previously approved.
- Existing evidence: V007 owner schema, VisitWorkflowPort adapter, and prior approved implementation/trace. Prior local tests 95/95 with T012 PostgreSQL 2/2; later user-provided full suite 124/124 and T012 PostgreSQL 2/2. No test was run in this preflight.
- Review blocker: fresh independent review found `ReceptionWorkflowPort.requireRecordedArrival(...)` declared but unbound. It is a read operation in T012's owner read-contract foundation; T027 owns the atomic check-in writer. Avoid a no-op adapter or prematurely implementing the writer.
- Decision needed: (1) approve splitting a read-only `RecordedArrivalQueryPort` from T027's `ReceptionWorkflowPort.checkInAtomically` writer and update the frozen contract to v1.1.0; or (2) amend T012's exit to defer the executable arrival reader explicitly to T027. Details in `sdd/reviews/preflight-feat-002-appointment-booking-T012-A2.md`.
- Grant: none issued or consumed. No source/schema changes, test command, production/Supabase access, or credential inspection occurred.
- Disposition: BLOCKED before implementation pending explicit contract-owner/task-exit decision. Prior A1 grant remains consumed and is not reused.

### T012 Execution Record — E-feat-002-appointment-booking-T012-A3

- Invocation: continuation of `/add-execute --feature=feat-002-appointment-booking --task=T012` after Owner Long selected “Split ports; T012 implements reader” on 2026-10-14 (Asia/Bangkok).
- Governance: `team` / `direct`, resolved from `backend/sdd/shared_context.md` v3.3.3. Runtime identity/enforcement UNVERIFIED; no host-controlled execution claim.
- Feature/selection/state: T012 only; COMPLETED after Owner Long approved implementation and trace on 2026-10-14.
- Dependencies: T002, T007, T009, T010, T011, T013 and T014 complete. `visit-reception-clinical` v1.0.0 was previously frozen; user-approved A3 split is now frozen as v1.1.0.
- Contract/checkpoint: `RecordedArrivalQueryPort` owns only the recorded-arrival read; `ReceptionWorkflowPort.checkInAtomically` remains the T027 writer. T031 care-capacity/overrun scope remains deferred.
- Shadow Plan: update the frozen contract; bind a read-only persistence query with exact appointment/patient match and inclusive caller-supplied window; add PostgreSQL regression cases for boundary success and fail-closed mismatches; obtain independent review; request implementation approval before testing; after approval run focused and local full suite, then refresh trace/closeout.
- Owned boundary: `backend/sdd/shared_context.md`; integration contract ports; Visit persistence adapter; `VisitT012PostgresIntegrationTest`; T012 execution and review evidence. No migration, endpoint, check-in writer, T031 behavior or production database change.
- Implemented: new `RecordedArrivalQueryPort`; removed its read operation from `ReceptionWorkflowPort`, leaving `checkInAtomically`; added `RecordedArrivalQueryPersistenceAdapter` that validates input, matches appointment and patient, applies inclusive bounds and fails closed; added exact-boundary, wrong-patient, out-of-window and invalid-window integration assertions. V007/V008 unchanged.
- Independent review: no blocking findings. Reviewer noted null arguments are not asserted by a dedicated case; implementation rejects them. Reviewer did not run tests.
- Approval: Owner Long approved `APPROVE T012 A3 implementation for testing` on 2026-10-14.
- Verification: ` .\mvnw.cmd -DskipTests test-compile` completed BUILD SUCCESS (155 main/39 test sources compiled; 2026-10-14 11:36:50 +07). Initial helper attempt could not accept secure prompts and was interrupted before DB inspection/Maven; no credentials were read and no DB connection/mutation occurred. Subsequent user-provided local output (2026-10-14 11:44:54 +07) reports BUILD SUCCESS, 125 tests, 0 failures/errors/skips; `VisitT012PostgresIntegrationTest` passed 3/3, including A3 regression. Earlier 124/124 is historical.
- Execution-record limitation: no distinct A3 task-grant lifecycle was persisted before the source edits; user explicitly authorized the contract/implementation direction, but this record does not claim host-level grant enforcement or replay protection. Runtime identity remains UNVERIFIED.
- Final human decision: Owner Long approved `APPROVE implementation and trace T012` on 2026-10-14; recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T012-A3.md` and `sdd/reviews/trace-feat-002-appointment-booking-T012-A3.md`.
- Disposition: COMPLETED for the TASKS v0.3.0 foundation exit. T012 is `[x]`; T027 remains responsible for atomic arrival/check-in/walk-in writers and T031 for care-capacity/overrun behavior.

### Execution Record — E-feat-002-appointment-booking-T017-A2

- Invocation: \`/add-execute --feature=feat-002-appointment-booking --task=T017\`; fresh preflight after T010–T012 closure, 2026-10-14 (Asia/Bangkok).
- Governance: \`team\` / \`direct\`, canonical \`backend/sdd/shared_context.md\` v3.3.3. Runtime identity/enforcement UNVERIFIED.
- Feature/selection/state: T017 only; dependencies T006–T016 are complete; implementation and verification complete, awaiting Human final approval. T017 remains unchecked pending that approval.
- Prior attempt: A1 was BLOCKED before grant or task action. No prior grant is reused.
- Execution grant: \`G-feat-002-T017-A2\`, attempt 2, direct route, consumer \`codex-direct-session-T017-A2\` (cooperative reference; host enforcement not claimed). State CONSUMED before source/test edits.
- Approved checkpoint: test only against dedicated local PostgreSQL database \`127.0.0.1:5432/otcms_test_db\`. T017 creates unique temporary schemas, runs a fresh empty-schema Flyway V001→V013 install and a V001-with-data→V013 upgrade, then drops only those schemas. Review found V013 may also install database-scoped \`btree_gist\` in this dedicated DB; that extension persists and will not be dropped. Owner Long explicitly approved this disclosed local test-DB side effect on 2026-10-14. No application/production/Supabase database access and no edits to existing migrations.
- Immutable input: approved TASKS v0.3.0 foundation checkpoint; T006–T016 exit evidence; V001 historical SHA-256 \`9a616dcb1d8ffbf007d6af3e8fd1fa3f1eb97c60294e1d5d5fe7ba37802d35a6\`; Architecture Profile v1.3.0 approved \`.\mvnw.cmd test\` and \`.\scripts\test-local.ps1\`.
- Shadow Plan: add a PostgreSQL-only checkpoint test for a fresh empty-schema V001→V013 install (Flyway `migrate()`, no `clean()`); upgrade a schema from V001 containing account/profile/external-identity/consent rows and verify preserved values plus V001 checksum; assert final Flyway history, owner FK/exclusion constraints, and schema mapping; document current guarded writers and future-writer admission/lock order; obtain independent review; run local suite after review/approval; refresh trace and close only after evidence.
- Owned boundary: new \`T017PersistenceCheckpointPostgresIntegrationTest\`; T017 TASKS execution record; T017 post-code/trace review artifacts; no production code, migrations, config, shared contract or production DB changes.
- Verification attempt 1: user-run \`backend/.\\scripts/test-local.ps1\` confirmed PostgreSQL 18.1, \`btree_gist\` available and installed, and then ran 127 tests. Result: 2 failures, both T017 current Flyway version assertions expected \`13\` while Flyway returned zero-padded \`013\`; 125 other tests passed, no errors/skips. No migration/data-preservation assertion after those failing version checks can be claimed from this run.
- Correction: changed the two current-version expectations to \`013\`; independent read-only review confirmed the Flyway target argument \`13\` remains correct and no other expectation needs changing. The user-approved local test scope remains in effect. Await rerun output before claiming verification or closing T017.
- Verification attempt 2: user-run full suite again reported 127 tests, 2 failures, 0 errors/skips. Both failures were test-only definition-fragment checks: PostgreSQL emitted FK target relations qualified by each generated temporary schema. The upgrade test passed version, source hash/Flyway checksum stability, migration history, account/profile/external-identity/consent preservation checks before failing at its final FK assertion; the clean-schema test applied V001–V013 and passed earlier table/current-version/history checks before its FK assertion.
- Correction: the FK assertion helper now verifies each FK references the generated schema and strips only that known schema prefix before comparing expected table/column definitions. Independent read-only review confirmed this preserves schema isolation validation. Await rerun output; no T017 completion claim until all assertions pass.
- Verification attempt 3: user-provided `backend/.\scripts/test-local.ps1` output reports PostgreSQL 18.1; Tests run 127, Failures 0, Errors 0, Skipped 0; BUILD SUCCESS; total time 01:08 min. This includes both T017 PostgreSQL checkpoint tests; the clean V001→V013 path, V001-with-data upgrade, V001 checksum/data preservation, and final FK/unique/exclusion assertions all passed.
- Final review: independent reviewer confirmed the version and schema-qualified FK assertion corrections. `post-code-feat-002-appointment-booking-T017-A2.md` and `trace-feat-002-appointment-booking-T017-A2.md` record the final evidence; checkbox closure followed Human final approval below.
- Human final approval: Owner Long approved `APPROVE implementation and trace T017` on 2026-10-14.
- Disposition: COMPLETE for the TASKS v0.3.0 persistence foundation checkpoint. T017 is `[x]`; T018 may proceed. Runtime cross-module races and rollback/failure injection remain T033/T035 as planned.

### Execution Record — E-feat-002-appointment-booking-T010-A2

- Coordinator: current Codex Agent, `/add-execute` preflight, 2026-10-14 (Asia/Bangkok).
- Project ownership: team.
- Agent execution: direct.
- Governance resolution: canonical `backend/sdd/shared_context.md` v3.3.2; invocation axis overrides: none.
- Feature/selection/state: `feat-002-appointment-booking`, T010 only; BLOCKED at preflight under approved TASKS v0.3.0. T010 marker remains unchecked; no code edit or verification command performed.
- Prior attempt: A1 remains PARTIAL with its grant consumed. A2 is a fresh preflight under the approved revised task contract; no A1 grant was reused.
- Task grant: none issued; consumption N/A because contract/checkpoint preconditions failed.
- Runtime identity/enforcement: UNVERIFIED; no host-controlled execution claim is available in this session.
- Dependencies: T002, T006 and T009 are complete. `clinic-service-catalog` v1.0.0 remains DRAFT; T010 exit requires an approved stable owner contract.
- Safety finding: read-only review found `DeactivateClinicServiceHandler` sets `active=false` under the coordination guard but does not query protected booking impact. Prior soft-delete tests prove identity/history preservation, not T010 v0.3 protected-booking serviceability. Current endpoint behavior must be explicitly gated or made impact-aware before claiming this exit.
- Profile/checkpoint/commands: approved profile commands are `backend/.\\mvnw.cmd test` and `backend/.\\mvnw.cmd clean verify`; neither was run because the contract shape and T010 endpoint checkpoint are not persisted.
- Preflight review: `sdd/reviews/preflight-feat-002-appointment-booking-T010-A2.md` records blockers and required decisions.
- Required resolution: approve the catalog owner contract and exact deactivate safety boundary, then rerun T010 preflight. No migration, production/Supabase access, credential inspection or `.env` read occurred.

### Task Contract Revision Record — TASKS v0.3.0

- Request: Owner Long asked to revise TASKS to break the T010/T032 dependency loop and provide a review draft.
- Result: v0.3.0 edits are limited to `TASKS.md`; T010/T011/T012/T017/T032 exits distinguish owner foundation from later protected workflow integration. Owner Long approved the revision on 2026-10-14. Their checkboxes remain unchanged.
- Safety: protected mutations that could make an existing booking unserviceable remain withheld/fail-closed until T032 passes. T017 no longer requires future runtime writers to be delivered; runtime races stay assigned to T033/T035.
- Review status: APPROVED by Owner Long on 2026-10-14. The v0.2.0 approval remains historical. No task grant, implementation, migration, test command, endpoint change or production database access occurred.
- Review artifact: `sdd/reviews/task-contract-review-feat-002-appointment-booking-TASKS-v0.3.0.md`.

### Execution Record — E-feat-002-appointment-booking-T018-A1

- Coordinator: current Codex Agent, `/add-execute` preflight, 2026-10-14 (Asia/Bangkok).
- Project ownership: team.
- Agent execution: direct.
- Governance resolution: canonical `backend/sdd/shared_context.md` v3.3.2; invocation axis overrides: none.
- Feature/selection/state: `feat-002-appointment-booking`, T018 only; BLOCKED at preflight. Task marker unchanged; no implementation or database/test action performed.
- Task grant: none issued; consumption N/A because T017 dependency failed. No grant may be inferred or reused.
- Runtime identity/enforcement: UNVERIFIED; no host-controlled execution claim is available in this session.
- Dependency: T017 remains unchecked and `E-feat-002-appointment-booking-T017-A1` is BLOCKED; required clean/upgrade-with-data and cross-module atomicity/lock-order evidence is not persisted.
- Profile/checkpoint/commands: Architecture Profile approves `backend/.\\mvnw.cmd test` and `backend/.\\mvnw.cmd clean verify`; neither was run. T018's implementation and exact verification must wait for T017 completion.
- Preflight review: `sdd/reviews/preflight-feat-002-appointment-booking-T018.md` records the dependency gate and required resolution.
- Required resolution: close T017 with its required evidence and task review, then rerun T018 preflight. No source/schema/database changes, production/Supabase access, or credential inspection occurred.

### Execution Record — E-feat-002-appointment-booking-T017-A1

- Coordinator: current Codex Agent, `/add-execute` preflight, 2026-10-14 (Asia/Bangkok).
- Project ownership: team.
- Agent execution: direct.
- Governance resolution: canonical `backend/sdd/shared_context.md` v3.3.2; invocation axis overrides: none.
- Feature/selection/state: `feat-002-appointment-booking`, T017 only; BLOCKED at preflight. Task marker unchanged; no implementation or database/test action performed.
- Task grant: none issued; consumption N/A because dependency and checkpoint preflight failed. No grant may be inferred or reused.
- Runtime identity/enforcement: UNVERIFIED; no host-controlled execution claim is available in this session.
- Dependencies: T006–T009 and T013–T016 are marked complete. T010, T011, and T012 remain unchecked; T010's catalog contract remains DRAFT and T010/T011 records retain partial/deferred delivery scope. T012 has frozen contract and prior local evidence but has not been closed in the task table.
- Profile/checkpoint/commands: approved profile commands are `backend/.\\mvnw.cmd test` and `backend/.\\mvnw.cmd clean verify`; neither was run. T017 still needs persisted clean-V001/upgrade-with-data and cross-module atomicity/lock-order exit evidence; no T017-specific checkpoint or human approval was found.
- Preflight review: `sdd/reviews/preflight-feat-002-appointment-booking-T017.md` records dependency and contract findings and required resolution.
- Required resolution: close T010–T012 with their required evidence/contract decisions or approve an updated dependency/exit contract; persist T017's local-only verification checkpoint; then rerun named T017 preflight. No production/Supabase access or credential inspection occurred.

### Execution Record — E-feat-002-appointment-booking-T016-A1

- Coordinator: current Codex Agent, `/add-execute`, 2026-10-14 (Asia/Bangkok).
- Project ownership: team.
- Agent execution: direct.
- Governance resolution: canonical `backend/sdd/shared_context.md` v3.3.2; invocation axis overrides: none.
- Feature/selection/state: `feat-002-appointment-booking`, T016 only; COMPLETED after Owner Long approved implementation and trace on 2026-10-14.
- Task execution grant: `G-feat-002-T016-A1`, attempt 1, route direct, state RUNNING/CONSUMED; consumer reference `codex-direct-session-T016-A1` (cooperative record only; no host enforcement claim).
- Runtime identity/enforcement: UNVERIFIED; no host-controlled execution claim is available in this session.
- Ownership/frozen contract: T016 appointment configuration boundary; no frozen shared contract change. Dependencies T003 and T009 are complete.
- Profile/checkpoint/commands: Architecture Profile v1.3.0 approves `backend/.\mvnw.cmd test`; T016 acceptance is directly covered by `.\mvnw.cmd '-Dtest=AppointmentBookingPropertiesTest' test`. Runtime configuration is the approved T016 material scope in TASKS v0.2.0; no additional business or shared-contract decision was introduced.
- Shadow Plan: bind the nine SPEC §3.1 settings as typed config; source defaults only from `application.yml`; add startup validation and injectable UTC Clock; verify actual YAML defaults, overrides, accepted zeros, invalid startup values, and deterministic Clock fixture; keep edits within backend config/test/example and T016 evidence files.
- Action Record: added `AppointmentBookingProperties`/configuration, nine YAML default/override keys, non-secret `.env.example` entries, and `AppointmentBookingPropertiesTest`; no database/schema/public API/shared contract changed. Post-code and trace drafts are in linked T016 review files.
- Verification: `.\mvnw.cmd '-Dtest=AppointmentBookingPropertiesTest' test` passed 3 tests, 0 failures/errors/skips; 153 production and 39 test sources compiled. Owner-provided `scripts/test-local.ps1` result for the approved full backend suite: `BUILD SUCCESS`, 123 tests, 0 failures, 0 errors, 0 skipped; finished 2026-10-14 00:40:17 +07. The local PostgreSQL-backed suite completed with Hibernate validation. No credential or `.env` content was read by this agent; no production/Supabase database was accessed.
- Human review: Owner Long sent `APPROVE implementation and trace T016` on 2026-10-14; recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T016.md` and `sdd/reviews/trace-feat-002-appointment-booking-T016.md`.
- Results/integration/blocker/sync-back: implementation, verification, requirement trace, and human review complete; no API, schema, frozen contract, or architecture documentation sync required. Production/Supabase rollout remains outside T016.

### Execution Record — E-feat-002-appointment-booking-T006-A1

- Coordinator: current Codex Agent, `/add-execute` preflight, 2026-10-13 (Asia/Bangkok).
- Project ownership: team.
- Agent execution: orchestrated.
- Governance resolution: canonical `sdd/shared_context.md` v2.0.0; invocation axis overrides: none.
- Feature: feat-002-appointment-booking.
- Selection/tasks/state: task=T006; ordered tasks: T006; BLOCKED at preflight; task marker unchanged.
- Classification: blocked; Auth/security-owned sequential migration/compatibility work.
- Task execution grants: none issued; consumption N/A because preflight failed.
- Host execution-claim evidence: UNVERIFIED; no matching host-controlled claim observed.
- Ownership and frozen contracts: Auth owns role/JWT/principal/session/portal compatibility; auth-persistence-schema v1.0.0 is FROZEN and V001 immutable. Exact T006 file boundary and shared compatibility checkpoint remain unresolved.
- Profile/checkpoint/commands: Architecture Profile v1.3.0; `.\mvnw.cmd test` and `.\mvnw.cmd clean verify` approved; neither executed. T004 exit evidence present. T006 authoritative per-account mapping/security/schema checkpoint not found in reviewed artifacts.
- Runtime identity evidence: UNVERIFIED; Codex collaboration tools are available, but the required Claude Code `Agent` tool is absent from this session.
- Runtime enforcement evidence: UNVERIFIED; Markdown/YAML are cooperative policy evidence only.
- Worker/host task references: unavailable; no worker launched, no fallback to direct.
- Attempt and retry counters: preflight attempt 1; implementation attempts 0; consecutive implementation failures 0.
- Integration state: not started.
- Residual blockers: orchestrated runtime eligibility; reviewed STAFF inventory/mapping; bounded T006 security/schema/shared-contract checkpoint.
- Review evidence: `sdd/reviews/preflight-feat-002-appointment-booking-T006.md`, PENDING HUMAN REVIEW.

### Action Record — T006 preflight A1

- Actor: current Codex coordinator.
- Approved scope and file boundary: read-only preflight/static auth inventory; additive execution evidence in this TASKS.md and `sdd/reviews/preflight-feat-002-appointment-booking-T006.md`.
- Profile binding and exact commands: Java21/Spring Security/JPA/Flyway/PostgreSQL profile; Maven verification not run because execution prerequisites failed.
- State-change category: none for implementation; documentation evidence only.
- Human checkpoint: TASKS v0.2.0 approved; material T006 security/schema checkpoint unresolved.
- Task execution grant: N/A; no grant issued or consumed at failed preflight.
- Host execution-claim evidence: UNVERIFIED.
- Actions and result: reviewed governance, task/dependency and role compatibility touchpoints; persisted BLOCKED evidence. No source/test/migration/config/DB changes, worker launch, build/test, commit or push.
- Validation route: static preflight only; implementation lint/audit/trace/sync deferred until authorized execution.
- Post-code review: N/A; no implementation delivery.
- Residual blocker: see Execution Record and linked preflight report.
- Sync-back decision: preserve approved baseline content and task checkbox; append handoff evidence only. Resume after runtime and checkpoint disposition; do not claim completion or Human approval.

### Execution Record — E-feat-002-appointment-booking-T006-A2

- Coordinator: current Codex Agent, `/add-execute --resume` preflight, 2026-10-13 (Asia/Bangkok).
- Project ownership: team.
- Agent execution: direct.
- Governance resolution: canonical `sdd/shared_context.md` header currently says `direct`; A1 is immutable historical evidence of the earlier `orchestrated` resolution. No invocation override.
- Feature: feat-002-appointment-booking.
- Selection/tasks/state: task=T006; ordered tasks: T006; BLOCKED during resume preflight; task marker unchanged.
- Classification: blocked; auth/security migration and compatibility work.
- Task execution grants: none issued; consumption N/A because T006 checkpoint/mapping precondition failed.
- Host execution-claim evidence: UNVERIFIED; no host-controlled claim observed.
- Ownership and frozen contracts: Auth owns role/JWT/principal/session/portal compatibility; auth-persistence-schema v1.0.0 is FROZEN and V001 immutable. Exact reviewed file boundary/checkpoint and authoritative account mappings remain absent.
- Profile/checkpoint/commands: Architecture Profile v1.3.0; approved Maven test/package commands remain available. No T006 implementation command or material-state checkpoint is approved by this preflight.
- Runtime identity evidence: UNVERIFIED. Runtime route resolves to direct from the current canonical header; this is not host identity evidence.
- Runtime enforcement evidence: UNVERIFIED.
- Worker/host task references: direct/no worker; no host task reference.
- Attempt and retry counters: resume preflight attempt 2; implementation attempts 0; consecutive implementation failures 0.
- Integration state: not started.
- Residual blockers: persisted checkpoint with authoritative per-account mapping for legacy STAFF rows (or verified zero-row inventory), plus bounded exact file boundary and compatibility approval.
- Review evidence: `sdd/reviews/preflight-feat-002-appointment-booking-T006.md`, PENDING HUMAN REVIEW; prior A1 record retained unchanged.

### Action Record — T006 resume preflight A2

- Actor: current Codex coordinator.
- Approved scope and file boundary: revalidate governance and T006 persisted approvals; append this execution evidence only.
- Profile binding and exact commands: Java 21/Spring Security/JPA/Flyway/PostgreSQL profile; no Maven command run because task-specific checkpoint is missing.
- State-change category: documentation evidence only.
- Human checkpoint: no persisted T006 checkpoint found for STAFF inventory/mapping and security/schema compatibility.
- Task execution grant: N/A; no grant issued or consumed at failed preflight.
- Host execution-claim evidence: UNVERIFIED.
- Actions and result: confirmed canonical route now resolves to `direct`; confirmed T006 remains unchecked and per-account STAFF mapping/checkpoint remains unresolved. No source, test, migration, config, or DB changes; no build/test, commit, or push.
- Validation route: static preflight; implementation review/trace/sync deferred.
- Post-code review: N/A; no implementation delivery.
- Residual blocker: authoritative STAFF mapping or verified zero-row inventory and persisted bounded T006 checkpoint.
- Sync-back decision: record current route while retaining A1 as historical evidence; do not mark T006 complete.
- User evidence addendum: User stated no accounts currently exist in the database. This is not independently verified; target environment was not specified. Resume preflight report records the statement and remaining demo-seeder decision. Zero existing-account mappings are applicable only if the statement covers the migration target.

### Execution Record — E-feat-002-appointment-booking-T006-A3

- Coordinator: current Codex Agent, `/add-execute --resume`, 2026-10-13 (Asia/Bangkok).
- Project ownership: team.
- Agent execution: direct.
- Governance resolution: canonical `sdd/shared_context.md`; route resolved as direct. No invocation override.
- Feature: feat-002-appointment-booking.
- Selection/tasks/state: task=T006; RUNNING after checkpoint and grant consumption.
- Classification: single-owned auth/security implementation with additive persistence migration.
- Task execution grant: T006; grant ID `G-T006-A3-OTCMS-20261013-01`; attempt 3; route direct; task grant RUNNING; consumer `C-T006-A3-OTCMS-CURRENT-SESSION`; consumption CONSUMED before Shadow Plan and edits. Cooperative record only.
- Host execution-claim evidence: UNVERIFIED; no host-controlled atomic claim observed.
- Ownership and frozen contracts: Auth compatibility touchpoints owned by this task; preserve frozen auth persistence baseline and V001. No change to appointment-side shared contracts.
- Profile/checkpoint/commands: checkpoint and bounded file boundary recorded in `sdd/reviews/preflight-feat-002-appointment-booking-T006.md`; Java21. Exact verification: `.\mvnw.cmd test` from `backend/`.
- Runtime identity evidence: UNVERIFIED; direct route is persisted policy, not host identity evidence.
- Runtime enforcement evidence: UNVERIFIED.
- Worker/host task references: direct/no worker.
- Attempt and retry counters: implementation attempt 1; retries 0.
- Integration state: in progress.
- Residual blockers: verification, independent post-code review and SDD trace/sync remain.

### Shadow Plan — T006 A3

- Intent/DoD: replace generic STAFF with explicit approved account types while preserving staff portal restrictions; zero existing-account mapping is user-reported; demo staff account becomes RECEPTIONIST.
- File boundary: `AccountType.java`; auth login/recovery/session/JWT touchpoints; `DemoAccountSeeder.java`; focused auth tests; additive Flyway migration only. Preserve V001. No frontend, appointment code, dependencies, Supabase, or production DB.
- State-change category: authorization/security and persistence schema.
- Checkpoint: approved bounded scope and choices in linked preflight report.
- Verification: user-run `backend/scripts/test-local.ps1` reported BUILD SUCCESS, 73 tests, 0 failures/errors/skips. Flyway V001→V002 succeeded on local `otcms_test_db`; details in the T006 review and trace. No production/Supabase access.
- Risks: legacy STAFF rows must cause migration failure; every explicit staff type must retain staff-network policy; patient portal must remain patient-only.
- Trace/sync: update T006 execution evidence and route requirement trace/sync after implementation/review.

### Action Record — T006 implementation A3

- Actor: current Codex coordinator.
- Approved scope and file boundary: per Shadow Plan above.
- Profile binding and exact commands: approved Java21 Maven test command `.\mvnw.cmd test` in `backend/`.
- State-change category: authorization/security and persistence schema.
- Human checkpoint: explicit user choices and bounded checkpoint persisted in linked preflight report.
- Task execution grant: `G-T006-A3-OTCMS-20261013-01`; direct; consumer `C-T006-A3-OTCMS-CURRENT-SESSION`; consumed before source edits; host claim UNVERIFIED.
- Actions and result: implementation in progress.
- Validation route: pending exact Maven test.
- Post-code review: required; pending.
- Residual blocker: tests/review/trace/sync.
- Sync-back decision: pending evidence; do not mark T006 complete yet.

### Execution update — E-feat-002-appointment-booking-T006-A3

- Current state: BLOCKED pending local PostgreSQL test credentials and execution of Flyway V002 against the dedicated `otcms_test_db`.
- Source edits: explicit `AccountType` values, shared staff-portal login/recovery matching, staff refresh-network classification, RECEPTIONIST demo seed, additive V002 guard/constraint, and focused tests. V001 was not edited. No production/Supabase DB was contacted.
- Verification command: `./mvnw.cmd '-Dtest=MarkAppointmentNoShowT028Test,MarkAppointmentNoShowT028PostgresIntegrationTest' test`. Owner ran `backend/scripts/test-local.ps1` against the dedicated local PostgreSQL test DB; no production/Supabase access.
- Migration syntax: corrected PL/pgSQL block terminator by static review after the test attempt; DB execution is still required.
- Static checks: `git diff --check` on the T006 Java/source/test/docs paths passed. A repository-wide check reports pre-existing trailing whitespace in `.gitignore`, outside this task.
- Residual blockers: run `backend/scripts/test-local.ps1` interactively with local test DB credentials, review Flyway V002 success and PostgreSQL integration results, then complete independent post-code review and SDD trace/sync. Do not mark T006 complete before these gates pass.
- Validation addendum (user-provided terminal output, 2026-10-13): `backend/scripts/test-local.ps1` reported PostgreSQL 18.1, btree_gist available/installed, role can CREATE. `.\mvnw.cmd test` returned BUILD SUCCESS: 73 tests, 0 failures, 0 errors, 0 skipped. Flyway validated 2 migrations, started at V001 and successfully applied V002 to `127.0.0.1:5432/otcms_test_db`; Hibernate validation and application context succeeded. Credentials were masked and not recorded. No Supabase/production DB was contacted.
- Current T006 state: implementation and local verification complete; independent post-code Human review and SDD trace/sync are prepared/pending. Keep checkbox unchecked until review and sync decisions are recorded.
- Follow-up to the preceding status: the user supplied `test-local.ps1` output confirming V002 applied and all 73 tests passed. The earlier missing-credentials blocker is resolved for the local test database. Current remaining gates are Human post-code review, trace disposition, and auth persistence contract owner review/sync proposal. Production/Supabase inventory remains outside this execution.
- Human decision received: `APPROVE implementation and trace` (current user, 2026-10-13 Asia/Bangkok); recorded in the post-code and trace review blocks. Remaining gate: auth persistence contract owner decision on `sync-feat-002-appointment-booking-T006.md`. T006 checkbox stays unchecked until contract sync-back is resolved.
- Human decision received: `APPROVE sync` (current user, 2026-10-13 Asia/Bangkok); recorded in the sync review. `shared_context.md` auth-persistence-schema advanced to v1.1.0; sync-back completed. Reviewer display name/role was not exposed by the session.
- T006 completion: `[x]` after approved post-code review, approved requirement trace, approved auth contract sync, local PostgreSQL V001→V002 migration and `.\mvnw.cmd test` BUILD SUCCESS (73 tests, 0 failures, 0 errors, 0 skipped). Residual deployment gate: production/Supabase STAFF inventory and migration are not part of this task execution and remain untouched.
- Completion disposition: Execution Record A3 is COMPLETED. Human review decisions for implementation, trace and shared-contract sync are recorded in their respective reports; `shared_context.md` v2.1.0/auth-persistence-schema v1.1.0 is synced. T006 remains complete for local implementation/readiness; production migration requires its own account inventory and deployment checkpoint.

### T007 Human Checkpoint and Contract Decision — 2026-10-13

- Migration decision: APPROVED by Owner Long, 2026-10-13. Add only a new Flyway migration after verifying the next version; preserve V001 and V002. Convert `patient_profiles` to an independent UUID `id`; retain nullable unique `account_id` FK to `accounts(id)`; backfill each existing profile with a new UUID while preserving all profile data and account links. Do not merge by name/phone or delete/change history.
- Database boundary: only `127.0.0.1:5432/otcms_test_db`; no Supabase/production connection. Stop on unsafe backfill data or schema/version drift.
- Contract decision: APPROVED, version 1.0.0, by Owner Long on 2026-10-13. Persisted as `patient-profile-search-create` in `shared_context.md` v2.2.0. Receptionist nonblank query, normalized full-name/canonical-phone matching, bounded pagination and minimal projection; no unfiltered listing. Receptionist may create no-account profile with fullName, phone, phoneOwnerType, dateOfBirth, gender. Independent profile UUID, nullable account link; no auto-merge; backend authorization required.
- Exact verification command: Maven Wrapper test from the backend working directory, per Architecture Profile. Required evidence includes Flyway clean install and upgrade with existing data, Hibernate validation, and registration/search/create coverage.
- Status: checkpoint and contract approved before implementation; A1 retired and A2 completed per final disposition below.

### Execution Record — E-feat-002-appointment-booking-T007-A1

- Coordinator/executor: current Codex session; date 2026-10-13 (Asia/Bangkok).
- Governance: `team` / `direct`, resolved from `backend/sdd/shared_context.md` v2.2.0. Host execution identity/enforcement: UNVERIFIED.
- Selection/classification: T007 only; sequential-handoff because it changes Patient-owned persistence/public contract consumed downstream.
- Immutable inputs: CONTEXT v0.15.0, SPEC v0.7.0, PLAN v0.3.0, TASKS v0.2.0 (all approved); T002/T004 phase-0 evidence; T007 checkpoint and patient-profile-search-create v1.0.0 decisions above; auth-persistence-schema v1.1.0.
- Grant: `G-feat-002-T007-A1`, state DISPATCHED then CONSUMED before implementation. Consumer: `codex-direct-session-T007-A1` (cooperative reference; no host claim).
- Owned boundary: `backend/src/main/resources/db/migration/V003__independent_patient_profile_identity.sql`; `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/auth/entity/PatientProfile.java`; auth registration/Google profile persistence and auth repository boundary; new `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/patient/**`; focused patient/auth tests; this T007 evidence and approved patient contract. No V001/V002 edits, dependencies, Supabase, appointment module or unrelated auth behavior.
- State change/checkpoint: additive schema/backfill and patient API; checkpoint APPROVED by Owner Long in `T007 Human Checkpoint and Contract Decision` above, local `127.0.0.1:5432/otcms_test_db` only.
- Exact command: from `backend`, ` .\mvnw.cmd test` (display whitespace excluded), approved Profile command. Runtime migration version must be confirmed as V002 before applying V003.
- Changed files: `V003__independent_patient_profile_identity.sql`; Patient domain/application/web/persistence adapter; auth PatientProfile JPA mapping/repository persistence facade; registration profile link; focused PatientProfile service tests; approved SDD checkpoint/contract evidence.
- Static validation at A1: `git diff --check` passed for then-tracked T007 edits. Initial Maven invocation lacked local DB credentials; later corrected in A2 as recorded below.
- Database safety: migration inventory statically shows V001 and V002 only; runtime Flyway head could not be re-confirmed in this session. V003 has not been applied. No Supabase/production connection was attempted.
- A1 disposition: RETRY_PENDING after local test defects; superseded by A2.
- Superseded A1 residual gates: local test rerun, V003 clean/upgrade/backfill and Hibernate validation, registration/search/create, review, trace, sync; all were closed under A2 as detailed below.
- A1 verification update: second Maven invocation compiled all 85 production and 24 test sources but could not start PostgreSQL contexts in the Codex shell; corrected tests and authenticated local verification were completed under A2.
- Latest local validation (user-provided `scripts/test-local.ps1` output, 2026-10-13): connection was pinned to `127.0.0.1:5432/otcms_test_db`; Flyway confirmed current version 002, applied V003 successfully, and Hibernate initialized/validated the schema. T004 PostgreSQL integration tests passed (5 tests). Suite summary: 78 tests, 0 assertion failures, 2 errors. The two errors were isolated to new T007 tests: missing JUnit assertion import in the HTTP integration test and a Mockito stub that did not match null phone-search term.
- Fixes after that run (validated in A2): added the missing assertion import; changed repository mock to accept nullable phone term; changed MockMvc authentication to request post-processors; added local temporary-schema tests for clean V001→V003 migration and V002→V003 data backfill/preservation.
- Attempt A1 disposition: `RETRY_PENDING` for the two bounded test implementation defects. The A1 grant is consumed/retired and must not be reused. No V003 migration was applied more than once; the local database is now at V003. Supabase/production remains untouched.

### Execution Record — E-feat-002-appointment-booking-T007-A2

- Invocation: named `T007 --retry`; new attempt after A1 `RETRY_PENDING`. No scope, checkpoint, contract, profile binding, or command changes.
- Governance: `team` / `direct` from shared_context v2.2.0; host execution identity/enforcement UNVERIFIED.
- Classification: sequential retry, implementation defect only, same approved T007 file boundary.
- Immutable inputs/checkpoint: same CONTEXT/SPEC/PLAN/TASKS approvals, T002/T004 evidence, T007 migration checkpoint and patient-profile-search-create v1.0.0 approval. Local DB verified at V003 by user-provided log; do not rerun V003 on the local DB.
- Grant: `G-feat-002-T007-A2`, DISPATCHED then CONSUMED before retry action. Consumer: `codex-direct-session-T007-A2` (cooperative reference; no host claim). A1 remains retired and cannot be reused.
- Changed after A1: two bounded test fixes and local temporary-schema migration tests, all within the prior test boundary.
- Exact verification command: `backend` working directory, Architecture Profile approved Maven Wrapper `test` command. Database stays pinned to local `127.0.0.1:5432/otcms_test_db`; Supabase/production forbidden.
- Verification update (2026-10-13): Codex-shell exact `.\\mvnw.cmd test` compiled all 85 main and 24 test sources; DB-backed execution was blocked there because local credentials were not available. Follow-up user-run `scripts/test-local.ps1` on the pinned local DB reports `BUILD SUCCESS`: 80 tests, 0 failures, 0 errors, 0 skipped; `PatientProfileT007PostgresIntegrationTest` passed all 4 tests, including its clean-install and V002-upgrade fixtures. Combined with the prior local-helper output confirming Flyway V003 and Hibernate validation, runtime verification for this local checkpoint is passed.
- Static validation: all main/test sources compile; `git diff --check` reports only pre-existing/unrelated `.gitignore:28` trailing whitespace. `git diff --check` does not cover untracked T007 files, so inspect them directly before closeout.
- Human decisions received (2026-10-13): `APPROVE implementation and trace` by Owner Long; `APPROVE evidence-only sync` by Owner Long acting as Patient contract owner. Reviews and sync evidence are recorded in the three linked reports; shared_context v2.3.0 retains patient contract v1.0.0 unchanged.
- Attempt A2 disposition: COMPLETED for local implementation/readiness after user-provided `scripts/test-local.ps1` BUILD SUCCESS (80 tests, 0 failures/errors/skips), T007 integration tests 4/4, V003 clean and upgrade tests, Flyway V003 and Hibernate validation, approved post-code review/trace and approved evidence-only sync. Production/Supabase migration or rollout is explicitly outside this task and remains a separate checkpoint.
- T007 status: `[x]` — local implementation, verification, review, trace, and shared-contract evidence sync complete. No production database was accessed or changed.

### T020 Execution Record — E-feat-002-appointment-booking-T020-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T020`.
- Dependencies: T018 slot engine/read projections and T019 public/staff availability are recorded complete; approved T018 shared read contract remains frozen. T020 consumes those boundaries without changing their shapes.
- Scope: appointment-scoped reschedule and reassignment availability GET endpoints only. No command, schema migration, or production database access.
- Implemented: same-Doctor reschedule uses Patient self-profile authorization or Receptionist scope, CONFIRMED state, old Patient deadline/new notice, current assignment and stored service/duration/buffer snapshots; it excludes only the appointment being changed and permits the established inactive-service/removed-provider existing-booking exception. Receptionist reassignment requires CONFIRMED and start >= Clock now, intersects accepting active-directory providers with active service assignments, excludes current Doctor, and evaluates the original slot with the same stored snapshots. Appointment rows are read through an Appointment-owned projection; other feature data stays behind public ports.
- Tests: focused `GetAppointmentChangeAvailabilityHandlerTest` passed 5/5. Owner-provided `scripts/test-local.ps1` result on 2026-10-14 after adding dedicated HTTP assertions: `BUILD SUCCESS`, 155 tests, 0 failures/errors/skips; `VisitT012PostgresIntegrationTest` passed 3/3. This successful local PostgreSQL run supersedes the earlier shell authentication failure. No production/Supabase connection was attempted.
- Review: independent review found SPEC error-code mismatches and one unused dependency; these were corrected. No remaining concrete blocker was reported for the reviewed application/security/slot paths. Owner Long approved implementation and trace on 2026-10-14 with the instruction to add dedicated HTTP assertions for both T020 routes; those assertions are now present. Evidence: `sdd/reviews/post-code-feat-002-appointment-booking-T020-A1.md`.
- Trace: `sdd/traces/trace-feat-002-appointment-booking-T020-A1.md` maps T020/REQ-BOOK criteria and dedicated HTTP assertion coverage.
- Status: COMPLETE. Owner Long approved implementation and trace on 2026-10-14; dedicated PostgreSQL HTTP assertions and full local suite passed 155/155, including Visit T012 3/3. T020 is `[x]`.

### T021 Execution Record — E-feat-002-appointment-booking-T021-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T021`; direct execution continuation, 2026-10-14 (Asia/Bangkok).
- Governance: `Project Ownership: team`, `Agent Execution: direct` from `shared_context.md` v3.3.7; runtime identity/enforcement UNVERIFIED.
- Feature/selection/state: T021 only; dependencies T016 and T019 are recorded complete. COMPLETE after Owner Long approved implementation/trace and accepted the missing persisted task-grant evidence as a process exception on 2026-10-14.
- Grant evidence: a persisted T021 task-grant/consumer record was not present when this continuation was resumed. No retroactive grant-consumption or host execution-enforcement claim is made; record this governance gap for Owner review.
- Approved inputs: TASKS v0.3.0, CONTEXT v0.16.0, SPEC v0.8.0, PLAN v0.4.0; T019 public catalog/availability routes and approved Architecture Profile Maven Wrapper/local PostgreSQL test binding.
- Shadow plan: resolve client IP only from peer or forwarding headers received from configured trusted proxies; rate-limit public catalog and availability independently per IP/window; inject Clock and ceil Retry-After; cap cleanup work; return generic correlated 429; add unit and PostgreSQL HTTP coverage; obtain post-fix independent review; run the focused and secure local database suites; finish trace/review and await Human approval.
- File boundary: Appointment public web rate limiter, trusted proxy resolver, typed booking configuration and YAML sample; focused unit and T021 PostgreSQL HTTP tests; T021 preflight/review/trace and execution record. No schema migration, shared contract change, distributed store, production/Supabase access, or unrelated FEAT-002 implementation.
- Implementation: the initial independent review identified an unbounded auxiliary expiry queue under distinct-IP churn and anonymous HEAD rejection in Spring Security. Replaced the queue with bounded map scans (at most 64 inspected entries per pass); allowed anonymous HEAD through Security/CORS for both limited public endpoints. Dedicated HEAD assertions were added to unit and PostgreSQL HTTP tests.
- Verification: focused Maven suite passed 12/12 (2026-10-14 18:58:47 +07:00). User-provided `scripts/test-local.ps1` result reports `BUILD SUCCESS`, 166 tests, 0 failures/errors/skips at 2026-10-14 19:06:02 +07:00; `VisitT012PostgresIntegrationTest` passed 3/3. Surefire evidence confirms `PublicRequestRateLimitT021PostgresIntegrationTest` passed 2/2 against the dedicated local PostgreSQL test DB. No production/Supabase connection was used.
- Review/trace: initial review findings were corrected. Post-fix independent code review and debugger review found no concrete residual findings; test-evidence review confirmed the T021 integration report and suite totals. Evidence is in `sdd/reviews/post-code-feat-002-appointment-booking-T021-A1.md` and `sdd/traces/trace-feat-002-appointment-booking-T021-A1.md`. Owner Long approval remains pending.
- Checkpoint: only `127.0.0.1:5432/otcms_test_db` via `scripts/test-local.ps1` may be used for PostgreSQL verification. Never access production/Supabase. Deployment assumes one app instance unless a shared limiter receives separate review.
- Human decision: `APPROVE implementation and trace T021; accept the recorded missing task-grant evidence as a process exception.` Reviewer: Owner Long. Received 2026-10-14 (Asia/Bangkok).
- Disposition: COMPLETE — implementation, focused tests (12/12), dedicated PostgreSQL HTTP tests (2/2), user-provided local full suite (166/166), independent post-fix review, trace, and Human approval are recorded. The grant-evidence exception is accepted by Owner Long; no retroactive grant-consumption claim is made. T021 is `[x]`.

### Execution Record — E-feat-002-appointment-booking-T022-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T022`; direct route, 2026-10-14 (Asia/Bangkok).
- Governance: canonical `Project Ownership: team`, `Agent Execution: direct`, `shared_context.md` v3.3.7 base, extended by the approved v3.3.8 Auth contract. Runtime identity/host enforcement UNVERIFIED.
- Feature/selection/state: T022 only; T008 and T014–T021 are checked complete. State: COMPLETE after local PostgreSQL/full suite, independent review and Owner Long approval.
- Grant: `G-feat-002-T022-A1`, attempt 1, direct route, consumer `codex-direct-session-T022-A1` (cooperative reference; no host replay-prevention claim). Status: DISPATCHED → CONSUMED before the first source edit; runtime host replay enforcement remains UNVERIFIED.
- Approved inputs: TASKS v0.3.0, CONTEXT v0.16.0, SPEC v0.8.0, PLAN v0.4.0, T009 neutral integration ports v1.0.0, T018 slot availability projections v1.0.0, frozen T010/T011/T019 owner contracts, and T014/T015/T016 execution evidence.
- Shadow Plan and scout: `sdd/reviews/preflight-feat-002-appointment-booking-T022-A1.md`. Implement Patient-online and Receptionist-assisted create only; derive source and creator from authenticated role; obtain current authorization/owner projections after T014 guard; enforce service/provider/time/grid/availability; insert appointment and append-only `CREATED` audit in one transaction; return approved 201/Location DTO; add unit and local PostgreSQL HTTP/concurrency tests; review and trace before Human closure.
- File boundary: Appointment create domain/application/HTTP/JDBC adapter and focused tests; task evidence under `sdd/reviews/`, `sdd/traces/`, and this execution record. No owner repository/table access, frozen contract changes, applied migration edits, follow-up (T030), other lifecycle commands, production/Supabase access, or frontend work. Existing V008/V013 schema is expected to suffice; verify before any migration proposal.
- Checkpoint: approved T022 contract requires no additional business/API/schema decision. If implementation needs a new shared contract or material SPEC/schema change, stop and request the relevant owner review before changing that boundary.
- Exact verification: focused `.\mvnw.cmd '-Dtest=CreateAppointmentHandlerTest,AppointmentBookingT022PostgresIntegrationTest' test`; full local verification only via `.\scripts\test-local.ps1` in `backend`, pinned to `127.0.0.1:5432/otcms_test_db`. No credential inspection/output.
- Implementation: added the Appointment create command/domain record, current-profile adapter through Auth's public directory API, parameterized JDBC insert plus `CREATED` audit in one transaction, strict POST DTO parsing and unknown-field rejection, actor-derived source/creator, correlated error envelope, and response/Location. No migration or frozen contract changed. Added the Boot 4 Jackson starter after compilation showed no JSON databind dependency; corrected initial namespace/API issues. Fixed zero-buffer validation after independent review confirmed zero is valid in the slot/schema contracts.
- Initial verification snapshot (superseded by final closure below): main/test sources compiled and handler unit tests passed 3/3. At that point the T022 PostgreSQL suite had not yet run. Initial compile failures due to absent Boot 4 JSON databind and Jackson 3 API differences were corrected before the passing compile/test-compile.
- Validation checkpoint (superseded by Final verification below): `mvnw.cmd -DskipTests test-compile` passed at 19:30:31 +07:00; focused unit suite passed 3/3 at 19:27:50 +07:00. The PostgreSQL HTTP/full suite was pending at that checkpoint.
- Focused handler tests rerun after final purpose normalization at 2026-10-14 19:31:20 +07:00: 3 run, 0 failures/errors/skips. The independent reviewer confirmed the normalization follow-up introduces no issue. Post-code review and trace were refreshed with this result.
- Integration hardening checkpoint: added independent-thread same-slot race assertions (exactly one HTTP 201 and one 409, one appointment and one CREATED audit), plus a forced audit-trigger failure test requiring HTTP 500 or propagation of the forced DB error and asserting appointment/audit absence. Fixture setup commits before worker requests; cleanup is transactional, handles partial setup, and restores the immutable-audit trigger's original PostgreSQL mode. `-DskipTests test-compile` passed at 19:35:33 +07:00; the then-pending PostgreSQL execution is superseded by Final verification below.
- Final verification: user-run approved `scripts/test-local.ps1` completed with BUILD SUCCESS at 2026-10-14 19:40:22 +07:00: 174 tests, 0 failures/errors/skips; Visit T012 passed 3/3. Surefire confirms `AppointmentBookingT022PostgresIntegrationTest` 5/5 and `CreateAppointmentHandlerTest` 3/3. Aggregating 49 Surefire XML reports yields 174 tests, 0 failures/errors/skips; newest report timestamp is 19:40:22 +07:00. Independent code, debugger and test-evidence reviews found no blocking issue. Owner Long approved `APPROVE implementation and trace T022` on 2026-10-14.
- Human decision: `APPROVE implementation and trace T022` — Owner Long, received 2026-10-14 (Asia/Bangkok).
- Disposition: COMPLETE — implementation, unit suite (3/3), PostgreSQL HTTP integration (5/5, including concurrency and audit rollback), user-run local full suite (174/174), independent review, trace and Human approval are recorded. No production/Supabase access, migration or frozen-contract change. T022 is `[x]`.

### T023 Execution Record — E-feat-002-appointment-booking-T023-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T023`; direct route, 2026-10-14 (Asia/Bangkok).
- Governance: `Project Ownership: team`, `Agent Execution: direct`, `shared_context.md` v3.3.7 base, extended by the approved v3.3.8 Auth contract. Runtime identity/host enforcement UNVERIFIED.
- Selection: T023 scoped appointment list/detail only; dependencies T008 and T013 complete, with T014–T022 complete. Existing scope/date/API decisions remain frozen.
- Grant: `G-feat-002-T023-A1`, attempt 1, consumer `codex-direct-session-T023-A1` (cooperative reference; no host replay-prevention claim). Preflight dispatch persisted; grant consumed immediately before source edits. Runtime host enforcement UNVERIFIED. Owner Long approved the Auth access-port checkpoint on 2026-10-14. `doctor-account-access-read` v1.0.0 is recorded in shared_context.md v3.3.8. The grant scope is extended only for that additive read-only contract, its Auth persistence adapter, and focused tests; no schema or account mutation is included.
- Immutable inputs: TASKS v0.3.0, CONTEXT v0.16.0, SPEC v0.8.0, PLAN v0.4.0, Architecture Profile, current Patient authorization/current-profile public ports, frozen Staff doctor-directory contract, existing V008/V013 schema.
- Scope: Patient current linked profile only; Doctor currently assigned profile only; Receptionist clinic-wide; Admin/Owner and other excluded roles endpoint 403; authorized missing/out-of-scope detail returns indistinguishable 404. Optional clinic-local inclusive independent `from`/`to` filters, status and owner filters intersect actor scope; no history horizon; stable descending start-time/id paging. Exact request/response and safe correlation error contracts as SPEC §4.
- File boundary: Appointment application/web/JDBC query code and focused tests; T023 review/trace and this record. No non-Appointment table/repository reads, migrations, contract changes beyond the approved `doctor-account-access-read` v1.0.0 checkpoint, production/Supabase access, unrelated lifecycle writes, commit or push.
- Checkpoint: T023 API requirements were approved. Owner Long approved the additive Auth-owned `doctor-account-access-read` v1.0.0 checkpoint on 2026-10-14; no schema, account mutation or other API change is in scope.
- Verification: focused unit `./mvnw.cmd '-Dtest=GetAppointmentsHandlerT023Test' test`; all PostgreSQL HTTP/full verification only `./scripts/test-local.ps1` in backend, fixed local database `127.0.0.1:5432/otcms_test_db`; no credential inspection/output.
- State: implementation is present; grant consumed. Main/test sources compile and focused handler tests pass 6/6. First helper run found two PostgreSQL Instant bind errors in date filters; changed both binds to `Timestamp.from(...)` and independent review confirmed the fix. User-run approved `scripts/test-local.ps1` completed BUILD SUCCESS at 2026-10-14 20:57:05 +07:00: 186 tests, 0 failures/errors/skips; T023 PostgreSQL HTTP integration 6/6, handler unit 6/6. Flyway validated 13 migrations at v013. Aggregating 51 Surefire XML reports confirms 186/0/0/0; newest report timestamp 20:57:05 +07:00. Independent review found and prompted a fix for a detail-query TOCTOU; detail now uses one SQL-scoped read and the application handler uses repeatable-read transactions. Post-code review: `sdd/reviews/post-code-feat-002-appointment-booking-T023-A1.md`; trace: `sdd/traces/trace-feat-002-appointment-booking-T023-A1.md`. Independent implementation/debugger/fixture reviewers confirmed the scoped-read TOCTOU fix, Auth port, and fixtures. Verification and review are complete. Owner Long approved `APPROVE implementation and trace T023` on 2026-10-14 (Asia/Bangkok). Disposition: COMPLETE — implementation, compile, focused handler tests (6/6), dedicated PostgreSQL HTTP integration tests (6/6), user-run local full suite (186/186), independent reviews, trace, and Human approval are recorded. No schema migration or production/Supabase access. T023 is `[x]`.


- Human checkpoint extension: Owner Long approved adding an Auth-owned read-only account-access port for Doctor status checks on 2026-10-14, in response to the REQ-BOOK-004/D07 gap. This extends the task boundary to `integration/contract`, Auth-owned query adapter, shared_context v3.3.8, and focused tests; no Auth schema or account mutation changes are authorized.










### T024 Execution Record — E-feat-002-appointment-booking-T024-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T024`; direct route, 2026-10-14 (Asia/Bangkok).
- Governance: `Project Ownership: team`, `Agent Execution: direct`, `shared_context.md` v3.3.8. Runtime identity/host enforcement UNVERIFIED.
- Selection: T024 ordinary reschedule only; T020/T022 dependencies complete. T025 cancellation and T026 reassignment remain out of scope.
- Grant: `G-feat-002-T024-A1`, attempt 1, consumer `codex-direct-session-T024-A1` (cooperative reference; no host replay-prevention claim). Consumed before first T024 source edit; runtime enforcement UNVERIFIED.
- Immutable inputs: TASKS v0.3.0, CONTEXT v0.16.0, SPEC v0.8.0, PLAN v0.4.0, Architecture Profile, T020 availability behavior, T022 guarded transaction/audit pattern, T023 scoped DTO/error pattern, frozen Patient/Staff/Visit/CoordinationGuard contracts, V008/V013 schema.
- Scope and acceptance: Patient self-only and Receptionist CONFIRMED appointment reschedule; Patient old deadline/new notice, Receptionist required reason, horizon/grid/non-past/availability recheck; same appointment row and protected booking facts; stored time snapshots/self-exclusion; one atomic RESCHEDULED audit; failed command leaves old interval/reservation/row/audit intact; correlated API errors.
- File boundary and non-goals: as `sdd/reviews/preflight-feat-002-appointment-booking-T024-A1.md`; no other lifecycle command, schema or contract change, foreign persistence access, production/Supabase, frontend, commit or push.
- Checkpoint: approved requirements cover T024; no new human contract checkpoint is expected. Stop if a scope/contract/schema change is needed.
- Verification: focused unit `./mvnw.cmd '-Dtest=RescheduleAppointmentHandlerT024Test' test`; PostgreSQL HTTP/full suite only `./scripts/test-local.ps1` in backend, fixed to `127.0.0.1:5432/otcms_test_db`; do not inspect credentials.
- Implementation: Added `PATCH /api/v1/appointments/{id}/reschedule`, actor-derived Patient/Receptionist command, guard-first row lock, Patient current self-profile authorization, CONFIRMED/deadline/notice/horizon/grid/past checks, exact snapshot-based self-excluding availability recheck, same-row interval update and atomic RESCHEDULED audit. Database exclusion SQLSTATE 23P01 maps to correlated `SLOT_UNAVAILABLE`; shared guarded-database errors now retain the request correlation ID.
- Verification: Initial user run exposed source incompatibility in `CoordinationFailureExceptionHandlerTest` after the request-aware handler signature change. Added source-compatible direct-call overloads while keeping MVC request correlation. Focused run: `./mvnw.cmd '-Dtest=RescheduleAppointmentHandlerT024Test,CoordinationFailureExceptionHandlerTest' test` — BUILD SUCCESS, 5 tests, 0 failures/errors/skips (2026-10-14 21:32 +07:00). User's subsequent local helper run reported `otcms_test_db` PostgreSQL 18.1, Flyway v013 and BUILD SUCCESS with 188 tests (21:36); that run predated the T024 HTTP test. After adding `RescheduleAppointmentT024PostgresIntegrationTest`, all 55 test sources compiled and focused T024 unit tests passed 2/2 (21:38). Latest user-run `./scripts/test-local.ps1` on local PostgreSQL completed BUILD SUCCESS with 191 tests, 0 failures/errors/skips (2026-10-14 21:40 +07:00), including T024 PostgreSQL HTTP integration 3/3 and Visit T012 3/3. Added T024 unit assertions for guard-before-lock, self-profile authorization, snapshot/self-excluding slot request and required Receptionist reason. HTTP assertions cover success/audit, cross-profile denial, mandatory Receptionist reason and occupied-target immutability.
- Review/trace: Final implementation review completed; trace at `sdd/traces/trace-feat-002-appointment-booking-T024-A1.md`. Owner Long approved implementation and trace with `Ok` on 2026-10-14 (Asia/Bangkok).
- State: COMPLETE — implementation, focused tests, local PostgreSQL HTTP tests (3/3), full suite (191/191), trace and Owner Long approval recorded. T024 is `[x]`.

### T025 Execution Record — E-feat-002-appointment-booking-T025-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T025`; direct route, 2026-10-14 (Asia/Bangkok).
- Dependencies: T022 and T024 complete. V008/V013 already support cancellation audit and occupancy release. T029 remains incomplete.
- Grant: `G-feat-002-T025-A1`, attempt 1, direct cooperative reference; runtime replay enforcement unverified. Consumed before first T025 source edit.
- Scope and acceptance: per `sdd/reviews/preflight-feat-002-appointment-booking-T025-A1.md`; CONFIRMED-only Patient self/Receptionist cancellation. Checked-in branch withheld pending T029. No schema or cross-owner contract changes.
- Implementation: Added `POST /api/v1/appointments/{id}/cancel` for CONFIRMED only. Patient requires current self-profile authorization and satisfies the inclusive change deadline; Receptionist must provide a reason and bypasses the Patient deadline. Status update and immutable CANCELLED audit share one guarded transaction. CHECKED_IN remains APPOINTMENT_NOT_CHANGEABLE until T029 supplies the atomic visit/reception cancellation boundary.
- Verification: `./mvnw.cmd '-Dtest=CancelAppointmentHandlerT025Test' test` — BUILD SUCCESS, 3 tests, 0 failures/errors/skips (2026-10-14 21:47 +07:00); all 57 test sources compile. User-run `./scripts/test-local.ps1` completed BUILD SUCCESS with 197 tests, 0 failures/errors/skips (2026-10-14 21:49:54 +07:00); `CancelAppointmentT025PostgresIntegrationTest` passed 3/3, `CancelAppointmentHandlerT025Test` 3/3, Visit T012 3/3.
- Review/trace: Implementation review at `sdd/reviews/post-code-feat-002-appointment-booking-T025-A1.md`; trace at `sdd/traces/trace-feat-002-appointment-booking-T025-A1.md`. Owner Long approved implementation and trace on 2026-10-14 (Asia/Bangkok).
- State: COMPLETE — implementation, focused tests, PostgreSQL HTTP tests (3/3), local full suite (197/197), review, trace and Owner Long approval recorded. T025 is `[x]`. CHECKED_IN cancellation remains deferred to T029.

### T026 Execution Record — E-feat-002-appointment-booking-T026-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T026`; direct route, 2026-10-14 (Asia/Bangkok).
- Dependencies: T020 and T022 complete; T024 and T025 complete. T032 protected owner mutations remain downstream.
- Grant: `G-feat-002-T026-A1`, attempt 1, direct cooperative reference; runtime replay enforcement unverified. Consumed before first T026 source edit.
- Scope and acceptance: as `sdd/reviews/preflight-feat-002-appointment-booking-T026-A1.md`; Receptionist-only same-slot reassign of CONFIRMED appointment to a different eligible, accepting and available Doctor; mandatory reason; immutable failure; no schema/owner contract change.
- Implementation: Added Receptionist-only `POST /api/v1/appointments/{id}/reassign`, guard-first locked CONFIRMED transition, current-start boundary (`start == now` allowed), active accepting-directory and service-assignment checks, exact same-slot recheck using stored snapshots/self-exclusion, same-row Doctor update, and atomic `DOCTOR_REASSIGNED` audit. No schema or owner contract change. Reassignment endpoint does not require new-booking public visibility; the Staff projection still requires accepting and active-directory status.
- Verification: `./mvnw.cmd '-Dtest=ReassignAppointmentHandlerT026Test' test` — BUILD SUCCESS, 2 tests, 0 failures/errors/skips (2026-10-14 21:59 +07:00); all 59 test sources compile. User-run `./scripts/test-local.ps1` on local `otcms_test_db` completed BUILD SUCCESS with 202 tests, 0 failures/errors/skips (2026-10-14 22:03:30 +07:00); Surefire confirms `ReassignAppointmentT026PostgresIntegrationTest` 3/3, `ReassignAppointmentHandlerT026Test` 2/2, and Visit T012 3/3.
- Review and trace: post-code review at `sdd/reviews/post-code-feat-002-appointment-booking-T026-A1.md`; requirement trace at `sdd/traces/trace-feat-002-appointment-booking-T026-A1.md`. Owner Long approved implementation and trace on 2026-10-14 (Asia/Bangkok).
- State: COMPLETE — implementation, focused tests, PostgreSQL HTTP integration (3/3), full local suite (202/202), review, trace and Owner Long approval are recorded. T026 is `[x]`.

### T027 Execution Record — E-feat-002-appointment-booking-T027-A1

- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T027`; direct route, 2026-10-14 (Asia/Bangkok).
- Dependencies: T012–T017 and T022 complete; T027 is the first scheduled Appointment↔Visit atomic check-in workflow.
- Grant: `G-feat-002-T027-A1`, attempt 1, direct cooperative reference; runtime replay enforcement unverified. Consumed immediately before first T027 source edit.
- Scope and acceptance: as `sdd/reviews/preflight-feat-002-appointment-booking-T027-A1.md`; Receptionist check-in, inclusive configured window, atomic Visit/reception/appointment/audit/replay, authorized replay precedence, immutable rollback; no queue/migration/other lifecycle scope.
- Implementation: Added Receptionist-only check-in endpoint; Appointment guarded handler/persistence for replay, link/status/audit; Visit-owned scheduled Visit/initial reception persistence and evidence check; exact shared checked-in timestamp/window validation; CORS permits Idempotency-Key. No schema migration.
- Verification: `./mvnw.cmd '-Dtest=CheckInAppointmentHandlerT027Test' test` — BUILD SUCCESS, 4 tests, 0 failures/errors/skips (2026-10-14 22:23:14 +07:00); all 61 test sources compile. User-run `./scripts/test-local.ps1` completed BUILD SUCCESS with 211 tests, 0 failures/errors/skips (2026-10-14 22:28:21 +07:00); `CheckInAppointmentT027PostgresIntegrationTest` 5/5, `CheckInAppointmentHandlerT027Test` 4/4 and Visit T012 PostgreSQL integration 3/3.
- Review and trace: post-code review at `sdd/reviews/post-code-feat-002-appointment-booking-T027-A1.md`; trace at `sdd/traces/trace-feat-002-appointment-booking-T027-A1.md`. Owner Long approved implementation and trace on 2026-10-14 (Asia/Bangkok).
- State: COMPLETE — implementation, focused and PostgreSQL integration tests (5/5), full local suite (211/211), review, trace and Owner Long approval recorded. T027 is `[x]`.

## Current Handoff State

- Dependency graph audit — 2026-10-18 (v0.6.1): T028 now explicitly waits for T041–T043; T029 waits for architecture/persistence alignment T041/T042; T030/T031 wait for the WALK_IN API T043; T033 includes T043 in the integrated race/readiness gate. T030–T032 inherit the T041/T042 prerequisite through T029/T031. All 43 task IDs resolve and the graph has no dependency cycles. No source or test files were changed.
- Human review — Owner Long (2026-10-18): approved TASKS v0.6.1 and approved closing T041 and T042. Decisions were received in conversation; implementation, review, and runtime evidence is recorded above.
- Execution gate: T041/T042 complete; T043 dependency T042 is satisfied. SPEC v0.9.0, PLAN v0.7.0, Architecture Profile v1.4.0 and Visit contract v1.2.0 are approved; Owner Long approved the T043 checkpoint on 2026-10-18. Owner-supplied local PostgreSQL 18.1 evidence confirms V014, clean/upgrade migration checks, T043 focused cases, independent review fixes, and full suite BUILD SUCCESS (224 tests, 0 failures/errors/skips; finished 2026-10-18 23:07:06 +07). T043 is complete and `[x]`; see preflight, post-code review and trace records.

### T033 Action Record — E-feat-002-appointment-booking-T033-A1
- Actor: Current Agent under Owner Long approval recorded in `sdd/reviews/preflight-feat-002-appointment-booking-T033.md`.
- Approved scope and file boundary: Startup production-readiness gate, explicitly listed command endpoints, focused gate and PostgreSQL race tests, T033 task/contract/review/trace and post-code evidence. No migration, production/Supabase access, frontend, or queue/lab scope.
- Profile binding and exact commands: `./mvnw.cmd -DskipTests test-compile`; focused gate tests `./mvnw.cmd '-Dtest=ProductionReadinessGateTest,ProductionReadinessGateHttpTest' test`; local PostgreSQL verification `./scripts/test-local.ps1` (backend working directory), which runs full `mvnw.cmd test` against loopback `otcms_test_db` after credential prompt.
- State-change category: runtime configuration, permission/command availability behavior, and shared contract.
- Human checkpoint: `sdd/reviews/preflight-feat-002-appointment-booking-T033.md` APPROVED by Owner Long, 2026-10-20 Asia/Bangkok.
- Task execution grant: direct execution under explicit Owner Long approval in conversation; opaque grant/host claim unavailable; host execution enforcement UNVERIFIED.
- Host execution-claim evidence: UNVERIFIED.
- Actions and result: Gate implementation and unit/HTTP tests added. After resolving the Spring bean cycle, owner full suites passed 293/293 (11:03 and 14:52 +07) and 294/294 (15:27:57 +07, including the new booking-vs-care-start barrier). Independent review then found that exact `POST /api/v1/admin/clinic-services` bypassed the gate. Added exact-path protection and matcher/HTTP regressions; focused gate tests passed 5/5 at 15:31:11 +07, and independent re-review confirmed resolution. Owner Long's corrected-source local PostgreSQL full suite finished 2026-10-20 15:34:56 +07 with BUILD SUCCESS, 295 tests, 0 failures/errors/skips. Surefire confirms gate HTTP 2/2, gate unit 3/3, T031 care/capacity 7/7, T031 room Admin 2/2, T032 6/6, T028 4/4 and T043 8/8. The local database was PostgreSQL 18.1 at `127.0.0.1:5432/otcms_test_db`, Flyway V019. No patient-profile relink/update endpoint exists; that race is currently non-applicable unless such a writer is introduced.
- Validation route: Complete. Corrected-source local PostgreSQL full suite, independent post-code review, trace synchronization and Owner Long closure approval recorded.
- Post-code review: Independent finding fixed and re-reviewed; report `sdd/reviews/post-code-feat-002-appointment-booking-T033-A1.md`.
- Residual blocker: None within approved T033 scope. No patient-profile relink/update mutation endpoint exists; revisit the race matrix if that capability is introduced.
- Sync-back decision: Shared contract v1.6.0, corrected-source full-suite evidence, independent review report, trace and Owner Long closure approval synchronized. T033 is complete.

### T034 Action Record — E-feat-002-appointment-booking-T034-A1 (complete)
- Actor: Current Agent under Owner Long's explicit test-only scope approval received in conversation on 2026-10-20 (Asia/Bangkok).
- Approved boundary: Three supplemental appointment service tests, AC01–AC24 evidence trace, and T034 review/action records. No production code, API/contract, schema, frontend, dependencies, or production/Supabase access.
- Grant/enforcement: Conversation approval authorizes this bounded work; opaque grant and host enforcement are unavailable and not claimed.
- Tests added: exact Receptionist creation at `start == now`; inclusive last supported availability date and rejection of the next date; Patient reschedule at exact deadline and rejection one second inside without writes.
- Focused verification: `./mvnw.cmd '-Dtest=CreateAppointmentServiceImplTest,GetAppointmentAvailabilityServiceImplTest,RescheduleAppointmentServiceImplT024Test' test` — BUILD SUCCESS, 13 tests, 0 failures/errors/skips (2026-10-20 15:49:57 +07).
- Trace: `sdd/traces/trace-feat-002-appointment-booking-T034-A1.md` indexes test evidence for AC01–AC24 and distinguishes focused evidence from baseline evidence.
- Full verification: Owner-provided current-source `./scripts/test-local.ps1` run finished 2026-10-20 15:54:00 +07 with BUILD SUCCESS, 298 tests, 0 failures/errors/skips. Log confirms Java 21.0.7, PostgreSQL 18.1 at `127.0.0.1:5432/otcms_test_db`, Flyway V019, no pending migrations, and the T031 V018 clean/upgrade migration test paths. Relevant suites passed: T031 care/capacity 7/7, T032 admin mutation 6/6, readiness gate HTTP 2/2 and unit 3/3; no tests were skipped.
- Log note: an auth fixture setup emitted a duplicate `account_id` key detail; `T004PostgresIntegrationTest` passed 5/5 and the complete build succeeded.
- Final review: Owner Long approved closing T034 in conversation on 2026-10-20 (Asia/Bangkok); trace `sdd/traces/trace-feat-002-appointment-booking-T034-A1.md` records the decision.
- State: COMPLETE — test additions, AC trace, focused verification, current-source PostgreSQL full suite (298/298), and Owner Long closure approval are recorded. T034 is `[x]`.

### T035 Action Record — E-feat-002-appointment-booking-T035-A1 (complete)
- Actor/scope: Current Agent under Owner Long's test-only checkpoint approval received in conversation on 2026-10-20 (Asia/Bangkok). No production source, API/contract, schema, frontend or production/Supabase changes.
- Added test: `CoordinationGuardT014PostgresIntegrationTest.realGuardLockTimeoutRollsBackPriorWriteAndMapsToDependencyUnavailable` forces a real T014 row-lock timeout across two PostgreSQL transactions, asserts SQLSTATE 55P03 and stable 503 mapping, and verifies the preceding probe write rolled back.
- Verification: `./mvnw.cmd '-Dtest=CoordinationFailureExceptionHandlerTest' test` passed 3/3 (2026-10-20 16:04:31 +07); final `./mvnw.cmd -DskipTests test-compile` passed (2026-10-20 16:05:49 +07). Direct focused PostgreSQL execution did not reach test methods because datasource auth received the unresolved `${OTCMS_TEST_DB_USERNAME}` placeholder (SQLSTATE 28P01); no test result is claimed from this attempt.
- Trace/review: `sdd/traces/trace-feat-002-appointment-booking-T035-A1.md` maps existing T035 evidence and current gaps. Approved checkpoint is `sdd/reviews/preflight-feat-002-appointment-booking-T035.md`.
- Full verification: Owner-provided `./scripts/test-local.ps1` finished 2026-10-20 16:08:18 +07 with BUILD SUCCESS, 299 tests, 0 failures/errors/skips. Log confirms Java 21.0.7, PostgreSQL 18.1 at `127.0.0.1:5432/otcms_test_db`, Flyway V019, and no pending public-schema migration. `CoordinationGuardT014PostgresIntegrationTest` passed 4/4; Surefire confirms the new real-timeout/rollback test ran in 2.032s and the log records PostgreSQL SQLSTATE 55P03. Also confirmed T017 persistence 2/2, T032 mutation 6/6, T031 care/capacity 7/7, T031 room Admin 2/2, T043 WALK_IN 8/8 and Visit T012 4/4. No tests were skipped.
- Final review: Owner Long approved closing T035 in conversation on 2026-10-20 (Asia/Bangkok); trace `sdd/traces/trace-feat-002-appointment-booking-T035-A1.md` records the decision.
- State: COMPLETE — PostgreSQL concurrency/rollback evidence, real 55P03 timeout test, full local suite (299/299), trace and Owner Long closure approval are recorded. T035 is `[x]`.

### T028 Execution Record — E-feat-002-appointment-booking-T028-A1 (complete)

- Scope: Appointment-owned manual Receptionist NO_SHOW only; no Visit write or WALK_IN endpoint. Dependencies T012, T014, T016, T027, T041, T042 and T043 are `[x]` with recorded evidence. SPEC v0.9.0, PLAN v0.7.0, Architecture Profile v1.4.0 and Visit contract v1.2.0 are approved. No additional material-state checkpoint is specified for T028.
- Shadow plan/file boundary: Add strict `PATCH /api/v1/appointments/{id}/status` Receptionist route for target `NO_SHOW`, Appointment command/service/transaction/repository projection, shared contract signal for definitive arrival absence, focused unit/PostgreSQL tests, and T028 trace/review record. No schema migration, Visit mutation, alternate status transition, frontend or production/Supabase access.
- Behavior: Guard before Appointment row lock; require CONFIRMED/no Visit link and strict `now > start + configured check-in late grace`; query the Visit-owned read contract for the inclusive check-in window; timely/mismatched/unavailable evidence blocks or fails closed; definitive absence permits atomic Appointment status + audit write. Reason is optional and validated; actor/time/action/correlation audit fields are mandatory.
- Verification command: `./mvnw.cmd '-Dtest=MarkAppointmentNoShowT028Test,MarkAppointmentNoShowT028PostgresIntegrationTest' test`. Owner ran `backend/scripts/test-local.ps1` against the dedicated local PostgreSQL test DB; no production/Supabase access.
- Final verification: owner-run helper finished 2026-10-19 10:37:34 +07 with BUILD SUCCESS, 231 tests, 0 failures/errors/skips. Surefire confirms `MarkAppointmentNoShowT028Test` 3/3, `MarkAppointmentNoShowT028PostgresIntegrationTest` 4/4, and `VisitT012PostgresIntegrationTest` 3/3. Earlier attempts exposed and resolved the Visit adapter exception import and an invalid race-test expectation; final evidence is from corrected sources. Independent source review PASS; T035 retains broader race/readiness coverage.
- State: COMPLETE — implementation, unit/PostgreSQL/full-suite verification, independent review, trace and post-code review recorded. T028 is `[x]`; feature-level Human closure remains T039.

### T043 Execution Record — E-feat-002-appointment-booking-T043-A1 (complete)

- Scope: Visit-owned Receptionist `POST /api/v1/visits/walk-ins`, additive V014 Visit audit schema, same-patient prior appointment enforcement by the approved database FK, same-transaction coordination guard, idempotency replay and conflict handling. No Appointment writes or cross-feature repository/entity access.
- Implementation: added Visit controller/service/JPA entities and repositories, request/response mapping, scoped HTTP error mapping and `V014__create_visit_walk_in_audit.sql` with UPDATE/DELETE/TRUNCATE rejection triggers. Creation writes Visit, initial reception, audit and replay atomically; server Clock supplies both `arrived_at` and `checked_in_at`.
- Tests added: service unit coverage for guard ordering, exact replay, conflicting replay, authorization and shared timestamp; PostgreSQL integration coverage for persisted evidence, replay, wrong-patient prior appointment, authorization, append-only enforcement and rollback on audit failure.
- Verification: `./mvnw.cmd -DskipTests test-compile` passed before review follow-up (Java 21; 296 production and 65 test source files compiled on the final focused run). Owner-run `backend/scripts/test-local.ps1` applied V014, confirmed clean/upgrade migration tests, T043 PostgreSQL cases 7/7, T043 service unit cases 4/4, `VisitApiExceptionHandlerTest` 1/1, and full-suite BUILD SUCCESS (224 tests, 0 failures/errors/skips; finished 2026-10-18 23:07:06 +07). Independent review found no blocking defect. SQLSTATE 23P01 maps to 409 `SLOT_UNAVAILABLE`; T007 uses per-run unique emails and scoped cleanup; T023 accounts for pre-existing local test DB rows. T043 removes only its own stale `t043-*` fixtures. The earlier 212/212 result is pre-change evidence at V013 only.
- State: COMPLETE — implementation, V014 migration, focused PostgreSQL/unit coverage, full-suite verification, independent review and AC24 trace are recorded. T043 is `[x]`; no production/Supabase database was accessed.

- T040 architecture migration is complete: source package/path review passed, no legacy Appointment `application`/`adapter` references remain, and controller/repository dependency checks passed.
- Verification evidence: focused replay/query tests passed 10/10; user reported full local suite 211/211 before the final service transaction-boundary adjustment; focused post-review tests passed 5/5 after that adjustment. A PostgreSQL test that forces a deferred constraint at transaction commit remains a useful follow-up.
- Independent review found and verified correction of controller persistence exception handling and deferred-commit translation. Owner Long approved implementation and trace on 2026-10-14. T040 is `[x]`; no further T040 action is pending.

### T036 Review Record — R-feat-002-appointment-booking-T036-A1

- Review artifact: sdd/reviews/post-code-feat-002-appointment-booking-T036-A1.md (2026-10-20).
- A1 outcome: FAIL with two architecture/API blockers and one CORS gap; follow-up fixes are recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T036-A2.md`.
- A2 outcome: PASS. Repository dependency direction, six typed/validated mutation DTOs, unknown-field/type rejection, role-scoped reads/mutations, privacy-limited Admin impact projection, safe correlation handling, and schema/security boundary reviewed; no schema change.
- Verification: clean test compilation passed; broad non-PostgreSQL suite 176/176; Owner-provided local PostgreSQL 18.1 full suite BUILD SUCCESS, 304 tests, 0 failures/errors/skips (2026-10-20 16:30:11 +07), Flyway V019/no pending public migration. Relevant T022/T024–T029 PostgreSQL reports and DTO/CORS tests pass.
- State: COMPLETE — A1 findings resolved, A2 re-review and full local verification recorded. T036 is `[x]`.

### T037 Review Record — R-feat-002-appointment-booking-T037-A1 (partial; runtime integration open)
- Review artifact: `sdd/reviews/post-code-feat-002-appointment-booking-T037-A1.md`.
- Evidence reviewed: approved Report 3 baseline/UC mapping, FEAT-002 SPEC/CONTEXT/PLAN, frozen owner contracts, Appointment DTO/controller/error/config sources, T034 AC trace, T035 trace, and T036 A2 review.
- Documentation artifacts: `features/feat-002-appointment-booking/openapi.yaml` (OpenAPI 3.1.0), `features/feat-002-appointment-booking/API-CONTRACT.md`, and reconciled `traces/trace-feat-002-appointment-booking-T034-A1.md`. README now documents the integrated Swagger UI/OpenAPI URLs.
- Findings disposition: OpenAPI added for Appointment/Visit workflow routes used by FEAT-002; all 32 referenced test-class groups have matching current-workspace Surefire XML with 0 failures/errors/skips; baseline, config/error and owner-contract source index added. Per-AC limits and ephemeral report caveat remain explicit.
- Validation: OpenAPI YAML parsed through repository `js-yaml` dependency (14 paths, 15 operations); T034 trace check found 32/32 referenced test reports and no non-pass/missing reports; `git diff --check` shows only pre-existing `.gitignore:28` trailing whitespace, unrelated to T037.
- Runtime implementation: added Springdoc WebMVC UI 3.1.1, set `/v3/api-docs` and `/swagger-ui.html` properties, and allowlisted only Swagger documentation paths in `SecurityConfig`. `OpenApiConfiguration` defines bearer JWT authorization for protected `/api/v1` operations and leaves public GET operations unauthenticated. Added `SwaggerOpenApiHttpTest` for docs access, generated paths/schemas, authorization metadata and unauthenticated business API denial.
- Validation: `./mvnw.cmd -DskipTests clean test-compile` passed (Java 21.0.7). Static OpenAPI YAML parses via repository `js-yaml` (14 paths, 15 operations); T034 trace check found 32/32 referenced current Surefire reports with no non-pass/missing reports.
- Runtime test status: focused test could not initialize the integration context because local datasource authentication received the unresolved `${OTCMS_TEST_DB_USERNAME}` placeholder (SQLSTATE 28P01). `@LocalPostgresTest` must use dedicated loopback `otcms_test_db` credentials via `backend/scripts/test-local.ps1`; no Supabase/remote DB was accessed. Runtime assertions and generated/static contract reconciliation remain pending.
- Scope note: no controller, business API, database schema, or frontend behavior changed. Swagger docs paths alone are public; API routes preserve existing authorization.
- Outcome: Documentation portion is complete, but overall T037 remains `[ ]` pending approved runtime Springdoc integration so `/swagger-ui/index.html` works on the backend. See the A2 follow-up section in the review artifact for the current documented limitation.
- Remaining work: run `SwaggerOpenApiHttpTest` with dedicated local PostgreSQL credentials, verify `/v3/api-docs` and `/swagger-ui/index.html`, and reconcile the generated runtime contract with the committed static contract before marking T037 complete.
- Approved implementation plan: `sdd/reviews/plan-feat-002-T037-swagger-runtime.md`; Owner Long approved in conversation on 2026-10-20.

### T037 Review Record — R-feat-002-appointment-booking-T037-A3 (runtime/static comparison)
- Review artifact: `sdd/reviews/post-code-feat-002-appointment-booking-T037-A3.md`.
- Latest verification evidence: Owner-run local suite completed 2026-10-20 17:35:43 +07 with 306 tests, 0 failures/errors/skips; `SwaggerOpenApiHttpTest` passed 2/2. Owner subsequently captured current `backend/target/openapi-runtime.json` (55 paths, 63 operations, 53 schemas; SHA-256 recorded in A3).
- Comparison: current runtime document contains all 15 static operations and effective auth matches all 15. It documents only `200` responses, while static contract documents creation `201` and error status codes; schema identifiers/shapes differ as detailed in A3. `ErrorResponse` is present in runtime.
- Reviewer recommendation: `REVISE` due the documented response/schema differences.
- Human decision: Owner Long approved T037 on 2026-10-20 in conversation, accepting the A3-documented response/status and schema differences as known limitations for this task. T037 is `[x]`; approval does not assert that the runtime and static contracts fully match. Address the remaining differences in a follow-up contract-alignment task if required.

### T037 Contract Alignment Follow-up — R-feat-002-appointment-booking-T037-A4
- Review artifact: `sdd/reviews/post-code-feat-002-appointment-booking-T037-A4.md`.
- Runtime capture: `target/openapi-runtime.json` (45,660 bytes; SHA-256 `6EA8B4E6EB44D2E90E683506FF3DBAB33051C15F508BA9B86DDE9F5033E96136`); captured by the passing parity test against the same-process `/v3/api-docs` endpoint.
- Verification: all 15 FEAT-002 operations compare exactly for response status sets, JSON response schema identifiers, request schema identifiers and required body flags. For aliased body schemas, top-level property names, required properties and `additionalProperties` match. Runtime API media is normalized to `application/json`; static YAML has one `components.schemas` block after duplicate-key cleanup.
- Commands: `.\mvnw.cmd '-Dtest=SwaggerOpenApiHttpTest#runtimeAppointmentSubsetMatchesCommittedResponseCodesAndSchemaProperties' test` passed 1/1; `.\mvnw.cmd '-Dtest=AppointmentRequestValidationTest,CreateFollowUpAppointmentT030PostgresIntegrationTest,CreateWalkInVisitT043PostgresIntegrationTest' test` passed 14/14, all with zero failures/errors/skips, against local `otcms_test_db` (PostgreSQL 18.1, Flyway V019).
- Outcome: the previously accepted A3 differences are reconciled by follow-up implementation and explicit static/runtime parity verification. This updates the T037/T039 review evidence; it does not change the historical A3 Owner approval or reopen T039. Release-specific phase7 readiness remains open and the production gate remains closed.

### T038 Review Record — R-feat-002-appointment-booking-T038-A1
- Review artifact: `sdd/reviews/post-code-feat-002-appointment-booking-T038-A1.md`.
- Evidence: full test output 2026-10-20 17:35:43 +07 and clean verify output 2026-10-20 20:55:51 +07; both Java 21.0.7, profile `test`, PostgreSQL 18.1 (`127.0.0.1:5432/otcms_test_db`); each BUILD SUCCESS and 306 tests, 0 failures/errors/skips. Flyway and upgrade migrations executed; Surefire reports reconcile to 306 tests, including 125 across 31 PostgreSQL-named integration classes.
- Outcome: reviewer recommendation PASS; T038 marked `[x]`. Pasted console output omits literal shell command line, wrapper version, and separately printed process exit code; Maven BUILD SUCCESS indicates successful completion. Human closure remains T039.

## 13. Approved tasks — complete reception check-in flow (2026-10-22)

**Status: APPROVED — revised 2026-10-25.** The user approved scheduled arrival outside walk-in FIFO and separate clinic-QR arrival evidence. This supersedes the prior T044 same-queue contract. T044 is closed under the revision; T045–T053 retain their dependency and task-specific implementation checkpoints. Existing T001–T043 remain unchanged.

| Task | Owner | Depends on | Deliverable and exit evidence |
|---|---|---|---|
| [x] T044 | Product + Appointment/Visit/Patient | User clarification | Revised scheduled-vs-walk-in, clinic-QR arrival, pending Receptionist worklist and delayed-processing/no-show contract approved by user on 2026-10-25. Product-specific UC22 variance accepted for this feature; synchronize SRS wording before delivery. Review: `sdd/reviews/feat-002-T044-reception-contract.md`. |
| [ ] T045 | Visit/persistence | T044, T043 | Add Visit-owned Flyway queue entry/history schema for WALK_IN only; allow multiple historical entries but enforce at most one active `WAITING` entry per Visit, preserve FIFO ordering, and retain records for the Visit lifetime. Database guards reject non-WALK_IN queue entries and reject Visit `source` changes while any queue entry references it. Add Visit `service` interface/`service.impl`, JPA repository and DTOs. Scheduled check-in creates no queue row. Preflight runtime Flyway head and persist schema checkpoint before edits; prove clean/upgrade migration and duplicate prevention. Checkpoint and T045 post-code/audit/trace decisions: approved evidence remains local and is SHA-256 pinned in `C:/Users/Admin/AppData/Local/Temp/otcms-t045-review-evidence-20261027.json` per Human approval on 2026-10-27; implementation and verification evidence is recorded there; SPEC v0.9.2, PLAN v0.5.3 and TASKS v0.6.3 alignment was approved by Owner Long on 2026-10-27; T045 remains pending validator clearance. |
| [ ] T046 | Appointment + Visit | T045, T027, T053 | Preserve atomic appointment/visit/reception/audit/replay check-in without queue insertion. Consume timely QR/manual arrival evidence even if staff confirms after grace; add patient attestation and Patient-owned identity projection. Prove retry/race/window behavior and no queue row. AC25/AC26/AC31/AC32. |
| [ ] T047 | Visit | T045, T043 | Extend WALK_IN creation to enqueue atomically; keep optional same-patient prior appointment reference, no synthetic appointment and no appointment status change. Prove idempotency/rollback and accountless profile support. AC27/AC31. |
| [ ] T048 | Appointment + Visit | T045, T028, T046, T053 | Reconcile no-show with timely QR/manual arrival and concurrent check-in; no visit/queue on no-show. Ensure WALK_IN care start/cancel/closure moves its active reception entry to history. AC28/AC29/AC34. |
| [x] T049 | Visit + Patient | T045, T048, T053 | Implement Receptionist pending scheduled-arrival worklist, WALK_IN queue query and Patient own progress query through owning APIs. Queue position only for active WALK_IN; current room only when Visit has assignment. AC29/AC30/AC32. Completion evidence: [T049 trace](../../traces/trace-feat-002-appointment-booking-T049-A1.md), [post-code review](../../reviews/post-code-feat-002-appointment-booking-T049-A1.md); local PostgreSQL suite 314/314, T049 integration 3/3. |
| [ ] T050 | Frontend/reception | T044, T046–T049, T053 | Build receptionist pending QR arrivals, appointment verification/check-in/no-show and walk-in reception; show FIFO position only for walk-ins. Use shared API client and feature components, keep route thin. |
| [ ] T051 | Frontend/patient | T049, T053 | Build authenticated patient clinic-QR arrival flow and own visit progress. Show arrival recorded/pending staff confirmation, and queue position only for active WALK_IN. No cross-patient list or direct database access. AC30/AC32/AC33. |
| [ ] T052 | API/docs + QA | T046–T051, T053 | Update DTO/OpenAPI and static/runtime parity, UC-to-AC trace, meaningful HTTP/security/PostgreSQL concurrency verification and frontend lint/build evidence; independent boundary review and final task sync. AC25–AC34. |
| [x] T053 | Visit/reception + Appointment/security | T044, T027, T028 | Add clinic-QR arrival claim before check-in: short-lived clinic-bound proof, authenticated Patient/appointment ownership, server `arrivedAt`, idempotent claim, pending Receptionist worklist and audited manual fallback. QR creates no Visit/queue or `CHECKED_IN`; serialize arrival with no-show through shared guard. Allocate additive schema migration only after runtime head/preflight and persisted material-state checkpoint. AC32–AC34. Completion evidence: T053 completion verification record and approved post-code review below. |

**Execution record rule:** each task records reviewed scope/file boundary, dependencies, schema/API decision if relevant, exact verification evidence, residual risks and human review. The extension contract and T053 task scope are approved. Material schema/security changes still need task-specific persisted checkpoints before implementation. Historical T039 closure and production gate are unaffected.

### AI Agent Recommendation — task extension

- Status: PENDING HUMAN REVIEW.
- Recommendation: approve or revise T044–T052 together with the proposed CONTEXT/SPEC/PLAN extension.

### Human Final Review — task extension

- Prior status: APPROVED on 2026-10-23 for the previous same-queue contract.
- Current status: APPROVED — revised 2026-10-25 scheduled-vs-walk-in and clinic-QR arrival contract, including T053 task scope. Requesting user explicitly approved CONTEXT, SPEC, PLAN, TASKS and T053. Approval is not implementation completion or a schema/security pre-edit checkpoint.

### T044 Action Record — 2026-10-23

- Authorization: user explicitly approved the four reception-extension artifacts and instructed immediate T044 execution.
- Scope/file boundary: reconcile current UC, prior FEAT-002 queue exclusion and existing owner contracts; write `sdd/reviews/feat-002-T044-reception-contract.md` and update the four approved extension review records plus T044 checkbox. No product code, schema, migration, API or dependency change.
- Decision: first queue is Visit-owned reception queue; Patient may see authorized own stage/status/current position; WALK_IN prior appointment remains optional; Receptionist verification is an explicit patient-profile attestation checked against the appointment by backend. Queue states, response fields and handoff are in the review artifact.
- Evidence: static read of UC HTML, SDD, current backend controllers/services/DTOs, frontend API and Flyway source files. Source migration head is V019; runtime DB head was not queried. Archived Report 3 source cited by historical metadata is not present in this checkout; historical variance is traceable in CONTEXT.
- Verification: documentation diff and path/reference review only; no tests were run for this documentation task.
- Result: T044 was reopened by the user's 2026-10-25 clarification. The revised contract was subsequently approved on 2026-10-25; T044 is closed under the revision. T045 still needs its schema checkpoint.

### T044 Revision Action Record — 2026-10-25

- Authorization: user requested updating CONTEXT/SPEC/PLAN/TASKS and T044 review record to split scheduled appointment arrival from walk-in reception FIFO, then review the revised contract.
- Scope/file boundary: revise reception extension, T044/T045 task definitions and T044 review record. No product code, migration or schema changes; T045 not started.
- Decision captured: scheduled check-in records arrival and linked Visit but does not enqueue to reception FIFO; only WALK_IN uses reception FIFO. A specific room is shown only when Visit has a current assignment; Appointment currently does not persist one.
- Follow-up clarification: patient scans a clinic QR to record arrival and notify Receptionist before staff confirmation. Timely server-recorded arrival remains valid when the front desk processes it after grace. T053 owns the distinct QR arrival capture; T046 consumes its evidence, T048 protects no-show, T049/T050/T051 expose pending status to authorized actors.
- Evidence: current UC22 wording includes booked patients in a first waiting list, so revised rule is documented as a product-specific SRS variance requiring reconciliation. Appointment response has no room assignment; Visit owns room assignment. Source migrations end at V019; runtime head unverified.
- Review: revised artifacts and T044 record cross-checked. Requesting user approved the revised contract and T053 scope on 2026-10-25. T044 is complete. T045/T053 have not started and need their own schema/security checkpoints.

### T053 Scope Approval Record — 2026-10-25

- Decision: Requesting user explicitly approved T053 together with the revised CONTEXT/SPEC/PLAN/TASKS.
- Approved objective: authenticated clinic-QR arrival claim with server `arrivedAt`, pending Receptionist alert, idempotent replay, audited staff fallback and no-show coordination; QR alone does not create a Visit, queue entry or `CHECKED_IN` appointment.

### T053 Test-Local Action Record — 2026-10-26

- Authorization: user asked about running `backend/scripts/test-local.ps1` and provided its output. Ran the exact script against the pinned local `otcms_test_db`; credentials came from the existing local test profile and were not printed. No Supabase/production connection.
- Initial failure: the supplied first attempt used username `postgres admin` and failed authentication. Retrying as `postgres` passed the local PostgreSQL 18.1 / `btree_gist` / CREATE capability checks. The first Maven attempt then found a stale `receptionistAccountId()` accessor; updated the test to `actorAccountId()`.
- Root cause discovered: appointment check-in returned `503` because V020's `enforce_appointment_visit_link()` evaluated `NEW.booking_source` on a `visits` trigger row. Persisted the V022 repair checkpoint addendum before editing schema. Added `V022__guard_appointment_visit_link_trigger.sql`, nesting appointment-only field access in the `appointments` branch. Updated legacy test fixtures to use V021's `actor_account_id` and `LEGACY_RECEPTIONIST` source.
- Verification: `backend/scripts/test-local.ps1` applied V022 to local `public` via Flyway (V021 -> V022), validated 24 migrations and compiled all 397 main / 84 test Java sources. Appointment check-in/no-show/lifecycle suites T027, T028 and T029 passed after the repair. Overall: 280 tests, 11 failures, 2 errors. Remaining failures are in older unrelated auth, relationship, persistence, catalog/staff tests plus historical migration-count assumptions in T013/T012; review their exact Surefire reports before claiming the branch suite is green.
- T053 status remains `[ ]`: dedicated QR proof/authorization/idempotency/worklist/concurrency tests and OpenAPI parity verification are still needed, then Human post-code review. Local DB is at V022. The user-provided remote startup log reported V021; remote was not changed and must receive V022 through Flyway before running the updated check-in flow there.

### T053 Test-Local Reverification — 2026-10-26

- After repairing stale historical migration fixtures, removing accidental Spring CSRF test-context pollution, and correcting excluded care-relationship service registration, reran the full `backend/scripts/test-local.ps1` against the dedicated local `otcms_test_db`.
- Result: **280 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** (2m09s). Flyway validation and compilation completed as part of the script. The run used only local PostgreSQL; no remote/Supabase database was touched.
- This supersedes the earlier 11-failure/2-error suite result recorded above. T053 remains `[ ]` pending dedicated QR flow tests, OpenAPI parity verification, and Human post-code review.
- Implementation state: `[ ]` pending. Runtime migration head, additive migration allocation, concrete QR proof TTL/rotation, exact route/DTO and persisted schema/security checkpoint are pre-edit work; this task-scope approval does not assert those facts or permit an unreviewed material-state change.

### T053 Implementation Action Record — 2026-10-26

- Authorization: user instructed T053 implementation on `feat/long/appointment-booking`; the separate schema/security checkpoint is HUMAN APPROVED and V020 was approved.
- Scope/files: V021 arrival-table extension; Appointment QR proof, claim/manual commands, worklist API/DTOs; Visit-owned arrival persistence and no-show evidence projection; application QR secret configuration, OpenAPI/API contract, checkpoint record. Existing unrelated working-tree changes were preserved.
- Implemented: HMAC-SHA-256 clinic-bound proof (`k1`, 60-second expiry, refresh hint 30 seconds, no-store display response); Patient self-claim checks current profile ownership; Receptionist manual fallback requires reason and stores actor/source/server timestamp; first claim per appointment is immutable/idempotent; all writers use the shared coordination guard and Appointment row lock; pending/late/early Receptionist worklist; no-show query reads the renamed Visit arrival evidence.
- Flyway: V021 `V021__extend_appointment_arrivals_for_patient_qr.sql` was applied by Flyway to local `127.0.0.1:5432/otcms_test_db`; validated 23 migrations and Hibernate initialized. The user-provided IntelliJ log reported Supabase at V021; do not re-run that migration on Supabase.
- Verification: `./mvnw -DskipTests compile` succeeded after implementation and review fixes (397 Java sources). No tests run. A local non-web Spring Boot probe validated/migrated/JPA-initialized then failed because non-web mode does not supply `HttpSecurity`; no endpoint runtime test evidence.
- Review: read-only reviewer flagged worklist pagination semantics and OpenAPI parameter mismatch; added `hasMore` for raw-page continuation and matched `{appointmentId}`. Follow-up review also flagged `recorded_at` being equal to `arrivedAt`, missing QR replay profile consistency check and incomplete database error mapping; fixed by stamping with `clock_timestamp()` at insert, verifying stored profile on replay and mapping persistence/guard/profile failures to `DEPENDENCY_UNAVAILABLE`. Final read-only review found these fixes addressed and no new blocking findings.
- State: T053 remains `[ ]` pending final compile, required HTTP/security/concurrency verification and Human post-code review. No commit or push.

### T053 Completion Verification Record — 2026-10-26

- Added `AppointmentArrivalT053PostgresIntegrationTest` with seven PostgreSQL/MockMvc cases covering Receptionist-only no-store proof issue, altered/wrong-clinic/future/expired proof rejection, Patient-only claim and current profile ownership, immutable/idempotent QR and manual records, required receptionist reason/actor, pending/late worklist state/date/page filters and role privacy, no Visit/queue writes from QR, timely-arrival/no-show reconciliation, late-arrival/no-show behavior, and a staged lock race.
- The staged race locks the appointment row externally, lets the QR transaction capture the on-time server instant and acquire the shared guard, queues the no-show transaction behind that guard, advances the test clock beyond grace, then releases the row. The QR persists its original timely instant and the later no-show returns `NO_SHOW_NOT_ALLOWED`. This exercises serialization across the grace boundary.
- The new worklist test exposed that filtering after a raw page could omit matching results. `AppointmentArrivalApplicationServiceImpl` now scans bounded 500-row batches, applies appointment/state eligibility, then slices the filtered result and computes `hasMore` from matching rows. A fixed ordering fixture covers a non-matching late row before a pending row.
- OpenAPI review against controller mappings, request/response records and exception mapping confirmed all four routes and DTO fields. Added missing 400/401/404/503 responses and corrected `hasMore` wording; synchronized `API-CONTRACT.md`. The independent review's missing worklist 503 finding was fixed.
- Targeted verification: `./mvnw.cmd -Dtest=AppointmentArrivalT053PostgresIntegrationTest test` — **7 tests, 0 failures, 0 errors, BUILD SUCCESS** against local `otcms_test_db` at V022.
- Full regression verification: `backend/scripts/test-local.ps1` — **287 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Flyway validated 24 migrations at V022 and the dedicated local `otcms_test_db` remained the only database target.
- Independent read-only review found and helped correct the race-test weakness and OpenAPI 503 omission. The independent re-review confirmed both fixes and found no new blocking correctness/security issue.
- Supabase: the user approved the separate remote V022 checkpoint. A read-only probe confirmed V021 and V022 successful, latest version 022, and Flyway validation passed for all 24 checked-out migrations. V022 was already applied and checksum-valid, so no migration command or database write was needed. No application workflows or clinical-data changes were performed.
- Status: COMPLETE — implementation, focused QR/security/concurrency tests, full local regression suite, OpenAPI parity review, independent code review, approved remote checkpoint and post-code Human review are recorded. T053 is `[x]`.

### T053 Human Post-Code Review — 2026-10-26

- Decision: `APPROVED`.
- Reviewer: requesting user; explicit decision received in conversation (“Approve post-code review T053”).
- Scope reviewed: T053 implementation and completion evidence in this record, including the QR claim/manual fallback/worklist, V021/V022 migration state, 7 dedicated PostgreSQL/MockMvc tests, 287-test full suite, OpenAPI updates and independent reviewer findings.
- Decision time (Asia/Bangkok): 2026-10-26; exact message timestamp unavailable.
- Result: T053 is complete and checked `[x]`. No commit or push was performed.

### T045 Implementation and Verification Record — 2026-10-26

- Authorization: requesting user approved the full T045 schema checkpoint, including Visit-lifetime queue/audit retention, on 2026-10-26.
- Scope: V023 queue/history schema; Visit queue entity, history entity, repositories, persistence service and response DTO; focused PostgreSQL integration test; update checkpoint. No endpoint/OpenAPI, Appointment check-in/no-show, frontend, Supabase, commit or push.
- Preflight: source migrations ended at V022; SHA-256 matched checkpoint. Read-only local history at `127.0.0.1:5432/otcms_test_db` showed V022 success, zero failures and no V023 row.
- Migration: Flyway applied V023 locally and validated 25 migrations. Post-apply Flyway head V023, checksum `1536607790`, zero failed rows. Migration source SHA-256 `962125C597A5FF7F52808F099C6621F8A2AEEDA4760A9F06FC0261D4E7E93BA1`.
- Focused verification after final service error mapping: `backend/.\mvnw.cmd -Dtest=WalkInReceptionQueueT045PostgresIntegrationTest test` — 5 passed, 0 failed/errors/skipped. Includes clean V001→V023 migration, isolated V022→V023 upgrade with existing Visit data, scheduled Visit rejection, one-active-entry rule, re-entry, timestamp/source protections, check constraints, append-only history and restrictive references.
- Full verification: `backend/scripts/test-local.ps1` — 292 passed, 0 failed/errors/skipped; BUILD SUCCESS. Hibernate `ddl-auto=validate` succeeded at V023. No remote database was accessed.
- Status: implementation and verification pass; T045 remains `[ ]` pending post-code Human review.

### T045 Audit Correction — 2026-10-26

- Requesting user approved `sdd/reviews/preflight-feat-002-T045-audit-correction.md` after the post-code findings. Auth/Visit now binds queue creation to the authenticated principal and an Auth-owned active Receptionist decision. V024 generates initial and exit history from queue-entry transitions and rejects ordinary direct history INSERT. The service sets actor/correlation as transaction-local values.
- User-provided local preflight: V023 successful (checksum `1536607790`); current role and history owner were both `postgres` with INSERT privilege. This privileged role can alter/disable triggers, so a restricted application role is still needed for protection against database owners or superusers.
- `backend/.\mvnw.cmd -DskipTests test-compile` passed. Requesting-user run of `backend/.\mvnw.cmd -Dtest=WalkInReceptionQueueT045PostgresIntegrationTest test` passed 5/5 with Flyway V024 applied locally, Hibernate validation, clean V001→V024 and isolated V022→V023→V024 migrations. Full post-V024 local suite remains unreported. T045 stays `[ ]` pending final Human review.

### T045 Supabase Startup Recovery — 2026-10-26

- Root cause: Supabase had an earlier successful V023 checksum `1615705605` and weaker Visit/queue guards; current source V023 checksum is `1536607790`. The remote queue tables contained zero entries/history rows. See approved `sdd/reviews/preflight-feat-002-T045-supabase-v023-recovery.md` for read-only schema evidence and exact constrained history correction.
- Added V025 to reconcile missing guards without editing V023/V024. Local focused test passed 5/5 with a remote-like weak V023 upgrade path, clean V001→V025 migration, and Hibernate validation.
- Requesting user executed the approved one-row V023 checksum correction in Supabase SQL Editor. Agent startup with `SPRING_FLYWAY_GROUP=true` validated 27 migrations, applied V024 and V025 to Supabase, started the backend on port 8080, and received HTTP 200 from local `/v3/api-docs`. No business workflow, commit, or push. T045 remains `[ ]` pending final Human code review.
- Ordinary Supabase restart after stopping the prior Java process validated V025 with no pending migration and started successfully on port 8080; local `/v3/api-docs` returned HTTP 200. The backend startup incident is resolved. T045 post-code, audit and trace reviews were approved by Owner Long on 2026-10-26. T045 remains [ ] pending validator clearance; PLAN v0.5.1 metadata sync was approved by Owner Long on 2026-10-26.


### T045 Review and verification sync — 2026-10-26

- Human Final Review: APPROVED by Owner Long at 2026-10-26 22:44:09 Asia/Bangkok in the T045 post-code, audit and trace reports.
- Post-lock focused PostgreSQL test: 6 passed, 0 failures/errors/skips. Full clean verify: 293 passed, 0 failures/errors/skips; Flyway validated 27 migrations at V025. Evidence ran on the full working tree, not an isolated commit snapshot.
- T045 remains unchecked: Owner Long approved SPEC v0.9.1, PLAN v0.5.2 and TASKS v0.6.2 alignment on 2026-10-27. PR validation has not run: this repair worktree has four SDD files modified, the remote branch diff contains 1,212 paths, and `git diff --check` reports 32 previously approved CommonMark hard-break diagnostics. The repository skill command is unavailable in this runtime. The remote branch is unchanged at 900891f; do not claim READY. Resolve contract drift and publish the approved SDD updates before final clean remote validation.

## Change Impact Record — SPEC v0.9.1 and PLAN v0.5.2 alignment
- Changed intent, requirement, architecture, or task assumption: Synchronized source versions and retained the user-approved T044–T053 task extension, including T045’s pending validator state.
- Feature Lock boundary affected: FEAT-002 task trace and dependency references.
- Material state-change classification: task planning documentation only.
- Downstream artifacts invalidated: current task artifact review; no task implementation evidence is invalidated.
- Required trace, test, and sync actions: review TASKS v0.6.2; then run feature audit/trace and PR validation.
- Review follow-up: retain T045 unchecked until all validator and sync-back gates pass.

## Historical AI Agent Recommendation — TASKS v0.6.2
- Status: PENDING HUMAN REVIEW
- Scope: FEAT-002 TASKS v0.6.2 source alignment and T044–T053 task trace.
- Recommendation: Approve if source references match the reviewed Spec and Plan and T045 remains pending its outstanding validator gates.
- Evidence: the existing T044 task extension approval, T045 post-code/audit/trace evidence, and the proposed Spec/Plan versions in this branch.
- Risks and assumptions: The feature registry and shared Visit contract may still require sync based on audit/trace results; this task update does not close T045.
- Alternatives considered: Retain stale source versions and leave T044/T045 trace inconsistent with the feature documents.
- Required human decision: Review TASKS v0.6.2 and record APPROVED, REVISE, or REJECTED.

## Historical Human Final Review — v0.6.2
- Status: APPROVED
- Decision: Approve synchronization of task source versions and retain T045 unchecked until validator and sync-back gates pass.
- Reviewer: Owner Long
- Reviewed at: 2026-10-27T17:24:48+07:00
- Follow-up: Continue `/sdd-audit --feature=feat-002-appointment-booking` and `/sdd-trace --feature=feat-002-appointment-booking --diff`; do not mark T045 complete until PR validation is READY.
### T045 SDD validator remediation — 2026-10-27

- Owner Long approved SPEC v0.9.1, PLAN v0.5.2 and TASKS v0.6.2 on 2026-10-27. Spec lint scan passes all 44 REQ-BOOK groups and 11 REQ-RECEPTION requirements for EARS shape and unwanted-behavior coverage.
- T045-scope audit/trace reports are APPROVED and held locally with SHA-256 pins; shared contract `visit-reception-clinical` v1.2.0 is synced. Full FEAT-002 trace remains outstanding; T045 remains unchecked pending remote PR validation.
- The remote PR validator has not run. Branch must include approved SDD updates and contract disposition, be pushed by the user, and then be checked from a clean worktree matching remote.

### T045 shared contract sync — 2026-10-27

- Owner Long confirmed Visit/Reception/Clinical contract owner/Lead authority and approved `visit-reception-clinical` v1.2.0 on 2026-10-27.
- `backend/sdd/shared_context.md` now records the approved T044 queue contract, T045 persistence invariants, V023–V025 compatibility, T047–T049 ownership boundaries and the privileged database-role residual. Registry status was synchronized; no code or database changed.
- T045 audit/trace reports are approved and hash-pinned in the local external evidence manifest. Full feature trace and clean remote PR validation remain outstanding; T045 remains unchecked.

## Change Impact Record — SPEC v0.9.2 and PLAN v0.5.3 alignment
- Changed intent, requirement, architecture, or task assumption: Updated task sources for the existing capacity-conflict error mapping; no task scope/dependencies changed.
- Feature Lock boundary affected: None.
- Material state-change classification: Documentation only.
- Downstream artifacts invalidated: This Tasks review; T045 remains unchecked.
- Required trace, test, and sync actions: review TASKS v0.6.3; run full feature trace after review.
- Review follow-up: keep T045 unchecked until full feature trace and remote PR validation pass.

## AI Agent Recommendation
- Status: PENDING HUMAN REVIEW
- Scope: FEAT-002 TASKS v0.6.3 source alignment.
- Recommendation: Approve if source references match SPEC v0.9.2 and PLAN v0.5.3; task assignment and verification evidence remain unchanged.
- Evidence: existing T031 behavior and tests already use `VISIT_CAPACITY_NOT_AVAILABLE` with HTTP 409.
- Risks and assumptions: This metadata update does not complete T045.
- Alternatives considered: Leave the task source references stale.
- Required human decision: Review TASKS v0.6.3 and record APPROVED, REVISE, or REJECTED.

## Human Final Review — v0.6.3
- Status: APPROVED
- Decision: Approve TASKS v0.6.3 source alignment with approved SPEC v0.9.2 and PLAN v0.5.3; task scope, ordering and recorded T045 evidence remain unchanged.
- Reviewer: Owner Long
- Reviewed at: 2026-10-27T17:44:11+07:00
- Follow-up: Run full `/sdd-audit --feature=feat-002-appointment-booking` and `/sdd-trace --feature=feat-002-appointment-booking --diff`; keep T045 unchecked until clean remote PR validation passes and required sync-back is recorded.
