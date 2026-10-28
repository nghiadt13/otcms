# Feature Specification — FEAT-002 Appointment Booking

## 1. Metadata

- Version: 0.9.2
- Status: APPROVED & LOCKED
- Source: CONTEXT.md v0.16.0 — APPROVED by Owner Long on 2026-10-14; v0.15.0 approval is historical.
- SRS: docs/requirements/baselines/report3-2026-10-12/BASELINE.md.
- Delivery: backend/API; frontend ngoài phạm vi.
- Dependencies: auth compatibility; patient current linked self-profile authorization; service/catalog; staff/account; visit/reception/clinical public contracts.
- Contract v0.4.0 bị superseded; approval cũ chỉ là lịch sử. v0.9.1 aligned the approved 2026-10-25 T044/T045–T053 reception scope and canonical EARS syntax; v0.9.2 documents the existing HTTP 409 capacity-conflict mapping. v0.8.0 approval remains historical for D03 and does not approve the API addition.
- API/DTO/error behavior ngoài D03 giữ nội dung v0.8.0 làm cơ sở, ngoại trừ additive Visit WALK_IN API contract in this revision.

## Change Log

| Version | Change |
|---|---|
| 0.9.2 | Add the existing 409 `VISIT_CAPACITY_NOT_AVAILABLE` code to the error catalog for REQ-BOOK-063; documentation alignment only. |
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

- **REQ-BOOK-001 — WHEN:** WHEN the public catalog is requested, the OTCMS SHALL trả active online services, không PII/contact/unavailability reasons. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-002 — WHEN:** WHEN appointment availability is requested, the OTCMS SHALL return Doctor-specific slots theo workflow §4.2. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-003 — WHEN:** WHEN a new appointment is created, the OTCMS SHALL require active workflow-allowed service; existing reschedule/reassign được giữ inactive service khi other rules pass. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-004 — WHEN:** WHEN booking eligibility is evaluated, the OTCMS SHALL tách visibility/newBookingAcceptance/account access/existing eligibility. Hidden chặn public selection; removed directory chặn mọi new booking/follow-up/replacement nhưng cho same-Doctor reschedule. Lock chỉ chặn Doctor access, không tự thay appointments/capacity. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-005 — WHEN:** WHEN a Patient submits an appointment creation request, the OTCMS SHALL derive patientProfileId from the current authenticated PATIENT account link; supplied other profileId SHALL be denied with scoped error. No cross-account dependent booking or relationship-derived authority. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-006 — WHEN:** WHEN a Receptionist submits an appointment creation request, the OTCMS SHALL use existing profile kể cả không account; source RECEPTIONIST_ASSISTED. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-043 — WHEN:** WHEN a staff member requests the service catalog, the OTCMS SHALL active allowed services; follow-up service phải có eligible accepting provider. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-044 — WHEN:** WHEN a public request is received, the OTCMS SHALL separate fixed-window IP buckets catalog/availability; trust forwarding only configured proxies; 429/Retry-After ceil remaining seconds minimum1; independent reset/bounded cleanup. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-049 — IF:** IF the requested date range is missing, reversed, or longer than the configured maximum horizon, THEN the OTCMS SHALL return `VALIDATION_FAILED`; IF the requested dates are outside today through today plus the configured horizon, THEN the OTCMS SHALL return `BOOKING_WINDOW_VIOLATION`.

### 3.3 Time/slots/snapshots

- **REQ-BOOK-007–009 — Ubiquitous:** The OTCMS SHALL use Clock/zone; inclusive dates -> half-open UTC `[fromStart,dayAfterToStart)`.
- **REQ-BOOK-010 — WHEN:** WHEN an appointment is created or rescheduled, the OTCMS SHALL enforce clinic-local horizon. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-011 — WHEN:** WHEN a Patient creates or reschedules an appointment, the OTCMS SHALL new start >= now+online notice; equality accepted. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-012 — WHEN:** WHEN a Receptionist creates or reschedules an appointment, or a Doctor creates a follow-up appointment, the OTCMS SHALL bypass notice nhưng enforce horizon/availability và start>=now. Mọi create/reschedule reject past start. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-013 — WHEN:** WHEN appointment slots are generated or reserved, the OTCMS SHALL convert startTime to clinic timezone and enforce configured grid anchored at local midnight, seconds=0 and fractional seconds=0. Apply to create/follow-up/reschedule; reject VALIDATION_FAILED without rounding. Reassignment preserves stored timestamp. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-014 — WHEN:** WHEN available appointment slots are requested, the OTCMS SHALL fit consultation+buffer inside one shift, avoid time off/occupied appointments/visit capacity; exclude chính appointment đang change, không others. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-015 — WHEN:** WHEN an appointment is created, the OTCMS SHALL derive end/duration/buffer; existing reschedule/reassign giữ snapshots kể cả service đổi/deactivate. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-051 — WHEN:** WHEN an appointment is completed, the OTCMS SHALL preserve occupied range through stored end+buffer; actual overrun không tự sửa snapshots/assume Doctor free. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.

### 3.3.1 Check-in window và NO_SHOW — confirmed policy

- **REQ-BOOK-055 — WHEN:** WHEN a first check-in is requested, the OTCMS SHALL require CONFIRMED and `startTime - check-in-early-minutes <= now <= startTime + check-in-late-grace-minutes`, cả hai boundaries inclusive. Dùng injected Clock instant; không hạn chế cùng calendar date nếu configured window vượt midnight. Valid successful replay theo §5.3 không bị reject chỉ vì now đã vượt window. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-056 — WHEN:** WHEN a Receptionist marks an appointment NO_SHOW, the OTCMS SHALL require CONFIRMED và `now > startTime + check-in-late-grace-minutes`. Không automatic scheduler trong MVP; NO_SHOW nghĩa là không có arrival được ghi nhận trong inclusive check-in window; Receptionist phải xác minh evidence qua reception public contract. Arrival đúng cửa sổ nhưng chưa được xử lý check-in không được NO_SHOW. Appointment CONFIRMED alone không đủ để kết luận absence. Reason nhập tay optional, nếu supplied theo reason validation; actor/time/action/correlation audit luôn mandatory. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-057 — Ubiquitous:** The OTCMS SHALL ensure that the check-in window does not kéo dài start/end/occupiedUntil/duration/buffer. Reception/visit kiểm tra actual capacity trước khi bắt đầu khám; arriving within grace không tự đảm bảo full consultation time hoặc cho phép chồng lịch kế tiếp. The OTCMS SHALL require Reception and Visit workflows to check actual capacity before care begins; arrival within the grace period does not guarantee a full consultation interval or authorize overlap with the next appointment.
- **REQ-BOOK-058 — WHEN:** WHEN a late arrival exceeds the grace period, the OTCMS SHALL reject first check-in; Receptionist có thể reschedule khi CONFIRMED theo normal time/availability/reason rules hoặc tiếp nhận WALK_IN theo D11. Không bypass bằng status endpoint/operational override. NO_SHOW terminal, không reschedule/reassign/restore; later walk-in preserves old appointment history. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-059 — WHEN:** WHEN Reception or Visit accepts a late arrival as WALK_IN, the OTCMS SHALL lưu server-derived `arrivedAt` và validated `priorAppointmentId` khi lịch cũ xác định được (reference nullable chỉ khi không xác định được lịch), theo Visit-owned API REQ-BOOK-061; cùng patient, không biến thành visit check-in của lịch cũ. Reference không thay appointment status, source hoặc occupancy; Visit module owns it. CONFIRMED cũ phải được xử lý riêng qua authorized command, không tự release khi tạo walk-in. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-060 — WHEN:** WHEN check-in and NO_SHOW operations run concurrently, the OTCMS SHALL serialize state và policy validation cùng writes; chỉ một transition thắng. Loser thấy changed state trả APPOINTMENT_NOT_CHANGEABLE; check-in replay vẫn theo §5.3. Không duplicate visit/audit hoặc NO_SHOW trên appointment đã checked-in. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-061 — WHEN:** WHEN a Receptionist accepts a WALK_IN, the OTCMS SHALL use the Visit-owned `POST /api/v1/visits/walk-ins` API with an existing `patientProfileId`, optional `priorAppointmentId`, and required nonblank `Idempotency-Key` up to 128 characters; actor identity and `arrivedAt` are server-derived. Visit SHALL validate that any prior appointment belongs to the same patient and atomically persist the WALK_IN visit, initial reception, append-only `WALK_IN_CREATED` audit, and replay result under the shared transaction/coordination guard. The new additive Flyway migration SHALL add a Visit audit table with actor account, visit, action, occurrence timestamp and correlation ID; applied migrations SHALL remain unchanged. WALK_IN SHALL set `checked_in_at` to the same server-derived instant as `arrived_at`. It SHALL NOT modify the referenced appointment's status or occupancy. Same actor/key/request replays the stored response; same actor/key with a different request returns `IDEMPOTENCY_CONFLICT`. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-062 — WHEN:** WHEN the assigned Doctor starts Visit care or extends active care, the OTCMS SHALL use Visit contract v1.3.0. Start uses configured per-step duration and fails closed when missing. Active care occupies `[actualStart,expectedEnd)`; extension for SCHEDULED and WALK_IN Visits checks only the new segment `[max(now,currentExpectedEnd),newExpectedEnd)` and never changes the linked Appointment status, booked interval, duration or buffer. Scheduled start/completion remain through Appointment's atomic lifecycle API; WALK_IN start/completion use Visit endpoints. Every write is Doctor-authorized, guarded and audited atomically; conflict rejects assignment/extension mutation but may append the minimal coordination audit in REQ-BOOK-063; dependency outage fails closed without audit. Room capacity remains gated by separate Room ownership. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-063 — WHEN:** WHEN the Doctor start or extension capacity check conflicts, the OTCMS SHALL reject with `VISIT_CAPACITY_NOT_AVAILABLE` and append a minimal Visit-owned `CAPACITY_CONFLICT` audit signal containing actor, Visit, attempted step, server time and correlation; existing appointment/care state remains unchanged. An assigned Doctor may append the same signal through the conflict endpoint for Receptionist coordination. No incident CRUD or appointment mutation is added. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.

Default example: appointment 09:00 cho first check-in từ 08:30 đến đúng 09:15; chỉ sau 09:15 mới được NO_SHOW. Overrides phải thay cả check-in-late boundary và NO_SHOW threshold bằng cùng property, tránh gap/overlap.

### 3.4 Commands/queries

- **REQ-BOOK-016–017 — WHEN:** WHEN an appointment is reserved or changed, the OTCMS SHALL re-check authorization/eligibility/capacity atomic; concurrent overlap at most one winner, loser SLOT_UNAVAILABLE, no partial writes/audit. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-018 — WHEN:** WHEN an appointment creation succeeds, the OTCMS SHALL persist patient/service/Doctor/source/purpose/times/snapshots/CONFIRMED/creator/timestamps/audit. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-019–022 — WHEN:** WHEN an appointment is queried or changed, the OTCMS SHALL enforce current linked self-profile/assigned Doctor/Receptionist scope. If the Patient account/profile link no longer matches, Patient access ceases without deleting appointment or profile history. Family clinical-record/results sharing does not expand appointment authority. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-048 — WHEN:** WHEN an Owner or Admin requests a general appointment read, the OTCMS SHALL ACCESS_DENIED; impact projection không bypass. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-023–025 — WHEN:** WHEN a Patient changes a CONFIRMED appointment, the OTCMS SHALL old start>=now+change deadline; equality allowed, reschedule còn checks new notice. Receptionist override allowed nhưng reason mandatory mọi reschedule/cancel. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-026 — WHEN:** WHEN an appointment is rescheduled, the OTCMS SHALL atomic slot replacement, failure giữ old occupancy; self-overlap allowed sau self-exclusion. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-050 — WHEN:** WHEN an appointment is rescheduled, the OTCMS SHALL preserve patient/Doctor/service/purpose/duration/buffer; reject protected client fields. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-027–028 — WHEN:** WHEN an appointment is canceled, the OTCMS SHALL release/audit; Patient only CONFIRMED, Receptionist CONFIRMED hoặc CHECKED_IN nếu §5.3 pass. Reschedule only CONFIRMED; invalid state APPOINTMENT_NOT_CHANGEABLE. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-045 — WHEN:** WHEN a Patient cancels or a Receptionist cancels, reschedules, or reassigns an appointment, the OTCMS SHALL allow the Patient reason to be optional and SHALL require a nonblank reason from the Receptionist. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-029/046 — Ubiquitous:** The OTCMS SHALL enforce §2.2, no arbitrary status/identity commands.

### 3.5 Follow-up/reassignment/conflicts

- **REQ-BOOK-030 — WHEN:** WHEN a Doctor creates a follow-up appointment, the OTCMS SHALL validate source visit care assignment/exam completion/indication qua port; no source appointment COMPLETED/visit closed requirement. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-031–032 — WHEN:** WHEN a Doctor follow-up is requested, the OTCMS SHALL derive patient, active target service/eligible accepting available provider, persist source_visit_id/source/ordering Doctor/reason; optional originating appointment resolve qua visit; no reason logs. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-033–035 — WHEN:** WHEN a Receptionist reassigns an affected CONFIRMED appointment, the OTCMS SHALL require startTime>=now (equality accepted), different eligible accepting Doctor, same slot/service/patient/purpose/snapshots/reason. Past appointment -> BOOKING_TIME_IN_PAST, không đổi Doctor hồi tố; xử lý qua reception hoặc reschedule/cancel/no-show đúng điều kiện. Success stays CONFIRMED/audited, old Doctor loses scope; failure immutable. Same Doctor VALIDATION_FAILED. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-036–039 — WHEN:** WHEN an Admin resource mutation invalidates a booking or active care, the OTCMS SHALL ensure that the owning module rejects the mutation until resolution. No auto-transition/release. Successful reassignment resolves BR30 extension khi no old dependency; active care không silently invalidated. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-052 — WHEN:** WHEN a Doctor or Service resource is hidden, removed, locked, or deactivated, the OTCMS SHALL preserve existing records và exceptions D07/D08. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-053 — WHEN:** WHEN an operational incident affects appointment or reception processing, the OTCMS SHALL record the actor, reason, time, and affected references; the entry alone SHALL preserve status and occupancy. Appointment resolution qua authorized reschedule/reassign/cancel. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.
- **REQ-BOOK-054 — Ubiquitous:** The OTCMS SHALL reserve Doctor only; room/orders/queue/walk-in owned elsewhere. Same-visit result return là care step; later examination uses new appointment/walk-in.
- **REQ-BOOK-040–042/047 — WHEN:** WHEN an appointment operation succeeds, the OTCMS SHALL immutable audit atomic/DTO-only/project errors/no sensitive logs; no audit-query/auto-delete. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6.

## 4. HTTP và DTO contracts

### 4.1 Shared DTOs

- Identifiers valid UUID; dates YYYY-MM-DD; startTime RFC3339 offset required. Purpose nonblank max1000; supplied reason nonblank max1000. Transport limits là proposals cho Human review.
- Protected client fields status/source/creator/endTime/duration/buffer/orderingDoctor và follow-up patient reject VALIDATION_FAILED.
- ServiceSelection: id,name,description,durationMinutes,bufferMinutes,onlineBookable. Public/staff catalog response `{items: ServiceSelection[]}`, empty returns200 `{items: []}`.
- Slot: serviceId,doctorId,doctorDisplayName,startTime,endTime,occupiedUntil; no patient/contact/reasons.
- AppointmentResponse: id,patientProfileId,serviceId,doctorId,status,bookingSource,purpose,startTime,endTime,durationMinutes,bufferMinutes,occupiedUntil,nullable visitId/sourceVisitId,createdAt,updatedAt. Scoped only; no embedded account/contact/clinical records/reasons.
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
| 409 | VISIT_CAPACITY_NOT_AVAILABLE | Visit care start/extension conflicts with active capacity; no capacity mutation. |
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

## Historical Human Final Review — v0.9.0
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

## Revised addendum — scheduled arrival and walk-in reception queue (2026-10-25)

**Status: APPROVED on 2026-10-25.** This revision separates scheduled check-in from the walk-in FIFO and adds clinic-QR arrival evidence. It uses UC09, UC21–UC23 and UC27 from `docs/requirements/UC_description.html`; the approved scheduled no-queue interpretation is a product-specific variance to UC22 and must be synchronized with the SRS before delivery. Existing check-in/no-show/walk-in rules remain binding except for the explicit timely-arrival processing exception below.

### Functional contract

| ID | Requirement / observable result |
|---|---|
| REQ-RECEPTION-001 | WHEN a Receptionist opens check-in, the OTCMS SHALL list/search clinic appointments through the scoped appointment query API with server pagination and date/status filters; select a current appointment and fetch detail before mutation. Empty results SHALL offer the walk-in path. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-002 | WHEN an appointment is selected, the OTCMS SHALL show only the Patient-owned minimum identity fields needed for receptionist verification. Receptionist SHALL explicitly confirm verification; server SHALL bind check-in to the appointment's `patientProfileId` and reject mismatched/inactive profile evidence. A client-supplied patient ID SHALL never replace it. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-003 | WHEN first scheduled check-in succeeds, the OTCMS SHALL atomically commit appointment `CHECKED_IN`, one scheduled visit and initial reception/arrival/audit/replay evidence. SHALL NOT create a reception FIFO entry. Response SHALL contain `appointmentId`, `visitId`, `checkedInAt`; replay returns the original result. Receptionist guides the patient by appointment time and current Visit assignment, if one exists. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-004 | WHEN no suitable appointment exists, the OTCMS SHALL allow Receptionist to find or create an accountless-capable patient profile through Patient-owned APIs, then create a WALK_IN visit through Visit. Visit creation SHALL atomically commit initial reception/audit/replay and one active reception FIFO entry; SHALL NOT create or change an appointment. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-005 | WHEN a no-show action is requested, the OTCMS SHALL use the existing Receptionist-only appointment status command and revalidate `CONFIRMED`, strict after-grace time and absent timely arrival under the shared guard. Success SHALL persist `NO_SHOW` and audit; SHALL NOT create visit/queue data. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-006 | WHEN a Receptionist views the walk-in reception queue, the OTCMS SHALL return active WALK_IN entries ordered FIFO by server `arrivedAt`, then stable entry ID; include visit ID, queue status and position. Scheduled appointment arrivals SHALL not appear. Pagination and clinic scope SHALL be enforced server-side. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-007 | WHEN a Patient views their own visit progress, the OTCMS SHALL return only their authorized visit's current stage/status. Include queue position only when their visit has an active WALK_IN reception queue entry. Deny other profiles, including guessed visit IDs; accountless walk-in has no Patient self-service view until separately authorized. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-008 | WHEN a WALK_IN visit leaves reception waiting because care starts, it is canceled, or it is closed, the OTCMS SHALL move its queue entry to history atomically with the owning Visit operation. Scheduled check-in has no reception FIFO entry to remove. No silent queue deletion. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-009 | WHEN an authenticated Patient scans a valid clinic QR for their own CONFIRMED appointment, the OTCMS SHALL record a server-derived `arrivedAt` immediately as pending arrival evidence and expose a pending item to authorized Receptionists. SHALL NOT set `CHECKED_IN`, create a Visit or enqueue to reception FIFO. Repeated valid submissions for the same appointment SHALL return the original arrival timestamp. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-010 | WHEN a Receptionist processes a pending arrival after late grace, the OTCMS SHALL use a valid claim recorded within the inclusive check-in window to permit check-in and prohibit `NO_SHOW`; processing time SHALL not replace arrival time. QR scan after grace SHALL record actual arrival but SHALL NOT authorize scheduled check-in. Authorized manual arrival recording uses server time, actor and audit for patients unable to use QR. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |
| REQ-RECEPTION-011 | WHEN QR arrival and NO_SHOW operations race, the OTCMS SHALL serialize arrival evidence and status decision under the existing coordination guard. Patient authentication and current appointment/profile ownership SHALL be checked on every request. A static public QR URL or copied image SHALL not by itself prove presence or grant authority; clinic proof SHALL be short-lived, bound to the clinic and validated server-side. IF the request has invalid parameters or violates an authorization, resource-scope, state, eligibility, or capacity precondition stated here, THEN the OTCMS SHALL return the corresponding error defined in §6. |

For a scheduled appointment with a timely QR/manual arrival record, REQ-RECEPTION-010 revises the **processing-time** check in REQ-BOOK-055/058: staff confirmation may occur after late grace, while the recorded server `arrivedAt` must fall within the inclusive window. Without timely evidence, the original first-check-in window applies. The pending-arrival worklist is an operational staff alert, not the WALK_IN FIFO or an external notification.

### API and persistence delta

- Keep existing `GET /api/v1/appointments`, `GET /api/v1/appointments/{id}`, `POST /api/v1/appointments/{id}/check-in`, `PATCH /api/v1/appointments/{id}/status`, and `POST /api/v1/visits/walk-ins`. Add only the minimal walk-in queue/profile/progress routes whose owning services lack a public route; use `/api/v1/` plural resources and DTOs. Proposed `GET /api/v1/reception-queue` returns WALK_IN entries only; `GET /api/v1/visits/{visitId}/progress` returns authorized own visit progress and optional walk-in position. Patient profile lookup/create must use Patient-owned routes/contracts, not appointment tables.
- Add a separate arrival-recording command for the clinic QR and an authorized Receptionist fallback, plus a scoped pending-arrival read for Receptionist. These are Visit/reception-owned arrival evidence operations; Appointment owns authorization/status through public contracts. The QR contains no patient identity or appointment details and cannot complete check-in by itself. Route, challenge TTL/rotation and worklist polling contract are frozen in the dedicated QR task before coding.
- Add a Visit-owned queue entry/history schema in a new Flyway migration. Unique active entry per visit and an immutable event history are required. Keep prior migrations immutable. The queue entry references a visit, with appointment ID derived through the visit when present; never fabricate an appointment for WALK_IN.
- Add `queueEntryId` and `queueStatus` to the WALK_IN response only; scheduled check-in keeps appointment/visit/time response fields and returns no queue data. Update OpenAPI/static-runtime parity. Preserve existing idempotency and audit semantics. Error responses use established shared error DTO and codes for denied scope, stale state, invalid identity, outside window and replay key conflict.
- Authorization is checked in backend on every list/detail/mutation and individual queue/visit record. The frontend cannot call database tables directly or invent transitions. No Patient list of other patients, no public queue endpoint.

### T044 selected contract

**Status: APPROVED on 2026-10-25.** The user approved this revised T044 contract, including scheduled arrival outside walk-in FIFO and the clinic-QR arrival claim, when approving the four feature artifacts and T053. It replaces the previous same-queue assumption.

- Only WALK_IN visits enter the Visit-owned reception FIFO. Entry starts `WAITING`; exit becomes `LEFT_RECEPTION` with reason `CARE_STARTED`, `CANCELLED` or `VISIT_CLOSED`; transition history is append-only. Position is 1-based among active walk-ins ordered by server `arrivedAt`, then entry UUID, and recalculated on read.
- Scheduled check-in verifies the patient's identity, records arrival and atomically links the scheduled Visit; it creates no reception queue row. Response contains appointment, visit and check-in time only. Receptionist uses the appointment schedule to guide the patient; a specific room may be shown only if Visit provides a current room assignment. Existing appointment persistence does not currently assign rooms.
- QR arrival is a separate `ARRIVAL_RECORDED` worklist condition, not a new appointment status. `arrivedAt` is captured by the server when the valid QR request reaches the backend; `checkedInAt` is captured when Receptionist confirms. A timely arrival remains eligible when confirmation is delayed. Manual arrival fallback is audited and cannot backdate arrival.
- Receptionist attests to `verifiedPatientProfileId`; backend compares it to the appointment's patient under the check-in guard and records actor/time. This is an operational attestation, not proof of government-ID verification. Safe Patient-owned identity projection remains needed.
- WALK_IN response adds `queueEntryId` and `queueStatus`; exact replay returns the same entry identity. Current position is read from the scoped queue API because it changes. `priorAppointmentId` stays optional and, when supplied, must match the same patient.
- Patient progress exposes only authorized own stage/status, and includes position only for an active walk-in entry. Current decision inventory is in `sdd/reviews/feat-002-T044-reception-contract.md`.

### Acceptance scenarios

| ID | Given / When / Then |
|---|---|
| AC25 | Given a confirmed appointment inside the inclusive window, when Receptionist verifies and confirms, then one checked-in appointment and linked visit/reception appear together and no reception FIFO row is created. |
| AC26 | Given the same scheduled check-in is retried or two check-ins race, then one visit exists; conflicting key reuse fails without a partial write or queue row. |
| AC27 | Given no appointment, when Receptionist selects/creates the correct patient and records WALK_IN, then a visit and one FIFO reception entry exist and no appointment is created. |
| AC28 | Given an absent confirmed patient after grace, when Receptionist marks no-show, then only appointment status/audit change; timely arrival or concurrent successful check-in prevents no-show. |
| AC29 | Given a receptionist walk-in queue read, then only walk-ins appear in FIFO order with scoped pagination; a care start removes active position but preserves history. |
| AC30 | Given Patient A requests Patient B's visit ID or an accountless walk-in, then access is denied/scoped absent; A sees only own authorized stage/status and position only if queued as WALK_IN. |
| AC31 | Given profile mismatch, expired window or transaction rollback, then no partial scheduled check-in or walk-in/queue record remains; scheduled path never writes a reception FIFO row. |
| AC32 | Given a booked Patient scans the valid clinic QR at 08:10 for an 08:00 appointment with 08:15 late grace, then pending arrival is recorded at 08:10 and Receptionist sees it; confirmation at 08:25 may complete check-in and `NO_SHOW` is rejected. Repeated scans preserve 08:10. |
| AC33 | Given an unauthorized account, invalid/expired QR proof or an arrival first recorded after grace, then the request cannot create a timely arrival claim or bypass the check-in window; no queue/Visit is created by QR. Manual Receptionist fallback records server time and actor without backdating. |
| AC34 | Given QR arrival and `NO_SHOW` race near late grace, then guard-serialized validation permits only an outcome consistent with the recorded arrival timestamp; no duplicate arrival claims or partial state. |

### AI Agent Recommendation — addendum

- Status: PENDING HUMAN REVIEW.
- Recommendation: confirm the Context queue scope and the API response/position visibility choices before implementing this addendum.

### Human Final Review — addendum

- Status: APPROVED — revised 2026-10-25 addendum.
- Decision: Requesting user explicitly approved CONTEXT, SPEC, PLAN, TASKS and T053, including the clinic-QR arrival/no-show contract. Exact QR proof rotation/TTL and schema version remain T053 preflight details before implementation.
- Reviewed at: 2026-10-25 (Asia/Bangkok). Prior 2026-10-23 approval remains historical for the superseded same-queue contract.

## Historical Change Impact Record — EARS normalization and T044 source synchronization v0.9.1
- Changed intent, requirement, architecture, or task assumption: No new behavior. Canonicalized wording for existing requirement statements and aligned the document version with the approved 2026-10-25 reception addendum already present in this Spec.
- Feature Lock boundary affected: FEAT-002 scheduled appointment, arrival, and reception requirements; the approved T044 boundary is preserved.
- Material state-change classification: public/shared behavior documentation only; no code or database change.
- Downstream artifacts invalidated: PLAN and TASKS source/version references require synchronization and fresh Human review. Existing T045 implementation review evidence remains scoped to code; it does not approve this revised Spec.
- Required trace, test, and sync actions: run SDD lint, audit and trace after Human review; update feature registry and affected Visit shared contract only where approved content requires it.
- Review follow-up: review this Spec v0.9.1 and approve/revise/reject before downstream implementation claims.

## Historical AI Agent Recommendation — SPEC v0.9.1
- Status: PENDING HUMAN REVIEW
- Scope: FEAT-002 SPEC v0.9.1 EARS normalization and alignment with the approved 2026-10-25 T044 reception addendum.
- Recommendation: Review the normalized requirements for semantic equivalence and confirm the approved T044 text is represented consistently.
- Evidence: EARS scan of all 44 functional requirement groups; approved T044 addendum dated 2026-10-25 in this artifact; downstream Plan/Tasks synchronization is proposed in the same branch change.
- Risks and assumptions: The compact EARS normalization may expose ambiguity that requires product clarification; no new behavior should be inferred from the wording changes.
- Alternatives considered: Keep noncanonical requirement syntax, which prevents the repository SDD linter from validating the feature.
- Required human decision: Review SPEC v0.9.1 and record APPROVED, REVISE, or REJECTED.

## Historical Human Final Review — v0.9.1
- Status: APPROVED
- Decision: Approve the EARS normalization and alignment with the previously approved 2026-10-25 T044 reception scope; no new business behavior is authorized.
- Reviewer: Owner Long
- Reviewed at: 2026-10-27T17:24:48+07:00
- Follow-up: Continue `/sdd-audit --feature=feat-002-appointment-booking` and `/sdd-trace --feature=feat-002-appointment-booking --diff`; resolve findings before T045 completion.

## Change Impact Record — error-catalog alignment v0.9.2
- Changed intent, requirement, architecture, or task assumption: No runtime behavior change. Added the already-implemented and tested `VISIT_CAPACITY_NOT_AVAILABLE` HTTP 409 mapping to §6 for REQ-BOOK-063.
- Feature Lock boundary affected: Error-contract documentation only; Visit capacity decisions remain Visit-owned.
- Material state-change classification: None; documentation-only error-code mapping.
- Downstream artifacts invalidated: Plan and Tasks source metadata; previous Spec v0.9.1 approval is historical for v0.9.2.
- Required trace, test, and sync actions: review Spec/Plan/Tasks; rerun manual lint and full feature trace after approval. No code test is required for this documentation-only change.
- Review follow-up: Human review the new Spec v0.9.2 and downstream metadata before PR validation.

## AI Agent Recommendation
- Status: PENDING HUMAN REVIEW
- Scope: FEAT-002 SPEC v0.9.2 error-code mapping for REQ-BOOK-063.
- Recommendation: Approve if the documented 409 mapping matches existing approved T031 behavior and implementation evidence.
- Evidence: REQ-BOOK-063 names `VISIT_CAPACITY_NOT_AVAILABLE`; existing Appointment/Visit exception handlers return HTTP 409; T031 preflight records the same mapping; focused tests assert HTTP 409.
- Risks and assumptions: This is intended as documentation alignment only; no endpoint or behavior is added.
- Alternatives considered: Leave the error absent from §6, which makes the generic EARS failure mapping incomplete.
- Required human decision: Review SPEC v0.9.2 and record APPROVED, REVISE, or REJECTED.

## Human Final Review — v0.9.2
- Status: APPROVED
- Decision: Approve the documentation-only addition of the existing `VISIT_CAPACITY_NOT_AVAILABLE` HTTP 409 mapping to §6; Plan and Tasks source metadata are synchronized. No runtime behavior or task scope changes.
- Reviewer: Owner Long
- Reviewed at: 2026-10-27T17:44:11+07:00
- Follow-up: Run full `/sdd-audit --feature=feat-002-appointment-booking` and `/sdd-trace --feature=feat-002-appointment-booking --diff`; then run PR validation from a clean worktree matching the published branch.
