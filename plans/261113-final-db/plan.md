---
title: "Gộp DB OTCMS 85 → 35 bảng (final-db)"
status: in-progress
created: 2026-11-13
owner: NghiaDT
branch: final-db
base: product @ 21e304cd (V108, 85 bảng)
---

# Gộp DB OTCMS 85 → 35 bảng

Người làm và agent đọc theo thứ tự: `agent-rules.md` → file này → file phase của mình → `schema-33-tables.md` khi cần cột chi tiết.

**Cập nhật thực thi 13/11:** phase 00 và 01 đã hoàn tất kiểm chứng trên nhánh `final-db-p01-audit-logs`; phase 02–10 vẫn pending. Hồi quy phase 01: 2067 test, đúng 5 lỗi baseline, 16 test mới đạt; xem `phase-01-verification.md`. Race billing phát hiện ở các lượt trước được bàn giao riêng, chưa sửa. Chưa merge vào nhánh tích hợp; dùng git/Jira để kiểm trạng thái phát hành thực tế.

## 1. Mục tiêu, ràng buộc, ngoài phạm vi, nghiệm thu

- **Mục tiêu:** DB còn 35 bảng = mức tối đa (33 bảng chốt 12/11 + giữ `website_images` và `imaging_reconciliation_cases` 13/11). Không phase nào được thêm bảng và mọi tính năng đang chạy trên `product` vẫn chạy như cũ.
- **Ràng buộc:**
  - API giữ nguyên.
  - Chỉ thêm migration V109+.
  - Hibernate `ddl-auto: validate`.
  - Dữ liệu chỉ là demo nên không cần script chuyển dữ liệu thật; migration vẫn phải chạy được từ DB rỗng.
  - Thời gian: 2 ngày.
- **Ngoài phạm vi:**
  - Tính năng mới.
  - Sửa SDS (làm sau).
  - Audit chất lượng toàn bộ BE (làm sau).
  - Merge nhánh `feat/long/diagnostic-request-result` hay `wip/diagnostic-start-imaging`. Chỉ lấy logic từ đó khi phase ghi rõ.
- **Bỏ tính năng (đã chốt):**
  - Nghỉ phép bác sĩ (các endpoint `…/time-offs` (vd `/api/v1/admin/doctor-profiles/{id}/time-offs`) + FE).
  - Đăng nhập Google.
- **Nghiệm thu cuối (phase 10):**
  - `\dt` đếm được ≤ 35 bảng.
  - `./mvnw test` chỉ còn các lỗi có sẵn.
  - Snapshot OpenAPI không lệch, trừ các endpoint đã bỏ.
  - FE `npm test` + `npx next build` đạt.
  - Đi tay luồng chính trên giao diện.

## 2. Tên bảng cuối cùng (tên vật lý)

Giữ tên cũ khi một bảng cũ "nuốt" bảng khác (D7). Chỉ 4 bảng có tên mới. Tổng 35 bảng.

> Mốc NghiaDT đã chốt là **33 bảng, trần 35**. Hai bảng giữ thêm trong lúc Claude soạn plan là `website_images` và `imaging_reconciliation_cases`. Kết quả phase 00 kiểm chứng phương án staged 35 bảng này; không đồng nghĩa đã kiểm chứng các tính năng sau refactor, hay người dùng đã chốt riêng từng thay đổi so với mốc 33.

| # | Bảng thật | Tên logic trong schema | Gộp từ | Phase |
|---|---|---|---|---|
| 1 | `accounts` | accounts | + account_avatars, account_consents | 02 |
| 2 | `sessions` | sessions | — | 02 |
| 3 | **`verification_codes`** (mới) | verification_codes | email_verifications, email_change_challenges, password_resets (02); care_link_otp_challenges, dependent_add_requests (06) | 02, 06 |
| 4 | `patient_profiles` | patient_profiles | + patient_medical_histories | 06 |
| 5 | `patient_caregivers` | patient_caregivers | — | 06 |
| 6 | `patient_reviews` | patient_reviews | (moderation history → audit_logs) | 09 |
| 7 | `staff_profiles` | staff_profiles | + doctor_profiles | 02 |
| 8 | **`work_shifts`** (mới) | work_shifts | doctor_shifts, staff_work_shifts | 07 |
| 9 | `clinic_services` | clinic_services | — | 07 |
| 10 | `doctor_services` | doctor_services | — | 07 |
| 11 | `rooms` | rooms | + room_capabilities (cột `text[]`) | 07 |
| 12 | `room_supported_services` | room_services | — | 07 |
| 13 | `room_reservations` | room_reservations | — | 07 |
| 14 | `appointments` | appointments | + appointment_arrivals, appointment_checkin_requests | 04 |
| 15 | `visits` | visits | + visit_check_ins, walk_in_requests (05); visit_imaging_result_receipts (03) | 05, 03 |
| 16 | `visit_assignments` | visit_assignments | — | 05 |
| 17 | **`queue_entries`** (mới) | queue_entries | walk_in_reception_queue_entries, visit_exam_queue | 05 |
| 18 | `clinical_examinations` | clinical_examinations | — | 03 |
| 19 | `clinical_examination_diagnoses` | examination_diagnoses | — | 03 |
| 20 | `clinical_service_orders` | service_orders | + clinical_ordering_rounds (→ cột nhóm `ordering_round_id`), clinical_imaging_request_replays, diagnostic_requests, diagnostic_attempts | 03 |
| 21 | `clinical_prescriptions` | prescriptions | — | 03 |
| 22 | `clinical_prescription_items` | prescription_items | — | 03 |
| 23 | `clinical_medical_documents` | medical_documents | — | 03 |
| 24 | `clinical_medical_document_payloads` | (giữ, D5) | — | — |
| 25 | `imaging_studies` | imaging_studies | + series, instances, uid_claims, ingest_operations, idempotency_records, confirmation_outbox, release_inbox | 03 |
| 26 | `imaging_viewer_grants` | (giữ, D3) | — | — |
| 27 | `diagnostic_reports` | service_results | + diagnostic_findings_versions (+ kết quả LAB) | 03 |
| 28 | `invoices` | invoices | — | 08 |
| 29 | `invoice_items` | invoice_items | (+ service_order_id, 03) | 08, 03 |
| 30 | `payment_attempts` | payments | + payment_gateway_events | 08 |
| 31 | `website_contents` | website_contents | + announcements | 09 |
| 31b | `website_images` | (giữ, 13/11 — API tải ảnh trả imageUrl trước khi có nội dung) | — | — |
| 25b | `imaging_reconciliation_cases` | (giữ, 13/11 — case mức sự kiện không có study) | — | 03 |
| 32 | `notifications` | notifications | — | 09 |
| 33 | **`audit_logs`** (mới) | audit_logs | mọi bảng `*_audits`, `*_history`, `*_audit_events`, auth_audits, diagnostic_history_access_audits | 01 (tạo) + từng phase (chuyển) |

**Bảng dùng chung, drop ở phase 10:** `staff_resource_audits` (02 ghi phần DOCTOR_PROFILE, 07 ghi phần WORK_SHIFT).

**Bỏ hẳn:**
- `transaction_guard`: phase 01.
- `account_identities`: phase 02.
- `doctor_time_off`: phase 02.
- `visit_examinations`: phase 05.
- `diagnostic_outbox`, `diagnostic_idempotency_records`: phase 03.

## 3. Phase, người làm, phụ thuộc

| Phase | Nội dung | Người | Migration (staged) | Phụ thuộc | Effort |
|---|---|---|---|---|---|
| [00](phase-00-setup-branch-docker-baseline.md) | Nhánh, Docker, profile `final-db`, baseline OpenAPI, viết toàn bộ migration staged | NghiaDT | V109–V120 (viết) | — | 4h |
| [01](phase-01-audit-logs-and-coordination-lock.md) | `audit_logs` + `AuditLogWriter`, view nhật ký, bỏ `transaction_guard` | NghiaDT | V109 | 00 | 3h |
| [02](phase-02-accounts-auth-staff.md) | Tài khoản, xác thực, bỏ Google, `verification_codes`, gộp `doctor_profiles`, bỏ nghỉ phép | NghiaDT | V110 | 01 | 6h |
| [03](phase-03-diagnostic-imaging-orders.md) | Chỉ định, cận LS, ảnh, kết quả, bỏ outbox; audit của clinical/diagnostic/imaging | NghiaDT | V118 | 01; merge sau 05 | 8h |
| [04](phase-04-appointments.md) | Lịch hẹn: arrivals, check-in requests, audit lịch hẹn | Long | V114 | 01 | 3h |
| [05](phase-05-visits-queue.md) | Lượt khám, check-in, walk-in, hàng chờ, phân công | Long | V115 | 01, 02; merge sau 04 | 8h |
| [06](phase-06-patients-caregivers.md) | Hồ sơ BN, tiền sử, người thân, OTP người thân | Hùng | V111 | 02 (`verification_codes`) | 3h |
| [07](phase-07-shifts-rooms-services.md) | Ca làm việc, phòng, dịch vụ | Hùng | V112 | 01 | 4h |
| [08](phase-08-billing-payos.md) | Hoá đơn, thanh toán payOS | Hùng | V119 | 01 | 3h |
| [09](phase-09-reviews-content.md) | Đánh giá, nội dung web, thông báo chung | Hùng | V113 | 01 | 3h |
| [10](phase-10-integration-regression.md) | Ghép, đếm bảng, hồi quy toàn bộ, merge vào `product` | NghiaDT + cả nhóm | V120 (dọn cuối) | tất cả | 3h |

**Lịch 2 ngày:**
- **Ngày 1, buổi sáng:** NghiaDT làm 00 + 01, merge 01 thật sớm. Từ lúc này Long và Hùng mới bắt đầu, vì mọi phase đều cần `AuditLogWriter`.
  - Trong lúc chờ: Long và Hùng cài Docker, đọc `agent-rules.md` và phase của mình, chạy thử Docker.
- **Ngày 1, buổi chiều đến ngày 2, buổi sáng:** chạy song song.
  - NghiaDT: 02 rồi 03.
  - Long: 04 rồi 05.
  - Hùng: 07, 09, 08, rồi 06 (06 chờ 02 merge).
- **Ngày 2, buổi chiều:** 10.

**Khoá `product` (chốt 13/11):** từ lúc tạo nhánh `final-db` tới khi merge xong phase 10, không đẩy tính năng mới lên `product`. Chỉ nhận sửa lỗi gấp; có thì NghiaDT merge `product` vào `final-db` ngay trong ngày. Nhờ vậy lần merge cuối là fast-forward, không có xung đột.

**Thứ tự merge vào `final-db`:** 01 → 02 → (04, 07, 09, 08 theo cái nào xong trước) → 06 → 05 → 03 → 10.
- 02 merge sớm, vì 02 sửa các chỗ gọi `DoctorProfile…` ở nhiều package. Ai đang làm thì rebase sau khi 02 vào.
- 03 merge sau 05, vì cả hai sửa entity `Visit`.

## 4. Trigger và view cần viết lại (đã kiểm từ schema V108)

| Hàm trigger | Gắn trên | Đọc/ghi bảng bị bỏ | Phase xử lý |
|---|---|---|---|
| `enforce_appointment_visit_link` (constraint trigger) | appointments, visits | visit_check_ins | 05: viết lại đọc cột mới trên `visits` |
| `enforce_checked_in_appointment_reception_evidence`, `enforce_initial_reception_type` (constraint trigger) | visit_check_ins | — | 05: chuyển điều kiện sang `visits` (CHECK hoặc trigger trên `visits`) |
| `enforce_checked_in_appointment_reception_for_visit` | — | visit_check_ins | 05 |
| `protect_queued_visit_source_and_arrival` | visits | walk_in_reception_queue_entries | 05: đổi sang `queue_entries` |
| `enforce_walk_in_reception_queue_visit`, `protect_walk_in_reception_queue_entry_lifecycle` | walk_in_reception_queue_entries | — | 05: chuyển sang `queue_entries` |
| `record_walk_in_reception_queue_transition`, `reject_*walk_in_reception_queue_history*` | queue / history | walk_in_reception_queue_history | 05: bỏ; ghi chuyển trạng thái bằng `AuditLogWriter` (category VISIT, theo phase 01) |
| `enforce_diagnostic_*_scope`, `enforce_diagnostic_request_study_state`, `prevent_*diagnostic*`, `touch_diagnostic_request_updated_at` | diagnostic_* | diagnostic_attempts, diagnostic_requests, diagnostic_findings_versions | 03 |
| `set_appointment_occupied_until` | appointments | — | giữ |
| `protect_patient_care_relationship_history`, `protect_patient_caregiver_r3_metadata` | patient_caregivers | — | giữ |
| `reject_*_audit_mutation`, `reject_append_only_*`, `reject_patient_review_history_mutation`, `reject_visit_imaging_result_receipt_mutation` | các bảng audit cũ | — | bỏ theo bảng; `audit_logs` có trigger append-only riêng (01) |
| `prevent_integration_coordination_guard_mutation` | transaction_guard | — | 01: bỏ |
| View `activity_log_entries` (UNION 16 bảng) | — | mọi bảng audit | 01: viết lại chỉ đọc `audit_logs` |

Hàm plpgsql không bị Postgres theo dõi phụ thuộc: drop bảng xong, trigger đọc bảng đó **chỉ lỗi lúc chạy**. Vì vậy phase nào drop bảng phải `grep` tên bảng trong mọi hàm (`SELECT proname FROM pg_proc WHERE prosrc ILIKE '%<bảng>%'`) và ghi kết quả vào PR.

## 5. Quy ước chung

- Chi tiết: `agent-rules.md`.
- Số migration cố định theo bảng ở mục 3, nằm trong `db/migration-staged/` cho tới khi phase chuyển sang. Đây là ngoại lệ của dải số migration theo người trong `AGENTS.md` (chỉ áp cho đợt refactor).
- Kiểm tra trên GitHub: `build.yml` chỉ chạy trên nhánh `main` và bỏ qua test. Vì vậy **test chạy local là cổng chặn duy nhất**, kết quả phải dán vào PR. NghiaDT review từng PR theo mục "Success Criteria" của phase.

## 6. Rủi ro

| Rủi ro | Dấu hiệu | Xử lý định sẵn |
|---|---|---|
| Hai phase sửa cùng file (entity `Visit`, `Account`, `Invoice`) | Rebase xung đột | Theo thứ tự merge ở mục 3; người merge sau giải xung đột, chỉ giữ dòng của phase mình |
| Trigger đọc bảng đã drop | Test lỗi `relation … does not exist` lúc INSERT/UPDATE | Kiểm `pg_proc` như mục 4 trước khi mở PR |
| Agent sửa lan ngoài phạm vi | PR có file ngoài "Related Code Files" | Reviewer trả PR; agent-rules mục 9 |
| 2 ngày không đủ | Hết ngày 1 mà 02 chưa merge | Dời 06 sang người rảnh; 03 có thể tách 03a (ảnh) / 03b (kết quả + chỉ định) |
| Migration staged sai cột | App không khởi động, lỗi `Schema-validation` | Người làm phase sửa file staged trong PR của mình, ghi "Đổi migration" |

## 7. Câu hỏi còn mở

1. `visit_check_ins.receptionist_account_id` có luôn bằng `visits.created_by_account_id` không? Phase 05 kiểm bằng code. Nếu không bằng thì giữ cột `receptionist_account_id` trên `visits`, như schema đang ghi.
2. Lịch sử nhiều phiên bản đồng ý điều khoản: hiện để ở `audit_logs` (category CONSENT). Nếu cần bảng riêng cho NĐ 13/2023 thì dùng 1 trong 2 chỗ dư.
