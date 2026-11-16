# Schema 33 bảng (bản đã chốt 12/11/2026 23:48)

> Bản sao của đề xuất NghiaDT đã chốt. **Tên bảng trong file này là tên logic.** Tên bảng thật trong DB (tên vật lý) theo bảng "Tên bảng cuối cùng" trong `plan.md` (D7: không đổi tên bảng cũ chỉ để đẹp). Ví dụ: `service_orders` ở đây = bảng thật `clinical_service_orders`; `service_results` = `diagnostic_reports`; `payments` = `payment_attempts`; `room_services` = `room_supported_services`; `examination_diagnoses` = `clinical_examination_diagnoses`; `prescriptions`/`prescription_items` = `clinical_prescriptions`/`clinical_prescription_items`; `medical_documents` = `clinical_medical_documents`.

## 0. Tóm tắt

- Đề xuất **33 bảng** (dư 2 chỗ trong hạn 35, xem mục 4).
- Nguyên tắc:
  1. **Giữ nguyên API** (URL, request, response). DB đổi bên dưới, FE không đổi.
  2. **Bảng trục giữ tên + khoá chính**: `accounts`, `patient_profiles`, `staff_profiles`, `clinic_services`, `rooms`, `appointments`, `visits` → FK từ cụm khác không gãy, các cụm sửa song song được. Riêng `doctor_profiles` gộp vào `staff_profiles` nhưng các cột `doctor_profile_id` ở bảng khác **giữ tên**, chỉ đổi FK sang `staff_profiles(id)` (xem 2.3).
  3. **Ngoại lệ "giữ nguyên API"** (12/11 NghiaDT chốt): bỏ 5 endpoint nghỉ phép `/api/v1/admin/doctors/{id}/time-offs…` và phần FE gọi chúng; bỏ đăng nhập Google (endpoint `GoogleExchangeController`, FE đã không còn nút Google).
  4. **Một sự kiện của một bản ghi = cột của bản ghi đó**, không phải bảng riêng (giờ đến, check-in, tiếp đón…).
  5. **Log gom về một bảng `audit_logs`** thay cho 15+ bảng `*_audits` / history.
  6. **Chống gửi trùng bằng `UNIQUE` trên bảng nghiệp vụ**, bỏ bảng idempotency/replay/outbox/inbox. Lý do chính đáng: tất cả module chung một DB, không cần cơ chế sự kiện giữa hệ thống.
- Không đổi: ràng buộc chống đặt trùng giờ bác sĩ (`EXCLUDE` trên `appointments`) và chống trùng phòng (`EXCLUDE` trên `room_reservations`), giữ giờ thanh toán 15 phút `AWAITING_PAYMENT`.

## 1. Danh sách 33 bảng

| # | Bảng | Cụm | Gộp từ (bảng cũ) |
|---|---|---|---|
| 1 | `accounts` | Tài khoản | accounts + account_avatars + account_consents |
| 2 | `sessions` | Tài khoản | giữ nguyên (refresh token, xoay vòng) |
| 3 | `verification_codes` | Tài khoản | email_verifications + email_change_challenges + password_resets + care_link_otp_challenges + dependent_add_requests |
| 4 | `patient_profiles` | Bệnh nhân | patient_profiles + patient_medical_histories |
| 5 | `patient_caregivers` | Bệnh nhân | giữ nguyên (người thân) |
| 6 | `patient_reviews` | Bệnh nhân | patient_reviews (lịch sử kiểm duyệt → audit_logs) |
| 7 | `staff_profiles` | Nhân sự | staff_profiles + doctor_profiles (D1 đổi 12/11: gộp) |
| 8 | `work_shifts` | Nhân sự | doctor_shifts + staff_work_shifts |
| 9 | `clinic_services` | Dịch vụ | giữ nguyên |
| 10 | `doctor_services` | Dịch vụ | giữ nguyên |
| 11 | `rooms` | Phòng | rooms + room_capabilities (thành cột mảng) |
| 12 | `room_services` | Phòng | room_supported_services (đổi tên) |
| 13 | `room_reservations` | Phòng | giữ nguyên (giữ EXCLUDE chống trùng phòng) |
| 14 | `appointments` | Lịch hẹn | appointments + appointment_arrivals + appointment_checkin_requests |
| 15 | `visits` | Lượt khám | visits + visit_check_ins + walk_in_requests + visit_imaging_result_receipts |
| 16 | `visit_assignments` | Lượt khám | giữ nguyên |
| 17 | `queue_entries` | Lượt khám | walk_in_reception_queue_entries + visit_exam_queue |
| 18 | `clinical_examinations` | Lâm sàng | giữ nguyên |
| 19 | `examination_diagnoses` | Lâm sàng | clinical_examination_diagnoses (đổi tên) |
| 20 | `service_orders` | Lâm sàng/Cận LS | clinical_service_orders + clinical_ordering_rounds + clinical_imaging_request_replays + diagnostic_requests + diagnostic_attempts |
| 21 | `prescriptions` | Lâm sàng | clinical_prescriptions |
| 22 | `prescription_items` | Lâm sàng | clinical_prescription_items |
| 23 | `medical_documents` | Lâm sàng | clinical_medical_documents (metadata; file nằm ở bảng 33, xem D5) |
| 24 | `imaging_studies` | Ảnh | imaging_studies + imaging_study_series + imaging_study_instances + imaging_uid_claims + imaging_ingest_operations + imaging_reconciliation_cases + imaging_idempotency_records + imaging_confirmation_outbox + imaging_release_inbox (xem D4) |
| 25 | `service_results` | Ảnh/Xét nghiệm | diagnostic_reports + diagnostic_findings_versions + (mới) kết quả xét nghiệm UC-48 |
| 26 | `invoices` | Thanh toán | giữ nguyên (bỏ FK tới ordering round) |
| 27 | `invoice_items` | Thanh toán | giữ nguyên (+ `service_order_id`) |
| 28 | `payments` | Thanh toán (payOS) | payment_attempts + payment_gateway_events |
| 29 | `website_contents` | Nội dung | website_contents + website_images + announcements (xem D6) |
| 30 | `notifications` | Nội dung | giữ nguyên |
| 31 | `audit_logs` | Hệ thống | 15 bảng audit/history (gồm account_management_audits, content_audits, patient_review_moderation_history, room_management_audits, room_reservation_audits, visit_assignment_audits, walk_in_reception_queue_history…) + auth_audits + diagnostic_audit_events + imaging_audit_events + diagnostic_history_access_audits + diagnostic_outbox + transaction_guard (bỏ, xem 2.11) |
| 32 | `imaging_viewer_grants` | Ảnh | giữ nguyên (quyền xem DICOM 5 phút, D3) |
| 33 | `clinical_medical_document_payloads` | Lâm sàng | giữ nguyên (file tài liệu y khoa, D5) |

**Tổng: 33 bảng** (dư 2 chỗ trong hạn 35). `notifications` vẫn là bảng riêng (12/11 NghiaDT xác nhận). 12/11 tối giữ thêm 2 bảng cũ để giảm rủi ro sửa code (D3, D5).

Bỏ hẳn, không thay thế: `visit_examinations` (code production không dùng — quét nhóm B), `diagnostic_idempotency_records`, `diagnostic_outbox`, **`doctor_time_off` + tính năng nghỉ phép bác sĩ** (12/11 NghiaDT chốt bỏ: R3 không có UC nghỉ phép).

## 2. Chi tiết từng cụm

Ký hiệu: **+** cột thêm vào bảng trục; **⟵** dữ liệu lấy từ bảng cũ; **⚠** mất gì so với hiện tại.

### 2.1 Tài khoản (10 → 3)

**Bỏ đăng nhập Google** (12/11 NghiaDT chốt): xoá bảng `account_identities` và code BE `auth/controller/GoogleExchangeController`, `auth/integration/GoogleOidcClient`, `UnavailableGoogleOidcClient`, `auth/service/GoogleAuthenticationService(+Impl)`, `GoogleOidcService`, `GoogleExchangeRequest`, `GoogleAuthenticationException` + phần xử lý trong `AuthExceptionHandler`, `AuthSessionService`. FE không còn gọi (đã kiểm tra `frontend/src`). Ghi chú: `GoogleAuthenticationServiceImpl` đang tạo bằng chứng đồng ý điều khoản khi onboard Google → bỏ cùng.

**`accounts`** — giữ toàn bộ cột cũ.
- **+** `avatar_content bytea`, `avatar_content_type` ⟵ account_avatars (1-1).
- **+** `terms_version`, `privacy_version`, `consent_actor_type` (PATIENT/GUARDIAN), `consent_accepted_at` ⟵ account_consents.
- ⚠ Mất lịch sử nhiều lần chấp nhận điều khoản → mỗi lần chấp nhận ghi một dòng `audit_logs` (category CONSENT).

**`verification_codes`** (mới, thay 5 bảng OTP/token)
```
id, account_id (người nhận), purpose (EMAIL_VERIFY | EMAIL_CHANGE | PASSWORD_RESET | CARE_LINK | DEPENDENT_ADD),
code_hash, target_email (EMAIL_CHANGE), link_id (CARE_LINK → patient_caregivers),
payload jsonb (DEPENDENT_ADD: họ tên, ngày sinh, giới tính, CCCD, quan hệ),
failed_attempts, expires_at, consumed_at, superseded_at, created_at
UNIQUE partial: (account_id, purpose) WHERE consumed_at IS NULL AND superseded_at IS NULL   -- một mã hiện hành
UNIQUE (code_hash) WHERE purpose = 'PASSWORD_RESET'
```
- Giữ được: giới hạn gửi lại (đếm theo account_id + purpose + created_at), tối đa 5 lần sai, thay mã cũ.
- ⚠ Mã CARE_LINK hiện "một mã hiện hành trên mỗi link" → unique partial theo `(link_id, purpose)` cho purpose này.

**`sessions`**: giữ nguyên.

### 2.2 Bệnh nhân (7 → 3)

**`patient_profiles`** — giữ cột cũ.
- **+** `conditions, allergies, procedures, injuries, orthopedic_history, medical_history_updated_at, medical_history_updated_by` ⟵ patient_medical_histories (1-1; `version` dùng chung).

**`patient_caregivers`**: giữ nguyên. Yêu cầu thêm người thân đang chờ OTP nằm ở `verification_codes.payload`; xác nhận xong mới tạo `patient_profiles` + `patient_caregivers` (đúng như luồng hiện tại).

**`patient_reviews`**: giữ nguyên (đã có cột trả lời). Lịch sử SUBMIT/APPROVE/HIDE/REPLY → `audit_logs`.

### 2.3 Nhân sự + dịch vụ (9 → 4)
> **Cập nhật 13/11 (phase 02, D-a…D-e):** để giữ nguyên API, `staff_profiles` giữ thêm `in_active_directory` (độc lập với `status`), `consultation_information`, `display_name`, `photo_content`, `photo_url`, thêm cờ `is_doctor`; `work_contact varchar(254) NULL`; không có cột `version`. Chi tiết: `phase-02-accounts-auth-staff.md`. Phần mô tả bên dưới là bản gốc.

**Nguyên tắc chia thông tin bác sĩ / nhân viên** (12/11 NghiaDT: chỉ giữ thông tin cơ bản):
- **Ai cũng có (ở `accounts`, một nguồn duy nhất):** họ tên (`full_name`), tên hiển thị (`display_name`), ảnh đại diện (`avatar_content`), username, email, loại tài khoản (`account_type` = DOCTOR / RECEPTIONIST / TECHNICIAN / ADMIN…), trạng thái đăng nhập.
- **Hồ sơ công tác (ở `staff_profiles`, mọi nhân viên):** liên hệ công tác, trạng thái hồ sơ (đang làm / đã gỡ).
- **Chỉ bác sĩ (cột NULL được ở `staff_profiles`):** chuyên môn, bằng cấp, giới thiệu, có hiện trên trang công khai không, có nhận đặt lịch mới không. Đây là các thông tin UC-16 (xem bác sĩ), UC-17/18 (tạo/sửa hồ sơ) và UC-20/21 (đặt lịch: bác sĩ không nhận lịch thì MSG-24) cần.

**`staff_profiles`** (gộp staff_profiles + doctor_profiles)
```
id, account_id UNIQUE → accounts,
status (ACTIVE | REMOVED),                 -- thay doctor_profiles.in_active_directory
work_contact,
-- chỉ bác sĩ, NULL với nhân viên khác:
specialties, qualifications, biography,
publicly_visible boolean DEFAULT false,
accepting_new_bookings boolean DEFAULT false,
created_at, updated_at, version
```
Bỏ vì trùng hoặc thừa:
| Cột cũ | Xử lý |
|---|---|
| doctor_profiles.full_name, staff_profiles.display_name | dùng `accounts.full_name` / `display_name` |
| doctor_profiles.photo_url, staff_profiles.photo_content(_type) | dùng ảnh đại diện ở `accounts`; API vẫn trả `photoUrl` = đường dẫn ảnh của tài khoản |
| doctor_profiles.in_active_directory | = `status = 'ACTIVE'`; API vẫn trả `inActiveDirectory` |
| doctor_profiles.consultation_information | gộp vào `biography`; API trả `consultationInformation` rỗng (FE còn đọc trường này ở 8 chỗ → hiển thị trống, không lỗi) |

Liên kết:
- Bảng khác (appointments, visit_assignments, queue_entries, work_shifts, doctor_services, appointment/visit…) **giữ tên cột `doctor_profile_id`**, chỉ đổi FK sang `staff_profiles(id)` và giữ nguyên giá trị UUID → code đọc/ghi các cột này không phải đổi.
- "Đây có phải bác sĩ không" kiểm tra bằng `accounts.account_type = 'DOCTOR'` trong service (CHECK không với sang bảng khác được).
- Phạm vi code: 23 file main + 39 file test đang chạm `doctor_profiles` (theo quét).

**`work_shifts`** (mới, thay 2 bảng)
```
id, staff_profile_id → staff_profiles, room_id NULL,
starts_at, ends_at, created_at, updated_at
```
- Ca bác sĩ và ca nhân viên giờ cùng trỏ `staff_profiles` nên không cần cột `kind`.

**Bỏ tính năng nghỉ phép bác sĩ** (12/11 NghiaDT chốt; R3 không có UC):
- Xoá bảng `doctor_time_off`, các endpoint `…/time-offs` (vd `/api/v1/admin/doctor-profiles/{id}/time-offs`) (thêm, sửa, xoá, xem, xem ảnh hưởng), phần trừ giờ nghỉ khi tính giờ trống đặt lịch.
- FE: bỏ phần nghỉ phép trên màn lịch bác sĩ của admin (`features/staff-shifts`, 4 file nhắc tới time-off).
- Phạm vi: 5 file main + 5 file test BE.

- `clinic_services`, `doctor_services`: giữ nguyên.
- Audit dịch vụ (service_audits) và nhân sự (staff_resource_audits) → `audit_logs` (cột before/after jsonb giữ y nguyên).

### 2.4 Phòng (6 → 3)

- **`rooms`** **+** `capabilities text[]` ⟵ room_capabilities.
- **`room_services`** = room_supported_services (đổi tên cho gọn; có thể giữ tên cũ để đỡ sửa code).
- **`room_reservations`**: giữ nguyên, kể cả `EXCLUDE` chống trùng phòng.
- Audit phòng + audit đặt phòng → `audit_logs`. ⚠ Unique partial chống ghi trùng CAPACITY_CONFLICT chuyển sang `audit_logs` (unique partial theo `entity_id, actor, correlation_id` khi action = CAPACITY_CONFLICT).

### 2.5 Lịch hẹn (4 → 1)

**`appointments`** — giữ toàn bộ cột cũ + `EXCLUDE` + trigger `occupied_until`.
- **+** `arrived_at`, `arrival_source` (PATIENT_QR | RECEPTIONIST), `arrival_recorded_by`, `arrival_reason` ⟵ appointment_arrivals ("ai đến trước thắng" = `UPDATE … WHERE arrived_at IS NULL`).
- **+** `checkin_idempotency_key` UNIQUE ⟵ appointment_checkin_requests (bấm 2 lần trả lại cùng `visit_id`).
- Lịch sử đổi trạng thái (appointment_audits) → `audit_logs`. ⚠ Ràng buộc "check-in phải có `verified_patient_profile_id`" hiện là CHECK trên bảng audit → chuyển thành kiểm tra trong service + lưu vào `audit_logs.metadata`.

### 2.6 Lượt khám + hàng chờ (11 → 3)

**`visits`** — giữ cột cũ.
- **+** `reception_type` (APPOINTMENT_CHECK_IN | WALK_IN), `receptionist_account_id` ⟵ visit_check_ins (1-1, `arrived_at`/`checked_in_at` đã có sẵn trên visits).
- **+** `walk_in_idempotency_key` UNIQUE ⟵ walk_in_requests.
- **+** `imaging_result_ready_at`, `findings_released_at` ⟵ visit_imaging_result_receipts (bỏ cơ chế "biên nhận sự kiện"; chẩn đoán cập nhật thẳng vào visit trong cùng transaction).
- Cần kiểm tra trước khi gộp: `visit_check_ins.receptionist_account_id` luôn bằng `visits.created_by_account_id`? Nếu đúng thì không cần thêm cột.

**`queue_entries`** (mới, thay hàng chờ lễ tân + hàng chờ khám)
```
id, visit_id, stage (RECEPTION | EXAM),
doctor_profile_id, room_id, service_id, clinic_date, queue_number,
status, exit_reason, entered_at, queued_at, started_at, awaiting_at, finished_at, left_at
UNIQUE (doctor_profile_id, clinic_date, queue_number) WHERE stage = 'EXAM'
```
- Lịch sử chuyển trạng thái hàng chờ lễ tân (đang do trigger ghi) → `audit_logs`.
- `visit_assignments`: giữ nguyên. Audit gán phòng/bác sĩ + visit_audits → `audit_logs`.

### 2.7 Lâm sàng (9 → 7)

- **`clinical_examinations`**, **`examination_diagnoses`**, **`prescriptions`**, **`prescription_items`**: giữ nguyên (chỉ bỏ tiền tố `clinical_` nếu muốn — xem D7).
- **`service_orders`** — gộp phiếu chỉ định + đợt chỉ định + yêu cầu chụp:
```
⟵ clinical_service_orders: tất cả cột
+ round_number, ordered_at                 ⟵ clinical_ordering_rounds
+ submit_idempotency_key (UNIQUE theo doctor) ⟵ clinical_imaging_request_replays
+ room_id, imaging_type, execution_status, cancellation_cause,
  technician_account_id, started_at, first_use_at, study_ref   ⟵ diagnostic_requests + diagnostic_attempts (attempt luôn = 1)
```
- **`medical_documents`**: giữ metadata như `clinical_medical_documents`; file vẫn ở bảng riêng `clinical_medical_document_payloads` (giữ nguyên, D5).

### 2.8 Ảnh + kết quả (19 → 3)

**`imaging_studies`** — một dòng mỗi lần upload/ca chụp
```
study_ref (PK), service_order_id, visit_id, patient_id, uploaded_by,
status (PROCESSING | QUARANTINED | FAILED | CONFIRMATION_PENDING | LINKED | RECONCILIATION_REQUIRED),
upload_idempotency_key UNIQUE, payload_sha256, payload_size_bytes,
study_instance_uid UNIQUE, archive_study_ref (Orthanc), modality, series_count, instance_count,
dicom_index jsonb   -- {series:[{uid, orthanc_ref, instances:[{sop_uid, orthanc_ref, frames}]}]}
verification_status, verification_evidence jsonb, safe_outcome_code,
reconciliation_reason, admin_decision, version, created_at, updated_at
```
- `study_instance_uid UNIQUE` thay imaging_uid_claims cho mức study; kiểm tra series/instance thuộc study bằng `dicom_index` (thay 2 bảng series/instances).
- Hàng đợi đối soát (reconciliation) = các dòng `status = RECONCILIATION_REQUIRED`.
- ⚠ Mất kiểm tra "một SOP UID không được nằm ở 2 study" trên toàn DB → Orthanc đã tự chống trùng SOP; chấp nhận được (D4).
- Viewer grant 5 phút: giữ nguyên bảng `imaging_viewer_grants` và code hiện tại (D3).

**`service_results`** — kết quả cận lâm sàng (ảnh + xét nghiệm)
```
id, service_order_id, visit_id, study_ref NULL, result_type (IMAGING | LAB),
version_number, state (DRAFT | COMPLETED), findings_text, lab_values jsonb,
report_storage_ref, report_sha256, validation_status, correction_reason,
recorded_by_doctor_id, completed_at, released_at, created_at
UNIQUE (service_order_id, version_number)
```
- Đọc kết quả bản sửa = dòng có version lớn nhất; các bản cũ vẫn còn trong bảng (giữ đúng tính năng "sửa kết quả có lý do").
- UC-48 (xét nghiệm) hiện chưa có chỗ lưu → dùng `result_type = LAB` + `lab_values`.

### 2.9 Thanh toán (4 → 3)

- **`invoices`**: bỏ `clinical_ordering_round_id` (thay bằng `invoice_items.service_order_id`).
- **`invoice_items`** **+** `service_order_id`.
- **`payments`** ⟵ payment_attempts (tất cả cột) **+** `last_event_fingerprint`, `signature_verified`.
  - Chống webhook payOS gửi lại: `UNIQUE (provider, provider_order_code)` + chuyển trạng thái chỉ đi một chiều (đã CONFIRMED thì bỏ qua).
  - Webhook bị từ chối (sai chữ ký) → `audit_logs` (category PAYMENT_GATEWAY).

### 2.10 Nội dung (6 → 2)

- **`website_contents`** **+** `cover_image bytea`, `cover_image_type` ⟵ website_images; **+** `audience`, `recipient_role` ⟵ announcements (`type = ANNOUNCEMENT`). Xem D6.
- **`notifications`**: giữ nguyên. Audit nội dung → `audit_logs`.

### 2.11 Hệ thống (→ 1)

**`audit_logs`**
```
id, occurred_at, category (AUTH | ACCOUNT | CONSENT | APPOINTMENT | VISIT | QUEUE | ROOM | SERVICE | STAFF
                            | CONTENT | REVIEW | DIAGNOSTIC | IMAGING | PAYMENT_GATEWAY | HISTORY_ACCESS),
action, actor_account_id, actor_type, entity_type, entity_id, record_label,
before_state jsonb, after_state jsonb, metadata jsonb,
correlation_id, client_ip, user_agent, identifier_hmac, outcome, duration_ms
INDEX (category, action, identifier_hmac, occurred_at)   -- giữ rate-limit đăng nhập/đăng ký đang đếm trên auth_audits
INDEX (category, occurred_at), (entity_type, entity_id)
Trigger chặn UPDATE/DELETE/TRUNCATE (append-only như các bảng audit hiện tại)
```
- View `activity_log_entries` (đang UNION 16 bảng) viết lại thành `SELECT … FROM audit_logs` → API `GET /api/v1/admin/activity-logs` giữ nguyên.
- **`transaction_guard`** bỏ: `CoordinationGuardRepository.lockGuardRow` đổi sang `pg_advisory_xact_lock(<hằng số>)`. Chỉ sửa 1 file; 40 file đang gọi `CoordinationGuardService` không đổi.

## 3. UC → bảng (kiểm tra phủ 78 UC)

| UC | Bảng chính |
|---|---|
| 01–06 Đăng nhập, quên MK, tài khoản, đăng ký | accounts, sessions, verification_codes, audit_logs |
| 07–09 Tài khoản nhân viên | accounts, staff_profiles, audit_logs |
| 10–15 Hồ sơ BN, tiền sử, người thân | patient_profiles, patient_caregivers, verification_codes |
| 16–19 Bác sĩ, hồ sơ nhân viên | accounts, staff_profiles, doctor_services |
| 20–24 Đặt / đổi BS / huỷ / xem lịch hẹn | appointments, work_shifts, doctor_services, clinic_services |
| 25–28 Ca làm việc | work_shifts |
| 29–36 Check-in, hàng chờ, phòng, tiến độ, đóng lượt khám | appointments, visits, queue_entries, visit_assignments, room_reservations |
| 37–42 Khám, sửa kết quả, hồ sơ cũ, lời dặn | clinical_examinations, examination_diagnoses, medical_documents |
| 43–44 Đơn thuốc | prescriptions, prescription_items |
| 45–48 Chỉ định CLS, worklist, upload ảnh, xét nghiệm | service_orders, imaging_studies, service_results |
| 49–52 Xem DICOM, đọc kết quả, trạng thái chỉ định | imaging_studies, service_results, service_orders |
| 53–58 Thanh toán, lịch sử, biên lai | invoices, invoice_items, payments |
| 59–62 Đánh giá | patient_reviews, audit_logs |
| 63 Thông báo | notifications |
| 64–67 Phòng | rooms, room_services, room_reservations |
| 68–71 Dịch vụ | clinic_services, doctor_services |
| 72–75 Nội dung web, thông báo chung | website_contents |
| 76 Nhật ký hệ thống | audit_logs (qua view) |
| 77–78 Dashboard, báo cáo | đọc tổng hợp từ appointments, visits, invoices, payments, service_orders |

Không UC nào thiếu chỗ lưu. UC-48 hiện chưa có bảng nào trong code cũ → bản mới bổ sung.

## 4. Các điểm quyết định (D1–D9) — ĐÃ CHỐT theo cột "Đề xuất" (12/11)

Tiêu chí chốt: không làm mất tính năng đang chạy; nơi nào bỏ bảng thì phải có thay thế giữ đúng hành vi (UNIQUE, cột trạng thái, token ký, cập nhật trong cùng transaction). D8 và D9 không thuộc đợt gộp bảng.

| # | Câu hỏi | Đề xuất (đã chốt) | Nếu chọn khác |
|---|---|---|---|
| D1 | Gộp `doctor_profiles` vào `staff_profiles`? | **Gộp** (12/11 NghiaDT đổi). Tránh nghẽn bằng cách giữ tên cột `doctor_profile_id` + giữ UUID, chỉ đổi FK; cụm Nhân sự làm trước | — |
| D2 | Bỏ toàn bộ outbox/inbox/idempotency giữa diagnostic ↔ imaging ↔ visit? | **Bỏ** — cùng một DB, cập nhật thẳng trong 1 transaction. Đã kiểm tra 12/11: `DiagnosticEvidenceServiceImpl.deliver()` là chỗ DUY NHẤT gọi `VisitDiagnosticPort.recordResultReady / acknowledgeFindingsRelease`, `ImagingStudy.acceptFindingsRelease` và set `diagnostic_reports.released_at`; `deliver()` chỉ chạy trong `dispatchDueOutbox`, mà hàm này **không có code production nào gọi** → hiện tại lượt khám không bao giờ nhận "đã có kết quả", kết quả không bao giờ "phát hành" (lỗi sẵn có). Bản mới: khi bác sĩ hoàn tất kết quả, cùng transaction set `service_results.released_at`, `visits.findings_released_at`, `imaging_studies.status`; khi ảnh LINKED, set `visits.imaging_result_ready_at`. Luồng khám: chỉ định → chụp → đọc → phát hành → bác sĩ khám xem được, kiểm bằng test hồi quy cụm Ảnh | Giữ: +3–4 bảng, vẫn phải sửa lỗi không ai gửi |
| D3 | Viewer grant DICOM 5 phút: bảng hay token ký? | **Giữ bảng `imaging_viewer_grants`** (12/11 tối đổi): code bảo mật DICOMweb đang chạy, không viết lại | Token ký HMAC: bớt 1 bảng, phải viết lại kiểm quyền viewer |
| D4 | Bỏ claim UID toàn cục (series/SOP)? | **Bỏ**, giữ UNIQUE study_instance_uid + Orthanc tự chống trùng | Giữ: +1 bảng |
| D5 | File tài liệu y khoa để chung bảng metadata? | **Giữ bảng payload riêng** `clinical_medical_document_payloads` (12/11 tối đổi): code đang tách sẵn, tránh bẫy bytea không lazy được | Chung bảng: bớt 1 bảng, phải đọc file qua query riêng |
| D6 | Thông báo chung (announcements) gộp vào website_contents? | **Gộp** (`type = ANNOUNCEMENT`) | Tách: +1 bảng |
| D7 | Đổi tên bảng cho gọn (bỏ tiền tố `clinical_`, `room_supported_services` → `room_services`)? | **Không đổi tên** bảng nào chỉ để đẹp — đỡ sửa code; chỉ bảng mới có tên mới | Đổi: đẹp ERD hơn, thêm việc |
| D8 | Ghi log thao tác admin của Owner (hiện không ghi)? | Ghi vào `audit_logs`, nhưng **làm sau** đợt gộp bảng | — |
| D9 | R3 UC-55 ghi VNPay, code dùng payOS | **payOS** (12/11 NghiaDT chốt; NghiaDT tự sửa R3) | — |

## 4b. Rà soát theo checklist thiết kế OLTP (skill ak:databases, 12/11)

| Mục kiểm | Kết quả | Quy tắc áp dụng khi viết migration |
|---|---|---|
| Khoá chính ổn định, ràng buộc nghiệp vụ bằng UNIQUE | Đạt: PK `uuid`; mọi chỗ bỏ bảng idempotency đều thay bằng UNIQUE (check-in, walk-in, upload, payOS order code, study UID) | Ghi đủ UNIQUE ở mục 2 vào migration, không để service tự kiểm |
| Tiền tệ, thời gian đúng kiểu | Đạt: giá/tiền đang là `numeric(12,2)`/`numeric(14,2)`, thời gian `timestamptz` | Giữ nguyên kiểu khi gộp, không đổi sang float |
| FK khi bản ghi mồ côi là lỗi | Đạt cho bảng nghiệp vụ. `audit_logs.entity_id` cố ý KHÔNG có FK (log phải còn khi bản ghi bị xoá) | Index `(entity_type, entity_id)` |
| Index cho FK | Postgres không tự tạo index cho cột FK | **Mỗi cột FK mới phải có index** (trừ khi đã đứng đầu một index/UNIQUE khác) |
| Nhiều-nhiều có bảng nối | Đạt: `doctor_services`, `room_services` | — |
| Mảng / JSON nhúng: chỉ khi đọc-ghi cùng bản ghi cha và có giới hạn kích thước | `rooms.capabilities text[]` (vài mã) ✔; `imaging_studies.dicom_index jsonb` (số series/instance của 1 study, đọc cùng study) ✔; `verification_codes.payload` (1 người thân chờ OTP) ✔; `service_results.lab_values` ✔ | GIN index trên `rooms.capabilities` và `dicom_index` (jsonb_path_ops) vì có truy vấn lọc theo giá trị bên trong |
| Dữ liệu cá nhân nhạy cảm | `verification_codes.payload` chứa CCCD, ngày sinh người thân | Xoá `payload` khi mã đã dùng/hết hạn (job dọn hoặc set NULL lúc consume) |
| Bảng một-bảng-nhiều-loại (staff_profiles, queue_entries, work_shifts không còn kind) | Cột riêng của bác sĩ để NULL với nhân viên khác; hàng chờ phân biệt bằng `stage` | Unique partial theo `stage`; kiểm tra "là bác sĩ" ở service vì CHECK không với sang `accounts` |
| Ảnh/file `bytea` trong bảng hay đọc | Dự án **không bật bytecode enhancement** của Hibernate → `@Basic(fetch = LAZY)` không có tác dụng; nếu map cột `bytea` vào entity `accounts` (đọc ở mọi request qua JWT filter) thì mỗi request kéo theo ảnh | **Không map cột ảnh/file vào entity chính**; đọc/ghi qua query riêng (projection hoặc native). `ddl-auto: validate` chỉ kiểm cột đã map nên không lỗi. Áp cho `accounts.avatar_content`, `website_contents.cover_image` (file tài liệu y khoa đã ở bảng riêng) |
| Lịch sử phải khôi phục được | Lịch sử thay đổi nằm ở `audit_logs` (append-only, chặn UPDATE/DELETE/TRUNCATE bằng trigger) | Giữ `before_state`/`after_state` jsonb như các bảng audit cũ |
| Thay đổi phá huỷ có ghi bước migrate/rollback | Dữ liệu Supabase là demo (12/11 NghiaDT xác nhận) → không cần script chuyển dữ liệu; rollback = dựng lại DB từ migration | Mỗi migration cụm: tạo bảng/cột mới → xoá trigger + bảng cũ; seed lại sau đợt |

## 5. Rủi ro và việc kèm theo (core xử lý)

- **30 hàm trigger + ~37 trigger** hiện giữ quy tắc trong DB (append-only, khoá trạng thái kết quả, liên kết lịch hẹn ↔ lượt khám…). Migration phải xoá trigger của bảng bị gộp và viết lại phần còn cần trên bảng mới.
- **`ddl-auto: validate`**: migration từng cụm phải vào cùng PR với code của cụm đó (đã chốt trong brainstorm).
- **Test**: 53 file test chạy SQL thẳng vào bảng nhóm A, nhiều file ở nhóm B/C; phải sửa cùng cụm.
- **Đọc chéo bằng SQL** (cần ghi vào ticket): appointment/content/staff/activitylog đọc `accounts`; staff đọc `rooms`; reporting đọc appointments/visits/visit_assignments; billing ghi `clinical_service_orders.payment_status`; diagnostic cancel ghi `clinical_service_orders`.
- **Code đang dở chưa commit** trên máy này (5 file diagnostic + 2 file FE imaging, `startImaging`/acquisition-starts) → phải commit hoặc chốt trước khi tạo nhánh refactor.
- FE: khu KTV ảnh (10 trang), lễ tân (10/14 phần), thanh toán phía BN đang chạy mock → các cụm này thiết kế theo R3, không theo FE.

## 6. Câu hỏi còn mở

1. (Đã chốt D1–D9.) Code dở diagnostic/imaging đã commit tạm ở nhánh `wip/diagnostic-start-imaging` (bcbfe794), chưa merge vào `product`.
2. Kiểm tra dữ liệu: `visit_check_ins.receptionist_account_id` có luôn bằng `visits.created_by_account_id` không (quyết định có cần thêm cột `receptionist_account_id`).
3. Có cần giữ lịch sử nhiều phiên bản đồng ý điều khoản trong bảng riêng cho mục đích pháp lý (NĐ 13/2023) không, hay `audit_logs` là đủ.
