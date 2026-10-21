# Implementation Plan — FEAT-002 Appointment Booking

## 1. Metadata và execution boundary

- Feature ID: FEAT-002
- Version: 0.5.0
- Status: APPROVED — D03 REVISION
- Sources: CONTEXT.md v0.16.0 and SPEC.md v0.8.0, approved by Owner Long on 2026-10-14; v0.15.0/v0.7.0 approvals are historical for D03.
- Artifact root: backend/sdd/features/feat-002-appointment-booking; application root: otcms/backend.
- Architecture: Spring Boot modular monolith, feature-first Spring layered architecture per `backend/CONSTITUTION.md` ARCH-01 and approved Architecture Profile; PostgreSQL/Flyway; package root fu.fa26.sep490.g58.otcms_be.
- Delivery: backend/API only. PLAN approval chưa cấp quyền triển khai khi TASKS/prerequisite gates chưa pass.
- Current repository evidence: migration head file V001__auth_schema.sql; runtime DB head chưa kiểm tra. Maven pom declares Java21; root AGENTS declares Java17. Phase0 phải reconcile toolchain trước build execution, không tự đổi dependency/toolchain.
- Bản này thay PLAN v0.3.0 theo approved SPEC v0.8.0. T008 implementation must conform to the self-only appointment authorization boundary.
- Java 21 là toolchain mục tiêu đã chốt theo `pom.xml`; phase 0 chỉ xác minh wrapper/JDK/profile thực tế, không đổi dependency hay hạ version.
- PostgreSQL capability, migration/exclusion strategy, prerequisite contracts và owner exit evidence là các execution gates đã được lập kế hoạch; chưa yêu cầu phải triển khai trước khi phê duyệt PLAN.
- Không bật booking production trước khi các prerequisite contracts, active-care/protected-mutation integration và verification gate của các phase liên quan đạt; không dùng production stub để vượt gate.

## 2. Ownership và prerequisite delivery

| Owner/module | Delivery required | Consumer / exit evidence |
|---|---|---|
| Auth/security | Explicit role mapping, JWT/principal/session/portal compatibility; authenticated account context | Appointment principal port; existing auth regression và mapping readiness. |
| Patient | Independent profile IDs, optional account linkage, search/create; current linked self-profile authorization | Appointment identity port; no-account assisted profile and scoped denial for other patient profiles. |
| Service/catalog | Admin service CRUD/deactivation và Doctor-service assignments | Snapshot/eligibility ports; historical exception và protected removal. |
| Staff/account | Doctor profile/provisioning/publication/newBookingAcceptance/account access, shifts/operational time off | Provider projection/mutation contract; no account-lock appointment transition. |
| Visit/reception/clinical | Visit/reception lifecycle, recorded arrival, care reservations/completion/indication, walk-in priorAppointmentId | Atomic check-in/cancel/follow-up/no-show/capacity contracts. |
| Appointment | Booking/query/change/lifecycle/audit/schema/catalog-availability projections và capacity/impact contracts | HTTP/DTO/error contracts SPEC §4–6, AC01–23. |
| Neutral integration infrastructure | Transaction orchestration/coordination contracts và concurrency guard | No module cycles, same DB transaction, all writers participate. |

Owning CRUD không chuyển vào appointment. Các prerequisite chưa tồn tại phải có owned tasks/contracts và Human checkpoints; không thay bằng stub trong production hoặc silently skip validation. Service/staff/patient/visit schema giữ owner; additive migrations trong cùng Flyway stream được phối hợp theo dependency, không import entity của owner khác.

### Dependency direction

- Each business capability is a feature module with `controller`, `dto/request`, `dto/response`, `mapper`, `service`, `service/impl`, `repository`, and `entity` packages where applicable; pure business value objects may remain under `domain`/`policy`.
- Dependency direction follows ARCH-01: controller -> public service interface -> service implementation -> owning repository -> entity. Service implementations own orchestration, authorization, invariants and `@Transactional` boundaries. DTOs never expose persistence entities.
- Cross-feature calls use the owning feature's public service/facade or approved neutral integration contract. No foreign repository/table/entity access. Appointment and Visit retain explicit ownership and share transactions through the established coordinator/ports.
- Neutral contracts do not depend on implementation modules. No appointment-to-visit-to-appointment implementation import cycle; integration coordinator handles combined transactions/capacity checks.

## 3. Transaction và concurrency design

### 3.1 MVP consistency strategy

Đề xuất một transaction-scoped coordination guard cho single clinic, backed by one dedicated DB row trong neutral integration schema. Mọi writer ảnh hưởng appointment authority/state/capacity phải acquire guard trước khi đọc rules và trước locks/write: create/reschedule/reassign/cancel/check-in/start/complete/no-show, shifts/time-off/assignments/service/provider state và care/room assignments. Thay đổi liên kết account/profile ảnh hưởng quyền Patient phải dùng cùng consistency boundary khi được triển khai. Guard được lấy qua neutral port, không feature đọc bảng feature khác.

- Chọn guard coarse-grained cho MVP để bảo đảm all-or-nothing ordering giữa nhiều modules. Không giữ guard trong public availability reads.
- Lock order duy nhất: coordination guard -> owning resource/appointment rows -> related visit/reception/replay rows theo UUID ascending -> writes/audit. Never acquire guard after owning row locks. Multi-resource mutation theo cùng order.
- Thực hiện dưới một transaction manager/shared PostgreSQL datasource, fresh policy reads sau guard (READ_COMMITTED binding); no external network/email/queue calls khi giữ guard.
- Captured Clock command instant lấy sau acquire guard, trước rule evaluation; validated state/authority không stale pre-lock snapshot.
- Patient appointment commands validate the current authenticated account/profile link after acquiring the guard. A future identity-link mutation that changes appointment authority must share the guard and commit order; read-only queries re-check the current link through the Patient public contract. Family clinical-record/results access uses a separately reviewed authorization policy.
- Guard contention là trade-off single-clinic MVP; bounded DB lock/statement timeout config và error/retry binding phải được review trong phase0/2. Do not silently retry command vào state mới. Scale-out/per-Doctor locks require reviewed redesign, không suy distributed coordination từ in-memory locks.

### 3.2 Database final protection

- PostgreSQL exclusion trên Doctor UUID + half-open tstzrange, partial to CONFIRMED/CHECKED_IN/IN_PROGRESS/COMPLETED. Proposed btree_gist extension requirement must be verified on target DB and approved before migration; if unavailable, block checkpoint, do not remove overlap enforcement.
- occupied_until persisted/derived with constraint equal end_time + buffer snapshot; start/end/duration/buffer consistent; no now-dependent partial predicate.
- Completed giữ original interval; không expiration job/truncate snapshots. Actual care reservation remains checked separately.
- Exclusion violation rollback then map409 SLOT_UNAVAILABLE at web boundary. Other uniqueness/state errors map SPEC errors, no leaked SQL.
- Reschedule validates excluding own appointment then updates same row atomic, no delete old reservation first and no insert competing self-row. Failure leaves row/audit/occupancy unchanged.

### 3.3 Lifecycle, arrival và replay

- Appointment state rows serialized after guard; cancel vs start, arrival vs no-show và check-in vs no-show must see latest committed owning evidence.
- Check-in one appointment -> one visit/initial reception constraint; replay unique actorAccountId/appointmentId/command/key. Different keys still rely appointment visit uniqueness/state.
- Replay storage committed with appointment/visit/reception/audit; success replay returns stored CheckInResponse after re-authorization before current-state/window validation, never reset progression. Failed transaction creates no success replay record.
- Retain replay records for full persisted appointment lifetime in MVP; no cleanup job or automatic audit deletion. Sensitive fields kept minimal (key/request fingerprint/result/references/time), not clinical payload.
- Check-in and cancel-after-check-in use same transaction across public owning services. Any mandatory failure rolls back all writes. Existing invalid visit linkage -> VISIT_LINK_CONFLICT, not create another visit.
- No-show must read arrival evidence, not merely CONFIRMED. Recorded timely arrival denies; unavailable port fails closed503. Receipt/arrival recording must acquire same guard before evidence commit.
- Follow-up validates assigned care/completed exam/indication and derives patient within consistency boundary; source_visit_id mandatory, source appointment optional.

## 4. Delivery phases

### Phase 0 — Approved inputs và baseline checkpoint

1. Verify persisted Context/Spec approvals; record hashes/version/file boundary, actual source tree và owning contracts.
2. Inventory DB/Flyway head without reading secrets; preserve V001 checksum. Verify clean/existing DB routes, next version allocation and btree_gist support.
3. Reconcile Java21 pom vs Java17 repository instructions, actual wrapper/test profile/database setup; persist exact commands/toolchain. No dependency changes without explicit architecture review.
4. Review coordination guard, timeout binding, module ownership/contracts and migration/security reviewers. Prepare TASKS after PLAN review, not execute obsolete tasks.

Exit: persisted PLAN review, prerequisite task ownership, schema/security boundaries, toolchain/DB capability decisions. Remaining unknowns tracked as gates, not assumed resolved.

### Phase 1 — Auth/patient compatibility và contract foundation

- Add independent patient_profiles UUID/backfill; account link optional unique; preserve registration/history. Update owning repositories/services through identity migration, no applied-migration edits.
- Inventory existing STAFF; approved per-account authoritative mapping. Two-step admit/backfill then remove legacy value only after zero unresolved rows. Update enum/JWT/session/portal/network checks and existing seeder/test fixtures.
- Deliver patient search/create and current linked self-profile public authorization contract; deny other patient profiles even when a care relationship exists.
- Deliver minimal neutral DTOs/ports and wiring layout for provider/service/visit/reception/capacity; architect review no cycles.

Exit: auth regression, profile migration clean/upgrade, mapping readiness, self/other-profile scoped-denial tests; prerequisite visit/service/staff specifications assigned and reviewed. Contract interfaces alone do not satisfy production dependency availability.

### Phase 2 — Owning schemas/configuration/concurrency foundation

- Service/staff owners deliver service/Doctor/assignments/shifts/time-off schema/provisioning, Admin maintenance authorization and public query contracts.
- Visit owner delivers visit/arrival/initial reception/care evidence/reservations; separate incoming WALK_IN source/priorAppointmentId with valid same-patient reference; no synthetic appointment.
- Add appointment/source_visit_id/FKs/constraints/indexes/audit/replay schema after referenced owner schemas. Preserve non-cascading historical references; UUID cross-module FKs do not require ORM entity imports.
- Add neutral coordination guard and public transaction wiring; prove all participating writers use it before own locks. Implement DB exclusion/occupied-until checks.
- Bind all nine booking config keys, injected Clock and fixed-clock fixtures: horizon/notice/deadline/grid/zone, check-in early30/late15, public rate requests/window.

Exit: schema validation clean and upgraded DB, existing auth preserved, DB protection tests incl COMPLETED, cross-module atomicity/lock-order review, real provider/visit prerequisites available for next slices.

### Phase 3 — Catalog và four availability workflows

- Shared slot engine current-service vs appointment snapshot context, local dates/UTC conversion/configured grid; seconds/fraction zero, no rounding. Bounded provider/time-off/appointment/care queries via ports.
- Public active-online/public-visible+accepting provider/notice; staff active-service accepting provider/no notice/non-past, Admin inspect only.
- Reschedule availability scoped CONFIRMED, same Doctor/service/snapshots, Patient old deadline/new notice; removed-Doctor/inactive-service exception, exclude self occupancy.
- Reassignment availability Receptionist-only CONFIRMED and start>=now, same slot/snapshots, different eligible accepting replacement.
- Catalog envelope items[], slot envelope slots[], empty200. Trusted proxy IP resolver, independent in-application fixed-window buckets, Clock reset/ceil Retry-After, bounded stale cleanup. Deployment one application instance unless separately reviewed shared limiter.

Exit: AC05/06/16/22/23 projections/boundaries/overrides tests; public endpoint no PII/unavailability reasons.

### Phase 4 — Booking/query/reschedule/cancel/reassign

- Actor-derived online/assisted source, Patient current linked self profile and Receptionist-selected existing/no-account profile, final authorization/capacity re-check+audit.
- Scoped paginated list/detail and history start-date filters, independent inclusive from/to, no booking horizon for history; scoped404/endpoint403/error envelope.
- Reschedule same Doctor/service/patient/purpose/snapshots, self-overlap atomic; notices/deadline/past validation.
- Cancel CONFIRMED initially; checked-in cancellation wired in phase5. Reassign only non-past CONFIRMED same slot/different provider/reason; failure immutable and old Doctor scope revoked.

Exit: AC01/02/07/08/21/23 and exclusion race; no incomplete CHECKED_IN cancellation endpoint silently exposed.

### Phase 5 — Visit/reception lifecycle và follow-up

- Atomic check-in/visit/reception/audit/replay; early/late inclusive boundaries incl midnight, successful replay after window/progression and different-key concurrency.
- Manual NO_SHOW strictly after configured grace, optional entered reason, mandatory audit and arrival evidence. Timely arrival with processing delay not NO_SHOW; no override/status bypass. Late arrival receives owning WALK_IN flow/reference, no restoration of terminal appointment.
- Assigned Doctor start/complete linked scheduled-care step; completion keeps buffer, does not close visit. Checked-in cancel only no clinical activity, ends visit/reception atomic under cancel/start race.
- Visit-source follow-up after assigned Doctor exam completion/indication; patient derived, active service/accepting provider/current snapshot, source_visit_id and reason audit; no source appointment completion requirement.

Exit: AC03/04/09/10/11/18/19/20 and dependency rollback/replay/state races; owning care/arrival contracts production-ready.

### Phase 6 — Protected mutation và capacity integration

- Admin owning APIs query minimal impact evidence and reject invalid shifts/time-off/assignment mutations; no status transition/release. Resolution via Receptionist reschedule/reassign/cancel; account lock/hide/deactivate do not change bookings.
- Composition boundary coordinates appointments AND active care reservations and suitable rooms; valid self-link exception only, completed buffer/upcoming appointment protected. No room/device reservation in booking.
- Incident tracking remains operational owner; appointment writes resolution audit, not new incident CRUD or status.
- Owning visit SPEC defines expected durations/overrun response before this slice. Queue/lab/external results remain separate scope, no mandatory seam invented.

Exit: AC12–15, real booking/mutation/care/arrival races, no invalid committed bookings or double assignments; privacy of Admin impact projection.

### Phase 7 — Verification và closure

- Map every AC01–AC23 to test/evidence; add domain policy tests, owning-port contract/integration tests, HTTP security/projection tests, PostgreSQL persistence/concurrency và architecture checks.
- Use actual separate transactions/thread barriers to prove ordering, not mocks alone. Failure injection after visit/reception/audit/replay writes must roll back. Deadlock/timeout evidence validates guard protocol; exact infrastructure/test dependencies reviewed separately.
- Test clean DB and applied-V001 upgrade with existing registration/profile/account data; approved role mapping và unchanged old migration checksum. Verify nine config defaults/overrides/startup rejection, timezone/grid precision và exact boundaries.
- Update OpenAPI/source baseline mapping/owning contract documentation và regenerate TASKS after PLAN approval; task completion requires results, not checkbox-only.
- From otcms/backend, run exact project commands after phase0 command/toolchain checkpoint:

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

- Record totals, failures, PostgreSQL/Flyway integration actually executed versus skipped, command environment and exit codes. Green build with skipped required DB tests is not full verification.
- Independent post-code review, known limitations, security/privacy/migration review và Human closure. No auto-approval/commit/push from this Plan.

## 5. Verification matrix

| Area | SPEC acceptance | Required evidence |
|---|---|---|
| Identity/auth | AC01, AC02, AC17 | Current linked self authorization and other-profile denial, explicit roles/mapping/portal, no-account assisted profile, migration/backfill/regression. |
| Lifecycle/buffer | AC03, AC04 | Every authorized/unauthorized transition, terminal rules, early completion preserves end+buffer, exact adjacency/overlap/history. |
| Availability/time | AC05, AC06, AC22 | Four contexts, inactive/removed exceptions, overrides/startup fail, grid seconds/fraction/offset equivalence, notice/horizon/deadline/now boundaries. |
| Changes | AC07, AC08, AC21 | Same-Doctor snapshot reschedule+self-overlap; replacement same slot and before/equal/after now; failures retain fields/occupancy/audit. |
| Follow-up | AC09 | Visit-source care/exam/indication, open/walk-in source, implicit patient, provider eligibility. |
| Visit atomicity | AC10, AC11 | Check-in replay/single visit/failure rollback/progression; cancel vs clinical-start race/end reception. |
| Mutations/incidents | AC12, AC13, AC14 | Guard races, blocked mutation, unchanged account-lock/deactivate/hide history, operational incident no auto-release. |
| Active care/rooms | AC15 | Booking vs care assignment, same-patient own-link, completed buffer, upcoming bookings, no double room/Doctor allocation. |
| Privacy/HTTP | AC16, AC23 | Error DTO/correlation/log review, limiter/proxy/reset, catalog empty/list independent filters/history/scoped pagination. |
| Arrival/no-show | AC18, AC19, AC20 | 30/15 default+override/zero values, inclusive/strict boundary, midnight, timely arrival delayed processing, late WALK_IN reference, check-in/no-show/arrival race/fail-closed port. |

## 6. Risks và mitigation

| Risk | Mitigation / gate |
|---|---|
| Visit/patient/service/staff prerequisites chưa tồn tại | Owned contract/schema tasks và exit evidence; no production stubs. |
| Reciprocal module imports | Neutral DTO/coordination ports, composition orchestrator và architecture tests. |
| Coarse guard contention | Single-clinic MVP, short transactions/no network under lock, reviewed bounded timeouts; monitor contention before finer-grained redesign. |
| Writer không tham gia guard | Enumerate every participating command/owning mutation/identity-link change/arrival, architecture/code review và adversarial race tests. |
| btree_gist unavailable hoặc migration permissions thiếu | Phase0 capability/schema approval; block rather than weaken overlap protection. |
| Completed row accidentally releases buffer | Four-status exclusion và central occupied predicate; no time-dependent indexes/cleanup. |
| Arrival đúng giờ bị NO_SHOW | Recorded arrival port/fail-closed lookup serialized cùng state changes. |
| Repeated check-in duplicates visit | Unique link+replay records+same transaction, different-key concurrency tests. |
| Applied migration edited hoặc STAFF bulk mapping | Checksum evidence, additive migrations, approved authoritative per-account mapping. |
| Test command passes but skips required DB evidence | Record executed/skipped tests and require PostgreSQL integration proof. |
| Toolchain drift Java17/21 | Reconcile phase0 trước execution, preserve pom until approved decision. |

## Historical Action Record — PLAN v0.3.0 rewrite

- Authorized scope: user yêu cầu bổ sung review findings vào backend/sdd/features/feat-002-appointment-booking/PLAN.md.
- Shadow Plan: approved inputs/inventory -> ownership/prerequisite order -> concurrency/atomicity -> delivery phases -> AC01–23 verification.
- File boundary: PLAN.md only; CONTEXT/SPEC approved, TASKS và source code không sửa.
- Result: PLAN v0.3.0 đã được Owner Long phê duyệt; không run build/tests hoặc migrate DB trong bước phê duyệt tài liệu.
- Residual gates: PLAN review, phase0 DB/toolchain/timeouts/dependency checkpoints, owning prerequisite contracts/duration-overrun review, TASKS regeneration/review.
- Verification: document checks assert approved source versions, six-state lifecycle/no removed status, all AC01–23 mapped, guard/transaction/replay/arrival/buffer requirements present, exact backend verification commands, Java21 target và execution gates được ghi rõ.

## AI Agent Recommendation

- Status: PENDING HUMAN REVIEW
- Recommendation: Review PLAN v0.4.0 after revised CONTEXT/SPEC D03. Reconcile T008 code, V012 and downstream appointment consumers; do not execute dependent authorization from the historical plan.

## Human Final Review — v0.4.0

- Status: COMPLETED.
- Decision: APPROVED.
- Reviewer: Owner Long (explicit user approval).
- Reviewed at: 2026-10-14 (Asia/Bangkok).
- Approved version: 0.4.0.
- Evidence: User explicitly instructed, "Approve luôn cho cả CONTEXT, SPEC và PLAN sửa lại luôn T008" on 2026-10-14. This approves the self-only D03 plan; T008 still requires implementation, verification and its own completion review.

## Human Final Review — v0.5.0 architecture alignment

- Status: APPROVED for the architecture migration scope.
- Decision: Migrate FEAT-002 package boundaries to Spring layered architecture as required by Constitution ARCH-01 and Architecture Profile; preserve existing feature behavior and approved cross-feature contracts.
- Reviewer: Owner Long (user instruction: "Tạo task và triển khai migration FEAT-002 sang layered architecture").
- Reviewed at: 2026-10-14 (Asia/Bangkok).
- Follow-up: Record migration as T040, retain T040 verification/review gates, and do not change Constitution or existing frozen owner contracts.

## Architecture follow-up — remaining FEAT-002 package alignment

- User follow-up on 2026-10-14 identified that `patient`, `servicecatalog`, `staff`, `visit`, and `integration` still used `application/adapter` package paths after Appointment T040.
- Outcome: align those implementation packages to Constitution ARCH-01's Spring layered boundaries and make folder navigation consistent with the Appointment layout.
- Target mapping: Patient, Service Catalog and Staff include `controller`, `dto/request`, `dto/response`, `mapper`, `service`, `service/impl`, `repository`, and `domain` (plus `entity` for JPA). Visit has domain and repository boundaries only because its current FEAT-002 contribution is persistence/evidence integration; Integration keeps shared contracts, HTTP exception advice, and its persistence adapter. Empty layers are omitted.
- Constraints: preserve public HTTP routes/request-response semantics, SQL/schema/Flyway history, ownership boundaries, transaction/security behavior, and frozen `integration.contract` FQNs. Keep only folders that contain classes; do not introduce empty placeholder packages.
- Non-goals: business rule changes, API/schema changes, JPA redesign, or collapsing feature ownership into actor packages.
- Acceptance: no legacy `application` or `adapter` package declarations/imports in the scoped packages; controllers depend on service contracts; request/response DTOs and mapping live outside controllers; repositories do not depend on services/controllers; JPA entities live in feature `entity`; neutral cross-feature interfaces remain in `integration.contract`; compilation and affected tests pass.
- Shadow plan: classify each existing class by responsibility -> relocate package paths and declarations mechanically -> update production/test references -> refresh architecture docs and T041 trace -> static dependency checks -> Maven compile/focused tests and full suite if local PostgreSQL is available -> independent review.
- Delivery task: T041, added to TASKS on 2026-10-14. No Constitution/RFC change is required because ARCH-01 already governs the target.

## Architecture follow-up — JPA persistence standardization

- User decision on 2026-10-15: use JPA for the entire backend.
- Outcome: standardize production database access on Spring Data JPA repositories and JPA `EntityManager`/repository queries. No production class should use `JdbcTemplate`, `NamedParameterJdbcTemplate`, or `JdbcClient`; test fixtures may continue using JDBC for direct setup/assertions.
- Scope: 16 production Java classes currently use JDBC directly across Appointment, Auth, Integration, Patient and Visit. Service Catalog and Staff already use JPA for their owned persistence. Auth JDBC calls currently located in service classes must move behind Auth-owned repositories.
- Invariants: no Flyway migration or API behavior change; preserve ARCH-01 layer direction and feature data ownership; preserve READ COMMITTED transaction boundaries, integration guard ordering/timeouts, row locks, exclusion/unique constraint handling, atomic lifecycle + audit/replay writes, SQLSTATE/error translation, timestamp/UUID semantics and query pagination/filter behavior.
- Technical approach: add JPA entities for tables currently mapped manually; express standard CRUD through Spring Data repositories; use parameterized JPQL or JPA native repository queries only where PostgreSQL-specific locking, atomic predicates, projections, or exclusion-aware behavior cannot be represented safely otherwise. Native SQL is executed through JPA APIs, never Spring JDBC.
- Delivery task: T042.
- Phases: (1) inventory existing SQL/constraints and map entities; (2) migrate Auth/Patient/Visit/Integration persistence boundaries; (3) migrate Appointment command/query adapters while preserving guard-first locks and error mapping; (4) run focused then full PostgreSQL suite; (5) independent review for behavior/schema parity and zero production JDBC references; update architecture docs and trace.
- Acceptance: all production DB access is through Spring Data JPA or JPA `EntityManager`; zero direct JDBC API references under `src/main/java`; no schema/Flyway history changes; compile, focused tests and full local PostgreSQL tests pass; independent review finds no lost transaction, locking, ownership or error semantics.

## Historical Human Final Review — v0.3.0

- Status: APPROVED
- Decision: APPROVE
- Reviewer: Owner Long
- Reviewed at: 2026-10-13 (Asia/Bangkok)
- Follow-up: Approval áp dụng cho PLAN v0.3.0 only. Revised D03 in proposed v0.4.0 needs new review; historical approval does not authorize its implementation.
