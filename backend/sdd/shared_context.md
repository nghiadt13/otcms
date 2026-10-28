# Ngữ cảnh ownership, execution và API contract

# Version: 3.3.9
# Last-Updated: 2026-10-27
# Lead Agent: Orchestrator (@main-agent)
# Project Ownership: team
# Agent Execution: direct

> `Project Ownership` là nguồn canonical cho human governance và delivery: `solo` có một Human project owner; `team` có nhiều Human collaborator và là mặc định. `Agent Execution` là nguồn canonical cho cách thực thi: `direct` chạy task trong session hiện tại; `orchestrated` cho phép `/add-execute` điều phối một hoặc nhiều worker sau khi runtime capability được quan sát. Hai trục độc lập; không suy số Human từ số Agent hoặc ngược lại.
>
> Trong một transition release, legacy `# Collaboration Mode: solo|team` chỉ được đọc khi **cả hai** header mới không tồn tại: `solo` map thành `solo/direct`, `team` map thành `team/orchestrated`. Legacy state phải báo migration warning và không được tự rewrite. Invocation mới không nhận `--team-size`, `--project-ownership` hoặc `--agent-execution`.
>
> **Resolver bắt buộc cho mọi consumer:** (1) chỉ dùng canonical khi có đúng một header hợp lệ cho mỗi trục. (2) Khi cả hai canonical header vắng mặt, chỉ dùng đúng một legacy header `# Collaboration Mode: solo|team`; header duplicate hoặc malformed là `BLOCKED`. Map header hợp lệ thành legacy pair rồi báo migration warning. (3) Thiếu, trùng hoặc malformed canonical header là `BLOCKED`; không fallback legacy. (4) Khi có canonical header, legacy header không được dùng để resolve và phải được ghi migration cleanup. `/add-execute` persist source của resolution trong Execution Record, không ghi invocation override.

---

## 1. Human ownership và Agent execution

| Project ownership | Human accountability | Delivery policy |
| :--- | :--- | :--- |
| `solo` | Một Human project owner; họ có thể tự persist Human Final Review. | Human-owned direct delivery sau validation/review; không bắt buộc PR. |
| `team` | Nhiều Human collaborator; collaborator được project ủy quyền có thể persist Human Final Review. | Remote PR/review flow. |

| Agent execution | Hành vi |
| :--- | :--- |
| `direct` | Agent hiện tại thực thi `/add-execute`; vẫn cần Shadow Plan, Action Record, checkpoint, exact command và validation. |
| `orchestrated` | `/add-execute` điều phối worker sau khi runtime Claude Code `Agent` availability được quan sát; unavailable là `BLOCKED`, không fallback sang direct. |

Human Final Review, Architecture Profile, shared-contract ownership, material-state checkpoint, validation và delivery safety không bị nới bởi bất kỳ combination nào. Agent không self-approve hoặc `git push`.

---

## 2. Agent roles và ownership boundary

Các role dưới đây là mẫu cho `Agent Execution: orchestrated`, dùng được trong cả `solo` và `team`. Project ownership chỉ quyết định Human accountability; không giới hạn số Agent.

| Tên Agent | Vai trò / chuyên môn | Ownership boundary |
| :--- | :--- | :--- |
| `@lead-architect` | Governance Plan và shared contract | `.sdd/`, `CLAUDE.md`; sau khi template phát hành, `CONSTITUTION.md` chỉ thay đổi qua RFC đã `APPROVED` |
| `@backend-agent` | Usecase và domain | `src/domain/`, `src/usecase/` |
| `@infra-agent` | DB, cache và integration | `src/infra/`, `tests/integration/` |
| `@interface-agent` | HTTP/event adapter, DTO và presenter | `src/interface/` |
| `@tester-agent` | Verification và E2E | `tests/unit/`, `tests/e2e/` |

`/add-execute` tạo Execution Record, chọn task hoặc feature snapshot, cấp task execution grant, invoke worker khi route orchestrated được persisted và runtime capability đã observed, rồi validate integration. Chỉ contract owner được mutate shared artifact. Mỗi grant có opaque consumer reference do `/add-execute` cấp trước direct action hoặc handoff; mỗi worker nhận feature, task ID, task execution grant ID/attempt, consumer reference, `DISPATCHED` lifecycle eligibility, `UNCONSUMED` consumption state, host execution-claim evidence bound to feature/task/grant/route/consumer khi có, frozen contract version, profile version, binding liên quan, evidence, exact command được phép chạy, ownership/file boundary, allowed action/checkpoint, audit evidence reference và MCP policy profile. Markdown record chỉ là cooperative evidence; consumer reference không chứng minh host đã ngăn replay. Khi host claim unavailable, runtime enforcement phải ghi `UNVERIFIED` và không claim host-level replay prevention; stale/mismatch recorded claim là `BLOCKED`. Worker trả consumer và consumption evidence; chỉ `/add-execute` được allocate, revoke, retire hoặc renew grant. Worker chỉ request contract change, không tự apply; Agent không được thêm package, adapter, path hoặc command ngoài profile đã approved.

Execution evidence lưu dưới `## Current Handoff State` của feature `TASKS.md`. Runtime identity/enforcement phải ghi `VERIFIED` hoặc `UNVERIFIED` theo observed host evidence; `.sdd/mcp-config.yaml` là policy specification, không phải chứng cứ enforcement. Historical Dispatch Record giữ immutable evidence nhưng không cấp authority cho invocation mới.

## 2. Quy tắc thay đổi shared contract (Shared-contract mutation rule)

1. Mọi agent đọc frozen contract record trước khi đổi interface, event, DTO, state hoặc shared behavior.
2. Chỉ contract owner hoặc Lead được sửa contract. Sub-agent phát hiện drift hoặc cần thay đổi ngoài ownership phải dừng, ghi evidence và gửi change request cho owner.
3. Shared contract chỉ dùng technology-neutral shape cho đến khi adapter binding được Architecture Profile approve.
4. Contract change phải cập nhật producer, consumers, compatibility và linked evidence; sau đó quyết định `/sdd-trace` và `/sdd-sync` theo Action Record.

## 3. Bản ghi shared contract đã đóng băng (Frozen shared-contract record)

Dùng một record cho từng contract đã fixed. Giữ placeholder khi chưa có contract thay vì suy đoán API syntax.

```markdown
### Contract: <contract-id>
- Version: <version>
- Status: DRAFT | FROZEN | SUPERSEDED
- Producer: <feature or component>
- Consumers: <feature/component list or none>
- Owner: <Lead or named role>
- Shape and semantics: <technology-neutral data/behavior contract>
- Compatibility: compatible | migration required | pending decision
- Linked requirements/tasks: <REQ-XXX, T00X>
- Review and execution evidence: <review/action-record reference>
- Last sync: <date and reason>
- Unresolved decision: <none or decision owner>
```

*(Các shared contract đã được evidence xác nhận được ghi thành record bên dưới; contract chưa có approval evidence vẫn phải giữ trạng thái `DRAFT` hoặc `PENDING HUMAN REVIEW`.)*

### Contract: auth-persistence-schema
- Version: 1.1.0
- Status: FROZEN
- Producer: `feat-001-auth-login` T002 persistence boundary; additive account-type evolution delivered by FEAT-002 T006
- Consumers: T004 registration/OTP, T005 credential login, T006 session/security, T007 Google identity, T008 password recovery, T009 email delivery linkage, T010 persistence/security tests; FEAT-002 T006 and downstream auth consumers
- Owner: `@lead-architect` / auth persistence owner
- Shape and semantics: Auth-owned PostgreSQL persistence comprises account/profile, external identity, consent evidence, email-verification challenge, password-reset challenge, auth session and authentication-audit records. Account type is one of `PATIENT`, `RECEPTIONIST`, `DOCTOR`, `OWNER`, or `ADMIN`; `STAFF` is retired. Stable identifiers, required uniqueness/check invariants, current-challenge partial uniqueness, bounded/paged retention access and concurrency lookup paths are defined by immutable `V001__auth_schema.sql`, additive `V002__replace_staff_account_type.sql`, and the matching JPA/repository boundary. Other modules consume account/session identity through service contracts rather than direct repository access.
- Compatibility: `V001` is immutable. V002 checks for legacy `STAFF` rows and aborts before changing the account-type constraint if any remain; no role is inferred. Environments with legacy rows require reviewed per-account mapping before migration. V002 was applied successfully only to local `otcms_test_db` during T006 verification; production/Supabase rollout remains separate.
- Linked requirements/tasks: `REQ-AUTH-003..005`, `REQ-AUTH-013..020`, `REQ-AUTH-038..040`, `REQ-AUTH-048..050`, `REQ-AUTH-054..065`, `NFR-AUTH-006..008`, FEAT-001 T002, FEAT-002 T006 (AC01, AC02, AC17)
- Review and execution evidence: T002 Migration Checkpoint Resolution approved by Long at 2026-09-29T23:25:13.3408501+07:00; `E-feat-001-auth-login-T002-A3`; `.sdd/reviews/post-code-feat-001-auth-login-T002.md`; `.sdd/reviews/trace-feat-001-auth-login.md`; FEAT-002 T006 `E-feat-002-appointment-booking-T006-A3`; `.sdd/reviews/post-code-feat-002-appointment-booking-T006.md`; `.sdd/reviews/trace-feat-002-appointment-booking-T006.md`; `.sdd/reviews/sync-feat-002-appointment-booking-T006.md` Human approval received 2026-10-13.
- Last sync: 2026-10-13 — `/sdd-sync` after FEAT-002 T006 implementation, local migration/test verification, post-code review and trace approval
- Unresolved decision: Production/Supabase account inventory and migration rollout checkpoint remain pending; no production database was inspected or changed.

### Contract: auth-error-observability
- Version: 1.0.0
- Status: FROZEN
- Producer: `feat-001-auth-login` T003 common/config/auth DTO boundary
- Consumers: T004 registration, T005 credential login, T006 session/security, T007 Google identity, T008 password recovery, T009 email delivery and T010 contract/security tests
- Owner: `@lead-architect` / common auth contract owner
- Shape and semantics: Shared error responses use a nested `error` object containing safe `code`, `message`, UTC `timestamp` and correlation ID. Correlation IDs are bounded UUID values propagated through request attributes and response header. Sensitive credential-like values are redacted before client/log boundaries. Typed `app.auth` rate-limit configuration includes login, registration, resend, refresh, Google exchange and credential-failure defaults; generic authentication errors remain anti-enumeration safe.
- Compatibility: additive shared foundation; downstream consumers must use the nested error contract and typed configuration rather than introduce competing envelopes or unbounded secret-bearing errors.
- Linked requirements/tasks: `REQ-AUTH-048..052`, `NFR-AUTH-011`, `T003`
- Review and execution evidence: T003 Security Checkpoint Resolution approved by Long at 2026-09-30T00:05:53.8153673+07:00; `E-feat-001-auth-login-T003-A5`; `.sdd/reviews/post-code-feat-001-auth-login-T003.md` Human `APPROVED`; `.sdd/reviews/trace-feat-001-auth-login-T003.md`
- Last sync: 2026-09-30 — `/sdd-sync` after T003 trace
- Unresolved decision: Human disposition of the T003 trace and completion of dedicated contract/redaction/anti-enumeration tests in T010

### Contract: patient-profile-search-create
- Version: 1.0.0
- Status: FROZEN
- Producer: FEAT-002 T007 Patient owner
- Consumers: FEAT-002 T008 and appointment/reception workflows that require an existing profile
- Owner: Patient module; Human approval recorded by Owner Long on 2026-10-13 (Asia/Bangkok)
- Shape and semantics: Receptionist search requires a nonblank query and bounded pagination; match normalized full name or canonical phone; return only patientProfileId, fullName, phoneCanonical, and dateOfBirth. No unfiltered list. Receptionist may create a profile without an account from fullName, phone, phoneOwnerType, dateOfBirth, and gender; profile gets an independent UUID and accountId remains nullable unless separately linked by an approved identity workflow. Backend enforces Receptionist authorization. Similar name/phone is a possible match only; never auto-merge. Exclude account, consent, encounter, and clinical data from results.
- Compatibility: additive Patient-owned API/schema contract; profile UUID is independent of account identity; no change to historical appointment/profile records or implicit account linking.
- Linked requirements/tasks: FEAT-002 T007, T008; REQ-BOOK-001, REQ-BOOK-002, REQ-BOOK-018, REQ-BOOK-023
- Review and execution evidence: T007 migration checkpoint and search/create contract decision supplied by Owner Long on 2026-10-13; user-provided local `scripts/test-local.ps1` output reports V003/Hibernate validation and 80 tests passed (T007 PostgreSQL integration 4/4); post-code implementation review and requirement trace approved by Owner Long, recorded in `sdd/reviews/post-code-feat-002-appointment-booking-T007.md` and `sdd/reviews/trace-feat-002-appointment-booking-T007.md`.
- Last sync: 2026-10-13 — evidence-only sync approved by Owner Long; frozen contract v1.0.0 shape retained; evidence recorded in `sdd/reviews/sync-feat-002-appointment-booking-T007.md`.
- Unresolved decision: none for v1 contract shape; production migration/rollout remains outside this local checkpoint

### Contract: patient-dependent-authorization
- Version: 1.1.0
- Status: SUPERSEDED FOR FEAT-002 APPOINTMENTS — HISTORICAL CHECKPOINT
- Producer: FEAT-002 T008 Patient owner
- Consumers: FEAT-002 appointment authorization/query/change use cases and T014 coordination integration
- Owner: Patient module; checkpoint approved by Owner Long on 2026-10-13 (Asia/Bangkok)
- Shape and semantics: Patient authorization input is authenticated `accountId` plus target `patientProfileId`; authorize the account-linked own profile or a target dependent with a currently `CONFIRMED` care relationship initiated by the requesting patient account and confirmed only by the account linked to the dependent profile. An unlinked dependent profile receives no dependent authorization. `guardianConsent` collected for registration terms/privacy is not representative authority. Receptionist selection and matching demographic fields do not create or confirm a relationship. Revocation disables subsequent authorization without deleting patient or appointment history.
- Lifecycle boundary: request/confirm/revoke use cases belong to Patient. This contract does not approve legal-guardian, delegated, OTP, document-proof, receptionist-created, or no-account dependent workflows. No patient-facing relationship endpoint may claim success until the T014-owned shared coordination guard is wired.
- Consistency: relationship revocation and appointment authority-changing commands participate in the same shared transaction guard and commit ordering in PLAN §3.1. Revocation committed first denies the later command; command committed first was authorized at its commit order. No private Patient-only lock/advisory key may substitute for the shared guard. Authorization is current-state and fail-closed when the consistency boundary is unavailable.
- Compatibility: additive Patient-owned relationship data/API; profile IDs remain independent; relation revocation preserves all records and history.
- Linked requirements/tasks: D03; SPEC REQ-BOOK-005, REQ-BOOK-019–022; FEAT-002 T008 and T014
- Review and execution evidence: T008 Human Checkpoint approved by Owner Long on 2026-10-13. T014 guard completion is recorded in TASKS.md. A2's former request/confirm implementation was reconciled in A3 after revised D03 approval; see T008 execution records and `sdd/reviews/trace-feat-002-appointment-booking-T008.md`.
- Last sync: 2026-10-14 — v1.1.0 retained as historical checkpoint only; it is not an appointment authorization contract.
- Superseding decision: Owner Long explicitly approved CONTEXT v0.16.0, SPEC v0.8.0 and PLAN v0.4.0 on 2026-10-14. FEAT-002 appointment authority is current linked self-profile only. A family member helping B use B's account does not gain authority from A's account. This historical v1.1.0 contract must not authorize appointment consumers. Partial T008 request/confirm and `CONFIRMED_DEPENDENT` source behavior, plus V012, are reconciled out of appointment authorization. Family clinical-record/results access remains a separate scope/contract.
- Verification: user-run local PostgreSQL suite passed on 2026-10-14 (117/117, zero failures/errors/skips); T008 `CareRelationshipT008PostgresIntegrationTest` passed 4/4 including clean and V011 upgrade. V012 remains historical/inert; do not mutate applied Flyway history or delete retained relationship data to resolve this change.

### Contract: patient-appointment-self-authorization
- Version: 1.0.0 — FROZEN, approved by Owner Long on 2026-10-14 as part of SPEC v0.8.0.
- Producer: Patient owner; consumers: Appointment create/query/change and owning identity-link workflow.
- Shape: given authenticated PATIENT accountId and requested patientProfileId, return SELF only when the current profile link matches; otherwise return scoped not-found. A missing account link gives no Patient appointment authority. Receptionist-assisted use of an existing profile, including one without an account, remains a distinct staff contract.
- Consistency: guarded appointment mutations re-check the current link after guard acquisition. Any future mutation that changes a Patient account/profile link must use the same guard and ordering. Read-only queries re-check current state.
- Exclusion: no cross-account dependent booking, family clinical-record/results access or inference from care-relationship/guardian-consent records.

### Contract: feat-002-integration-ports
- Version: 1.0.0
- Status: FROZEN — LOGICAL CONTRACT FOUNDATION
- Producer: FEAT-002 T009 Integration
- Consumers: Patient, Service/Catalog, Staff, Visit/Reception/Clinical and Appointment owner modules; T014 neutral transaction integration
- Owner: FEAT-002 integration/architecture; owning modules retain their data and behavior
- Shape and semantics: technology-neutral Java records/interfaces in `fu.fa26.sep490.g58.otcms_be.integration.contract`: `PatientAuthorizationPort` accepts authenticated account and target patient profile and returns authorized profile/scope; `StaffDirectoryPort` resolves account to Doctor profile and separates visibility, new-booking acceptance and active-directory status; `ServiceCatalogPort` returns current audience-appropriate service/duration/buffer snapshot and Doctor assignment evidence; `VisitWorkflowPort` returns assigned-care, completed-exam, follow-up-indication and optional source-appointment evidence; `ReceptionWorkflowPort` returns same-patient recorded-arrival evidence; `AppointmentCapacityPort` accepts Doctor interval, visit, optional linked appointment and patient and returns conflict/self-link decision; `CoordinationGuardPort.acquire()` defines the transaction-scoped neutral guard boundary before owner reads/locks. Contract interfaces contain no Spring, HTTP, JPA or owner repository dependencies.
- Compatibility: additive logical contract foundation approved by Owner Long on 2026-10-13. Owner use case bindings are completed in T010–T012; implementations must call owner public use cases and must not access foreign repositories/entities/tables. T014 supplies the CoordinationGuardPort implementation and shared transaction wiring. No placeholder/no-op production adapter is permitted.
- Linked requirements/tasks: SPEC §5.1–5.5; FEAT-002 T009; owner bindings T008, T010–T012 and T014.
- Review and execution evidence: T009 logical foundation approved by Owner Long on 2026-10-13. Seven neutral ports were added/compiled; `IntegrationContractArchitectureTest` passed 1/1 after forced recompilation. Exact full suite compiled 92 production and 25 test sources, then local PostgreSQL authentication was unavailable in this shell. Owner bindings and integration tests remain pending by task assignment.
- Last sync: 2026-10-13 — Owner Long approved logical contract foundation v1.0.0; no owner implementation or cross-module wiring claimed.
- Unresolved decision: None for the logical foundation. T010–T012 owner use case bindings and T014 shared guard implementation/transaction wiring remain delivery tasks, not contract approval blockers.

### Contract: appointment-slot-availability-read-projections
- Version: 1.0.0
- Status: FROZEN v1.0.0 — Owner Long approved T018 implementation, trace, and contract on 2026-10-14; post-fix local test suite passed 135/135 on 2026-10-14
- Producer: Appointment slot engine (T018), consuming bounded projections from Staff and Visit owners
- Consumers: T019 public/staff availability and T020 reschedule/reassignment availability
- Owner: FEAT-002 integration/architecture; Staff retains shift/time-off ownership, Appointment retains appointment occupancy, Visit retains care-assignment ownership
- Shape and semantics: `StaffScheduleAvailabilityPort` accepts non-empty Doctor profile IDs and a positive half-open instant range, returning overlapping shifts and operational-time-off intervals grouped by Doctor. `AppointmentOccupancyQueryPort` is Appointment-local and returns only CONFIRMED/CHECKED_IN/IN_PROGRESS/COMPLETED occupied intervals overlapping the same range, with an optional current-appointment exclusion. `VisitCareReservationQueryPort` accepts Doctor IDs and a positive half-open range and returns overlapping RESERVED/IN_PROGRESS care intervals with patient and originating-appointment identity. All projections are read-only and bounded by explicit Doctor IDs and the clinic-local inclusive date range converted to UTC `[fromStart, dayAfterToStart)`; they expose no clinical payload or patient demographics. Slot generation applies half-open overlap, service duration+buffer, one-shift coverage, time-off/appointment/care exclusion, and permits self-exclusion only for the exact appointment row or care reservation linked to the same patient and appointment.
- Compatibility: additive technology-neutral read boundaries; no existing frozen port shape, API endpoint, schema or migration changes. Empty Doctor-ID input is short-circuited by the appointment handler; adapters reject unbounded/invalid ranges. Existing appointment status occupancy rules match V013 and CONTEXT §4.4. Snapshot contexts preserve stored duration/buffer and exclude only their own appointment; current-service contexts use the current catalog values supplied by T019/T020.
- Linked requirements/tasks: SPEC REQ-BOOK-007–015; CONTEXT §4.3–4.4; TASKS T018–T020.
- Review and execution evidence: independent post-code review passed (T018 A2); Owner Long approval received 2026-10-14; post-fix local helper run passed 135 tests with 0 failures/errors/skips.
- Last sync: 2026-10-14 — v1.0.0 frozen after Owner Long approval; implementation review and post-fix local verification passed.
- Unresolved decision: None. T019/T020 may consume the approved contract.

### Contract: appointment-availability-provider-queries
- Version: 1.0.0
- Human decision: `APPROVE implementation and trace T019; approve and freeze the provider-query contract` received from Owner Long on 2026-10-14 (Asia/Bangkok).
- Status: FROZEN for downstream use.
- Producer: Staff and Service/Catalog owner adapters.
- Consumer: Appointment T019 public and staff availability query.
- Owner: FEAT-002 integration; Staff owns Doctor profile projection, Service/Catalog owns active Doctor-service assignment evidence.
- Shape and semantics: `StaffAvailabilityProviderQueryPort.findAcceptingDoctorsInActiveDirectory()` returns only Doctor profile ID, display name and public-visibility flag for Doctors whose active-directory and accepting-new-bookings flags are both true. It excludes account IDs, work contact, account status and reasons. Account lock/status does not imply schedule unavailability (D07). `ServiceDoctorAssignmentQueryPort.findActiveDoctorProfileIds(serviceId)` returns IDs for active assignments owned by the selected service. Appointment intersects the two bounded read projections; public additionally requires `publiclyVisible=true`, while staff does not. Selected IDs outside that eligible intersection fail with `DOCTOR_NOT_ELIGIBLE`; no matching candidates returns `{slots:[]}`.
- Compatibility: additive read-only Java ports in `integration.contract`; no frozen T009/T011/T018 port shape changes, schema migration, or owner table access from Appointment. Owner adapters query only their own repositories.
- Linked requirements/tasks: SPEC REQ-BOOK-002–004, REQ-BOOK-043, REQ-BOOK-049; TASKS T019.
- Review and execution evidence: independent code review passed; Owner-provided local suite passed 146/146 tests on 2026-10-14; Owner Long approved implementation and trace and froze this additive contract.
- Last sync: 2026-10-14 — provider-query contract approved and frozen with T019 A1.
- Unresolved decision: None.

### Contract: clinic-service-catalog
- Version: 1.0.0
- Status: FROZEN v1.0.0 — shape approved by Owner Long on 2026-10-14
- Producer: FEAT-002 T010 Service/Catalog
- Consumers: public/staff catalog callers and appointment eligibility/snapshot consumers through `ServiceCatalogPort`
- Owner: Service/Catalog module; Admin owns service and Doctor-service assignment maintenance
- Shape and semantics: service identity, name, optional description, consultation duration, buffer duration, online-bookable flag and active state. Public `GET /api/v1/clinic-services` returns `{items:[...]}` containing only active online-bookable services; Receptionist/Doctor staff catalog returns all active workflow services. Admin create/update/activate/deactivate is backend-authorized. Deactivation is soft, preserves service identity/history and existing appointment snapshots, and removes the service only from new-booking catalogs. Until T032 impact checks are implemented, a deactivation without verified impact evidence is rejected; the API must not report success merely because it can set `active=false`. Doctor assignment rows are keyed by service ID and Doctor profile UUID; only a valid Doctor profile may be assigned. Assignment removal is rejected until T032 can verify protected-booking impact. No assignment mutation endpoint is currently exposed.
- Compatibility: V004 adds the catalog/assignment tables and V006 adds a restrictive FK from `doctor_service_assignments.doctor_profile_id` to the Staff-owned Doctor profile; V001–V005 remain unchanged by that link migration. Existing appointments retain snapshots/history when the service is deactivated. Assignment removal remains unavailable until T032; no applied migration or assignment API is changed by this contract sync.
- Linked requirements/tasks: D02/D08; SPEC REQ-BOOK-001, REQ-BOOK-003, REQ-BOOK-015, REQ-BOOK-036–039, REQ-BOOK-043, REQ-BOOK-052; FEAT-002 T010/T011/T032.
- Review and execution evidence: user-run local `scripts/test-local.ps1` applied V004 successfully on `127.0.0.1:5432/otcms_test_db`; Hibernate/JPA validation and V004 clean-schema migration passed. After correcting the T007 fixture targets and T010 request-scoped principal auth, the full post-fix suite passed 88/88 and T010 PostgreSQL integration passed 4/4. Owner Long approved T010 implementation and requirement trace on 2026-10-13; evidence is in `sdd/reviews/post-code-feat-002-appointment-booking-T010.md` and `sdd/reviews/trace-feat-002-appointment-booking-T010.md`. T011 local verification later passed the V006 identity-FK migration and clean install.
- Review and checkpoint: Owner Long approved the exact v1.0.0 shape and T010 safety checkpoint on 2026-10-14: Admin service create/update/activate; public/staff catalog audience rules; soft deactivation preserving identity/history/snapshots; reject a potentially booking-impacting deactivation without impact verification until T032; assignment only to valid Doctor profiles; reject assignment removal until T032.
- Last sync: 2026-10-14 — `clinic-service-catalog` v1.0.0 frozen; no assignment mutation endpoint or production migration was introduced.
- Unresolved implementation gate: fail-closed deactivation behavior must be verified in T010; assignment-removal impact check remains T032. Existing assignment FK enforces Doctor-profile existence.

### Contract: staff-doctor-directory
- Version: 1.0.0
- Status: FROZEN v1.0.0 — owner approved; guard-dependent commands remain implementation gates
- Producer: FEAT-002 T011 Staff/Account
- Consumers: public Doctor directory and appointment eligibility via `StaffDirectoryPort`; Service/Catalog validates assignment Doctor identity through the Doctor-owned profile identity.
- Owner: Staff/Account module; Admin maintains Doctor profiles, directory/publication, acceptance, shifts and operational time off.
- Shape and semantics: a Doctor profile references one existing DOCTOR account and remains distinct from account access. Profile projection includes independent `publiclyVisible`, `acceptingNewBookings`, and `inActiveDirectory` states, plus requested-interval shift coverage and operational-time-off overlap. Account status is not used as a proxy for new-booking acceptance or existing appointment serviceability (D07). Public directory includes only published and in-directory profiles and returns profile ID, full name, photo URL and professional information; it excludes account ID, work contact, account status and eligibility reasons. Admin creates a profile for an existing Doctor account; new profile defaults to unpublished and not accepting bookings. Doctors read their own shifts; Admin may read a Doctor's shifts over a bounded interval. Shift intervals are half-open instants and may not overlap for a Doctor.
- Compatibility: V005 creates Doctor profile, shift and operational-time-off tables; V006 adds a restrictive FK from the V004 Doctor-service assignments to the Staff-owned profile. V001–V004 remain unchanged. Existing T010 assignment rows that do not map to a Doctor profile cause V006 to fail closed.
- Mutation boundary: publication, acceptance, directory state, shift and time-off mutations are withheld until T014 shared transaction guard and T032 protected-booking impact resolution are available. No account status mutation or credential provisioning is introduced by this contract.
- Linked requirements/tasks: D02/D04/D06/D07; SRS UC17–UC20, UC26–UC29, BR-13, BR-22–BR-30; FEAT-002 T011/T014/T032.
- Review and execution evidence: focused Staff provisioning unit tests 2/2 and architecture test 1/1 passed. After correcting the test to use the shared error envelope path `$.error.code`, the user-run local suite passed 93/93 with 0 failures/errors/skips; `DoctorProfileT011PostgresIntegrationTest` passed 3/3, including clean V001→V006 migration and constraints. Owner Long approved implementation and trace and separately approved this contract v1.0.0 on 2026-10-13; records are in `sdd/reviews/post-code-feat-002-appointment-booking-T011.md` and `sdd/reviews/trace-feat-002-appointment-booking-T011.md`. No production database was contacted.
- Last sync: 2026-10-13 — Owner Long approved and froze `staff-doctor-directory` v1.0.0; runtime mutation gates remain assigned to T014/T032.
- Unresolved decision: None for contract v1.0.0. T014/T032 eligibility and schedule writer integration remain delivery tasks.

### Contract: visit-reception-clinical
- Version: 1.2.0
- Status: FROZEN — CONTRACT v1.2.0; approved by Owner Long (Visit/Reception/Clinical contract owner/Lead) on 2026-10-27.
- Producer: Visit/Reception/Clinical; T012 owns the base visit/reception contract and T045 adds queue persistence
- Consumers: Appointment check-in/no-show workflows through `ReceptionWorkflowPort` and `RecordedArrivalQueryPort`; Visit queue creation/exit/query workflows; Receptionist queue read; Patient authorized own-visit progress read. T047–T049 implement the downstream atomic workflow/read integrations.
- Owner: Visit/Reception/Clinical module; Human contract owner/Lead: Owner Long, confirmed 2026-10-27.
- Shape and semantics: Visit source is `SCHEDULED` or `WALK_IN`; `WALK_IN` remains a Visit source and never creates an appointment. A visit retains patient, recorded arrival, check-in time, creator, optional originating appointment, optional prior appointment for WALK_IN, and open/closed state. At most one scheduled visit is linked to an originating appointment and each visit has one initial reception record. Only WALK_IN visits enter the Visit-owned reception FIFO; scheduled check-in creates no queue row. A queue entry starts `WAITING`; queue position is ordered FIFO by server `arrived_at`, then stable queue-entry ID. Multiple historical entries per visit are retained, with at most one active `WAITING` entry. Leaving reception is recorded as `LEFT_RECEPTION` with reason `CARE_STARTED`, `CANCELLED`, or `VISIT_CLOSED`; transition history is append-only and generated by database triggers using server time. T045 queue persistence derives visit source and arrival from the Visit row, binds actor identity to the authenticated principal and active Receptionist account, and locks the Visit row before validating its open state. T045 provides a queue-entry persistence foundation only: atomic walk-in enqueue belongs to T047, lifecycle exit orchestration to T048, and scoped queue/progress reads to T049. Walk-in retries remain actor/key/fingerprint scoped and must not use patient-wide uniqueness. Recorded arrival remains distinct from check-in time. The read-only `RecordedArrivalQueryPort.requireRecordedArrival(...)` returns minimal evidence only when appointment and patient IDs match and `arrivedAt` is inside the inclusive permitted window; missing/mismatched/out-of-window evidence fails closed. `ReceptionWorkflowPort.checkInAtomically(...)` remains the separate guarded T027 write contract. Follow-up evidence derives from the visit patient, authenticated Doctor care assignment, completed examination and recorded follow-up indication; patient and ordering Doctor are never client-selected. Care assignment persistence takes an explicit interval; this contract does not invent expected-duration or overrun policy.
- Compatibility: V007/V008 owner tables and restrictive appointment/patient composite FKs remain unchanged. Additive V023–V025 migrations provide queue entry/history and guards; V020–V022 remain sequential prerequisites. No applied migration is rewritten and no new public endpoint or Patient/Appointment direct persistence access is introduced by T045. Database trigger protection assumes the application role cannot disable triggers or alter schema; a PostgreSQL owner/superuser can bypass those controls.
- Mutation boundary: Visit owns queue entry/history persistence. Queue writes run in a transaction, acquire the Visit row lock before checking source/open state, and insert the entry; database triggers create the initial and allowed transition history. Auth public account-access service verifies active Receptionist authority. Appointment and Patient modules consume Visit-owned APIs/contracts and do not access Visit repositories/entities. T047 must atomically join WALK_IN visit creation to queue insertion; T048 must atomically couple queue exit with Visit lifecycle; T049 owns scoped reads.
- Linked requirements/tasks: FEAT-002 SPEC REQ-BOOK-061 and REQ-RECEPTION-004/006/007/008; SRS UC22–UC23 and UC27; T044/T045/T047/T048/T049. The approved scheduled-arrival interpretation is a product-specific UC22 variance that remains subject to SRS reconciliation before delivery.
- Review and execution evidence: T044 reception contract approval and T045 preflight, post-code, audit and trace decisions are held locally and SHA-256 pinned in `C:/Users/Admin/AppData/Local/Temp/otcms-t045-review-evidence-20261027.json`. Owner Long approved this v1.2.0 sync on 2026-10-27T17:37:34+07:00. T045 focused PostgreSQL test passed 6/6 and full local `clean verify` passed 293/293 at V025 in the previously approved evidence; these tests were not rerun for this documentation sync. Current T045-scope audit and trace reports dated 2026-10-27 are approved and hash-pinned in the same manifest.
- Last sync: 2026-10-27T17:37:34+07:00 — Owner Long approved and synchronized v1.2.0 to reconcile the approved T044 queue behavior and T045 persistence implementation; registry updated, full remote PR validation remains outstanding.
- Unresolved decision: Reconcile the product-specific UC22 variance with the SRS before delivery. T047/T048/T049 work remains pending. The stronger direct-SQL threat boundary requires a restricted application database role; a database owner/superuser can bypass triggers.
### Contract: doctor-account-access-read
- Version: 1.0.0
- Status: FROZEN — approved by Owner Long on 2026-10-14 for FEAT-002 T023
- Producer: Auth account-access boundary
- Consumers: Appointment Doctor list/detail authorization
- Owner: Auth owns account type/status and current Doctor account access; Appointment consumes only this port.
- Shape and semantics: `DoctorAccountAccessPort.isActiveDoctorAccount(accountId)` returns true only when the Auth-owned account exists, has account type `DOCTOR`, and current status `ACTIVE`. It returns only a boolean decision; status, email and other account data remain inside Auth. Missing accounts, type mismatch, lock, disabled/archived and all non-active states deny access. The read performs no mutations and does not change appointment/capacity state.
- Compatibility: additive Java integration contract; no schema or API migration. Auth persistence stays private. Appointment must not access Auth tables, repositories or entities.
- Linked requirements/tasks: FEAT-002 SPEC REQ-BOOK-004, CONTEXT D07, T023.
- Review and execution evidence: Owner Long approved adding the Auth-owned read-only access port for Doctor status checks on 2026-10-14. Implementation, local suite (186/186), dedicated PostgreSQL HTTP tests (6/6), independent review and approved trace are recorded in T023 A1 execution/review/trace artifacts; Owner Long approved implementation and trace on 2026-10-14.
- Last sync: 2026-10-14 — T023 implementation, PostgreSQL verification, and Human final approval.
- Unresolved decision: None for v1.0.0.

## Action Record — FEAT-002 T045 Visit contract sync 2026-10-27
- Actor: Owner Long (Visit/Reception/Clinical contract owner/Lead) and Agent.
- Approved scope and file boundary: update only `backend/sdd/shared_context.md` Visit contract and FEAT-002 registry status; no product code, migrations, endpoints or runtime configuration changed.
- Profile binding and exact commands: Spring layered modular monolith, Visit-owned JPA/Flyway contract; no verification command required for this documentation-only sync.
- State-change category: shared/public contract documentation.
- Human checkpoint: Owner Long approved contract v1.2.0 and confirmed contract owner/Lead authority on 2026-10-27; T044/T045 approved scope and current T045 audit/trace reports are pinned by the external local SHA-256 manifest.
- Task execution grant: N/A — documentation contract synchronization.
- Host execution-claim evidence: N/A.
- Actions and result: bumped shared_context metadata 3.3.8→3.3.9 and `visit-reception-clinical` 1.1.0→1.2.0; recorded WALK_IN-only queue, FIFO/WAITING/history invariants, actor binding, Visit row lock, migration compatibility, T047–T049 boundaries and residual privileged-role risk; updated feature registry.
- Validation route: SDD sync completed manually per `sdd-sync` workflow; `git diff --check` passed for the repair worktree. Full feature trace and remote PR validator remain outstanding.
- Post-code review: N/A — no implementation behavior changed.
- Residual blocker: remote branch still needs the approved SDD changes, then validation from a clean worktree matching remote; reconcile the approved UC22 variance with SRS before delivery.
- Sync-back decision: shared Visit contract and FEAT-002 registry updated; rerun full `/sdd-trace --feature=feat-002-appointment-booking --diff` after branch publication.
