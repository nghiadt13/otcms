# Feature Specification — FEAT-002 Appointment Booking

## 1. Metadata

- Version: 0.9.0
- Status: APPROVED — Owner Long, 2026-10-18
- Source: CONTEXT.md v0.16.0 — APPROVED by Owner Long on 2026-10-14; v0.15.0 approval is historical.
- SRS: docs/requirements/baselines/report3-2026-10-12/BASELINE.md.
- Delivery: backend/API; frontend ngoài phạm vi.
- Dependencies: auth compatibility; patient current linked self-profile authorization; service/catalog; staff/account; visit/reception/clinical public contracts.
- Contract v0.4.0 bị superseded; approval cũ chỉ là lịch sử. This v0.9.0 adds a Visit-owned Receptionist WALK_IN API; v0.8.0 approval remains historical for D03 and does not approve this API addition.
- API/DTO/error behavior ngoài D03 giữ nội dung v0.8.0 làm cơ sở, ngoại trừ additive Visit WALK_IN API contract in this revision.

## Change Log

| Version | Change |
|---|---|
| 0.9.0 | Add Receptionist-only Visit WALK_IN creation API with idempotency, server arrival timestamp with `checked_in_at = arrived_at`, same-patient prior appointment validation, additive append-only Visit audit table migration, and atomic Visit/reception/audit/replay persistence. No appointment status/occupancy mutation. |

## 2. Identity, lifecycle và authorization

### 2.1 Identity

- Account types liên quan: PATIENT, RECEPTIONIST, DOCTOR, OWNER, ADMIN; một type/account trong MVP.
- Patient profile có UUID độc lập và optional unique account link; appointment dùng patient_profile_id.
- Patient chỉ quản lý lịch hẹn của patient profile hiện gắn với tài khoản PATIENT đã xác thực. Patient module sở hữu kiểm tra liên kết hiện tại; nếu A giúp B sử dụng tài khoản B thì appointment vẫn thuộc actor/profile B. Tài khoản A không nhận quyền trên hồ sơ B. Phương thức xác thực/hỗ trợ thao tác A→B cần policy riêng từ Auth/product. Không suy quyền đặt lịch từ quan hệ gia đình, `guardianConsent` hoặc care relationship.
- Booking sources: PATIENT_ONLINE, RECEPTIONIST_ASSISTED, DOCTOR_FOLLOW_UP. WALK_IN là visit source, không appointment source.
- doctor_id tham chiếu Doctor profile; principal resolve qua public staff/security contract.

### 2.2 Lifecycle

Statuses duy nhất: CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW.

| From | To / command | Actor và điều kiện |
|---|---|---|
| CONFIRMED | CHECKED_IN / check-in | Receptionist; atomic visit/reception integration. |
| CONFIRMED | CANCELLED / cancel | Authorized Patient hoặc Receptionist; deadline/reason. |
| CONFIRMED | NO_SHOW / status | Receptionist; now > startTime + configured late grace; manual confirmation/audit. |
| CHECKED_IN | IN_PROGRESS / status | Assigned Doctor; visit/care cho phép bắt đầu. |
| CHECKED_IN | CANCELLED / cancel | Receptionist; chưa clinical activity và end visit/reception atomic. |
| IN_PROGRESS | COMPLETED / status | Assigned Doctor; scheduled service hoàn tất. |

Reschedule/reassign chỉ CONFIRMED, giữ CONFIRMED. COMPLETED/CANCELLED/NO_SHOW terminal; unlisted transitions rejected. COMPLETED không đóng toàn visit.

Occupied statuses: CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED. Interval `[start_time,end_time + buffer_minutes)` dùng chung availability/commands/DB. Completion không truncate snapshot hoặc tính lại buffer từ completedAt. Elapsed intervals không chặn future slots. CANCELLED/NO_SHOW không occupy. Actual ongoing/overrun care phải xét riêng qua visit capacity.

### 2.3 Authority

| Capability | Authority |
|---|---|
| Public catalog/availability | Public; active online service/public-selectable Doctor/minimal payload. |
| Staff catalog | Receptionist, Doctor. |
| Staff availability | Receptionist, Doctor; Admin inspect only. |
| Appointment list/detail | Patient profile hiện gắn với tài khoản đăng nhập; assigned Doctor; Receptionist toàn clinic. |
| Profile search/create | Patient module; Receptionist theo owning contract. Family clinical-record/results sharing được quyết định riêng ngoài FEAT-002. |
| Reschedule/cancel | Authorized Patient hoặc Receptionist; Patient không đổi CHECKED_IN. |
| Check-in/no-show | Receptionist. |
| Start/complete | Assigned Doctor. |
| Reassignment | Receptionist only. |
| Service/profile/shift/time-off/assignment maintenance | Admin qua owning APIs. |
| Mutation impact | Admin minimal evidence trong workflow; không general appointment read. |
| Account lock | Admin theo account contract; không tự đổi appointment/capacity. |
| Owner | Oversight; không appointment read/maintenance/reassignment. |

Endpoint actor denied -> ACCESS_DENIED; authorized actor nhưng absent/out-of-scope resource -> scoped 404. Command/replay re-authorize; không tiết lộ sensitive state trước scope check.

## 3. Policy và functional requirements

### 3.1 Configuration

| Key dưới appointment.booking | Default | Validation |
|---|---:|---|
| max-horizon-days | 30 | Positive integer |
| online-minimum-notice-minutes | 120 | Non-negative integer |
| patient-change-deadline-minutes | 1440 | Non-negative integer |
| check-in-early-minutes | 30 | Non-negative integer |
| check-in-late-grace-minutes | 15 | Non-negative integer; shared check-in/no-show boundary |
| slot-interval-minutes | 15 | Positive, divides 1440 |
| clinic-time-zone | Asia/Ho_Chi_Minh | Valid IANA zone |
| public-rate-limit.requests | 60 | Positive integer |
| public-rate-limit.window-seconds | 60 | Positive integer |

Bind AppointmentBookingProperties; invalid values fail startup. Injected Clock là business time authority. Grid neo midnight clinic-local date; persist UTC timestamptz, request RFC3339 offset, response UTC Z. Defaults không hard-code thành rules.

### 3.2 Catalog/identity

- **REQ-BOOK-001 — WHEN** public catalog, SHALL trả active online services, không PII/contact/unavailability reasons.
- **REQ-BOOK-002 — WHEN** availability, SHALL return Doctor-specific slots theo workflow §4.2.
- **REQ-BOOK-003 — WHEN** create mới, SHALL require active workflow-allowed service; existing reschedule/reassign được giữ inactive service khi other rules pass.
- **REQ-BOOK-004 — WHEN** eligibility, SHALL tách visibility/newBookingAcceptance/account access/existing eligibility. Hidden chặn public selection; removed directory chặn mọi new booking/follow-up/replacement nhưng cho same-Doctor reschedule. Lock chỉ chặn Doctor access, không tự thay appointments/capacity.
- **REQ-BOOK-005 — WHEN** Patient create, SHALL derive patientProfileId from the current authenticated PATIENT account link; supplied other profileId SHALL be denied with scoped error. No cross-account dependent booking or relationship-derived authority.
- **REQ-BOOK-006 — WHEN** Receptionist create, SHALL use existing profile kể cả không account; source RECEPTIONIST_ASSISTED.
- **REQ-BOOK-043 — WHEN** staff catalog, SHALL active allowed services; follow-up service phải có eligible accepting provider.
- **REQ-BOOK-044 — WHEN** Public request, SHALL separate fixed-window IP buckets catalog/availability; trust forwarding only configured proxies; 429/Retry-After ceil remaining seconds minimum1; independent reset/bounded cleanup.
- **REQ-BOOK-049 — IF** missing dates/reversed/range > max-horizon-days+1 inclusive, THEN VALIDATION_FAILED; outside today..today+horizon -> BOOKING_WINDOW_VIOLATION.

### 3.3 Time/slots/snapshots

- **REQ-BOOK-007–009 — ALWAYS** SHALL use Clock/zone; inclusive dates -> half-open UTC `[fromStart,dayAfterToStart)`.
- **REQ-BOOK-010 — WHEN** create/reschedule, SHALL enforce clinic-local horizon.
- **REQ-BOOK-011 — WHEN** Patient create/reschedule, SHALL new start >= now+online notice; equality accepted.
- **REQ-BOOK-012 — WHEN** Receptionist create/reschedule hoặc Doctor follow-up, SHALL bypass notice nhưng enforce horizon/availability và start>=now. Mọi create/reschedule reject past start.
- **REQ-BOOK-013 — WHEN** generate/reserve, SHALL convert startTime to clinic timezone and enforce configured grid anchored at local midnight, seconds=0 and fractional seconds=0. Apply to create/follow-up/reschedule; reject VALIDATION_FAILED without rounding. Reassignment preserves stored timestamp.
- **REQ-BOOK-014 — WHEN** slots, SHALL fit consultation+buffer inside one shift, avoid time off/occupied appointments/visit capacity; exclude chính appointment đang change, không others.
- **REQ-BOOK-015 — WHEN** create, SHALL derive end/duration/buffer; existing reschedule/reassign giữ snapshots kể cả service đổi/deactivate.
- **REQ-BOOK-051 — WHEN** completion, SHALL preserve occupied range through stored end+buffer; actual overrun không tự sửa snapshots/assume Doctor free.

### 3.3.1 Check-in window và NO_SHOW — confirmed policy

- **REQ-BOOK-055 — WHEN** first check-in, SHALL require CONFIRMED and `startTime - check-in-early-minutes <= now <= startTime + check-in-late-grace-minutes`, cả hai boundaries inclusive. Dùng injected Clock instant; không hạn chế cùng calendar date nếu configured window vượt midnight. Valid successful replay theo §5.3 không bị reject chỉ vì now đã vượt window.
- **REQ-BOOK-056 — WHEN** Receptionist marks NO_SHOW, SHALL require CONFIRMED và `now > startTime + check-in-late-grace-minutes`. Không automatic scheduler trong MVP; NO_SHOW nghĩa là không có arrival được ghi nhận trong inclusive check-in window; Receptionist phải xác minh evidence qua reception public contract. Arrival đúng cửa sổ nhưng chưa được xử lý check-in không được NO_SHOW. Appointment CONFIRMED alone không đủ để kết luận absence. Reason nhập tay optional, nếu supplied theo reason validation; actor/time/action/correlation audit luôn mandatory.
- **REQ-BOOK-057 — ALWAYS** check-in window SHALL không kéo dài start/end/occupiedUntil/duration/buffer. Reception/visit kiểm tra actual capacity trước khi bắt đầu khám; arriving within grace không tự đảm bảo full consultation time hoặc cho phép chồng lịch kế tiếp.
- **REQ-BOOK-058 — WHEN** late arrival vượt grace, SHALL reject first check-in; Receptionist có thể reschedule khi CONFIRMED theo normal time/availability/reason rules hoặc tiếp nhận WALK_IN theo D11. Không bypass bằng status endpoint/operational override. NO_SHOW terminal, không reschedule/reassign/restore; later walk-in preserves old appointment history.
- **REQ-BOOK-059 — WHEN** late arrival được reception/visit tiếp nhận WALK_IN, SHALL lưu server-derived `arrivedAt` và validated `priorAppointmentId` khi lịch cũ xác định được (reference nullable chỉ khi không xác định được lịch), theo Visit-owned API REQ-BOOK-061; cùng patient, không biến thành visit check-in của lịch cũ. Reference không thay appointment status, source hoặc occupancy; Visit module owns it. CONFIRMED cũ phải được xử lý riêng qua authorized command, không tự release khi tạo walk-in.
- **REQ-BOOK-060 — WHEN** check-in/no-show chạy đồng thời, SHALL serialize state và policy validation cùng writes; chỉ một transition thắng. Loser thấy changed state trả APPOINTMENT_NOT_CHANGEABLE; check-in replay vẫn theo §5.3. Không duplicate visit/audit hoặc NO_SHOW trên appointment đã checked-in.
- **REQ-BOOK-061 — WHEN** Receptionist accepts a WALK_IN, SHALL use the Visit-owned `POST /api/v1/visits/walk-ins` API with an existing `patientProfileId`, optional `priorAppointmentId`, and required nonblank `Idempotency-Key` up to 128 characters; actor identity and `arrivedAt` are server-derived. Visit SHALL validate that any prior appointment belongs to the same patient and atomically persist the WALK_IN visit, initial reception, append-only `WALK_IN_CREATED` audit, and replay result under the shared transaction/coordination guard. The new additive Flyway migration SHALL add a Visit audit table with actor account, visit, action, occurrence timestamp and correlation ID; applied migrations SHALL remain unchanged. WALK_IN SHALL set `checked_in_at` to the same server-derived instant as `arrived_at`. It SHALL NOT modify the referenced appointment's status or occupancy. Same actor/key/request replays the stored response; same actor/key with a different request returns `IDEMPOTENCY_CONFLICT`.
- **REQ-BOOK-062 — WHEN** assigned Doctor starts Visit care or extends active care, SHALL use Visit contract v1.3.0. Start uses configured per-step duration and fails closed when missing. Active care occupies `[actualStart,expectedEnd)`; extension for SCHEDULED and WALK_IN Visits checks only the new segment `[max(now,currentExpectedEnd),newExpectedEnd)` and never changes the linked Appointment status, booked interval, duration or buffer. Scheduled start/completion remain through Appointment's atomic lifecycle API; WALK_IN start/completion use Visit endpoints. Every write is Doctor-authorized, guarded and audited atomically; conflict rejects assignment/extension mutation but may append the minimal coordination audit in REQ-BOOK-063; dependency outage fails closed without audit. Room capacity remains gated by separate Room ownership.
- **REQ-BOOK-063 — WHEN** Doctor's start/extension capacity check conflicts, SHALL reject with `VISIT_CAPACITY_NOT_AVAILABLE` and append a minimal Visit-owned `CAPACITY_CONFLICT` audit signal containing actor, Visit, attempted step, server time and correlation; existing appointment/care state remains unchanged. An assigned Doctor may append the same signal through the conflict endpoint for Receptionist coordination. No incident CRUD or appointment mutation is added.

Default example: appointment 09:00 cho first check-in từ 08:30 đến đúng 09:15; chỉ sau 09:15 mới được NO_SHOW. Overrides phải thay cả check-in-late boundary và NO_SHOW threshold bằng cùng property, tránh gap/overlap.

### 3.4 Commands/queries

- **REQ-BOOK-016–017 — WHEN** reserve/change, SHALL re-check authorization/eligibility/capacity atomic; concurrent overlap at most one winner, loser SLOT_UNAVAILABLE, no partial writes/audit.
- **REQ-BOOK-018 — WHEN** create succeeds, SHALL persist patient/service/Doctor/source/purpose/times/snapshots/CONFIRMED/creator/timestamps/audit.
- **REQ-BOOK-019–022 — WHEN** query/change, SHALL enforce current linked self-profile/assigned Doctor/Receptionist scope. If the Patient account/profile link no longer matches, Patient access ceases without deleting appointment or profile history. Family clinical-record/results sharing does not expand appointment authority.
- **REQ-BOOK-048 — WHEN** Owner/Admin general appointment read, SHALL ACCESS_DENIED; impact projection không bypass.
- **REQ-BOOK-023–025 — WHEN** Patient changes CONFIRMED, SHALL old start>=now+change deadline; equality allowed, reschedule còn checks new notice. Receptionist override allowed nhưng reason mandatory mọi reschedule/cancel.
- **REQ-BOOK-026 — WHEN** reschedule, SHALL atomic slot replacement, failure giữ old occupancy; self-overlap allowed sau self-exclusion.
- **REQ-BOOK-050 — WHEN** reschedule, SHALL preserve patient/Doctor/service/purpose/duration/buffer; reject protected client fields.
- **REQ-BOOK-027–028 — WHEN** cancel, SHALL release/audit; Patient only CONFIRMED, Receptionist CONFIRMED hoặc CHECKED_IN nếu §5.3 pass. Reschedule only CONFIRMED; invalid state APPOINTMENT_NOT_CHANGEABLE.
- **REQ-BOOK-045 — WHEN** Patient cancel, reason optional; Receptionist cancel/reschedule/reassign nonblank reason mandatory.
- **REQ-BOOK-029/046 — ALWAYS** SHALL enforce §2.2, no arbitrary status/identity commands.

### 3.5 Follow-up/reassignment/conflicts

- **REQ-BOOK-030 — WHEN** Doctor follow-up, SHALL validate source visit care assignment/exam completion/indication qua port; no source appointment COMPLETED/visit closed requirement.
- **REQ-BOOK-031–032 — WHEN** follow-up, SHALL derive patient, active target service/eligible accepting available provider, persist source_visit_id/source/ordering Doctor/reason; optional originating appointment resolve qua visit; no reason logs.
- **REQ-BOOK-033–035 — WHEN** Receptionist reassign affected CONFIRMED, SHALL require startTime>=now (equality accepted), different eligible accepting Doctor, same slot/service/patient/purpose/snapshots/reason. Past appointment -> BOOKING_TIME_IN_PAST, không đổi Doctor hồi tố; xử lý qua reception hoặc reschedule/cancel/no-show đúng điều kiện. Success stays CONFIRMED/audited, old Doctor loses scope; failure immutable. Same Doctor VALIDATION_FAILED.
- **REQ-BOOK-036–039 — WHEN** Admin resource mutation invalidates booking/active care, owning module SHALL reject until resolution. No auto-transition/release. Successful reassignment resolves BR30 extension khi no old dependency; active care không silently invalidated.
- **REQ-BOOK-052 — WHEN** hide/remove/lock/deactivate, SHALL preserve existing records và exceptions D07/D08.
- **REQ-BOOK-053 — WHEN** incident, operational/reception SHALL record actor/reason/time/affected references; entry alone giữ status/occupancy. Appointment resolution qua authorized reschedule/reassign/cancel.
- **REQ-BOOK-054 — ALWAYS** SHALL reserve Doctor only; room/orders/queue/walk-in owned elsewhere. Same-visit result return là care step; later examination uses new appointment/walk-in.
- **REQ-BOOK-040–042/047 — WHEN** success, SHALL immutable audit atomic/DTO-only/project errors/no sensitive logs; no audit-query/auto-delete.

## 4. HTTP và DTO contracts

### 4.1 Shared DTOs

- Identifiers valid UUID; dates YYYY-MM-DD; startTime RFC3339 offset required. Purpose nonblank max1000; supplied reason nonblank max1000. Transport limits là proposals cho Human review.
- Protected client fields status/source/creator/endTime/duration/buffer/orderingDoctor và follow-up patient reject VALIDATION_FAILED.
- ServiceSelection: id,name,description,durationMinutes,bufferMinutes,onlineBookable. Public/staff catalog response `{items: ServiceSelection[]}`, empty returns200 `{items: []}`.
- Slot: serviceId,doctorId,doctorDisplayName,startTime,endTime,occupiedUntil; no patient/contact/reasons.
- AppointmentResponse: id,patientProfileId,serviceId,nullable serviceName/servicePrice (giá hiện hành của dịch vụ, không phải giá chụp lúc đặt; chỉ GET danh sách/chi tiết điền, endpoint ghi trả null),doctorId,nullable doctorDisplayName (cùng quy tắc),status,bookingSource,purpose,startTime,endTime,durationMinutes,bufferMinutes,occupiedUntil,nullable visitId/sourceVisitId,createdAt,updatedAt. Scoped only; no embedded account/contact/clinical records/reasons.
- Page<T>: items,page,size,totalElements,totalPages; page>=0,size default20 within1..100; invalid -> VALIDATION_FAILED. Fixed appointment sort startTime desc,id desc.
- CheckInResponse: appointmentId,visitId,checkedInAt; replay trả stored response, không reset progressed lifecycle.

### 4.2 Availability theo workflow

| Endpoint | Actor | Contract |
|---|---|---|
| GET /api/v1/clinic-services | Public | Active online catalog. |
| GET /api/v1/staff/clinic-services | Receptionist, Doctor | Active workflow catalog. |
| GET /api/v1/appointments/availability | Public | serviceId/from/to required, doctorId optional; online notice, public-visible accepting eligible provider. |
| GET /api/v1/staff/appointments/availability | Receptionist, Doctor, Admin | serviceId/from/to required, doctorId optional; active service/accepting eligible provider/non-past/no notice; Admin inspect only. Doctor may select other provider for follow-up. |
| GET /api/v1/appointments/{id}/reschedule-availability | Scoped Patient, Receptionist | from/to required; derive patient/service/Doctor/snapshots; CONFIRMED, Patient old deadline/new notice; same Doctor and inactive-service/removed-provider exception, self-occupancy excluded. |
| GET /api/v1/appointments/{id}/reassignment-availability | Receptionist | CONFIRMED and startTime>=now, same slot/snapshots derived; eligible accepting replacements excluding current Doctor; inactive existing service allowed. |

Availability response `{slots: Slot[]}`, empty->200; explicit ineligible service/Doctor -> eligibility error. Workflow backend-derived, no client privilege flag. Slots không reserve; command re-check. Exceptions không enable new booking inactive service/removed provider. DTO không expose clinical rights/data.

### 4.3 Commands và queries

Request columns là JSON fields, không JPA entities.

| Endpoint | Request / response | Authority |
|---|---|---|
| POST /api/v1/appointments | patientProfileId?,serviceId,doctorId,startTime,purpose; 201 AppointmentResponse+Location | Patient omit profile=self, supplied value must equal current linked self profile; Receptionist required existing profile. Source actor-derived. |
| GET /api/v1/appointments | page/size, optional from/to inclusive dates,status,doctorId,patientProfileId; 200 Page | Patient/Doctor/Receptionist filters intersect scope, không mở quyền. |
| GET /api/v1/appointments/{id} | 200 AppointmentResponse | Scoped Patient/Doctor/Receptionist. |
| PATCH /api/v1/appointments/{id}/reschedule | startTime,reason?; 200 AppointmentResponse | Patient/Receptionist; no provider/service replacement. |
| POST /api/v1/appointments/{id}/cancel | reason?; 200 AppointmentResponse | Patient/Receptionist; visit rules cho CHECKED_IN. |
| POST /api/v1/appointments/{id}/reassign | doctorId,reason; 200 AppointmentResponse | Receptionist; reason explains current inability. |
| POST /api/v1/visits/{visitId}/follow-up-appointments | serviceId,doctorId,startTime,purpose,reason; 201 AppointmentResponse+Location | Doctor via source-visit contract; patient derived. Appointment adapter invokes visit port. |
| POST /api/v1/visits/walk-ins | `patientProfileId`, `priorAppointmentId`?; required `Idempotency-Key`; 201 `WalkInVisitResponse` (`visitId`,`patientProfileId`,`priorAppointmentId`,`arrivedAt`) | Receptionist only; actor and arrival time server-derived. Visit validates same-patient prior reference and atomically writes visit/reception/audit/replay. Exact replay returns the stored 201 response; no appointment status/occupancy change. |
| POST /api/v1/appointments/{id}/check-in | Empty object + required Idempotency-Key; 200 CheckInResponse | Receptionist; atomic §5.3. |
| PATCH /api/v1/appointments/{id}/status | targetStatus,reason?; 200 AppointmentResponse | Receptionist NO_SHOW only (optional reason); assigned Doctor IN_PROGRESS/COMPLETED only (reason input not accepted for these targets). CHECKED_IN uses check-in endpoint; no window override. |

Appointment list from/to là optional độc lập và inclusive, lọc clinic-local date của startTime, không interval overlap: chỉ from lấy start-date>=from; chỉ to lấy start-date<=to; cả hai range inclusive, reversed -> VALIDATION_FAILED. Không áp dụng booking horizon để cho phép xem history; pagination và actor scope luôn bắt buộc. Persistence converts supplied date bounds to half-open UTC start-time limits, missing end unbounded on that side.

Former appointment-source follow-up endpoint không thuộc contract mới. Patient search/create và Admin maintenance APIs thuộc owning modules; PLAN phải bind prerequisites, không duplicate ownership trong appointment.

### 4.4 Mutation impact privacy

Owning Admin mutation exposes resourceId/blocking count/affected appointment IDs,status,Doctor,times tối thiểu; no patient names/contact/purpose/reasons/clinical data. Không standalone general appointment search/detail cho Admin. Owning resource error semantics ở contract sở hữu; appointment impact port returns structured decision.

## 5. Public application integration contracts

Ports là logical contracts được PLAN bind; no cross-module entity/repository/table access. PLAN phải tránh cycles bằng neutral contract/orchestration, không reciprocal imports giữa feature implementations.

### 5.1 Patient/staff/service

- Patient appointment authorization input accountId+targetProfileId -> current linked self profile or scoped not-found. Patient owns this lookup; no `CONFIRMED_DEPENDENT` result may authorize appointment commands or queries. Profile search/create remains Patient-owned. Family clinical-record/results delegation requires a separate contract and approval.
- Staff resolution accountId->Doctor-profile ID; provider projection separates visibility/newBookingAcceptance/assignment/shifts/time off. Account access không physical availability.
- New create lookup current service snapshots; existing change uses stored snapshots/eligibility exception; deactivate service không tự remove assignment cho lịch cũ.
- Receptionist profile search prerequisite: nonblank normalized query identifying information, no unfiltered listing, pagination/result bounds/minimal identity projection. Search fields được owning patient contract chốt; không assume patient code tồn tại trong schema.

### 5.2 Visit follow-up

- Input visitId+authenticated Doctor; output patientProfileId/care assignment/exam completion/indication evidence và optional originatingAppointmentId; no diagnosis/results.
- Absent/out-of-scope source -> SOURCE_VISIT_NOT_FOUND; assigned nhưng exam/indication not ready -> FOLLOW_UP_NOT_ALLOWED. Re-check trong consistency boundary; client không set source patient/ordering Doctor.

### 5.3 Atomic lifecycle và idempotency

- Reception arrival-evidence port input appointmentId/patientProfileId/window instants; output valid recorded arrival timestamps/reference evidence. Validate matching patient/appointment association; no contact/clinical payload. Check NO_SHOW against this evidence in the same consistency boundary as arrival recording/check-in/status writes. Timely recorded arrival -> NO_SHOW_NOT_ALLOWED even if now exceeds grace; unavailable evidence -> DEPENDENCY_UNAVAILABLE, never assume absence.
- Reception stores arrivedAt distinct from checkedInAt; late WALK_IN keeps same-patient priorAppointmentId/arrivedAt. A valid timely arrival does not automatically bypass the first check-in window after processing delay; reception handles delayed processing through permitted reception/reschedule workflow, never incorrectly labels it NO_SHOW or restores a terminal state.

- Check-in authorized CONFIRMED -> create/link one same-patient visit -> initial reception -> CHECKED_IN/audit/replay result commit cùng nhau. Existing inconsistent link không che bằng visit mới.
- At most one check-in visit/initial reception per appointment. Replay tuple actorAccountId/appointmentId/command/Idempotency-Key; key nonblank max128. Same key/request trả stored result sau re-authorize; different payload -> IDEMPOTENCY_CONFLICT.
- Different keys concurrent không duplicate. Non-replay already checked-in/progressed -> APPOINTMENT_NOT_CHANGEABLE. Replay sau progression không reset state/repeat audit.
- Replay evidence giữ ít nhất trong lifecycle; exact persistence/retention trong PLAN, không delete audit. Failure không lưu success replay result.
- Visit WALK_IN API uses Visit-owned persistence: actor-scoped key/fingerprint, captured server `arrivedAt`, Visit and initial reception (`checked_in_at = arrived_at`), append-only `WALK_IN_CREATED` audit and stored response commit atomically under the shared guard. A new additive migration supplies the dedicated Visit audit table; applied migration history is immutable. Same request returns the stored 201 response; payload conflict returns `IDEMPOTENCY_CONFLICT`. No appointment status or occupancy write occurs.
- Any mandatory failure rollback appointment/visit/reception/audit/replay writes. PLAN bind same DB transaction hoặc equivalent all-or-nothing proof; không eventual delivery cho mandatory reception.
- Start/complete verify linked visit/care assignment through public port, synchronize scheduled-care step theo owning contract; completion không đóng whole visit.
- Cancel CHECKED_IN requires no clinical activity và end reception/cancel visit atomic; failure -> VISIT_CANCELLATION_NOT_ALLOWED, giữ mọi state. Kiểm tra clinical activity dưới concurrency boundary với Doctor start.
- Queue not mandatory. Timing/no-show rules tại §3.3.1; first request checks current policy trong state consistency boundary. Replay successful check-in returns stored result after authorization, không re-run time validation/transition.

### 5.4 Protected mutation/incident

- Owning resource calls validation với proposed Doctor/service/range mutation; reject lost coverage/eligibility for booking/active care. Failure retains resource/appointments/audit.
- Final validation/commit synchronized với create/reschedule/reassign/check-in/care; no automatic appointment transition.
- Incident tracking thuộc reception/operational; FEAT-002 không incident CRUD/status, chỉ resolution reason/audit/correlation.
- Account lock không trigger automatic appointment changes; publication/deactivation theo D07/D08.

### 5.5 Capacity coordination

- Visit reserves Doctor capacity using its configured expected care-step duration; missing configuration fails closed. Active assignments occupy `[startsAt,expectedEnd)`.
- Start/extension checks Appointment-owned occupancy through `AppointmentCapacityPort` and active Visit-care assignments in the same T014 guard/transaction. Extension checks only the newly added prospective segment `[max(now,currentExpectedEnd),newExpectedEnd)`.
- Exclude own appointment only after exact Visit/Patient/Doctor linkage validation; no WALK_IN self-link or blanket bypass of unrelated assignments. Preserve completed appointment buffer and every stored appointment snapshot.
- Conflict rejects start/extension without assignment/extension mutation, appends a minimal Visit conflict audit signal, and requires Receptionist coordination; no silent reschedule/cancel or numeric grace threshold. Dependency outage fails closed without a conflict signal.
- Room capacity remains Visit/Room-owner responsibility and is not implemented by this Doctor-capacity slice; queue/lab/external results remain separate scope.
- Không room/device reservation trong appointment. Same-visit result return/orders thuộc visit; laboratory/external results chưa scope-approved.

## 6. Error contract

Project ErrorResponse envelope: `{error:{code,message,timestamp,correlationId}}`; no PII/internal exceptions. Authentication errors theo auth contract.

| HTTP | Code | Meaning |
|---|---|---|
| 400 | VALIDATION_FAILED | Invalid field/date/grid/protected field/same-provider replacement/target. |
| 403 | ACCESS_DENIED | Endpoint actor denied. |
| 404 | APPOINTMENT_NOT_FOUND | Absent/out-of-scope appointment. |
| 404 | SOURCE_VISIT_NOT_FOUND | Absent/out-of-scope source. |
| 404 | PATIENT_PROFILE_NOT_FOUND | Absent/out-of-scope booking profile. |
| 409 | SLOT_UNAVAILABLE | Appointment/care overlap; re-query before retry. |
| 409 | APPOINTMENT_NOT_CHANGEABLE | Unsupported current state. |
| 409 | SCHEDULE_MUTATION_BLOCKED | Owning mutation invalidates booking/active care; minimal impact projection in owning contract. |
| 409 | IDEMPOTENCY_CONFLICT | Replay key/request conflict. |
| 409 | VISIT_LINK_CONFLICT | Appointment/patient/visit inconsistent. |
| 409 | VISIT_CANCELLATION_NOT_ALLOWED | Clinical activity started or visit cannot end consistently. |
| 422 | SERVICE_NOT_BOOKABLE | Workflow service invalid. |
| 422 | DOCTOR_NOT_ELIGIBLE | Provider selection/assignment invalid. |
| 422 | BOOKING_WINDOW_VIOLATION | Outside horizon. |
| 422 | BOOKING_TIME_IN_PAST | Create/reschedule new start or reassignment existing start before now. |
| 422 | BOOKING_NOTICE_TOO_SHORT | Patient new-slot notice fails. |
| 422 | APPOINTMENT_CHANGE_TOO_LATE | Patient old deadline fails. |
| 422 | FOLLOW_UP_NOT_ALLOWED | Care/exam/indication prerequisites fail. |
| 422 | CHECK_IN_TIME_NOT_ALLOWED | First check-in outside configured inclusive window. |
| 422 | NO_SHOW_NOT_ALLOWED | NO_SHOW at/before late-grace boundary or valid timely arrival evidence exists. |
| 503 | DEPENDENCY_UNAVAILABLE | Required port unavailable, writes rolled back. |
| 429 | RATE_LIMIT_EXCEEDED | Public bucket exhausted; Retry-After. |

Precedence: authentication -> endpoint authority -> resource scope -> state/input -> eligibility/time/capacity. Map DB overlaps after rollback, no aborted-transaction partial response. Empty date-filter list ->200 empty Page, malformed/range-reversed filter -> VALIDATION_FAILED.

## 7. Persistence và architecture

- Additive Flyway only; V001 unchanged. Profile identity/type compatibility before appointment FK/auth; unresolved STAFF mappings fail readiness, no arbitrary bulk mapping.
- Appointment fields: id,patient_profile_id,service_id,doctor_id,status,source,purpose,times,snapshots,nullable visit reference/source_visit_id,optional source_appointment_id,creator/timestamps và PLAN-bound concurrency token. Follow-up requires source_visit_id; other sources không client source links.
- Duration>0, buffer>=0,end=start+duration,occupied_until=end+buffer; valid status/source/FKs; history preserved/no destructive cascades. Cross-module schema coordination không ORM entity imports.
- PostgreSQL Doctor/range exclusion partial to FOUR occupied statuses including COMPLETED. No current-time-dependent predicate to expire completed records; historical intervals naturally không overlap future.
- Immutable audit appointment/actor/type/action/old-new state/Doctor/time/reason/linked or source visit/timestamp/correlation atomic; failed command không success audit. No query/delete audit API.
- Business value models belong under the feature's `model` package; service implementations in `service.impl` own orchestration and business rules. Architecture checks reject cross-feature repository/entity imports and dependency cycles. PLAN binds tools/dependencies separately.
- Bounded/indexed queries/pagination, proxy/limiter/privacy tests. Captured injected Clock instant cho command policy; final state/capacity protected concurrency. Verify real commit races, không chỉ mocks.

## 8. Acceptance và traceability

| ID | Evidence required | Mapping |
|---|---|---|
| AC01 | Patient self-profile booking/query/change allowed; other patient profile denied even with a care relationship; Receptionist may use an existing no-account profile. | Revised D03/D11; REQ005–006/019–022 |
| AC02 | Actor/resource scope matrix; Admin maintenance/impact only; Receptionist reassignment; Owner denied. | D02/D06; §2.3 |
| AC03 | Six-state transitions only; completion terminal without whole-visit closure. | REQ029/046 |
| AC04 | Early completion retains buffer; touching occupiedUntil accepted, overlap rejected, history không block future. | REQ051 |
| AC05 | Public/staff/change availability policies; exceptions không enable inactive/removed new bookings. | D07/D08; §4.2 |
| AC06 | Config default/override/startup rejection; zone/grid/horizon; exact now/notice/deadline/past rejection. | REQ007–015/049 |
| AC07 | Same Doctor/service/patient/purpose/snapshot reschedule; self-overlap allowed; failure giữ old slot. | D05/D08; REQ026/050 |
| AC08 | Same-slot eligible accepting replacement; inactive existing service allowed; failure immutable; old Doctor loses scope. | D06–D08 |
| AC09 | Visit-based follow-up incl walk-in/open visit; no source appointment required; client patient substitution denied. | D09/D11 |
| AC10 | Atomic check-in rollback/replay/different-key concurrency/progression replay no duplicate/reset. | D10; §5.3 |
| AC11 | Cancel CHECKED_IN before care ends visit/reception atomic; care-start race/port failure no partial changes. | §5.3 |
| AC12 | Mutation blocked until resolution; booking/mutation race cannot leave uncovered booking; active care preserved. | D04/D06; REQ036–039 |
| AC13 | Hide/remove/lock/deactivate no auto-transition; existing exceptions/snapshots retained. | D07/D08 |
| AC14 | Incident alone keeps status/occupancy; successful commands only alter appointment. | REQ053 |
| AC15 | Booking/care race prevents double-use; validated own-link/buffer/upcoming booking protected; no room reserve/synthetic appointment. | D10–D12; §5.5 |
| AC16 | DTO/error/correlation/log privacy; independent public buckets/trusted proxies/reset/Retry-After. | REQ040–044/047 |
| AC17 | Clean/V001 migrations/STAFF mapping/stable IDs/auth regression; architecture/cycle checks. | §7 |
| AC18 | Defaults 30/15, overrides, zero values và negative startup rejection; exact early/late boundary and immediately before/after; midnight window; no-show manual/audited/optional reason; concurrent check-in/no-show single winner; successful replay outside window. | REQ055–056/060 |
| AC19 | Late check-in rejected/no status bypass; reschedule only CONFIRMED; NO_SHOW terminal; WALK_IN preserves old history with validated same-patient reference and does not auto-release old booking; capacity and snapshots unchanged by grace. | REQ057–059/D10–D12 |

| AC20 | Timely recorded arrival with delayed check-in cannot be NO_SHOW; late arrival may have NO_SHOW+WALK_IN with priorAppointmentId/arrivedAt; arrival/status races remain consistent, unavailable evidence fails without writes. | REQ056/059; §5.3 |
| AC21 | Reassignment before/equal/after now; past rejection preserves Doctor/status/occupancy/audit. | REQ033–035 |
| AC22 | Exact grid valid; seconds/fraction invalid; equivalent instants with different offsets produce same clinic-grid result; no rounding on create/follow-up/reschedule. | REQ013 |
| AC23 | Public/staff catalog empty envelope; list only from/only to/both/reversed and clinic-date boundaries; history beyond booking horizon allowed while pagination/scope enforced. | §4.1–4.3 |
| AC24 | Receptionist WALK_IN creation uses Visit ownership, required actor-scoped idempotency, server arrival time with `checked_in_at = arrived_at`, same-patient optional prior reference, additive append-only Visit audit table, atomic visit/reception/audit/replay, unchanged prior appointment state, and no duplicate on replay. | REQ059/061; D11; §4.3/5.3 |

### SRS và Context mapping

| Area | Baseline UC/BR | Context | Contract |
|---|---|---|---|
| Availability | UC21/BR25; Doctor directory UC17/BR23 | D01/D07/D08 | §3.2–3.3, §4.2 |
| Booking/follow-up | UC22/BR21/BR26 | D03/D09 | §3.2/3.5, §5.1–5.2 |
| Reschedule/cancel/history | UC23–25/BR21/BR24/BR27 | D03/D05/D08 | §3.4, §4.3/5.3 |
| Shift/eligibility | UC26–29/BR28–30; supplementary time-off/assignment | D02/D04/D06 | §3.5/5.4 |
| Check-in | UC30/BR31, queue separated | D10 | §2.2/5.3 |
| Profiles/walk-in | BR18–19/BR21–23 | D03/D07/D11 | REQ059/061; §2.1/4.3/5.3/5.5 |
| Service management | BR65/BR67 | D02/D08 | §3.2/3.5 |
| Capacity/room/service-step boundary | Council feedback and owning-feature SRS | D12 | §3.5/5.5 |
| Buffer retention and operational incident | Approved supplementary Context policy | Context §4.4/5.5 | REQ051/053, AC04/14 |

Existing REQ identities retained but old text superseded; ranges denote individual requirements in named group. REQ051–054 cover buffer/provider-state/incident/boundary rules; REQ055–060 cover check-in/no-show/late arrival và concurrency. No old HTML UC numbering.

## 9. Policy decision record và downstream gates

| ID | Status | Recorded decision |
|---|---|---|
| SPEC-OPEN-01 | CONFIRMED | Typed check-in-early-minutes default30; check-in-late-grace-minutes default15; inclusive check-in boundaries, no status bypass/override or booking extension. |
| SPEC-OPEN-02 | CONFIRMED | Manual Receptionist NO_SHOW strictly after late-grace boundary; no automatic marking, optional entered reason, mandatory actor/time/audit; terminal with late arrival handled as WALK_IN. |

Evidence: user yêu cầu bổ sung policy 30/15 và late-arrival rules trong conversation ngày 2026-10-12. Confirmation của hai policy không phải final SPEC approval.

Owning visit SPEC/PLAN reviews duration estimate/overrun/capacity synchronization. The WALK_IN API contract in REQ-BOOK-061 is a Visit-owned reception write; it does not create an appointment or alter its status/occupancy. Queue/laboratory expansion needs a separate scope decision.

## Change Impact Record
- Changed intent, requirement, architecture, or task assumption: Added a Visit-owned Receptionist WALK_IN HTTP API to make the already-approved late-arrival/WALK_IN behavior executable; existing D11 intent and appointment lifecycle are unchanged.
- Feature Lock boundary affected: Additive Visit API and Visit-owned persistence; no new Appointment endpoint, appointment status transition, or occupancy release.
- Material state-change classification: public API/shared contract; business-data mutation/audit/replay; coordination-guard transaction.
- Downstream artifacts invalidated: PLAN, TASKS, shared Visit contract v1.2.0, API/OpenAPI and acceptance/trace artifacts; source/tests remain unchanged pending approval.
- Required trace, test, and sync actions: Add T043 for Visit API and additive audit migration; make T028 depend on T043; verify route security, idempotency, prior-reference validation, audit append-only enforcement, `checked_in_at = arrived_at` and atomic persistence with the approved commands and local PostgreSQL route bound in task preflight; sync shared contract and trace after implementation.
- Review follow-up: SPEC v0.9.0, TASKS v0.6.1 and Visit contract v1.2.0 are approved. PLAN v0.7.0 and Architecture Profile v1.4.0 remain pending review; do not execute T028/T043 until required reviews and task checkpoints are persisted.

## Action Record — SPEC revision

- Authorized scope: user yêu cầu áp dụng review contracts, bốn contract còn thiếu và bốn clarification về arrival/reassignment precision/catalog-filter-path; confirmed trong conversation ngày 2026-10-12.
- File boundary: backend/sdd/features/feat-002-appointment-booking/SPEC.md theo file user chỉ định; bản root .sdd, CONTEXT approved và PLAN/TASKS không sửa.
- Shadow Plan: replace stale contract -> API/DTO/errors/public ports -> acceptance/traceability -> consistency verification.
- Result: SPEC v0.7.0 for Human review; no code/migration/self-approval. Historical record only; proposed v0.8.0 now revises D03.
- Historical v0.7.0 disposition: its final review was APPROVED and SPEC-OPEN-01/02 confirmed. That approval does not cover proposed v0.8.0 D03 or permit implementation under the revised boundary.

## Historical AI Agent Recommendation — superseded by Human Final Review

- Scope: FEAT-002 SPEC v0.9.0 additive Visit-owned WALK_IN API
- Recommendation at review time: Approve or revise the additive API requirement, request/response and security/atomicity contract. Keep existing Appointment lifecycle, NO_SHOW authorization and D03 self-only authorization unchanged. Visit is the only owner of the new endpoint and its visit/reception/audit/replay writes.
- Evidence: CONTEXT D11 already assigns WALK_IN visit creation to Visit and prohibits synthetic appointments; REQ-BOOK-059 preserves prior appointment history; no Visit mutation route existed in the approved API table; Human explicitly approved the proposed endpoint, actor, fields, server Clock timestamp, prior-reference validation, atomic writes and response fields in the current conversation.
- Risks and assumptions: HTTP creation uses 201 and an exact idempotent replay returns the stored creation response; Owner Long approved this status behavior on 2026-10-18. Existing V007/V008 lack a dedicated Visit audit table, so T043 requires a new additive migration; `checked_in_at` equals server-derived `arrived_at`. API response intentionally contains only identifiers and arrival time.
- Alternatives considered: Appointment-owned endpoint/write (rejected because Visit owns visit/reception); leave WALK_IN behavior without a backend API (rejected after Human chose API scope); additive Visit-owned API (recommended).
- Human decision: Owner Long approved SPEC v0.9.0 on 2026-10-18. TASKS v0.6.1 and Visit contract v1.2.0 are approved; PLAN v0.7.0 and Architecture Profile v1.4.0 remain separate pending reviews. No T043 execution authority is implied.

## Historical Human Final Review — v0.8.0

- Status: COMPLETED.
- Decision: APPROVED.
- Reviewer: Owner Long (explicit user approval).
- Reviewed at: 2026-10-14 (Asia/Bangkok).
- Approved version: 0.8.0.
- Evidence: User explicitly instructed, "Approve luôn cho cả CONTEXT, SPEC và PLAN sửa lại luôn T008" on 2026-10-14. Approval includes current linked self-profile appointment authorization and scoped denial of other Patient profiles; family clinical-record/results sharing remains outside FEAT-002.

## Human Final Review
- Status: APPROVED — SPEC v0.9.0
- Decision: APPROVE SPEC v0.9.0 under Owner Long — received in conversation on 2026-10-18.
- Reviewer: Owner Long
- Reviewed at: 2026-10-18 (Asia/Bangkok).
- Review boundary: SPEC v0.9.0, including T043 additive audit migration, append-only requirement, checked_in_at = arrived_at, Visit API and 201 replay behavior.
- Follow-up: PLAN v0.7.0 and Architecture Profile v1.4.0 remain pending. T043 still requires its task-specific pre-edit checkpoint.

## Historical Human Final Review — v0.7.0

- Status: COMPLETED
- Decision: APPROVED
- Reviewer: Owner Long
- Reviewed at: 2026-10-12
- Approved version: 0.7.0 (historical; D03 changed in proposed v0.8.0)
- Approval evidence: Explicit user instruction in this conversation to approve SPEC under Owner Long.
- Follow-up: Use approved SPEC v0.7.0 with CONTEXT v0.15.0 to revise PLAN, then TASKS. Approval covers SPEC only; prerequisite contracts, downstream reviews and implementation execution remain separately gated.

## Historical Human Final Review — v0.4.0

- Status: COMPLETED
- Decision: APPROVED
- Reviewer: Owner Long
- Reviewed at: 2026-10-09
- Follow-up: Historical approval covered Context v0.4.0-based SPEC only.
