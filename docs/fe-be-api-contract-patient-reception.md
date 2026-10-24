# Hợp đồng API FE ↔ BE — khu Bệnh nhân, Lễ tân, trang công khai

Cập nhật: 24/10/2026 · Người tổng hợp: NghiaDT (điều phối FE) · Nguồn: code FE `frontend/src` + ghi chú từng phase (`plans/261024-1610-fe-patient-reception-implementation/api-notes/`).

FE đã dựng xong khu `/portal` (bệnh nhân), `/staff` (Lễ tân: tiếp nhận, hàng đợi, lịch hẹn) và các trang công khai trên **lớp API giả**. Mỗi hàm trong `features/<feature>/api/*Api.ts` đã đúng URL, method, DTO và hình lỗi mà BE cần trả. Tài liệu này là thứ BE làm theo để khi nối chỉ đổi lớp api, không sửa màn hình.

Nguồn nghiệp vụ: R3 SRS bản Drive (số BR/MSG theo bản Drive; `docs/appendix-rules.md` trong repo là bản cũ 19/10, đánh số BR lệch) và đặc tả tách tài khoản/hồ sơ 23/10.

## 1. Quy ước chung (mọi endpoint)

- Base `/api/v1/`, danh từ số nhiều, key `camelCase`, enum `UPPER_SNAKE`, id chuỗi.
- Ngày `"2026-10-24"`; thời điểm ISO có offset `"2026-10-24T09:15:00+07:00"`; tiền số nguyên VND (`250000`).
- Danh sách: query `page` (0-based), `size` (mặc định 20, tối đa 100), `sort=field,asc|desc`. Response `{ "items": [], "page": 0, "size": 20, "totalItems": 134, "totalPages": 7 }`. Một số danh sách ngắn chỉ trả `{ "items": [] }` (ghi rõ ở từng endpoint).
- **`allowedActions`**: bản ghi có thao tác trả mảng tên thao tác (`["cancel", "payExamFee"]`). FE chỉ vẽ nút có trong mảng, không tự suy từ trạng thái, nên BE là nơi quyết định quyền và hạn (BR-22 không đổi giờ, hạn hủy, phí đã trả…).
- Tạo → `201` + bản ghi; sửa/đổi trạng thái → `200` + bản ghi mới; xóa/gỡ → `204`.
- **Lỗi = ProblemDetail (RFC 9457) + `code` MSG**, FE tra câu chữ theo mã trong `lib/messages/appMessages.ts`:

```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "code": "MSG-30", "detail": "…", "params": { "time": "08:30" },
  "fieldErrors": [{ "field": "phone", "code": "MSG-17" }] }
```

  `fieldErrors` → hiện dưới ô; còn lại → toast. 400 dữ liệu sai · 403 không đủ quyền (MSG-40 khu bệnh nhân, MSG-38/40 khu Lễ tân, xem câu hỏi) · 404 không còn hoặc không thuộc tài khoản (MSG-41, không lộ là có tồn tại) · 409 xung đột trạng thái/trùng · 5xx MSG-04.
- **Phạm vi dữ liệu**: tài nguyên của chính người đăng nhập dùng `/api/v1/me/...`; dữ liệu nghiệp vụ dùng `/api/v1/<nghiệp vụ>` và **BE lọc theo tài khoản** (hồ sơ đã gắn qua liên kết, gồm "Bản thân"). FE không gửi id người dùng. Lọc một người khám dùng query `profileId=`.
- **Xác thực**: FE gắn `Authorization: Bearer <Supabase access token>` qua `apiRequest` (`lib/api/client.ts`). Trang công khai `/api/v1/public/**` phải gọi được **khi chưa đăng nhập** → BE mở không cần token, FE cần thêm biến thể `apiRequest` không gắn token khi nối.
- Vai trò ứng dụng nằm trong dữ liệu tài khoản (`GET /api/v1/me` trả `kind`/`role`); không dùng claim `role` của Supabase.

## 2. Cách nối BE (FE làm khi BE xong từng nhóm)

1. Trong file `api/*Api.ts` của feature, đổi cờ `USE_MOCK` (hoặc nhánh mock) sang gọi `apiRequest` — nhánh thật đã viết sẵn ở hầu hết hàm.
2. Xóa module chỉ phục vụ mock (đều có comment "xóa khi nối BE"): `lib/mock/*` (kho bệnh nhân, registry phí khám dùng chung), `lib/session/mockCurrentActor.ts`, `lib/session/mockSignedInKind.ts`, `features/appointments/components/BookingMockStoreBootstrap.tsx`, `features/notifications/components/PatientNotificationMockBootstrap.tsx`, các file `*MockData.ts`/`*MockStore.ts`.
3. Test component đi qua lớp API giả; khi nối, mock ở ranh giới `apiRequest` (không mock hook) — xem `otcms/AGENTS.md`.

## 3. Bảng tra endpoint theo nghiệp vụ

Cột "Chi tiết" trỏ tới mục phụ lục cùng file (DTO, lỗi, luật BE phải kiểm). "Đề xuất" = FE chưa gọi URL (hàm vẫn mock), BE chốt rồi FE nối.

### 3.1 Tài khoản, phiên (UC01, UC03–UC06)
| Method | URL | Dùng ở | Chi tiết |
| --- | --- | --- | --- |
| GET | `/api/v1/me` | Mọi khu: chuyển trang theo vai trò `/workspace`, header | P01 |
| PATCH | `/api/v1/me` | `/portal/account` sửa tên hiển thị | P01 |
| PUT / DELETE | `/api/v1/me/avatar` | Ảnh đại diện (multipart) | P01 |
| POST | `/api/v1/me/email-change`, `/api/v1/me/email-change/verify` | Đổi email qua OTP 2 bước | P01 |
| POST | `/api/v1/me/password` | `/portal/account/password` | P01 |
| POST | `/api/v1/patient-accounts` · `/email-verification` · `/email-verification/resend` | Đăng ký bệnh nhân (chỉ tạo tài khoản: họ tên, email, mật khẩu) — **Đề xuất** | P01 |

### 3.2 Hồ sơ bệnh nhân của tài khoản (UC12–UC15)
| Method | URL | Dùng ở | Chi tiết |
| --- | --- | --- | --- |
| GET | `/api/v1/me/patient-profiles` | Mọi màn có "Người khám" (`ProfileScopeSelect`), đặt lịch | P00, P02 |
| GET | `/api/v1/me/patient-profiles/suggestions` | Gợi ý hồ sơ khám tại quầy có email trùng email đã xác minh | P02 |
| POST | `/api/v1/me/patient-profiles/links` | Gắn hồ sơ gợi ý (chọn quan hệ) | P02 |
| POST | `/api/v1/me/patient-profiles` | Tạo hồ sơ mới (Bản thân / người thân) | P02 |
| GET / PATCH | `/api/v1/me/patient-profiles/{id}` | Chi tiết, sửa theo `editableFields` | P02 |
| DELETE | `/api/v1/me/patient-profiles/{id}/link` | Bỏ gắn (lịch sử khám vẫn giữ) | P02 |
| GET / PUT | `/api/v1/patients/{id}/medical-history` | Tiền sử (khu bệnh nhân) | P02 |

### 3.3 Đặt lịch, lịch hẹn (UC20–UC24)
| Method | URL | Dùng ở | Chi tiết |
| --- | --- | --- | --- |
| GET | `/api/v1/appointments/bookable-services` | Đặt lịch (chỉ dịch vụ Khám) | P03 |
| GET | `/api/v1/appointments/open-times` | Ngày/giờ còn trống, bác sĩ có ca | P03 |
| POST | `/api/v1/appointments` | Đặt lịch (bệnh nhân và Lễ tân) | P03, I01 |
| GET | `/api/v1/appointments` | Danh sách lịch: khu bệnh nhân (`profileId`, `tab`) và Lễ tân (`date`, `doctorId`, `status`, `q`) | P04, S02 |
| GET | `/api/v1/appointments/{id}` | Chi tiết lịch hẹn | P04 |
| POST | `/api/v1/appointments/{id}/cancel` | Bệnh nhân hủy trước check-in | P04 |
| GET | `/api/v1/appointments/booking-patients/{patientId}` | Lễ tân đặt hộ: thông tin người khám | S02 |
| GET | `/api/v1/appointments/doctors` | Bộ lọc bác sĩ khu Lễ tân (mới, ngoài spec) | S02 |
| GET | `/api/v1/appointments/{id}/eligible-doctors` | Chuyển bác sĩ cùng giờ | S02 |
| POST | `/api/v1/appointments/{id}/reassign` | Chuyển bác sĩ | S02, I02b |
| POST | `/api/v1/appointments/{id}/notify-no-replacement` | Báo bệnh nhân khi không có bác sĩ thay (AF1) | S02 |

### 3.4 Tiếp nhận, hàng đợi tại quầy (UC10, UC29, UC30, phí khám UC54)
| Method | URL | Dùng ở | Chi tiết |
| --- | --- | --- | --- |
| GET | `/api/v1/patients/lookup` | Tìm hồ sơ: mã BN, hoặc SĐT + ngày sinh (+ họ tên khi nhiều kết quả) | S04a |
| POST | `/api/v1/patients` | Tạo hồ sơ vãng lai (email không bắt buộc, cần hỏi đồng ý) | S04a |
| GET | `/api/v1/reception/today-appointments` | Danh sách lịch hôm nay ở màn Tiếp nhận | S04b |
| GET | `/api/v1/reception/check-in-context` | Bối cảnh tiếp nhận theo `appointmentId` hoặc `patientId` | S04b |
| GET | `/api/v1/reception/priority-groups` | Nhóm ưu tiên | S04b |
| POST | `/api/v1/reception/exam-fee-payments` | Thu phí khám: tiền mặt / tạo QR VNPay | S04b |
| GET | `/api/v1/reception/exam-fee-payments/{id}` | Poll trạng thái QR (3 giây) | S04b |
| POST | `/api/v1/reception/check-ins` | Tiếp nhận → cấp số thứ tự, phòng | S04b |
| GET | `/api/v1/reception/queue` | Hàng đợi tiếp nhận (chỉ xem, tự tải 20 giây) | S05 |
| GET | `/api/v1/reception/queue/{queueEntryId}` | Tóm tắt lượt khám trong Sheet | S05 |

### 3.5 Lượt khám, thanh toán (UC34, UC53, UC55, UC56)
| Method | URL | Dùng ở | Chi tiết |
| --- | --- | --- | --- |
| GET | `/api/v1/visits` | Danh sách lượt khám của bệnh nhân | P05 |
| GET | `/api/v1/visits/{id}/progress` | Tiến độ: số thứ tự từng phòng, chỉ định (tự tải 30 giây) | P05 |
| GET | `/api/v1/payments/payable-sources` | Khoản cần thanh toán (phí khám lịch hẹn, chỉ định của lượt) | P06, I01 |
| GET | `/api/v1/payments/payable-sources/{kind}/{id}` | Chi tiết nguồn cần trả (`kind` = `appointment` \| `visit`) | P06, I01 |
| POST | `/api/v1/payments/online` | Tạo giao dịch VNPay (FE gửi tạm tính để BE đối chiếu tổng) | P06 |
| GET | `/api/v1/payments/{id}` | Kết quả giao dịch (poll 10 giây, tối đa 15 phút) | P06 |
| GET | `/api/v1/payments` | Lịch sử thanh toán + phiếu thu | P06 |

### 3.6 Kết quả, đơn thuốc, ảnh (UC41, UC44, UC51)
| Method | URL | Dùng ở | Chi tiết |
| --- | --- | --- | --- |
| GET | `/api/v1/visits/results` | Danh sách lượt có kết quả | P07 |
| GET | `/api/v1/visits/{visitId}/results` | Kết quả một lượt (khối theo `visibleBlocks`) | P07 |
| GET | `/api/v1/prescriptions`, `/api/v1/prescriptions/{id}` | Đơn thuốc | P07 |
| GET | `/api/v1/imaging-studies`, `/api/v1/imaging-studies/{id}` | Ảnh chụp, `viewerUrl` có token BE cấp (null = chưa xem được) | P07 |

### 3.7 Thông báo, đánh giá (UC59, UC63)
| Method | URL | Dùng ở | Chi tiết |
| --- | --- | --- | --- |
| GET | `/api/v1/me/notifications` | Trang thông báo (khu bệnh nhân + admin) | P08 |
| GET | `/api/v1/me/notifications/unread-count` | Số trên chuông | P08 |
| POST | `/api/v1/me/notifications/{id}/read`, `/api/v1/me/notifications/read-all` | Đánh dấu đã đọc | P08 |
| GET | `/api/v1/me/reviews/eligible-visits` | Lượt khám có thể đánh giá | P08 |
| GET | `/api/v1/me/reviews` | Đánh giá đã gửi | P08 |
| POST | `/api/v1/reviews` | Gửi đánh giá (`visitId`, số sao, nội dung) | P08 |

### 3.8 Trang công khai (UC16, UC60, UC71, UC75) — không cần đăng nhập
| Method | URL | Dùng ở | Chi tiết |
| --- | --- | --- | --- |
| GET | `/api/v1/public/services`, `/api/v1/public/services/{id}` | `/services` | P09 |
| GET | `/api/v1/public/doctors`, `/api/v1/public/doctors/{id}` | `/doctors` (bác sĩ tắt hiển thị → 404) | P09 |
| GET | `/api/v1/public/reviews` | `/reviews`, trang chủ | P09 |
| GET | `/api/v1/public/announcements`, `/api/v1/public/announcements/{id}` | `/announcements` (nhóm PUBLIC) | P09 |
| GET | `/api/v1/public/website-content/ABOUT_PAGE` · `CLINIC_INFO` · `{articleId}` | `/gioi-thieu`, `/bai-viet/[id]`, trang chủ | P09 |

## 4. Việc BE bắt buộc kiểm (FE ẩn nút chỉ là hỗ trợ giao diện)

- **Quyền theo bản ghi**: mọi dữ liệu khu bệnh nhân chỉ thuộc hồ sơ đã gắn với tài khoản; ngoài phạm vi → 404 MSG-41 (không lộ tồn tại). Vãng lai không có tài khoản không nhận thông báo (BR-68 theo đặc tả).
- **Đặt lịch**: chỉ dịch vụ Khám; giờ phải còn trống lúc ghi (trùng → 409 MSG-30); không đổi giờ (BR-22); chỉ bệnh nhân hủy lịch Booked trước check-in; Lễ tân/Admin chỉ chuyển bác sĩ cùng giờ.
- **Tiền**: số tiền, khoản được trả, trạng thái do BE tính; FE gửi tạm tính và dừng nếu BE trả tổng khác. Trả trùng/đã trả → 409 MSG-49. Không trả một phần online. Chặn SUCCESS muộn của QR VNPay khi Lễ tân đã chuyển sang tiền mặt.
- **Tiếp nhận**: một lịch chỉ check-in một lần (409 MSG-37); quay lại ≤ 7 ngày không thu phí; `peopleAhead` do BE tính có xét ưu tiên để phiếu số và hàng đợi khớp nhau.
- **Hồ sơ**: tìm theo BR-14 mới (mã BN, hoặc SĐT + ngày sinh + họ tên khi nhiều kết quả, không trả danh sách người khác); gắn online chỉ khi email trùng email đã xác minh; tối đa một "Bản thân" mỗi tài khoản và mỗi hồ sơ (MSG-21).
- **Kết quả, ảnh**: chỉ khối được phép hiện mới có trong `visibleBlocks`; `viewerUrl` chỉ cấp khi study đã phát hành, kèm token kiểm theo study.

## 5. Câu hỏi mở cho BE / nhóm (gom từ các phase)

1. Mã 403 khu Lễ tân: MSG-38 hay MSG-40 (hai màn đang dùng khác nhau).
2. Lỗi trùng hồ sơ (409) và tiền sử bị sửa đồng thời (STALE) chưa có mã MSG — đang dùng `detail: "DUPLICATE_PROFILE"` / `"STALE"`.
3. Câu "không hoàn tiền" trước khi trả và thông báo thanh toán chưa có mã MSG trong R3.
4. Đăng ký tài khoản: BE gọi Supabase `auth.signUp` (với `data.displayName`) hay FE gọi trực tiếp?
5. Lịch hủy khi chưa trả phí: `examFee.status` giữ `PENDING_PAYMENT` hay chuyển `NOT_REQUIRED`?
6. Giới hạn đặt trước bao nhiêu ngày, chặn hủy sát giờ (FE mock đang 14 ngày, không chặn).
7. POST đặt lịch nên trả kèm `allowedActions` (bớt một lần GET).
8. `PrescriptionDocument.patient.relationship`, `PatientPayment.patientId/relationship`, `BookingPatient.patientCode`: FE cần để ghi "của ai" — BE trả thêm.
9. Xem study ở lượt Chưa hoàn tất: có cấp `viewerUrl` không?
10. Mã bệnh nhân định dạng `BN-000123`?

## Phụ lục — Chi tiết theo phase (DTO, lỗi, URL giữa màn)

Nội dung dưới đây ghép nguyên văn từ ghi chú của từng phase, đã bỏ các mục chỉ mô tả dữ liệu giả.

### P00 · API notes — P00 (nền khu bệnh nhân + dùng chung)

Trạng thái: FE đang dùng lớp API giả, chữ ký khớp REST. Phase P00 không gọi endpoint ghi nào.

#### Endpoint FE gọi

| Method | URL | Query / body | Response | Lỗi | File FE | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/me/patient-profiles` | Không query, không body. Header `Authorization: Bearer <access token>` (FE gắn qua `apiRequest`). | `200 { items: LinkedProfile[] }` | `500` ProblemDetail `code: "MSG-04"` (FE hiện "Hệ thống đang gặp sự cố…"). Không có 401/403 riêng ở P00 (FE chưa xử lý phiên hết hạn). | `src/lib/patient-scope/linkedProfilesApi.ts` → `listLinkedProfiles(signal?)`; dùng bởi `src/hooks/useLinkedProfiles.ts` | Chỉ trả hồ sơ đã gắn với **tài khoản đang đăng nhập** (BR-01, BR-16); FE không gửi id người dùng. Sắp xếp: bản thân (`SELF`) đứng đầu, rồi theo `fullName` (tiếng Việt). Không trả CCCD, SĐT hay địa chỉ ở đây. |

##### Kiểu dữ liệu trong `items[]`

`LinkedProfile` (`src/lib/patient-scope/types.ts`):

| Trường | Kiểu | Ghi chú |
| --- | --- | --- |
| `id` | string (UUID) | Mã hồ sơ, dùng cho `?profileId=` và route `/portal/profiles/{id}`. |
| `patientCode` | string | Dạng `BN-000123`. |
| `fullName` | string | |
| `dateOfBirth` | string `YYYY-MM-DD` | Chưa hiển thị ở P00. |
| `sex` | enum `"MALE" \| "FEMALE"` | |
| `relationship` | enum `"SELF" \| "PARENT" \| "CHILD" \| "SPOUSE" \| "SIBLING"` | Nhãn FE: Bản thân, Cha/Mẹ, Con, Vợ/Chồng, Anh/Chị/Em. |

Hình ví dụ: `{ "items": [{ "id": "5c1e7a20-…-4f01", "patientCode": "BN-000123", "fullName": "Nguyễn Văn An", "dateOfBirth": "1992-07-21", "sex": "MALE", "relationship": "SELF" }] }`

#### Hợp đồng URL giữa màn (query param)

| Tham số | Nơi dùng | Ý nghĩa | Ghi chú |
| --- | --- | --- | --- |
| `?profileId=<id>` | Mọi màn có `ProfileScopeSelect` (phase 04–08) | Lọc theo một hồ sơ; vắng tham số = "Tất cả". | Giá trị là `LinkedProfile.id`. Trang đọc và truyền xuống component, component không tự đọc router. BE nhận `profileId` ở các endpoint nghiệp vụ sẽ định ở phase tương ứng. |
| `/portal/payments/result?paymentId=<id>` | Kết quả VNPay (phase 06) | Mã phiếu/giao dịch thanh toán. | Helper `PATIENT_ROUTES.paymentResult` chỉ là đường dẫn gốc, trang thêm query. BE xác nhận kết quả rồi chuyển về đây (BR-51). |
| `/portal/reviews/new?visitId=<id>` | Đánh giá lượt khám (phase 08) | Lượt khám cần đánh giá. | Helper `PATIENT_ROUTES.newReview(visitId)`. BE kiểm lượt khám thuộc tài khoản và đã hoàn tất (quyền theo bản ghi). |

#### Ngoài phạm vi P00 (đã ghi để BE biết hướng)

- Dữ liệu nghiệp vụ dùng `/api/v1/<nghiệp vụ>` (`/appointments`, `/visits`, `/payments`…), BE lọc theo phạm vi tài khoản; không dùng tiền tố `/patient`.
- Ghi hồ sơ (gắn, tạo, bỏ gắn) thuộc phase 02, sẽ có endpoint `/api/v1/me/patient-profiles` kiểu POST/PUT/DELETE; không có trong P00.

### P01 · API notes — P01 (đăng nhập, đăng ký, tài khoản bệnh nhân)

FE hiện chạy lớp API giả cùng chữ ký hàm; bảng dưới là hợp đồng đích để BE nối (chỉ đổi thân hàm). Lỗi theo ProblemDetail + `code` MSG; `fieldErrors` gắn vào trường.

#### Quy ước chung
- `kind`: `STAFF` | `PATIENT`. `role` (STAFF): `ADMIN` | `CLINIC_OWNER` | `RECEPTIONIST` | `ORTHOPEDIC_DOCTOR` | `IMAGING_TECHNICIAN` | `LAB_TECHNICIAN`. `status`: `ACTIVE` | `INACTIVE`.
- `allowedActions[]`: `updateAvatar` | `removeAvatar` | `changePassword` | `updateDisplayName` | `changeEmail`. FE chỉ vẽ nút có trong mảng.
- Không có phiên: 401.

#### Endpoint

| # | Method · URL | Query / body (kiểu) | Response (kiểu) | Lỗi (HTTP · MSG · field) | FE định nghĩa | Ghi chú BE |
|---|---|---|---|---|---|---|
| 1 | Supabase `auth.signInWithPassword` (không REST của app) | `email: string`, `password: string` | session Supabase | 4xx → MSG-01; còn lại → MSG-04 | `features/auth/api/authApi.ts#signIn` | BR-01, BR-02: chỉ tài khoản bệnh nhân đủ điều kiện đăng nhập qua `/login`. Khóa theo chính sách (BR-02). FE không phân biệt lý do từ chối. |
| 2 | `POST /api/v1/patient-accounts` (đề xuất; FE: `registerPatient`) | body `{ fullName: string, email: string, password: string }` | 201 hoặc 204; gửi OTP tới email | 400 MSG-08 (trùng khóa, không lộ chi tiết, BR-03/BR-10) · 500 MSG-04 | `features/auth/api/authApi.ts#registerPatient` | Chỉ tạo tài khoản, không tạo hồ sơ bệnh nhân (UC06). Không nhận ngày sinh, giới tính, SĐT. Supabase: `auth.signUp(email, password, { data: { displayName: fullName } })`. |
| 3 | `POST /api/v1/patient-accounts/email-verification` (đề xuất; FE: `verifyRegistrationEmail`) | body `{ email: string, code: string }` (code 6 chữ số) | 204 | 400 MSG-08 (sai hoặc hết hạn mã; field `code`) | `authApi.ts#verifyRegistrationEmail` | Mã sai dùng MSG-08 (MSG-82 chưa có trong appendix, xem báo cáo). |
| 4 | `POST /api/v1/patient-accounts/email-verification/resend` (đề xuất; FE: `resendRegistrationCode`) | body `{ email: string }` | 204 | 500 MSG-04 | `authApi.ts#resendRegistrationCode` | Giới hạn tần suất gửi lại. |
| 5 | `GET /api/v1/me` | — | `MyAccount` = `StaffAccount` \| `PatientAccount`. Chung: `id`, `kind`, `displayName: string`, `avatarUrl: string \| null`, `status`, `allowedActions[]`. STAFF: `username: string`, `role`, `mustChangePassword: boolean`. PATIENT: `email: string` (đã xác minh). | 401 · 500 MSG-04 | `features/my-account/api/myAccountApi.ts#getMyAccount` | Lấy tài khoản từ phiên của app, không từ claim `role` của Supabase. `allowedActions` tính theo quyền thật (BR-01). |
| 6 | `PATCH /api/v1/me` | body `{ displayName: string }` (sau trim, 2–100 ký tự) | `MyAccount` | 400 MSG-12 (field `displayName`) · 403 MSG-14 (không phải bệnh nhân) | `features/my-account/api/myAccountProfileApi.ts#updateMyDisplayName` | BR-05: chỉ sửa trường được phép. |
| 7 | `POST /api/v1/me/email-change` | body `{ newEmail: string }` | 204; gửi OTP tới `newEmail` | 400 MSG-12 (field `newEmail`, sai định dạng) · 400 MSG-08 (field `newEmail`: thuộc tài khoản khác hoặc trùng email hiện tại) · 403 MSG-14 | `myAccountProfileApi.ts#requestMyEmailChange` | BR-03: không lộ email đã tồn tại (cùng MSG-08). BR-06: email cũ còn hiệu lực tới khi xác minh. Gửi lại mã = gọi lại endpoint này. |
| 8 | `POST /api/v1/me/email-change/verify` | body `{ otp: string }` (6 chữ số) | `MyAccount` (đã có email mới) | 400 MSG-08 (field `otp`: sai/hết hạn; không field: không có yêu cầu đang chờ) · 403 MSG-14 | `myAccountProfileApi.ts#verifyMyEmailChange` | Đúng mã mới đổi email. Cần giới hạn số lần nhập sai (chính sách). |
| 9 | `PUT /api/v1/me/avatar` (multipart, part `file`) | `file`: JPEG/PNG/WEBP, ≤ 2 MB | `MyAccount` | 400 MSG-08 (field `file`) · 500 MSG-04 | `myAccountApi.ts#updateMyAvatar` | FE kiểm loại và cỡ trước khi gửi. |
| 10 | `DELETE /api/v1/me/avatar` | — | 204 | 500 MSG-04 | `myAccountApi.ts#removeMyAvatar` | — |
| 11 | `POST /api/v1/me/password` | body `{ currentPassword: string, newPassword: string }` | 204 | 400 MSG-08 (field `currentPassword` hoặc `newPassword`) · 500 MSG-04 | `myAccountApi.ts#changeMyPassword` | BR-07: xác minh mật khẩu hiện tại. BR-04: chính sách mật khẩu. STAFF: sau khi đổi `mustChangePassword=false`. |
| 12 | Supabase `auth.getSession()` (client) | — | session hoặc null | lỗi cấu hình → MSG-04 | `features/my-account/components/RoleHomeRedirect.tsx` (`/workspace`) | Chỉ dùng để biết có phiên; quyền do `/me` và backend. |
| 13 | Supabase `auth.signOut()` (client) | — | — | bỏ qua lỗi, về `/login` | menu header bệnh nhân (P00) | — |

#### Hợp đồng URL giữa màn (query)
- `/login?next=<đường dẫn>`: FE chỉ nhận `next` là đường dẫn bắt đầu bằng `/portal` (không `//`, không `\`, không ký tự điều khiển, không URL tuyệt đối); còn lại về `/portal`. CTA "Đặt lịch khám": `/login?next=/portal/appointments/new`.
- `/workspace`: không tham số. Chuyển theo tài khoản (`homeRouteForAccount`): PATIENT → `/portal`; ADMIN và CLINIC_OWNER → `/admin`; nhân viên khác → `/staff`.
- `/portal/account`, `/portal/account/password`: không tham số.

#### Ghi chú nghiệp vụ BE phải kiểm
- BR-01, BR-02: đăng nhập bệnh nhân ở `/login`; tài khoản không đủ điều kiện bị từ chối (FE hiện MSG-01 chung).
- UC06: đăng ký không tạo hồ sơ bệnh nhân; không đọc ngày sinh, giới tính, SĐT từ `user_metadata` cũ (R3 rủi ro, phase 14).
- BR-06: email đổi chỉ có hiệu lực khi xác minh xong. BR-03: không lộ email đã tồn tại.
- Vai trò trong `GET /me` lấy từ bản ghi tài khoản của ứng dụng, không từ claim `role` của Supabase (AGENTS.md).

### P02 · API notes P02: hồ sơ bệnh nhân trong cổng (UC12–UC15)

Nguồn hợp đồng: `plans/261024-0512-patient-portal-fe/phase-02-patient-profiles.md` §6–§7, đặc tả R3 `r3-change-spec-261023-1637`. Mọi endpoint dưới đây hiện là lớp API giả trong `otcms/frontend/src/features/patient-profiles/api/`. Chữ ký hàm giữ nguyên khi nối BE, chỉ đổi thân sang `apiRequest`. Tiền ISO, ngày `YYYY-MM-DD`, thời điểm ISO `+07:00`, enum UPPER_SNAKE.

#### Kiểu dữ liệu chung

- `CareRelationship`: `SELF | PARENT | CHILD | SPOUSE | SIBLING`
- `PatientSex`: `MALE | FEMALE`
- `ProfileAction`: `update | unlink | editHistory` (FE chỉ vẽ nút có trong `allowedActions`)
- `EditableProfileField`: `fullName | dateOfBirth | sex | phone | address | citizenId | email`
- `LinkedProfile`: `{ id, patientCode, fullName, dateOfBirth, sex, relationship }` (đã có ở P00)
- `SuggestedProfile`: `{ id, patientCode, fullName, birthYear: number, sex }` (không trả ngày sinh đủ, SĐT, email)
- `PatientProfileDetail`: `{ id, patientCode, relationship, fullName, dateOfBirth, sex, phone, address: string|null, citizenId: string|null, email: string|null, hasVisits: boolean, editableFields: EditableProfileField[], allowedActions: ProfileAction[] }`
- `MedicalHistory`: `{ conditions, allergies, procedures, injuries, orthopedicHistory (string, rỗng nếu chưa có), updatedAt: string|null (ISO +07:00), updatedBy: string|null, version: number }`

#### Endpoint

| Method | URL | Query / body (tên trường: kiểu) | Response (2xx) | Lỗi (HTTP + MSG / detail) | FE định nghĩa | Ghi chú nghiệp vụ BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/me/patient-profiles` | — | `200 {items: LinkedProfile[]}`, Bản thân đứng đầu, rồi theo tên | 5xx MSG-04 | `lib/patient-scope/linkedProfilesApi.ts` → `listLinkedProfiles(signal?)` (P00, đọc) | Chỉ hồ sơ gắn với tài khoản đăng nhập (BR-16). |
| GET | `/api/v1/me/patient-profiles/suggestions` | — (FE không gửi email) | `200 {items: SuggestedProfile[]}` | 5xx MSG-04 | `features/patient-profiles/api/patientProfileLinkApi.ts` → `listSuggestedProfiles(signal?)` | Lọc: email hồ sơ trùng email đã xác minh của tài khoản (không phân biệt hoa thường) và chưa gắn với tài khoản này (BR-17). Chỉ trả 5 trường trên. |
| POST | `/api/v1/me/patient-profiles/links` | `{patientId: uuid, relationship: CareRelationship}` | `201 LinkedProfile` | 400 MSG-17 (`fieldErrors` theo `relationship`); 404 MSG-41 (không phải gợi ý hoặc không tồn tại); 409 MSG-21 (đã gắn; tài khoản đã có Bản thân khi gắn SELF; hồ sơ là Bản thân của tài khoản khác khi gắn SELF); 5xx MSG-04 | `patientProfileLinkApi.ts` → `linkSuggestedProfile(body: LinkProfileRequest)` | 404 thay vì 403 cho hồ sơ không còn là gợi ý, để không lộ hồ sơ người khác. Gắn SELF: BR-67 (tối đa một Bản thân mỗi tài khoản và mỗi hồ sơ). |
| POST | `/api/v1/me/patient-profiles` | `{relationship, fullName: string, dateOfBirth: YYYY-MM-DD, sex, phone: string (10 số, bắt đầu 0), address: string (bắt buộc, xem câu hỏi), citizenId?: string (8–12 chữ/số), email?: string}` | `201 PatientProfileDetail` (đã gắn với tài khoản) | 400 MSG-17 (`fieldErrors: [{field, code: "MSG-17"}]`); 409 `detail: "DUPLICATE_PROFILE"` (không có MSG); 409 MSG-21 (Bản thân thứ hai); 5xx MSG-04 | `patientProfileApi.ts` → `createPatientProfile(body: CreateProfileRequest)` | Kiểm trùng theo SĐT (chuẩn hóa chỉ còn số) + ngày sinh (BR-14/BR-18). Trùng → 409 và không tạo bản ghi nào. Tạo hồ sơ và gắn tài khoản trong cùng một giao dịch. |
| GET | `/api/v1/me/patient-profiles/{id}` | path `id`: uuid | `200 PatientProfileDetail` | 404 MSG-41 (không gắn hoặc không tồn tại); 403 MSG-40 (FE xử lý như 404); 5xx MSG-04 | `patientProfileApi.ts` → `getPatientProfile(id, signal?)` | `editableFields`: hồ sơ có lượt khám (`hasVisits`) khóa `fullName`, `dateOfBirth`. `allowedActions` hiện luôn đủ ba mục ở mock; BE trả theo quyền thật. |
| PATCH | `/api/v1/me/patient-profiles/{id}` | `UpdateProfileRequest`: các trường trong `editableFields`, mỗi trường tùy chọn. `dateOfBirth` YYYY-MM-DD. Ô tùy chọn rỗng gửi `null` để xóa (`citizenId`, `email`) | `200 PatientProfileDetail` | 400 MSG-17 (`fieldErrors`, gồm cả trường không được sửa); 404 MSG-41; 5xx MSG-04 | `patientProfileApi.ts` → `updatePatientProfile(id, body: UpdateProfileRequest)` | BE phải tự chặn trường ngoài `editableFields` (không tin FE). Sửa `fullName`/`dateOfBirth`/`sex` thì cập nhật cả kho gắn của tài khoản. |
| DELETE | `/api/v1/me/patient-profiles/{id}/link` | — | `204` | 404 MSG-41; 403 MSG-40 (FE xử lý như 404); 5xx MSG-04 | `patientProfileLinkApi.ts` → `unlinkPatientProfile(id)` | BR-16: chỉ bỏ quyền truy cập; hồ sơ và lịch sử khám giữ nguyên. Bỏ gắn Bản thân thì hồ sơ không còn là Bản thân của tài khoản (BR-67). FE hiện cảnh báo khi bỏ gắn Bản thân. |
| GET | `/api/v1/patients/{id}/medical-history` | path `id`: uuid (không có tiền tố `/me`) | `200 MedicalHistory` | 403 MSG-40 (không gắn với tài khoản); 404 MSG-41; 5xx MSG-04 | `features/patient-profiles/api/medicalHistoryApi.ts` → `getMedicalHistory(id, signal?)` | Người dùng và bác sĩ cùng ghi (BR-20). `updatedAt`/`updatedBy` do BE đặt, không nhận từ FE. |
| PUT | `/api/v1/patients/{id}/medical-history` | `MedicalHistoryUpdate`: `{conditions, allergies, procedures, injuries, orthopedicHistory: string (≤ 2000 ký tự mỗi mục), version: number}`. FE gửi cả năm mục, mục không sửa gửi giá trị đang lưu | `200 MedicalHistory` (version + 1, `updatedBy` = người vừa sửa, `updatedAt` = giờ hiện tại +07:00) | 400 MSG-17 (`fieldErrors` theo mục hoặc `version`); 409 `detail: "STALE"` (version lệch, không có MSG); 403 MSG-40; 404 MSG-41; 5xx MSG-04 | `medicalHistoryApi.ts` → `updateMedicalHistory(id, body: MedicalHistoryUpdate)` | Khóa lạc quan theo `version`: lệch thì 409 STALE, không ghi đè. Giới hạn 2000 ký tự là giá trị FE đặt, chưa có trong đặc tả (xem câu hỏi). |

#### Hợp đồng URL giữa màn (không có query string)

| URL FE | Màn | Ghi chú |
| --- | --- | --- |
| `/portal/profiles` | Danh sách hồ sơ đã gắn | Băng gợi ý hiện khi có gợi ý; link "Xem và gắn" → `/portal/profiles/new#suggested` |
| `/portal/profiles/new` | Thêm hồ sơ | Hash `#suggested` chỉ là neo cuộn trang, không phải tham số |
| `/portal/profiles/{profileId}` | Chi tiết hồ sơ | `profileId` = uuid, mã hóa bằng `encodeURIComponent`; không đưa họ tên, SĐT lên URL |
| `/portal/profiles/{profileId}/history` | Tiền sử bệnh | Cùng `profileId` |

#### Lỗi và cách nhận diện ở FE

- Lỗi nghiệp vụ có mã MSG: FE dựa vào `code` và HTTP status (`ApiError`).
- `DUPLICATE_PROFILE` và `STALE` không có mã MSG trong appendix hoặc bảng MSG R3. BE trả HTTP 409 với `detail` đúng nguyên văn chuỗi đó (không phải câu hoàn chỉnh). FE nhận diện theo `error.message` (xem `api/patientProfileErrors.ts`). Nếu nhóm chọn mã MSG mới, FE đổi một hàm trong file này.
- 403 và 404 của chi tiết, tiền sử, bỏ gắn: FE chuyển sang trang không tìm thấy của khu (`notFound()`). Thông điệp không lộ dữ liệu.

#### Ghi chú cho BE (kiểm tra bắt buộc)

- Gợi ý: không nhận email từ FE; email đối chiếu là email đã xác minh của tài khoản (BR-17).
- Gắn SELF: kiểm cả hai điều kiện, tài khoản đã có Bản thân và hồ sơ đã là Bản thân của tài khoản khác (BR-67).
- Tạo hồ sơ: chuẩn hóa SĐT về chỉ còn số trước khi so trùng; trùng SĐT + ngày sinh là 409, kể cả khi trùng với một hồ sơ đang được gợi ý.
- PATCH: chặn trường ngoài `editableFields`; hồ sơ có lượt khám khóa họ tên và ngày sinh.
- Bỏ gắn: giữ hồ sơ và lịch sử khám (BR-16); không xóa bản ghi.
- Tiền sử: khóa lạc quan theo `version`; mỗi lần PUT thành công tăng version đúng một.

### P03 · P03 — Ghi chú API cho BE: lõi đặt lịch (UC20, UC21)

Nguồn code: `otcms/frontend/src/features/appointments/api/bookingApi.ts` (lớp API giả, chữ ký khớp REST).
Quy ước chung: JSON; ngày `YYYY-MM-DD`; thời điểm ISO có `+07:00`; tiền số nguyên VND; enum `UPPER_SNAKE`; key `camelCase`.
Lỗi: ProblemDetail + `code` (mã MSG) + `fieldErrors: [{ field, code }]` khi lỗi ô nhập. FE đọc `code`, không đọc `detail`.
Khi nối thật: `GET` ghép query bằng `toQueryString` (`lib/api/pagination.ts`); `patientId` đi trong query/body, không đặt tên hay SĐT lên URL.

#### Endpoint FE gọi

| # | Method · URL | Query / body | Response (200/201) | Lỗi (HTTP · MSG) | FE định nghĩa | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `GET /api/v1/appointments/bookable-services` | — | `{ items: BookableService[] }` | 5xx · MSG-04 | `listBookableServices(signal?)` · hook `useBookableServices()` | BR-65: chỉ dịch vụ loại Khám đang hoạt động; giá và `bookingDetails` hiện hành; sắp theo tên. FE không lọc lại. |
| 2 | `GET /api/v1/appointments/open-times` | `serviceId` string (bắt buộc) · `patientId` string (bắt buộc) · `from` date (tùy, mặc định hôm nay giờ VN) · `to` date (tùy, mặc định `from` + 13, tối đa 14 ngày) | `OpenTimesResponse` (xem dưới) | 400 · MSG-31 (`fieldErrors`: from, to, serviceId, patientId) · 403 · MSG-29 (người khóa) · 404 · MSG-29 (dịch vụ không đặt được, không có hồ sơ) · 5xx · MSG-04 | `getOpenTimes({ serviceId, patientId, from?, to? }, signal?)` · hook `useOpenTimes(serviceId, patientId, from?, onError?)` | BR-21, BR-26 (người khám: chính mình hoặc người phụ thuộc có quan hệ chăm sóc đang hiệu lực). BR-25 (giờ trong ca bác sĩ, theo bước `slotMinutes`, trừ lịch Đã đặt/Đã tiếp nhận; lịch hủy nhả giờ). Bỏ giờ đã qua so với `serverNow`. Chỉ liệt kê bác sĩ đang nhận đặt mới; giờ không còn bác sĩ nào thì không trả. |
| 3 | `POST /api/v1/appointments` | body `CreateAppointmentRequest` (xem dưới) | 201 `BookedAppointment` (xem dưới) | 400 · MSG-31 (`fieldErrors`: patientId, serviceId, startAt, doctorId; giờ lệch bước; ngoài 14 ngày; cùng người đã có lịch Đã đặt đúng giờ đó) · 403 · MSG-29 (người bị khóa) · 404 · MSG-29 (không có hồ sơ/dịch vụ không đặt được) · 409 · MSG-30 (giờ đã có người đặt, đã qua, hoặc không còn bác sĩ khi chọn phòng khám sắp xếp) · 409 · MSG-24 (bác sĩ đã chọn không nhận giờ này) · 5xx · MSG-04 | `createAppointment(body, signal?)` | BR-26: bệnh nhân đặt cho mình/người phụ thuộc đã duyệt; lễ tân đặt cho bệnh nhân mình phục vụ. BR-21. Kiểm và ghi trong một giao dịch có khóa: hai người cùng giờ thì người sau nhận 409 MSG-30 (FE không giữ chỗ). BR-59/BR-66: phí chốt lúc đặt, FE không tự tính. `doctorId = null` → BE gán một bác sĩ có ca giờ đó (UC21 N1.3) và trả `doctor` khác null. |

##### Chi tiết kiểu

**BookableService**: `{ id: string, name: string, price: number /* VND */, bookingDetails: string | null }`

**OpenTimesResponse**: `{ serviceId: string, from: date, to: date, slotMinutes: number /* 15 */, serverNow: string /* ISO +07:00 */, days: OpenDay[] }`
- **OpenDay**: `{ date: date, times: OpenTime[] }`. Ngày không còn giờ trả `times: []`, không bỏ ngày (FE vẽ đủ 14 ô).
- **OpenTime**: `{ startAt: string /* ISO +07:00 */, doctors: DoctorRef[] }`; **DoctorRef**: `{ id: string, fullName: string }`.

**CreateAppointmentRequest**: `{ patientId: string, serviceId: string, startAt: string /* ISO +07:00, đúng bước giờ */, doctorId: string | null }`

**BookedAppointment** (201): `{ id: string, startAt: string, patient: { id: string, fullName: string }, service: { id: string, name: string }, doctor: DoctorRef, roomName: string, status: AppointmentStatus /* FE dùng BOOKED */, examFee: { amount: number /* VND, giá chốt */, status: ExamFeeStatus /* PENDING_PAYMENT khi mới đặt */ }, isFollowUp: boolean }`
- Không trả SĐT, mã BN, người đặt ở response này.

**Enum dùng**: `AppointmentStatus` = BOOKED | CHECKED_IN | CANCELLED | NO_SHOW; `ExamFeeStatus` = PAID | PENDING_PAYMENT | NOT_REQUIRED (`lib/domain/clinicVisit.ts`).

#### Hợp đồng URL giữa màn (query param)

- BookingFlow **không tự đưa gì lên URL**: lựa chọn người khám, dịch vụ, ngày, giờ, bác sĩ đều ở state trong trang.
- Khu bệnh nhân (P04, `/portal/appointments/new`): không cần `?profileId=`. Trang gọi `useLinkedProfiles()` rồi truyền `profiles` vào `<BookingFlow variant="patient" />`; khối "Ai đi khám?" nằm trong luồng. Sau khi đặt, `onBooked(appointment)` → trang hiện `BookingResult`; link chi tiết dùng `appointment.id`.
- Khu Lễ tân (S02, `/staff/appointments/new?patientId=`): trang đọc `?patientId=`, lấy `BookingPatient` (`getBookingPatient`, API của S02), truyền `<BookingFlow variant="staff" patient={...} />`. Đổi người: đặt `key={patient.id}` để dựng lại luồng.

#### Quy ước mã người khám (cần BE thống nhất)

- `patientId` trong open-times và POST là **mã hồ sơ gắn tài khoản** (`LinkedProfile.id`, UUID) khi người đó đã gắn; khu Lễ tân dùng mã bệnh nhân BE trả. Hai mã này phải là một mã cho cùng một người.
- Kho giả đang quy về mã hồ sơ gắn khi cùng `patientCode` (BN-000123: `pat-an` và uuid của An là một người). Xem câu hỏi 1.

#### Câu hỏi mở cho BE / nhóm

1. Mã và ngày sinh của An: `linkedProfilesMockStore` dùng uuid, ngày sinh 1992-07-21; `features/patients` dùng `pat-an`, ngày sinh 1958-03-12. Cần một mã và một ngày sinh cho cùng người.
2. Đổi giờ: BR-27 trong `appendix-rules.md` cho phép đổi giờ ("a time change retains..."), còn phase-03 và BA ghi lễ tân và bệnh nhân không đổi giờ (BR-22 cũ). Phase này không dựng đổi giờ.
3. Cùng bệnh nhân, khác giờ, cùng ngày: BE có cho đặt nhiều lịch không? Kho giả cho phép, chỉ chặn cùng giờ (MSG-31).
4. Đặt trước tối đa bao xa và chặn trước giờ khám bao lâu: kho giả giới hạn 14 ngày từ hôm nay; giờ đã qua theo `serverNow` bị loại. BE quyết.
5. Định dạng giờ: FE gửi `startAt` ISO có `+07:00`, đúng bước giờ của ca. BE nên từ chối giờ lệch bước bằng MSG-31.

### P04 · P04 — Ghi chú API cho BE: lịch hẹn khu bệnh nhân (UC20 AF1, UC21 màn đặt, UC23, UC24)

Nguồn code: `otcms/frontend/src/features/appointments/api/patientAppointmentApi.ts` (lớp API giả trong bộ nhớ, chữ ký = REST bên dưới). Nối BE: thay thân từng hàm bằng `apiRequest`; không đổi tên hàm, không đổi type ở `types/patientAppointment.ts`.
Quy ước chung: base `/api/v1`; ngày `YYYY-MM-DD`; thời điểm ISO có `+07:00`; tiền số nguyên VND; enum `UPPER_SNAKE`; key `camelCase`. Lỗi = ProblemDetail + `code` (MSG); FE đọc `code`, không đọc `detail`.
Phạm vi: mọi endpoint chỉ trả lịch của hồ sơ **đã gắn với tài khoản đang đăng nhập** (BR-19). FE không gửi id tài khoản.

#### Endpoint FE gọi (phần P04)

| # | Method · URL | Query / body | Response (200) | Lỗi (HTTP · MSG) | FE định nghĩa | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `GET /api/v1/appointments` | `when`: `upcoming`\|`past` (bắt buộc) · `patientId`: string (tùy; uuid hồ sơ đã gắn) · `page`: int 0-based · `size`: int (FE gửi 10) | `PageResponse<PatientAppointment>` = `{ items, page, size, totalItems, totalPages }` | 400 · MSG-31 (`fieldErrors`: `when`) · 403 · MSG-29 (`patientId` không thuộc tài khoản) · 5xx · MSG-04 | `listMyAppointments(query, signal?)` · hook `usePatientAppointmentList(when, patientId?)` | `upcoming` = trạng thái BOOKED, sắp tăng dần theo `startAt`. `past` = CHECKED_IN, CANCELLED, NO_SHOW, giảm dần. Lọc phạm vi theo hồ sơ đã gắn (BR-19). FE chỉ gửi `patientId` lấy từ danh sách hồ sơ đã gắn. |
| 2 | `GET /api/v1/appointments/{id}` | path `id`: string | `PatientAppointment` (xem dưới) | 403 hoặc 404 · MSG-29 (không thuộc phạm vi hoặc không có; FE xử lý như nhau → trang không tìm thấy) · 5xx · MSG-04 | `getMyAppointment(id, signal?)` · hook `usePatientAppointment(id)` | Hình trả cho người bệnh: không có SĐT, mã BN, người đặt. Dùng để đọc `allowedActions` sau khi đặt (xem mục ghi chú 2). |
| 3 | `POST /api/v1/appointments/{id}/cancel` | body rỗng | `PatientAppointment` (status `CANCELLED`, `allowedActions: []`) | 403/404 · MSG-29 · 409 · MSG-32 (đã check-in, đã qua, hoặc không còn Đã đặt) · 5xx · MSG-04 | `cancelMyAppointment(id)` (gọi trong `components/portal/PatientCancelDialog.tsx`) | BR-22: chỉ người bệnh hủy lịch Đã đặt trước check-in; giờ được nhả. BR-53: phí đã trả trực tuyến **không hoàn lại** (BE không hoàn tiền khi hủy). FE không đổi giờ (không có endpoint đổi giờ). |

Đầu vào đặt lịch (`POST /api/v1/appointments`, `GET .../open-times`, `GET .../bookable-services`) là của P03 (`api-notes/P03.md`); màn P04 chỉ gọi qua `BookingFlow` dùng chung.
Hồ sơ đã gắn (`GET /api/v1/me/patient-profiles`, P00) được màn dùng để lọc người khám và để hiện mã BN trong dòng "Khi đến phòng khám, báo mã bệnh nhân…".

##### Kiểu dữ liệu

**PatientAppointment** (trả bởi 1, 2, 3):
```json
{
  "id": "apt-1001",
  "startAt": "2026-10-26T08:30:00+07:00",
  "patient": { "id": "uuid", "fullName": "Nguyễn Văn An", "relationship": "SELF" },
  "visitId": null,
  "service": { "id": "svc-kham-tq", "name": "Khám chỉnh hình tổng quát" },
  "doctor": { "id": "doc-ha", "fullName": "BS. Phạm Thu Hà" },
  "roomName": "Phòng khám 2",
  "isFollowUp": false,
  "status": "BOOKED",
  "examFee": { "amount": 200000, "status": "PENDING_PAYMENT" },
  "doctorUnavailable": false,
  "allowedActions": ["cancel", "payExamFee"]
}
```
- `patient.relationship`: `SELF` | `PARENT` | `CHILD` | `SPOUSE` | `SIBLING`.
- `status`: `BOOKED` | `CHECKED_IN` | `CANCELLED` | `NO_SHOW`.
- `examFee.status`: `PAID` | `PENDING_PAYMENT` | `NOT_REQUIRED`. `amount` là giá chốt lúc đặt (BR-59).
- `visitId`: có khi đã check-in (`CHECKED_IN`) để FE mở `/portal/visits/{visitId}`; `null` khi chưa.
- `doctorUnavailable`: `true` khi bác sĩ vắng, không có người thay (UC22 AF1; MSG-89 gửi thông báo). Lịch vẫn `BOOKED`.
- `allowedActions` ⊂ {`cancel`, `payExamFee`}. FE chỉ vẽ nút có trong mảng; BE quyết (không suy từ trạng thái ở FE).
  - Mock: `BOOKED` → `cancel`; thêm `payExamFee` khi `examFee.status = PENDING_PAYMENT`. Các trạng thái khác → `[]`.
  - BE cần thêm: `cancel` khi còn trước check-in và chưa hết hạn hủy (câu hỏi mở của phase, BE quyết); `payExamFee` khi phí chưa trả và lịch còn hiệu lực.

**Query của màn (URL, FE):**

| Route FE | Query | Ghi chú |
| --- | --- | --- |
| `/portal/appointments` | `tab=past` (có = tab Đã qua; bỏ trống = Sắp tới), `profileId` (uuid hồ sơ đã gắn; mã không thuộc tài khoản bị bỏ qua) | Đổi tab = push; đổi người khám = replace. |
| `/portal/appointments/new` | — | Hiện chưa đọc `profileId` (xem câu hỏi 1). |
| `/portal/appointments/{appointmentId}` | — | Dùng `PatientAppointment.id`. |

#### Ghi chú BE phải kiểm

1. **Phạm vi:** list, chi tiết và hủy đều lọc theo hồ sơ đã gắn còn hiệu lực. Bỏ gắn hồ sơ thì lịch của hồ sơ đó mất ngay (404/403 MSG-29).
2. **Hình trả của POST đặt lịch** (`BookedAppointment`, P03) không có `allowedActions`. FE tải `GET /api/v1/appointments/{id}` ngay sau khi đặt để biết có hiện "Thanh toán trực tuyến" không. Có thể thêm `allowedActions` vào 201 để bỏ một lượt gọi (không bắt buộc).
3. **Hủy (BR-22, BR-53):** trạng thái → `CANCELLED`, giờ được nhả cho đặt mới (kiểm tra cùng giờ trong BE). Phí `PAID` giữ nguyên `PAID`, không hoàn tiền. Hủy hai lần: lần sau 409 MSG-32.
4. **Lịch đã check-in ở quầy mà danh sách chưa cập nhật:** hủy → 409 MSG-32 (FE đóng hộp thoại, tải lại chi tiết). Mock có kịch bản `apt-race`.
5. **Đổi giờ:** không có endpoint. FE chỉ hiện câu "Muốn đổi giờ? Hủy lịch này rồi đặt lịch mới."
6. **Lịch quá giờ còn BOOKED:** FE xếp vào "Sắp tới" theo trạng thái. BE tự chuyển `NO_SHOW` khi hết ca (NF-08); FE không tự suy.
7. **Mã BN:** không trả trong `PatientAppointment`. FE lấy từ danh sách hồ sơ đã gắn để nhắc người bệnh.

#### Câu hỏi mở cho BE / nhóm

1. **`?profileId=` trên `/portal/appointments/new`:** phase yêu cầu chọn sẵn hồ sơ khi vào từ trang hồ sơ. `BookingFlow` (P03) chưa có prop chọn trước người khám. Cần P03 thêm `initialPatientId?: string` (một dòng vào trạng thái khởi tạo). Hiện chưa làm.
2. **Đề nghị P06 nối với kho đặt lịch** (xem bảng trên).
3. **Thời hạn hủy:** BE quyết qua `allowedActions` (câu hỏi mở của phase 04).
4. **Mã lỗi 403 hay 404** cho lịch ngoài phạm vi: FE xử lý như nhau (MSG-29 → trang không tìm thấy). BE chọn một.
5. **Chữ MSG-29 và MSG-32 trong bảng dùng chung** (`lib/messages/appMessages.ts`) lệch đặc tả R3: MSG-32 bảng ghi câu về "chuyển lịch", R3 ghi "không đủ điều kiện để hủy hoặc chuyển bác sĩ". Khu bệnh nhân hiện câu R3 cho lỗi hủy (inline). Người giữ bảng MSG nên đối chiếu lại.

### I01 · API notes — I01 (nối Đặt lịch ↔ Thanh toán khu bệnh nhân; chọn sẵn người khám)

Không thêm endpoint mới. Phase này chỉnh hành vi mock của endpoint đã có (P03, P04, P06) để hai
khu đọc cùng một dữ liệu phí khám, như BE một DB. Nguồn mock: `lib/mock/sharedAppointmentFeeMock.ts`
(kho phí khám dùng chung, chỉ cho lớp API giả, xóa khi nối BE).

#### 1. Endpoint bị ảnh hưởng (hợp đồng BE phải giữ)

| # | Method · URL | Hành vi mới ở FE/mock | Ghi chú BE phải kiểm |
| --- | --- | --- | --- |
| 1 | `GET /api/v1/payments/payable-sources` (P06) | Nguồn `kind: "appointment"` gồm lịch **BOOKED** của hồ sơ đã gắn có `examFee.status = PENDING_PAYMENT`. `date` = `startAt` ISO +07:00. Lịch CANCELLED/CHECKED_IN/NO_SHOW không là nguồn chờ. | Phí lịch hẹn và phí trong danh sách lịch (`GET /api/v1/appointments`) phải là một nguồn dữ liệu. |
| 2 | `GET /api/v1/payments/payable-sources/appointment/{id}` (P06) | Một khoản: `label` "Phí khám · {tên dịch vụ}", `amount` = giá chốt lúc đặt (BR-59), `status` PENDING_PAYMENT hoặc PAID; `receipt` chỉ khi PAID. | Lịch ngoài phạm vi / không có / đã hủy: mock trả 404 MSG-41. Xem câu hỏi 1, 3. |
| 3 | `POST /api/v1/payments/online` (P06), body `{sourceKind: "appointment", sourceId, itemIds}` | Tổng = giá chốt của khoản phí. Khoản đang nằm trong giao dịch PENDING → 409 MSG-49. | Khi giao dịch chuyển SUCCESS (chỉ BE quyết): BE đặt `examFee.status = PAID` cho lịch đó, cùng bảng phí mà endpoint 4 và 5 đọc. |
| 4 | `GET /api/v1/payments/{id}` (P06) | Giao dịch của lịch: `sourceKind: "appointment"`, `sourceId` = mã lịch, `items` = [phí khám]. | Không đổi hình trả. `queueAssignments` rỗng (phí khám không có phòng phục vụ). |
| 5 | `GET /api/v1/appointments`, `GET /api/v1/appointments/{id}` (P04) | `examFee.status` đọc từ kho phí chung: sau thanh toán thành `PAID`; `allowedActions` bỏ `payExamFee`, còn `cancel`. | `payExamFee` chỉ khi lịch BOOKED và phí PENDING_PAYMENT (đúng P04). |
| 6 | `POST /api/v1/appointments/{id}/cancel` (P04) | Lịch hủy: không còn là nguồn chờ thanh toán. Phí đã PAID giữ PAID, không hoàn tiền (BR-53). Phí chưa trả không bị xóa khỏi lịch, chỉ mất khả năng thanh toán. | Hủy lịch đã trả: BE không tạo khoản hoàn; giao dịch cũ vẫn có trong lịch sử thanh toán. |
| 7 | `POST /api/v1/appointments` (P03) | Lịch vừa đặt có `examFee` PENDING_PAYMENT và xuất hiện ngay ở nguồn chờ thanh toán (endpoint 1). | Không đổi hình trả 201 (xem đề xuất `allowedActions` trong P04). |

#### 2. URL giữa màn (query param)

| Route FE | Query | Ghi chú |
| --- | --- | --- |
| `/portal/appointments/new` | `profileId?` (uuid hồ sơ đã gắn) | Chọn sẵn người khám ở khối "Ai đi khám?". Mã không thuộc hồ sơ đã gắn của tài khoản → bỏ qua, người dùng tự chọn; không báo lỗi. FE vẫn gửi `patientId` lấy từ hồ sơ đã gắn khi đặt. BE không cần endpoint mới. |

#### 4. Câu hỏi cho BE

1. Lịch không thuộc phạm vi hoặc không có khi gọi endpoint 2 và 3: 403 MSG-40 hay 404 MSG-41? P06 ghi 403 MSG-40 cho nguồn ngoài phạm vi; mock hiện trả 404 MSG-41 cho lịch không gắn tài khoản, đã hủy, hoặc không có phí. BE chọn một mã và báo lại.
2. Lịch đã hủy mà phí chưa trả: BE giữ `PENDING_PAYMENT` hay chuyển `NOT_REQUIRED` ("Không thu")? FE hiển thị đúng giá trị BE trả; mock giữ `PENDING_PAYMENT`.
3. Lịch đã hủy mà phí đã PAID: BE có cho xem chi tiết nguồn (khoản PAID có phiếu thu) không? Mock trả 404 MSG-41 (lịch đã hủy không còn là nguồn); lịch sử thanh toán vẫn hiện giao dịch.

### P05 · API notes — P05 (tiến độ lượt khám UC34 + trang chủ portal)

Trạng thái: FE dùng lớp API giả `otcms/frontend/src/features/clinic-visits/api/visitApi.ts`, chữ ký khớp REST bên dưới. Nối BE: thay thân hai hàm bằng `apiRequest`; không đổi tên hàm hay type trong `features/clinic-visits/types/visitProgress.ts`.
Quy ước chung: base `/api/v1`; ngày `YYYY-MM-DD`; thời điểm ISO `+07:00`; enum `UPPER_SNAKE`; key `camelCase`; lỗi = ProblemDetail + `code` MSG (FE đọc `code`, không đọc `detail`).
Phạm vi: mọi endpoint chỉ trả lượt của hồ sơ **đã gắn với tài khoản đang đăng nhập** (BR-19). FE không gửi id tài khoản.

#### Endpoint FE gọi

| # | Method · URL | Query / body | Response (200) | Lỗi (HTTP · MSG) | FE định nghĩa | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `GET /api/v1/visits` | `when`: `active` \| `recent` (bắt buộc) · `patientId`: uuid hồ sơ đã gắn (tùy; vắng = mọi hồ sơ) · `page`: int 0-based · `size`: int (FE gửi 10) | `PageResponse<VisitSummary>` = `{ items, page, size, totalItems, totalPages }` | 400 · MSG-31 (`fieldErrors`: `when`) · 403 · MSG-40 (`patientId` không thuộc tài khoản) · 5xx · MSG-04 | `listVisits(query, signal?)` · hook `useVisitList(when, patientId?)` | `active` = WAITING_FOR_EXAMINATION, IN_EXAMINATION, AWAITING_CONCLUSION. `recent` = mọi trạng thái, `visitDate` trong 30 ngày gần nhất tính theo ngày VN. Sắp: `visitDate` giảm dần, rồi `id`. |
| 2 | `GET /api/v1/visits/{id}/progress` | path `id`: string | `VisitProgress` (xem §2) | 403 · MSG-40 (lượt không thuộc phạm vi) · 404 · MSG-41 (không có) · 5xx · MSG-04 | `getVisitProgress(visitId, signal?)` · hook `useVisitProgress(visitId)` | FE tự gọi lại mỗi 30 s khi tab đang hiện; tab ẩn thì dừng. Lỗi lần gọi tự động không hiện toast. |

#### 2. DTO (tên trường đúng như FE)

- `VisitSummary`: `{ id: string, visitDate: string (YYYY-MM-DD), status: VisitStatus, type: FIRST_VISIT | FOLLOW_UP, patient: { id: uuid, fullName: string, relationship: SELF | PARENT | CHILD | SPOUSE | SIBLING } }`
- `VisitStatus`: `WAITING_FOR_EXAMINATION | IN_EXAMINATION | AWAITING_CONCLUSION | COMPLETED | INCOMPLETE | CANCELLED_LEFT`
- `VisitProgress` (= VisitSummary cộng):
  - `updatedAt: string (ISO +07:00)`: lúc BE tính tiến độ; FE hiện "Cập nhật lúc HH:mm".
  - `returnDeadline: string (YYYY-MM-DD) | null`: chỉ có khi `AWAITING_CONCLUSION`. FE hiện nguyên văn "đến hết ngày dd/mm/yyyy", không tự cộng ngày.
  - `queues: VisitQueuePosition[]`
  - `orders: VisitOrderProgress[]`
  - `allowedActions: ("payOrders" | "viewResults" | "review")[]`
- `VisitQueuePosition`: `{ roomName: string, queueType: EXAMINATION | SERVICE | RESULT_REVIEW, queueNumber: int, peopleAhead: int, status: WAITING | BEING_SERVED | ABSENT }`
- `VisitOrderProgress`: `{ id: string, serviceName: string, kind: IMAGING | LABORATORY, roomName: string | null, status: PENDING_PAYMENT | PAID | RESULTS_AVAILABLE | CANCELLED, cancellationReason: string | null }`
- `PageResponse<T>`: `{ items, page, size, totalItems, totalPages }` (0-based, size tối đa 100).

Ví dụ `GET /api/v1/visits/vis-lan-await/progress` (rút gọn):
```json
{
  "id": "vis-lan-await", "visitDate": "2026-10-22", "status": "AWAITING_CONCLUSION",
  "type": "FOLLOW_UP", "patient": { "id": "5c1e7a20-…-4f02", "fullName": "Nguyễn Thị Lan", "relationship": "PARENT" },
  "updatedAt": "2026-10-24T09:42:00+07:00", "returnDeadline": "2026-10-29",
  "queues": [
    { "roomName": "Phòng X-quang 1", "queueType": "SERVICE", "queueNumber": 5, "peopleAhead": 1, "status": "WAITING" },
    { "roomName": "Phòng xét nghiệm", "queueType": "SERVICE", "queueNumber": 8, "peopleAhead": 0, "status": "BEING_SERVED" }
  ],
  "orders": [{ "id": "svc-endoscopy", "serviceName": "Nội soi dạ dày", "kind": "IMAGING", "roomName": null, "status": "PENDING_PAYMENT", "cancellationReason": null }],
  "allowedActions": ["payOrders"]
}
```

#### 3. Hợp đồng URL giữa màn (FE)

| URL FE | Query | Ghi chú |
| --- | --- | --- |
| `/portal/visits` | `tab=recent` (có = tab Gần đây; vắng = Đang diễn ra), `profileId` (uuid hồ sơ đã gắn; mã lạ coi như Tất cả) | `profileId` → `patientId`. Đổi tab = push; đổi người khám = replace. |
| `/portal/visits/{visitId}` | — | `visitId` mã hóa URI. 403/404 → trang không tìm thấy của khu. |
| Đi ra từ chi tiết | — | `/portal/payments/visit/{visitId}` (phase 06, khi có `payOrders`); `/portal/results/{visitId}` (phase 07, khi có `viewResults`); `/portal/reviews/new?visitId={visitId}` (phase 08, khi có `review`; trang đích do phase 08 làm). |
| Đi ra từ trang chủ | — | `/portal/visits/{visitId}` từ thẻ "Lượt khám đang diễn ra"; `/portal/appointments/{id}` từ "Lịch hẹn sắp tới". |

#### 4. Ghi chú BE phải kiểm

1. **Phạm vi (BR-19, luồng AF1):** lượt của người thân đã gắn được xem; hồ sơ đã bỏ gắn thì lượt không còn trong danh sách và chi tiết trả 403 MSG-40. FE không tự kiểm.
2. **Không có kết quả (BR-35):** endpoint này không trả kết quả, chẩn đoán, đơn thuốc, ảnh. Kết quả đi qua phase 07.
3. **Hàng đợi:** chỉ trả `WAITING`, `BEING_SERVED`, `ABSENT` của lượt. Không trả `DONE`/`CLOSED`. `peopleAhead` = số `WAITING` đứng trước trong cùng phòng theo thứ tự BE (cùng quy tắc với hàng đợi Lễ tân, xem câu hỏi 3 của S05).
4. **`allowedActions`:** `payOrders` khi có chỉ định `PENDING_PAYMENT` và lượt chưa đóng; `viewResults` khi `COMPLETED` hoặc `INCOMPLETE` (BR-35); `review` khi `COMPLETED` (phase 08 quyết chi tiết). FE không suy từ trạng thái.
5. **`returnDeadline` (BR-65):** BE tính: ngày check-in đầu là ngày thứ nhất, hết hạn cuối ngày thứ bảy (kể cả ngày đó). Chỉ có khi `AWAITING_CONCLUSION`.
6. **`roomName` của chỉ định:** `null` khi chưa gắn phòng. FE không suy phòng từ cấu hình.
7. **Tần suất:** FE gọi lại mỗi 30 s khi tab hiện (chỉ trang chi tiết và trang chủ, mỗi lượt đang diễn ra một luồng). Không cần push. Nếu tải cao, BE cân nhắc cache ngắn (≤ 30 s) theo `visitId`.
8. **`patientId` lạ** trên `GET /visits`: FE không gửi mã lạ (lấy từ hồ sơ đã gắn), nên chỉ là chốt an toàn.

#### 6. Câu hỏi mở cho BE / nhóm

1. `patientId` không thuộc tài khoản: 403 MSG-40 (FE mock hiện tại) hay bỏ qua? P04 dùng 403 MSG-29 cho lịch hẹn. Chọn một mã thống nhất.
2. Tab "Gần đây" 30 ngày: giữ hay bỏ khi đã có Kết quả khám (phase 07)? FE làm theo API hiện tại.
3. `returnDeadline` có phải `YYYY-MM-DD` không (FE giả định vậy, hiện "đến hết ngày dd/mm/yyyy")?
4. Lượt Chờ kết luận có vẫn trả hàng đợi (mock `vis-lan-await` có hai hàng đợi)? Xác nhận theo luồng UC37/UC45.
5. Chữ cho `ABSENT` (vắng mặt) do FE đặt: "Bạn đang vắng mặt. Vui lòng đến quầy lễ tân để được gọi lại." Chưa có trong bảng MSG R3; người giữ bảng MSG xác nhận hoặc thay.

### P06 · API notes — P06 (Thanh toán khu bệnh nhân: UC53, UC55, UC56)

Nguồn code: `otcms/frontend/src/features/billing/api/patientBillingApi.ts` (lớp giả trong bộ nhớ; chữ ký = REST bên dưới). Nối BE: thay thân từng hàm bằng `apiRequest`; không đổi tên hàm, type ở `features/billing/types/patientBilling.ts`.

#### Quy ước chung
- Base `/api/v1`; ngày `yyyy-MM-dd`; thời điểm ISO có offset `+07:00`; tiền = số nguyên VND; enum `UPPER_SNAKE`; key `camelCase`.
- `PageResponse<T>` = `{items, page, size, totalItems, totalPages}`; `page` 0-based; `size` mặc định 20, tối đa 100.
- Lỗi = ProblemDetail + `code` (MSG). FE hiển thị theo `code`; không có `code` → MSG-04.
- FE không gửi số tiền để chốt giao dịch. Tổng do BE tính; FE chỉ so với tạm tính đã hiện (lệch → không chuyển VNPay, báo MSG-49).

#### Endpoint

##### 1. GET `/api/v1/payments/payable-sources`
- Query: `patientId?` (uuid hồ sơ đã gắn; rỗng = mọi hồ sơ của tài khoản).
- 200: `{ items: PayableSourceSummary[] }`, chờ thanh toán trước. `PayableSourceSummary` = `{ kind: "appointment" | "visit", id: string, date: string, patient: {id: string, fullName: string, relationship: "SELF"|"PARENT"|"CHILD"|"SPOUSE"|"SIBLING"}, pendingCount: number, pendingAmount: number }`. `date`: lượt khám `yyyy-MM-dd`; lịch hẹn ISO có giờ.
- Lỗi: 5xx MSG-04.
- FE: `patientBillingApi.ts` › `listPayableSources`; hook `hooks/usePayableSources.ts`.
- BE kiểm: chỉ nguồn có ≥1 khoản `PENDING_PAYMENT`; chỉ hồ sơ thuộc tài khoản (đã gắn).

##### 2. GET `/api/v1/payments/payable-sources/{kind}/{id}`
- Path: `kind` = `appointment` | `visit`; `id` = id lịch hẹn hoặc lượt khám.
- 200: `PayableSourceDetail` = summary + `items: PaymentItem[]`. `PaymentItem` = `{ id: string, label: string, amount: number, status: "PENDING_PAYMENT"|"PAID"|"CANCELLED", cancellationReason: string|null, receipt: {paymentId: string, receiptNo: string}|null }`. Phí khám và chỉ định đều quy về 3 trạng thái này.
- Lỗi: 403 MSG-40; 404 MSG-41; 5xx MSG-04.
- FE: `patientBillingApi.ts` › `getPayableSource`; hook `hooks/usePayableSource.ts`.
- BE kiểm: không trả phòng phục vụ (FE không cần); `receipt` chỉ có khi `PAID`; `cancellationReason` chỉ khi `CANCELLED`.

##### 3. POST `/api/v1/payments/online`
- Body: `{ sourceKind: "appointment"|"visit", sourceId: string, itemIds: string[] }`. FE gửi đúng các khoản người dùng tick (đã lọc `PENDING_PAYMENT`), không gửi số tiền.
- 201: `{ paymentId: string, total: number, redirectUrl: string }`. `redirectUrl` là đường dẫn nội bộ `/portal/...` hoặc https tới máy chủ VNPay (FE chỉ chấp nhận `sandbox.vnpayment.vn`, `pay.vnpay.vn`; danh sách nằm trong `patientBillingRedirect.ts`).
- Lỗi: 400 MSG-12 (`itemIds` rỗng hoặc trùng; `fieldErrors[{field:"itemIds", code:"MSG-12"}]`); 403 MSG-40; 404 MSG-41 (nguồn không có); 409 MSG-49 (khoản không còn `PENDING_PAYMENT`, khoản đang nằm trong giao dịch PENDING khác, hoặc không thuộc nguồn); 5xx MSG-04.
- FE: `patientBillingApi.ts` › `createOnlinePayment`; gọi trong `components/portal/PaymentConfirm.tsx`; 409 → về chi tiết nguồn.
- BE kiểm: chỉ một nguồn; trọn từng khoản (không trả một phần, không đặt cọc, không giảm giá); tổng = Σ giá chốt; khóa khoản khi giao dịch PENDING; chỉ hồ sơ thuộc tài khoản.

##### 4. GET `/api/v1/payments/{id}`
- Path: `id` = `paymentId`.
- 200: `PatientPayment` = `{ id, status: "SUCCESS"|"PENDING"|"FAILED", method: "CASH"|"VNPAY", total: number, paidAt: string|null, vnpayRef: string|null, receiptNo: string|null, patientName: string, items: {label: string, amount: number}[], queueAssignments: {roomName: string, queueNumber: number}[], sourceKind: "appointment"|"visit", sourceId: string }`.
- `queueAssignments` chỉ khi `SUCCESS` và khoản vừa trả có chỉ định phòng (số thứ tự do BE cấp khi thành công).
- Lỗi: 403 MSG-50 và 404 MSG-50 (FE hiện "không tìm thấy", không hiện câu MSG); 5xx MSG-04.
- FE: `patientBillingApi.ts` › `getPayment`; hook `hooks/usePaymentStatusPolling.ts` (PENDING → kiểm lại mỗi 10 s, tối đa 15 phút, sau đó hiển thị như FAILED); dùng cho cả `/result` và phiếu thu.
- BE kiểm: chỉ BE quyết định kết quả; khoản chuyển `PAID` chỉ khi cổng xác nhận; FE không đọc `vnp_*` trên URL.

##### 5. GET `/api/v1/payments`
- Query: `status=SUCCESS` (FE cố định), `from?` (`yyyy-MM-dd`, gồm cả ngày), `to?` (`yyyy-MM-dd`, gồm cả ngày), `patientId?`, `page` (0-based), `size` (mặc định 20).
- 200: `PageResponse<PatientPayment>`, mới nhất trước.
- Lỗi: 400 MSG-54 (`from > to`; `fieldErrors[{field:"to", code:"MSG-54"}]`); 5xx MSG-04.
- FE: `patientBillingApi.ts` › `listMyPayments`; hook `hooks/useMyPaymentHistory.ts` (dùng `usePagedList`). FE chặn `from > to` trước khi gọi.
- BE kiểm: chỉ thanh toán đã hoàn tất trong phạm vi tài khoản.

#### Hợp đồng URL giữa màn (FE)
| Route FE | Query | Ghi chú |
| --- | --- | --- |
| `/portal/payments` | `profileId?` | Đổi người khám: `router.replace` kèm `?profileId=`. `profileId` → `patientId` |
| `/portal/payments/visit/{id}` và `/portal/payments/appointment/{id}` | — | Chi tiết nguồn (chọn khoản). Hằng: `PATIENT_ROUTES.payVisit`, `payAppointment` |
| `/portal/payments/pay` | `source=visit|appointment`, `id`, `items=a,b` (dấu phẩy) | Xác nhận trước VNPay. Hằng: `PATIENT_ROUTES.paymentConfirm` (mới thêm) |
| `/portal/payments/result` | `paymentId` | Kết quả sau khi VNPay chuyển về. Hằng: `PATIENT_ROUTES.paymentResult` |
| `/portal/payments/history` | `from?`, `to?` (`yyyy-MM-dd`), `profileId?` | Lịch sử đã hoàn tất |
| `/portal/payments/history/{paymentId}` | — | Phiếu thu |

#### Ghi chú BE phải kiểm (tóm tắt)
- Phạm vi hồ sơ: nguồn, giao dịch, phiếu thu chỉ của hồ sơ thuộc tài khoản; ngoài phạm vi → 403 MSG-40 (nguồn, POST) hoặc 404/403 MSG-50 (giao dịch, phiếu thu).
- Chống trả trùng: khoản đang trong giao dịch PENDING không tạo giao dịch thứ hai (409 MSG-49).
- Giao dịch PENDING không xác nhận trong khoảng 15 phút: BE trả PENDING cho đến khi có kết quả; FE tự coi như thất bại sau 15 phút và cho "Thử lại".
- Trừ tiền mà không có kết quả: BE giữ mã giao dịch (`vnpayRef`) để lễ tân đối soát (UC55 E3).
- Không trả dữ liệu thẻ; chỉ `vnpayRef`.

#### Câu hỏi cho BE
1. `PatientPayment` không có `patientId` và quan hệ người khám: FE chỉ hiện tên. Đề nghị thêm `patientId` (lọc và đối chiếu) và `patientRelationship` để dòng lịch sử ghi "của ai" đầy đủ.
2. Mã hiển thị cho người dùng khi đối soát: FE hiện `id` của giao dịch và `vnpayRef`. Có muốn BE trả mã ngắn dễ đọc hơn không?
3. Có cần endpoint hủy giao dịch PENDING phía người dùng không? Hiện FE không có (phase không yêu cầu).

### P07 · API notes — P07 (Kết quả khám · Đơn thuốc · Ảnh chụp, khu bệnh nhân)

Nguồn code: `otcms/frontend/src/features/{medical-records,prescriptions,imaging}/api/*Api.ts` (mock, `USE_MOCK = true`). Nối BE: đổi `USE_MOCK = false`, nhánh `apiRequest` đã viết sẵn.

#### Quy ước chung
- Base `/api/v1`; ngày `YYYY-MM-DD`; thời điểm ISO có offset `+07:00`; tiền không dùng ở phase này; enum `UPPER_SNAKE`; key `camelCase`.
- `PageResponse<T>` = `{ items, page, size, totalItems, totalPages }`; `page` 0-based, `size` mặc định 20, tối đa 100.
- Lỗi theo ProblemDetail + `code` (MSG). FE đọc `code` để chọn xử lý: 403 MSG-40 và 404 MSG-41 → trang không tìm thấy; 409 MSG-42 → toast rồi về danh sách; còn lại → MSG-04 kèm "Thử lại".
- `patientId` là id hồ sơ đã gắn (`LinkedProfile.id`, UUID). Thiếu `patientId` = mọi hồ sơ đã gắn với tài khoản.

#### Hợp đồng URL giữa màn (FE)
| Route FE | Query | API gọi |
| --- | --- | --- |
| `/portal/results` | `?profileId=<id>` (→ `patientId`) | GET `/api/v1/visits/results` |
| `/portal/results/{visitId}` | — | GET `/api/v1/visits/{visitId}/results` |
| `/portal/prescriptions` | `?profileId=<id>` (→ `patientId`) | GET `/api/v1/prescriptions` |
| `/portal/prescriptions/{id}` | — | GET `/api/v1/prescriptions/{id}` |
| `/portal/images` | `?profileId=<id>` (→ `patientId`) | GET `/api/v1/imaging-studies` |
| `/portal/images/{studyId}` | — | GET `/api/v1/imaging-studies/{id}` |

Đổi người khám: FE `router.replace` với `?profileId=`, về trang 0 (`page=0`). `?profileId` thiếu hoặc rỗng = tất cả.

#### Endpoint

##### 1. GET `/api/v1/visits/results`
- Query: `patientId?` (uuid), `page?` (int, mặc định 0), `size?` (int, mặc định 20).
- 200: `PageResponse<ResultVisitSummary>`; `ResultVisitSummary` = `{ visitId: string, visitDate: date, status: "COMPLETED" | "INCOMPLETE", serviceName: string, patient: { id: string, fullName: string, relationship: "SELF" | "PARENT" | "CHILD" | "SPOUSE" | "SIBLING" } }`.
- Lỗi: 500 MSG-04. Đề xuất: `patientId` ngoài phạm vi tài khoản → 403 MSG-40 (FE hiện câu MSG-40; chưa có mã riêng, xem câu hỏi).
- FE: `features/medical-records/api/examinationResultApi.ts` › `listVisitResults`.
- BE phải kiểm: chỉ lượt `COMPLETED`/`INCOMPLETE`; chỉ hồ sơ thuộc tài khoản (đã gắn, quan hệ được xác nhận; BR-17, BR-21); mới nhất trước.

##### 2. GET `/api/v1/visits/{visitId}/results`
- Path: `visitId`.
- 200: `ExaminationResult`:
  - `visitId: string`, `visitDate: date`, `status: "COMPLETED" | "INCOMPLETE"`
  - `patient: { id, fullName, relationship }` (như trên)
  - `previousVisitId: string | null`, `nextVisitId: string | null` (lượt liền trước/sau cùng người, cùng hồ sơ)
  - `visibleBlocks: ResultBlock[]`, `ResultBlock` = `EXAMINATION | IMAGING | LABORATORY | PRESCRIPTIONS | ADMINISTRATIVE | RESULT_SHEET`
  - `examination?`: `{ reason, symptoms, findings: string, primaryDiagnoses: Icd10Ref[], secondaryDiagnoses: Icd10Ref[], treatmentDirection, advice: string, followUpDate: date | null }`; `Icd10Ref` = `{ code: string, title: string }`
  - `imaging?`: `[{ studyId: string | null, serviceName: string, finding: string | null }]`
  - `laboratory?`: `[{ serviceName: string, lines: [{ indicator, value, unit, referenceRange: string, flag: "HIGH" | "LOW" | null }] }]`
  - `prescriptions?`: `[{ id: string, issuedAt: datetime, itemCount: int }]`
  - `administrative?`: `{ fullName, dateOfBirth: date, sex: "MALE" | "FEMALE", patientCode, phone: string, address: string | null }`
  - `resultSheets?`: `[{ version: int, status: "CURRENT" | "REPLACED", issuedAt: datetime, fileUrl: string }]`
- Lỗi: 403 MSG-40; 404 MSG-41; 409 MSG-42 (lượt đang khám, chờ kết luận, chưa có kết quả để xem); 500 MSG-04.
- FE: `examinationResultApi.ts` › `getVisitResult`.
- BE phải kiểm:
  - `visibleBlocks` là nguồn duy nhất FE tin. Lượt `COMPLETED`: các khối có dữ liệu đã phát hành. Lượt `INCOMPLETE`: chỉ `IMAGING`, `LABORATORY`, `ADMINISTRATIVE`; khối không liệt kê thì không trả dữ liệu của khối đó (FE test: trường vắng).
  - Kết quả khám, phiếu kết quả chỉ khi bác sĩ đã phát hành (BR-39); phiếu PDF đúng bệnh nhân và lượt; bản cũ `REPLACED` (BR-40).
  - Lời dặn và chẩn đoán thuộc lượt khám (BR-42).
  - `imaging[].finding` chỉ khi lượt `COMPLETED` (FE không tự ẩn).
  - `prescriptions` chỉ đơn đã phát hành.
  - `resultSheets[].fileUrl`: URL có chữ ký, có hạn do BE cấp; FE mở tab mới, không lưu.
  - `flag` tính theo khoảng tham chiếu; FE không tự suy.

##### 3. GET `/api/v1/prescriptions`
- Query: `patientId?`, `page?`, `size?`.
- 200: `PageResponse<PrescriptionListItem>`; `PrescriptionListItem` = `{ id: string, issuedAt: datetime, itemCount: int, doctorName: string, patient: { id, fullName, relationship } }`.
- Lỗi: 500 MSG-04.
- FE: `features/prescriptions/api/prescriptionApi.ts` › `listPrescriptions`.
- BE phải kiểm: chỉ đơn đã phát hành của lượt `COMPLETED`; đúng hồ sơ trong phạm vi tài khoản (BR-44).

##### 4. GET `/api/v1/prescriptions/{id}`
- 200: `PrescriptionDocument` = `{ id, issuedAt: datetime, doctorName, clinicName: string, patient: { fullName, dateOfBirth: date, patientCode, relationship }, diagnosis: string, items: [{ medicine, dose, frequency, usage: string }] }`.
- Lỗi: 403 MSG-40; 404 MSG-41; 409 MSG-42 (đơn chưa phát hành).
- FE: `prescriptionApi.ts` › `getPrescription`.
- Ghi chú: FE thêm `patient.relationship` so với đặc tả (để giữ quy tắc "của ai" trên màn). BE cần trả trường này.
- BE phải kiểm: chỉ bác sĩ phụ trách kê đơn (BR-43); quyền xem theo hồ sơ (BR-44).

##### 5. GET `/api/v1/imaging-studies`
- Query: `patientId?`, `page?`, `size?`.
- 200: `PageResponse<PatientImagingStudySummary>`; `PatientImagingStudySummary` = `{ id, visitId, visitStatus: "COMPLETED" | "INCOMPLETE", serviceName, modality: "XRAY" | "ULTRASOUND" | "CT" | "MRI", studyDate: date, seriesCount: int, patient: { id, fullName, relationship }, finding: string | null }`. Không có `viewerUrl`.
- Lỗi: 500 MSG-04.
- FE: `features/imaging/api/imagingStudyApi.ts` › `listPatientStudies`.
- BE phải kiểm: study thuộc lượt `COMPLETED`/`INCOMPLETE` của hồ sơ trong phạm vi; `finding` chỉ khi `COMPLETED`.

##### 6. GET `/api/v1/imaging-studies/{id}`
- 200: `PatientImagingStudy` = `PatientImagingStudySummary` + `viewerUrl: string | null` (URL trình xem có hạn, gắn quyền study). `null` thì FE khóa nút "Mở trình xem ảnh".
- Lỗi: 403 MSG-40; 404 MSG-41.
- FE: `imagingStudyApi.ts` › `getPatientStudy`.
- BE phải kiểm: bệnh nhân – lượt – study – trạng thái phát hành trước khi cấp `viewerUrl` (AGENTS.md: DICOMweb có dữ liệu người dùng phải qua xác thực và kiểm quyền theo study). FE không ghép URL OHIF/Orthanc; mở bằng `window.open(viewerUrl, "_blank", "noopener")`.

#### Câu hỏi cho BE
1. Lọc `patientId` ngoài phạm vi trả 403 MSG-40 hay danh sách rỗng? FE hiện cả hai đúng cách.
2. Lượt `INCOMPLETE` có được trả `viewerUrl`/xem study không? Đặc tả BR-48 và BR-49 (bản thống nhất) chỉ mở study khi kết quả đã phát hành; phase 07 cho xem ảnh ở lượt Chưa hoàn tất. FE làm theo BE: có `viewerUrl` thì bật nút.
3. `Xem PDF` mở `fileUrl` trực tiếp; không có endpoint kiểm quyền riêng. Nếu BE cần kiểm lại khi mở file, cần endpoint riêng (chưa có trong hợp đồng).

### P08 · API notes — P08 (thông báo UC63 + đánh giá UC59, khu bệnh nhân)

Trạng thái: FE dùng lớp API giả, chữ ký khớp REST bên dưới. Nối BE: giữ tên và chữ ký hàm, thay thân bằng `apiRequest` (`lib/api/client.ts`). Quy ước chung: base `/api/v1`; ngày `YYYY-MM-DD`; thời điểm ISO `+07:00`; enum `UPPER_SNAKE`; key `camelCase`; lỗi = ProblemDetail + `code` MSG (FE đọc `code`). Phạm vi: mọi endpoint chỉ trả dữ liệu của tài khoản đang đăng nhập (BR-01); FE không gửi id tài khoản.

#### Endpoint FE gọi

| # | Method · URL | Query / body | Response (200/201) | Lỗi (HTTP · MSG) | FE định nghĩa | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `GET /api/v1/me/notifications` | `unread`: `true` (chỉ chưa đọc; vắng = tất cả) · `page`: int 0-based (FE dùng 0) · `size`: int (FE dùng 10; tối đa 100) | `PageResponse<NotificationItem>` = `{ items, page, size, totalItems, totalPages }` | 500 · MSG-04 | `features/notifications/api/notificationApi.ts` › `listMyNotifications(query, signal?)`; hook `useNotificationList(size)` | Mới nhất trước. Chỉ thông báo gửi đến người đăng nhập (BR-01). Chỉ tài khoản có hồ sơ đã gắn mới có thông báo (BR-68). |
| 2 | `GET /api/v1/me/notifications/unread-count` | — | `{ unreadCount: int }` | 500 · MSG-04 | `notificationApi.ts` › `getUnreadNotificationCount(signal?)`; hook `useUnreadNotificationCount()` | Chuông gọi khi mở trang và khi đổi route (không polling). |
| 3 | `POST /api/v1/me/notifications/{id}/read` | path `id`: string | `NotificationItem` (`read: true`, `allowedActions: []`) | 404 · MSG-50 (không có hoặc không phải của người đăng nhập) · 500 · MSG-04 | `notificationApi.ts` › `markNotificationRead(id)` | Gọi lại khi đã đọc vẫn 200 (idempotent). FE gọi lúc **mở dòng chưa đọc** ở khu bệnh nhân (UC63 bước 5) và không toast; nút "Đánh dấu đã đọc" trong chi tiết có toast MSG-60. |
| 4 | `POST /api/v1/me/notifications/read-all` | — | `{ updatedCount: int }` | 500 · MSG-04 | `notificationApi.ts` › `markAllNotificationsRead()` | Chỉ đổi các thông báo của người đăng nhập. FE hiện toast MSG-60. |
| 5 | `GET /api/v1/me/patient-profiles` (P00, dùng lại) | — | `{ items: LinkedProfile[] }` | 500 · MSG-04 | `lib/patient-scope/linkedProfilesApi.ts` › `listLinkedProfiles` | FE dùng để ẩn chuông và thay danh sách bằng hướng dẫn "thêm hồ sơ" khi `items` rỗng (BR-68). |
| 6 | `GET /api/v1/me/reviews/eligible-visits` | — | `{ items: ReviewableVisit[] }` (không phân trang) | 500 · MSG-04 | `features/patient-reviews/api/myReviewApi.ts` › `listEligibleVisits(signal?)`; hook `useEligibleVisits()` | Chỉ lượt **COMPLETED** của hồ sơ đã gắn và **chưa có đánh giá** (BR-54). Hồ sơ đã bỏ gắn: lượt biến mất (BR-19). Sắp xếp `visitDate` giảm dần. |
| 7 | `GET /api/v1/me/reviews` | — | `{ items: MyReview[] }` (không phân trang) | 500 · MSG-04 | `myReviewApi.ts` › `listMyReviews(signal?)`; hook `useMyReviews()` | Mới nhất trước. Gồm cả `PENDING`, `PUBLISHED`, `HIDDEN`. `clinicReply` hiện kèm cả khi đã ẩn (theo phase). |
| 8 | `POST /api/v1/reviews` | body `SubmitReviewRequest` = `{ visitId: string, rating: int 1–5, text: string }` | `201 MyReview` (`status: "PENDING"`, `clinicReply: null`) | 400 · MSG-57 (`fieldErrors`: `rating` và/hoặc `text`, `code: "MSG-57"`) · 403 · MSG-56 (lượt không tồn tại hoặc không thuộc tài khoản) · 409 · MSG-56 (lượt chưa hoàn tất, hoặc đã có đánh giá) · 500 · MSG-04 | `myReviewApi.ts` › `submitReview(request, signal?)` | FE trim `text` trước khi gửi. Nội dung 10–1000 ký tự sau trim là **đề xuất** (R3 không nói), BE quyết. Kiểm: quyền sở hữu lượt (BR-01, BR-19), `COMPLETED` (BR-54), một đánh giá mỗi lượt, tạo ở `PENDING` (BR-55), không sửa/xóa sau khi gửi. |

##### Kiểu dữ liệu

- `NotificationItem`: `{ id: string, title: string, content: string (văn bản thuần, đã điền tham số), createdAt: ISO+07:00, read: boolean, relatedRecord: { kind: NotificationRecordKind, id: string, available: boolean } | null, allowedActions: ("markRead")[] }`
- `NotificationRecordKind`: `APPOINTMENT | VISIT | VISIT_RESULT | PAYMENT`. Admin chỉ nhận `APPOINTMENT`.
- `ReviewableVisit`: `{ visitId: string, visitDate: YYYY-MM-DD, serviceName: string, patient: { fullName: string, relationship: SELF | PARENT | CHILD | SPOUSE | SIBLING } }`
- `MyReview`: `{ id: string, visitId: string, visitDate: YYYY-MM-DD, rating: 1–5, text: string, status: PENDING | PUBLISHED | HIDDEN, clinicReply: string | null, createdAt: ISO+07:00, patient: { fullName, relationship } }`
- `PageResponse<T>`: `{ items, page, size, totalItems, totalPages }` (0-based, size tối đa 100).

Ghi chú so với phase: `patient` (tên + quan hệ) được thêm vào `ReviewableVisit` và `MyReview` để luôn ghi "của ai" (quy tắc khu bệnh nhân). Phase chỉ có `patient.fullName`.

#### Hợp đồng URL giữa màn (FE)

| URL FE | Query | Ghi chú |
| --- | --- | --- |
| `/portal/notifications` | — (tab Tất cả/Chưa đọc là trạng thái màn, không lên URL) | Chuông trên header mở trang này. |
| `/portal/reviews` | — | Hai khối: lượt có thể đánh giá, đánh giá đã gửi. |
| `/portal/reviews/new?visitId={visitId}` | `visitId` bắt buộc | FE gọi `eligible-visits` để kiểm lượt. Thiếu hoặc không có trong danh sách: MSG-56 và về `/portal/reviews`. Lượt đến từ phase 05 (`VisitProgressView`, khi `allowedActions` có `review`) và danh sách `/portal/reviews`. |

Đường dẫn bản ghi liên quan (lớp ghép `app/(patient)/portal/notificationRecordHref.ts`; bản ghi `available: false` thì không có đường dẫn):

| `kind` | Mở tới |
| --- | --- |
| `APPOINTMENT` | `/portal/appointments/{id}` |
| `VISIT` | `/portal/visits/{id}` |
| `VISIT_RESULT` | `/portal/results/{id}` |
| `PAYMENT` | `/portal/payments/history/{id}` |

#### Ghi chú BE phải kiểm

1. **BR-68:** thông báo và chuông chỉ cho tài khoản có hồ sơ đã gắn. Tài khoản chưa gắn hồ sơ: FE không hiện chuông và không gọi danh sách, nhưng API vẫn phải trả rỗng nếu được gọi.
2. **BR-01 / E1 (UC63):** thông báo của người khác: `POST .../read` trả 404 MSG-50, không lộ sự tồn tại.
3. **E2 (UC63):** `relatedRecord.available = false` khi bản ghi không còn truy cập được (ví dụ hồ sơ đã bỏ gắn, BR-19). Không lộ bản ghi đó trong thông báo.
4. **Mở chi tiết:** FE đánh dấu đã đọc im lặng khi mở dòng chưa đọc. Bản ghi đã đọc giữ nguyên (AF1).
5. **Nội dung thông báo:** BE điền tham số trước khi trả (MSG-76, 83, 84, 85, 87, 89 của R3). FE không ghép chuỗi. R3 chưa có MSG cho thông báo thanh toán (xem câu hỏi mở).
6. **Đánh giá — danh sách:** `eligible-visits` chỉ trả lượt COMPLETED, chưa có đánh giá, của hồ sơ đã gắn (BR-54, BR-19). `allowedActions.review` ở phase 05 phải khớp với lượt này.
7. **Đánh giá — gửi:** kiểm theo thứ tự: nội dung/điểm (400 MSG-57 có `fieldErrors`), rồi quyền sở hữu lượt (403 MSG-56), rồi trạng thái COMPLETED và chưa đánh giá (409 MSG-56). Tạo ở `PENDING`. Không có endpoint sửa/xóa.
8. **Phản hồi phòng khám:** `clinicReply` có trong `MyReview` cả khi `HIDDEN` (theo phase). BR-60 chỉ bắt buộc với danh sách công khai.
9. **Văn bản:** nội dung đánh giá và phản hồi là văn bản thuần, FE không render HTML/Markdown.
10. **Tần suất:** chuông gọi `unread-count` khi đổi route; không cần push.

#### Câu hỏi cho BE / nhóm

1. Giới hạn nội dung đánh giá 10–1000 (đề xuất). Admin trả lời dùng 1–1000 (`reviewReplyFieldRules.ts`). Thống nhất giới hạn nào?
2. Lượt không tồn tại và lượt của người khác: FE chấp nhận 403 MSG-56 (mock). Hay BE dùng 404 MSG-41?
3. Thông báo thanh toán (`PAYMENT`): R3 không có MSG riêng. Mock dùng câu viết tạm (xem báo cáo P08).
4. `GET /api/v1/me/reviews/eligible-visits` có cần phân trang không? FE hiện nhận toàn bộ.

### P09 · API notes P09 — khu công khai (dịch vụ, bác sĩ, đánh giá, thông báo, giới thiệu, bài viết)

Mọi endpoint dưới đây là **công khai** (không cần JWT). FE hiện gọi lớp giả (mock trong bộ nhớ) có chữ ký đúng REST. Khi nối BE: giữ hàm, đổi thân. Lưu ý: `apiRequest` (`lib/api/client.ts`) hiện luôn đòi phiên Supabase và ném lỗi khi khách chưa đăng nhập, nên cần một biến thể không gắn `Authorization` cho nhóm `/api/v1/public/**`.

Quy ước chung: ngày/giờ ISO có offset `+07:00`; tiền VND nguyên; enum `UPPER_SNAKE`; key `camelCase`; `page` 0-based trên API (URL trang đếm từ 1, FE đổi). Lỗi là ProblemDetail + `code` MSG.

#### Bảng endpoint

| # | Method · URL | Query / body | Response (2xx) | Lỗi | FE định nghĩa | Ghi chú nghiệp vụ BE phải kiểm |
|---|---|---|---|---|---|---|
| 1 | GET `/api/v1/public/services` | `q?: string` (đã cắt khoảng trắng, tối đa 100 ký tự); `type?: EXAMINATION \| IMAGING \| LABORATORY` | `{ items: PublicService[] }` | 400 MSG-54 (`fieldErrors` `q` hoặc `type`); 5xx MSG-04 | `features/clinic-services/api/publicServiceApi.ts` → `listPublicServices(query)` | Chỉ dịch vụ đang cung cấp (BR-65). Thứ tự hiển thị do BE quyết định, FE không sắp xếp lại. `q` so khớp không phân biệt hoa thường và dấu (FE mock làm vậy). |
| 2 | GET `/api/v1/public/services/{id}` | — | `PublicService` | 404 MSG-50; 5xx MSG-04 | `publicServiceApi.ts` → `getPublicService(id)` | Dịch vụ đã ngừng coi như không tồn tại (404). |
| 3 | GET `/api/v1/public/doctors` | — | `{ items: PublicDoctor[] }` (không `availability`) | 5xx MSG-04 | `features/staff-profiles/api/publicDoctorApi.ts` → `listPublicDoctors()` | Chỉ bác sĩ đang hoạt động và đã bật hiển thị (BR-13). Không trả tên đăng nhập, SĐT/email nội bộ, trạng thái. |
| 4 | GET `/api/v1/public/doctors/{id}` | — | `PublicDoctorDetail` = `PublicDoctor` + `availability: DoctorAvailabilityDay[]` (đúng 7 ngày, từ hôm nay theo giờ VN) | 404 MSG-24; 5xx MSG-04 | `publicDoctorApi.ts` → `getPublicDoctor(id)` | Bác sĩ tắt hiển thị hoặc đã gỡ → 404 MSG-24. `hasOpenTime` tính từ ca làm trừ lịch đã đặt (BR-25). **Không** trả từng giờ. |
| 5 | GET `/api/v1/public/reviews` | `page?` (0-based, mặc định 0), `size?` (FE dùng 10; tối đa 100) | `PageResponse<PublicReview>` + `summary: ReviewSummary` | 5xx MSG-04 | `features/patient-reviews/api/publicReviewApi.ts` → `listPublicReviews({page,size})` | Chỉ đánh giá `PUBLISHED` (BR-59). `clinicReply` chỉ có khi đánh giá còn công khai (BR-60). `summary` tính trên **toàn bộ** đánh giá đã duyệt, không theo trang. Không trả tên bệnh nhân, bác sĩ, dịch vụ. |
| 6 | GET `/api/v1/public/announcements` | `page?`, `size?` (FE dùng 10) | `PageResponse<PublicAnnouncement>` | 5xx MSG-04 | `features/announcements/api/publicAnnouncementApi.ts` → `listPublicAnnouncements({page,size})` | Chỉ thông báo có nhóm nhận `PUBLIC` (BR-69). Mới nhất trước. |
| 7 | GET `/api/v1/public/announcements/{id}` | — | `PublicAnnouncement` | 404 MSG-50; 5xx MSG-04 | `publicAnnouncementApi.ts` → `getPublicAnnouncement(id)` | Nhóm nhận khác `PUBLIC` → 404 MSG-50 (không lộ sự tồn tại). |
| 8 | GET `/api/v1/public/website-content/ABOUT_PAGE` | — | `PublicAboutPage` = `{ title, body }` (body Markdown) | 404 MSG-50 khi chưa đăng trang nào; 5xx MSG-04 | `features/clinic-website/api/websiteContentApi.ts` → `getPublishedAboutPage()` (trả `null` khi chưa có) | Khi có nhiều trang giới thiệu đã đăng, chọn bản cập nhật gần nhất (FE mock làm vậy). Cần BE xác nhận quy tắc này. |
| 9 | GET `/api/v1/public/website-content/{articleId}` | — | `PublicHealthArticle` = `{ id, title, body (Markdown), publishedAt }` | 404 MSG-50 khi chưa đăng hoặc không tồn tại; 5xx MSG-04 | `websiteContentApi.ts` → `getPublishedHealthArticle(id)` | Chỉ bài đã đăng lên website (BR-68). `imageUrl` chưa dùng ở trang công khai (chưa có remotePatterns). |
| 10 | GET `/api/v1/public/website-content/CLINIC_INFO` (đã có, chưa đổi) | — | `{ contact, hero }` | 5xx MSG-04 | `websiteContentApi.ts` → `getPublishedWebsiteContent()` | Dùng cho header, footer, trang chủ và trang Giới thiệu (giờ làm việc, liên hệ). Chưa chốt hình dạng JSON, giữ như hiện tại. |

#### Kiểu dữ liệu

- `PublicService`: `{ id: string, name: string, type: "EXAMINATION"|"IMAGING"|"LABORATORY", price: number /* VND nguyên */, description: string|null, bookingDetails: { slotMinutes: number|null, patientNote: string|null } | null }`
- `PublicDoctor`: `{ id, fullName, photoUrl: string|null, specialties: string[], qualifications: string, biography: string, consultationInfo: string }`
- `DoctorAvailabilityDay`: `{ date: "YYYY-MM-DD", hasOpenTime: boolean }`
- `PublicReview`: `{ id, rating: 1..5, text: string /* có thể rỗng */, createdAt: ISO+07:00, clinicReply: string|null }`
- `ReviewSummary`: `{ average: number /* 1 chữ số thập phân, 0 khi chưa có */, count: number }`
- `PublicAnnouncement`: `{ id, title, body: string /* văn bản thuần, xuống dòng bằng \n */, publishedAt: ISO+07:00 }`
- `PageResponse<T>`: `{ items, page, size, totalItems, totalPages }` (+ `summary` ở reviews)

#### Hợp đồng URL giữa các màn (query param)

| Màn | URL | Ghi chú |
|---|---|---|
| Dịch vụ | `/services?q=&type=` | `type` rỗng = tất cả; `q` sai → MSG-54 ngay trên trang, không gọi API |
| Chi tiết dịch vụ | `/services/{id}` | Chỉ dịch vụ Khám có CTA đặt lịch |
| Danh bạ bác sĩ | `/doctors` | |
| Hồ sơ bác sĩ | `/doctors/{id}` | Admin "Xem trang công khai" đã trỏ về đây (`publicUrl`) |
| Đánh giá | `/reviews?page=N` | N đếm từ 1; sai/thiếu → trang 1 |
| Thông báo | `/announcements?page=N`, `/announcements/{id}` | |
| Giới thiệu | `/gioi-thieu` | |
| Bài viết | `/bai-viet/{id}` | |
| CTA "Đặt lịch khám" (khách) | `/login?next=/portal/appointments/new` | **Login phải honour `next`** (chỉ đường dẫn tương đối cùng site, chặn open redirect). Đã đăng nhập: FE đổi thẳng sang `/portal/appointments/new`. |

#### Ghi chú cho BE

- Tất cả endpoint `public/**` không lộ dữ liệu cá nhân: không trả `username`, `workContact`, tên bệnh nhân, tên khách trong đánh giá.
- Nút đặt lịch công khai chỉ dẫn vào luồng đăng nhập; FE không chọn sẵn bác sĩ (đặt bắt đầu từ thời gian).
- Trạng thái "Còn giờ"/"Hết giờ" là ước lượng cho khách; màn đặt lịch phải kiểm lại khi chọn giờ (BR-25).
- Admin: `publicUrl` của hồ sơ bác sĩ trả `/doctors/{id}` (trước đây `/bac-si/{id}`, không có route).

### S00 · API notes — S00 (khung khu Lễ tân)

Phase S00 không gọi endpoint nghiệp vụ nào. Không có `api/*Api.ts` mới.

| Method | URL | Ghi chú |
| --- | --- | --- |
| POST | Supabase Auth `signOut` (SDK `getSupabaseBrowserClient().auth.signOut()`, không phải REST `/api/v1`) | Menu tài khoản "Đăng xuất". Lỗi bị nuốt, luôn chuyển về `/login`. |

Danh tính hiển thị lấy từ `getMockCurrentActor("staff")` (`lib/session/mockCurrentActor.ts`, giả, xóa khi có `/api/v1/me`). BE cần:
- `GET /api/v1/me` trả `accountId`, `role` (enum `AccountType`: `RECEPTIONIST` | `ORTHOPEDIC_DOCTOR` | `IMAGING_TECHNICIAN` | `LAB_TECHNICIAN` | `PATIENT`), `displayName`. FE dùng để hiển thị tên/vai trò và lọc mục menu theo vai trò. Quyền thật vẫn do BE kiểm từng thao tác.

### S04a · API notes S04a: `features/patients` (tìm và tạo hồ sơ bệnh nhân tại quầy)

Nguồn: phase-02 §6–§8 + cập nhật 24/10 (r3-change-spec: tìm theo mã bệnh nhân, `email?`, giữ `hasAccount`).
FE hiện chạy mock: `USE_MOCK = true` trong `frontend/src/features/patients/api/patientApi.ts`. Nhánh `apiRequest` đã viết sẵn, BE xong thì đổi cờ.

#### 1. Endpoint

| Method | URL | Query / body | Response | Lỗi (HTTP · MSG) | Hàm FE | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/patients/lookup` | Query, đúng một cách: `patientCode` (string, dạng `BN-000123`) **hoặc** `phone` (string, 10 chữ số, bắt đầu 0) + `dateOfBirth` (string, `YYYY-MM-DD`) + `fullName` (string, tùy chọn: chỉ gửi khi lần trước trả MULTIPLE) | `200` `PatientLookupResponse`: `{ result: "SINGLE" \| "NONE" \| "MULTIPLE", matches: PatientMatch[] }` | 400 MSG-17 + `fieldErrors` (`patientCode`, `phone`, `dateOfBirth`, `fullName`) · 403 MSG-40 · 5xx MSG-04 | `lookupPatients(query: PatientLookupQuery, signal?)` · `api/patientApi.ts` | Xem mục 3 (quy tắc tìm, BR-14). MULTIPLE luôn `matches: []`. |
| POST | `/api/v1/patients` | Body `CreateWalkInPatientRequest`: `{ fullName: string; dateOfBirth: string (YYYY-MM-DD); sex: "MALE" \| "FEMALE"; phone: string (10 số); address: string; citizenId: string \| null; email?: string }` | `201` `PatientMatch` (`hasAccount: false`) | 400 MSG-17 + `fieldErrors` (`fullName`, `dateOfBirth`, `sex`, `phone`, `address`, `citizenId`, `email`) · 409 MSG-18 (`params.existing: true`) · 403 MSG-40 · 5xx MSG-04 | `createWalkInPatient(body: CreateWalkInPatientRequest, signal?)` · `api/patientApi.ts` | UC10. Sinh `patientCode` duy nhất. Không tạo tài khoản (BR-15). Trùng: xem mục 3. |

Kiểu dùng chung (`features/patients/types/patient.ts`):
- `PatientMatch` = `{ id: string; patientCode: string; fullName: string; dateOfBirth: string; sex: "MALE" \| "FEMALE"; hasAccount: boolean }`.
- `PatientLookupQuery` = `{ patientCode: string } \| { phone: string; dateOfBirth: string; fullName?: string }`.
- `PatientLookupResponse` = `{ result: "SINGLE" \| "NONE" \| "MULTIPLE"; matches: PatientMatch[] }`.
- `Sex` = `"MALE" \| "FEMALE"`.

Quy ước chung: `PatientMatch` không có SĐT, địa chỉ, email, CCCD ở bất kỳ response nào của feature này (FE không hiển thị, giữ tối thiểu dữ liệu cá nhân). Không có tiền sử, dị ứng (BR-12).

#### 2. Hợp đồng URL giữa màn (FE)

| Từ | Đến | URL | Ghi chú |
| --- | --- | --- | --- |
| `PatientLookup` (prop `selectHref`) | màn chứa khối | `${selectHref}?patientId=<id>` | Chọn một kết quả, hoặc tạo xong. Thay mọi tham số khác (`push`). Hiện dùng: `/staff/check-in` (S04b), `/staff/appointments/new` (S02). |
| Trang chứa khối | — | `?patientId=` | `id` là định danh mờ (mock: `pat-an`), không chứa tên hay SĐT. |

SĐT, ngày sinh, họ tên, mã bệnh nhân **không** đưa lên URL của trang. Chỉ đi trong query của `GET /lookup` (theo hợp đồng trên).

#### 3. Quy tắc nghiệp vụ BE phải kiểm

**Tìm (BR-14, tìm trước):**
- Theo `patientCode`: 1 hồ sơ → SINGLE; không có → NONE.
- Theo `phone` + `dateOfBirth`:
  - 0 hồ sơ → NONE, `matches: []`.
  - 1 hồ sơ → SINGLE (bỏ qua `fullName` nếu có).
  - Nhiều hồ sơ, không có `fullName` → MULTIPLE, `matches: []` (không lộ ai).
  - Nhiều hồ sơ, có `fullName` → lọc theo họ tên (không phân biệt hoa thường, gọn khoảng trắng): 1 → SINGLE; 0 → NONE; nhiều → MULTIPLE `[]`.
- Quyền: chỉ vai trò Lễ tân; 403 MSG-40 khi không đủ quyền.

**Tạo (UC10, BR-15):**
- Trùng (409 MSG-18): khớp 1 hồ sơ theo `phone` + `dateOfBirth` thì trùng, bất kể tên. Khớp nhiều hồ sơ thì trùng chỉ khi họ tên trùng một hồ sơ trong đó.
- `patientCode` cấp mới, duy nhất. `hasAccount` luôn `false` khi tạo.
- `email` chỉ lưu khi có gửi. Không có cờ đồng ý riêng (xem câu hỏi mở).
- `citizenId` là `null` khi lễ tân để trống.

**Dữ liệu FE gửi đã chuẩn hóa** (BE vẫn kiểm lại, không tin tuyệt đối):
- `phone`: chỉ chữ số, `^0\d{9}$`.
- `dateOfBirth`: `YYYY-MM-DD` có thật, từ 1900-01-01 đến hôm nay (giờ Việt Nam), không ở tương lai.
- `patientCode`: `^BN-\d{6}$`.
- `fullName`: gọn khoảng trắng, ≥ 2 từ, mỗi từ có chữ cái, chỉ chữ cái (kể cả có dấu), khoảng trắng, `.`, `'`, `-`; tối đa 100 ký tự.
- `address`: không rỗng, tối đa 255 ký tự.
- `citizenId`: `null` hoặc `^[A-Za-z0-9]{8,12}$` (viết hoa).
- `email`: bỏ khi rỗng; có thì tối đa 254 ký tự, dạng `a@b.c`.
- `sex`: bắt buộc, `MALE` hoặc `FEMALE` (FE không chọn sẵn).

#### 5. Câu hỏi mở cho BE

1. Định dạng mã bệnh nhân: FE đang chấp nhận `BN-` + 6 chữ số (ví dụ `BN-000123`), khớp mọi ví dụ trong spec. R3 còn để mở. Nếu BE đổi định dạng, sửa `PATIENT_CODE_PATTERN` trong `schema/patientFieldRules.ts`.
2. Đồng ý ghi email: spec chỉ yêu cầu lễ tân hỏi bệnh nhân (câu hỏi hiển thị dưới ô email). Hợp đồng không có cờ `emailConsent`. Có cần cờ và lưu lại người đồng ý hay không.
3. `params.existing: true` của 409 MSG-18: BE gửi theo spec. `ApiError.params` của FE chỉ nhận `string | number`, nên FE không đọc và mock không gửi.
4. Nhiều hồ sơ cùng SĐT + ngày sinh và họ tên không khớp ai: mock trả NONE (không MULTIPLE). BE xác nhận.
5. `PatientMatch` không có `email`. Nếu màn tiếp nhận cần hiện email (R3: "hiển thị và tìm theo mã bệnh nhân"), bổ sung ở `GET /reception/check-in-context` (phase 04), không thêm vào đây.

### S04b · API notes — S04b · Tiếp nhận bệnh nhân `/staff/check-in` (UC29, phí khám của UC54)

Tiền tố: `/api/v1/reception/`. Auth: Bearer (session Supabase) qua `apiRequest`. Enum `UPPER_SNAKE`, key `camelCase`, tiền số nguyên VND, ngày `YYYY-MM-DD`, thời điểm ISO `+07:00`.
FE: `frontend/src/features/reception/api/checkInApi.ts` (`USE_MOCK = true`; nhánh `apiRequest` đã viết sẵn). DTO: `features/reception/types/checkIn.ts`.

#### 1. Endpoint

| # | Method · URL | Query / body | Response | Lỗi (HTTP · MSG) | FE (hàm) | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `GET /api/v1/reception/today-appointments` | `status`: `BOOKED` \| `CHECKED_IN` \| rỗng (= tất cả). FE **luôn gửi** `status=` tường minh | `{ items: TodayAppointmentRow[], counts: { waiting: int, checkedIn: int } }` | 400 MSG-39 (status lạ) · 403 MSG-40 · 5xx MSG-04 | `listTodayAppointments(status, signal)` · `checkInApi.ts` | Thiếu tham số thì BE mặc định BOOKED, FE không dựa vào đó. `counts` luôn theo cả ngày, không phụ thuộc `status`. Sắp theo `startAt`. Không phân trang (< 200 dòng). Lọc tên/SĐT làm ở FE. |
| 2 | `GET /api/v1/reception/check-in-context` | đúng một trong `appointmentId` \| `patientId` | `CheckInContext` (xem §2) | 400 MSG-39 (thiếu/thừa tham số) · 403 MSG-40 · 404 MSG-41 · 5xx MSG-04 | `getCheckInContext({appointmentId} \| {patientId}, signal)` · `checkInApi.ts` | Xem §3 (BR-26, BR-66, BR-27, BR-12). Không trả tiền sử/dị ứng. |
| 3 | `GET /api/v1/reception/priority-groups` | — | `{ items: { code: string, label: string }[] }` | 403 MSG-40 · 5xx MSG-04 | `listPriorityGroups(signal)` · `checkInApi.ts` | Danh mục cấu hình (BR-31). "Không ưu tiên" là lựa chọn của FE (gửi `null`). |
| 4 | `POST /api/v1/reception/exam-fee-payments` | `{ patientId: string, appointmentId: string \| null, serviceId: string \| null, method: "CASH" \| "VNPAY" }` | `201` `ExamFeePayment` (§2) | 400 MSG-39 · 403 MSG-40 · 404 MSG-41 · 409 MSG-49 · 5xx MSG-04 | `collectExamFee(body)` · `checkInApi.ts` | FE **không gửi số tiền**: BE tính theo giá chốt của lịch (BR-59) hoặc giá dịch vụ vãng lai. Đúng một trong `appointmentId`/`serviceId`. Lịch phải `BOOKED` và phí `PENDING_PAYMENT`, nếu không → 409 MSG-49. CASH → `SUCCESS` + `receiptNo`. VNPAY → `PENDING` + `vnpay`. |
| 5 | `GET /api/v1/reception/exam-fee-payments/{id}` | — | `ExamFeePayment` | 403 MSG-40 · 404 MSG-41 · 5xx MSG-04 | `getExamFeePayment(id, signal)` · `checkInApi.ts` | FE hỏi mỗi 3 giây khi `PENDING`, dừng ở `SUCCESS`/`FAILED`, khi FE thấy hết hạn, hoặc khi rời màn. Lỗi 5xx: FE giữ poll và báo "chưa kiểm tra được". |
| 6 | `POST /api/v1/reception/check-ins` | `{ patientId: string, appointmentId: string \| null, visitId: string \| null, serviceId: string \| null, priorityGroupCode: string \| null, examFeePaymentId: string \| null }` | `201` `CheckInResult` (§2) | 400 MSG-39 (không đúng một lựa chọn, mã nhóm lạ) · 403 MSG-40 · 409 MSG-37 (lịch hủy/đã tiếp nhận/không hợp lệ) · 409 MSG-49 (phí chưa trả) · 5xx MSG-04 | `checkInPatient(body)` · `checkInApi.ts` | Đúng một trong `appointmentId` (lịch hẹn), `visitId` (quay lại 7 ngày), `serviceId` (vãng lai). Kiểm lại phí ở BE (BR-26), không tin FE. `examFeePaymentId` phải là khoản `SUCCESS` của đúng bệnh nhân và lịch/dịch vụ. |

Tất cả lỗi có `code` MSG trong ProblemDetail. FE không hiện mã; chữ theo `lib/messages/appMessages.ts`.

#### 2. DTO (rút gọn, tên trường đúng như FE)

- `TodayAppointmentRow`: `appointmentId`, `startAt`, `patientName`, `patientCode`, `phone`, `serviceName`, `doctorName`, `roomName`, `isFollowUp: boolean`, `examFeeStatus: PAID|PENDING_PAYMENT|NOT_REQUIRED`, `status: BOOKED|CHECKED_IN|CANCELLED|NO_SHOW`.
- `CheckInContext`: `patient { id, patientCode, fullName, dateOfBirth, sex: MALE|FEMALE, phone }`, `options: CheckInOption[]`, `ineligible: { appointmentId, startAt, reason: ALREADY_CHECKED_IN|CANCELLED|NO_SHOW|NOT_TODAY, checkedIn?: { at, queueNumber, roomName } }[]`, `notice: PREVIOUS_VISIT_INCOMPLETE | null`.
- `CheckInOption` (phân biệt theo `kind`):
  - `APPOINTMENT`: `appointmentId, startAt, serviceName, visitType: FIRST_VISIT|FOLLOW_UP, doctorName, roomName, examFee { amount, status, paidOnline }`
  - `RETURN_VISIT`: `visitId, firstCheckInDate, dayOfPeriod (1–7), nextQueue { type: SERVICE|RESULT_REVIEW, roomName, doctorName }, examFee` (luôn `NOT_REQUIRED`, `amount: 0`)
  - `WALK_IN`: `services: { id, name, price }[]` (chỉ dịch vụ Khám)
- `ExamFeePayment`: `id, method: CASH|VNPAY, amount, status: SUCCESS|PENDING|FAILED, receiptNo: string | null, vnpay: { qrImageUrl, transactionRef, expiresAt } | null`
- `CheckInResult`: `visitId, queueEntryId, queueNumber: int, queueType: EXAMINATION|SERVICE|RESULT_REVIEW, roomName, doctorName, priorityLabel: string | null, peopleAhead: int`

#### 3. Ghi chú nghiệp vụ cho BE

> **Lưu ý đánh số BR:** mã BR trong file này lấy từ phase S04b và phân tích nghiệp vụ (bản R3 trên Drive). `docs/appendix-rules.md` đánh số BR khác (ví dụ BR-26 ở appendix là quy tắc đặt lịch, BR-12 là vai trò nhân viên). BE đối chiếu theo nội dung nghiệp vụ, không theo số.

1. **Quyền**: chỉ Lễ tân, và chỉ với bệnh nhân lễ tân phục vụ (phân tích §3.1). Sai quyền → 403 MSG-40, không phải 404.
2. **Điều kiện cứng (BR-26)**: người xác định được, có mặt (FE tick, BE không kiểm được), phí `PAID`/`NOT_REQUIRED`. Phí chưa trả → 409 MSG-49 ở `check-ins`. FE vẽ nút theo `missing[]` của nó, nhưng BE là nguồn đúng.
3. **Options do BE quyết** (FE không suy): lịch hôm nay còn `BOOKED` → `APPOINTMENT`; visit `AWAITING_CONCLUSION` trong 7 ngày (BR-66) → `RETURN_VISIT`, không thu phí; không có visit mở và chưa tiếp nhận hôm nay → `WALK_IN`; lượt trước `INCOMPLETE` quá 7 ngày → `notice` + `WALK_IN` (AF3). Lịch không đủ điều kiện → `ineligible` kèm lý do (BR-64 quá ca → `NO_SHOW`).
4. **Vãng lai (BR-27, NF-07)**: không tạo `Appointment`. BE chọn phòng ít người chờ nhất và bác sĩ có ca ở phòng đó. FE không gửi phòng/bác sĩ.
5. **Số thứ tự**: theo phòng trong ngày, tăng dần; `peopleAhead` = số bản ghi `WAITING` đang chờ trong cùng phòng (cách mock đang tính; BE quyết theo hàng đợi thật). Nhóm ưu tiên đưa vào thứ tự hàng đợi theo BR-29 (BE sắp, FE chỉ hiển thị).
6. **VNPay**: `qrImageUrl` là ảnh PNG dạng data URL (FE không lưu, không log). `expiresAt` = tạo + ~15 phút (BR-51); hết hạn không có kết quả → `FAILED`. Khi lễ tân chuyển sang tiền mặt lúc QR đang `PENDING`, FE bỏ khoản đó: **BE phải bảo đảm khoản PENDING đó không thể thành SUCCESS sau khi đã thu tiền mặt** (xem câu hỏi mở).
7. **Thu tiền mặt**: FE không gửi `amount`. Khoản thu trọn vẹn (BR-49), không hoàn (BR-53). Sau khi thu tiền mặt, FE khóa đổi lựa chọn trong phiên này.
8. **Đụng độ**: hai lễ tân cùng tiếp nhận một lịch → người sau nhận 409 MSG-37, FE tải lại `check-in-context` và hiện "đã tiếp nhận lúc … · Số …" (cần `checkedIn` đầy đủ trong `ineligible`).
9. **Hồ sơ**: `check-in-context` không được trả tiền sử hay dị ứng (BR-12). Màn chỉ cần `patient` như trên.
10. **Phân biệt trạng thái**: `today-appointments?status=` rỗng phải trả cả đã hủy và không đến; `counts` không đổi theo `status`.

#### 4. Contract URL giữa màn (query param)

- `/staff/check-in` — màn trống (gợi ý tìm bệnh nhân).
- `/staff/check-in?appointmentId=<id>` — FE đặt khi chọn một dòng trong "Lịch hẹn hôm nay". Đây là đích ưu tiên khi cả hai tham số có mặt.
- `/staff/check-in?patientId=<id>` — `PatientLookup` (feature `patients`) đẩy sang sau khi chọn hoặc tạo hồ sơ (`selectHref="/staff/check-in"`).
- Chỉ chứa id, không chứa tên hay SĐT. Khi tiếp nhận xong, FE bỏ tham số (`/staff/check-in`).
- Link ra ngoài: `/staff/reception-queue` (phase S05).

#### 5. Câu hỏi mở cho BE

1. Khoản VNPay `PENDING` bị bỏ vì lễ tân thu tiền mặt: BE hủy khoản đó ngay khi nhận thu tiền mặt, hay từ chối `SUCCESS` muộn? (tránh thu hai lần)
2. Có trả `peopleAhead` theo cùng thứ tự với BE (nhóm ưu tiên trước) hay theo giờ vào hàng?
3. `CheckInOption.APPOINTMENT.examFee.paidOnline` có cần kèm thời điểm và kênh trả (VNPay/ngân hàng) để hiện chi tiết không? Hiện FE chỉ hiện "Đã trả trực tuyến trước khi đến."

### S05 · API notes — S05 · Hàng đợi tiếp nhận `/staff/reception-queue` (UC30, chỉ xem)

Tiền tố: `/api/v1/reception/`. Auth: Bearer (session Supabase) qua `apiRequest`. Enum `UPPER_SNAKE`, key `camelCase`, thời điểm ISO `+07:00`, số nguyên thuần.
FE: `frontend/src/features/reception/api/receptionQueueApi.ts` (`USE_MOCK = true`; nhánh `apiRequest` đã viết sẵn). DTO: `features/reception/types/receptionQueue.ts`.

#### 1. Endpoint

| # | Method · URL | Query / body | Response | Lỗi (HTTP · MSG) | FE (hàm) | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `GET /api/v1/reception/queue` | `roomId`: string, không bắt buộc (bỏ trống = mọi phòng). `status`: lặp, `WAITING` \| `BEING_SERVED` \| `ABSENT`. FE **luôn gửi** `status` tường minh; thiếu thì BE mặc định `WAITING`, `ABSENT`. Không phân trang. | `ReceptionQueueResponse` (§2) | 400 MSG-39 (`status` lạ/rỗng, `roomId` rỗng hoặc không thuộc phòng khám) · 403 MSG-38 (không phải Lễ tân) · 5xx MSG-04 | `getReceptionQueue(query, signal)` · `receptionQueueApi.ts` | Xem §3 (quyền, thứ tự, lọc, đếm). Lọc tên và số thứ tự làm ở FE. |
| 2 | `GET /api/v1/reception/queue/{queueEntryId}` | — | `ReceptionVisitSummary` (§2) | 400 MSG-39 (id rỗng) · 403 MSG-38 · 404 MSG-38 (không có bản ghi) · 410 MSG-38 (đã rời hàng: xong, đã đóng, hoặc hàng dịch vụ) · 5xx MSG-04 | `getReceptionVisitSummary(queueEntryId, signal)` · `receptionQueueApi.ts` | Chỉ hành chính và hàng đợi. Không trả tiền sử, dị ứng, chẩn đoán. |

Tất cả lỗi có `code` MSG trong ProblemDetail. FE không hiện mã; chữ theo `lib/messages/appMessages.ts` (MSG-04, MSG-38, MSG-39).

#### 2. DTO (tên trường đúng như FE)

- `ReceptionQueueResponse`:
  - `items: ReceptionQueueRow[]`
  - `counts: { total: int, waiting: int, absent: int }`
  - `rooms: { id: string, name: string }[]`
  - `generatedAt: ISO+07:00`
- `ReceptionQueueRow`:
  - `queueEntryId: string`
  - `queueNumber: int` (số trong phòng, cột "Số")
  - `position: int | null` (vị trí trong hàng Đang chờ của phòng; null khi khác)
  - `patientName: string`, `patientCode: string`
  - `roomId: string`, `roomName: string`
  - `queueType: EXAMINATION | RESULT_REVIEW`
  - `appointmentStartAt: ISO+07:00 | null` (null = vãng lai hoặc quay lại)
  - `priorityLabel: string | null`
  - `status: WAITING | BEING_SERVED | ABSENT`
  - `joinedAt: ISO+07:00` (giờ vào hàng)
- `ReceptionVisitSummary`:
  - `queueEntryId`, `queueNumber: int`, `roomName: string`
  - `patient: { fullName: string, patientCode: string, sex: MALE | FEMALE, age: int }`
  - `checkedInAt: ISO+07:00`, `appointmentStartAt: ISO+07:00 | null`
  - `visitType: FIRST_VISIT | FOLLOW_UP`
  - `responsibleDoctorName: string`, `priorityLabel: string | null`
  - `queueType: EXAMINATION | RESULT_REVIEW`
  - `queueStatus: WAITING | BEING_SERVED | ABSENT`
  - `peopleAhead: int | null` (null khi không đang chờ)
  - `visitStatus: WAITING_FOR_EXAMINATION | IN_EXAMINATION | AWAITING_CONCLUSION | COMPLETED | INCOMPLETE | CANCELLED_LEFT`

#### 3. Ghi chú nghiệp vụ cho BE

> Số BR theo phân tích và phase (R3). `docs/appendix-rules.md` đánh số khác. Đối chiếu theo nội dung. Ánh xạ: quyền xem hàng đợi = appendix BR-32; hàng đợi phải thấy đang chờ và đang phục vụ = appendix BR-34; chỉ Lễ tân đổi vị trí = appendix BR-35 (màn này không có thao tác đó).

1. **Quyền**: chỉ Lễ tân xem được hàng đợi. Sai quyền → 403 (FE hiện "không có quyền", không có Thử lại). Phase S05 ghi **MSG-38** cho 403; S04b ghi **MSG-40** cho 403 cùng loại quyền. BE chọn một mã thống nhất (xem §5).
2. **Thứ tự (BR-29)**: trong từng phòng, Đang chờ xếp theo nhóm ưu tiên trước, rồi giờ vào hàng. `position` là vị trí BE tính (1 = đến lượt gọi kế). FE không sắp lại; thứ tự của `items` là thứ tự BE trả.
3. **Trạng thái đưa ra**: chỉ `WAITING`, `BEING_SERVED`, `ABSENT`. Không trả `DONE` và `CLOSED`. Không trả hàng dịch vụ (`queueType` = `SERVICE`, thuộc kỹ thuật viên, UC46).
4. **Lọc**: `status` lặp; FE gửi: mặc định `WAITING`+`ABSENT`; "Tất cả" thêm `BEING_SERVED`. Lọc tên hoặc số thứ tự làm ở FE.
5. **Đếm (`counts`)**: đếm theo cả ngày, không theo `roomId` và `status`. `total` = bệnh nhân đã tiếp nhận hôm nay vào hàng khám hoặc đọc kết quả, kể cả đã xong (FE hiện "N đã tiếp nhận hôm nay · N đang chờ · N vắng mặt"). Cần xác nhận định nghĩa `total`.
6. **Phòng (`rooms`)**: danh sách phòng khám, không lọc theo `roomId` đang chọn.
7. **Vắng mặt (BR-28 của phân tích)**: `ABSENT` phải hiện (lễ tân trả lại ở UC32). Màn này không có nút đánh dấu vắng.
8. **Tóm tắt (`peopleAhead`)**: số bản ghi `WAITING` đứng trước trong cùng phòng, theo thứ tự BE (kể cả ưu tiên). Khi không đang chờ: null.
9. **Quay lại đọc kết quả** (`queueType` = `RESULT_REVIEW`): có `appointmentStartAt` = null. Mock coi cùng hàng phòng với khám (đếm `peopleAhead` chung phòng).
10. **Riêng tư (BR-12 của phân tích)**: tóm tắt không có tiền sử, dị ứng, chẩn đoán.
11. **Bản ghi đã rời hàng**: `GET queue/{id}` trả 410 khi bản ghi đã `DONE`/`CLOSED` hoặc là hàng dịch vụ; 404 khi không tồn tại. FE đóng Sheet và tải lại bảng khi 404/410.
12. **Tần suất**: FE tự tải lại mỗi 20 giây khi tab hiển thị, không realtime. BE không cần push.

#### 4. Contract URL giữa màn (query param)

- `/staff/reception-queue`: không có query param. Bộ lọc phòng, trạng thái và ô tìm nằm trong state của màn, không lên URL (tên bệnh nhân không được nằm trên đường dẫn).
- Link vào: từ màn Tiếp nhận (`/staff/check-in`, nút "Xem hàng đợi tiếp nhận") và từ mục menu "Hàng đợi tiếp nhận".
- Link ra: "Tiếp nhận bệnh nhân" → `/staff/check-in`.

#### 5. Câu hỏi mở cho BE

1. Mã 403 của queue và của tóm tắt: MSG-38 (phase S05) hay MSG-40 (S04b, quyền tiếp nhận)? Chọn một.
2. `counts.total`: xác nhận định nghĩa "đã tiếp nhận hôm nay, kể cả đã xong" (theo ví dụ "18 bệnh nhân · 11 đang chờ · 2 vắng mặt" của phase S05 thì đúng như vậy).
3. `peopleAhead` của tiếp nhận (S04b, `checkInPatientMock`) đếm mọi `WAITING` của phòng, chưa tính nhóm ưu tiên. Màn này dùng vị trí theo BR-29. Thống nhất một cách khi nối BE, để phiếu số và màn hàng đợi ra cùng con số.
4. `visitType` của khách vãng lai: BE xác định theo lịch sử (FE chưa có nguồn). Mock tạm dùng khám lần đầu.
5. `visitStatus` theo `queueType`/`status`: mock dùng `WAITING_FOR_EXAMINATION` (khám, đang chờ hoặc vắng), `IN_EXAMINATION` (khám, đang phục vụ), `AWAITING_CONCLUSION` (đọc kết quả). Xác nhận với luồng lượt khám (UC31–UC33).
6. Thứ tự giữa các nhóm ưu tiên (ví dụ cấp cứu trước người từ 75 tuổi): BE quyết theo cấu hình BR-31; FE chỉ hiển thị thứ tự trả về.

### S02 · S02 — Ghi chú API cho BE: lịch hẹn và đặt lịch của Lễ tân (UC10, UC21, UC22, UC24)

Nguồn code: `otcms/frontend/src/features/appointments/api/staffAppointmentApi.ts` (lớp API giả, chữ ký khớp REST). Quy ước chung như P03: JSON; ngày `YYYY-MM-DD`; thời điểm ISO `+07:00`; tiền số nguyên VND; enum `UPPER_SNAKE`; key `camelCase`. Lỗi: ProblemDetail + `code` (mã MSG) + `fieldErrors: [{ field, code }]`. FE đọc `code`.

#### 1. Endpoint FE gọi

| # | Method · URL | Query / body | Response (200/201) | Lỗi (HTTP · MSG) | FE định nghĩa | Ghi chú BE phải kiểm |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `GET /api/v1/appointments` | Query: `date` string `YYYY-MM-DD` (tùy, mặc định hôm nay giờ VN) · `doctorId` string (tùy) · `status` `AppointmentStatus` (tùy) · `q` string (tùy: tên hoặc SĐT) · `page` int (0-based) · `size` int (mặc định 20, tối đa 100) | `PageResponse<StaffAppointment>` = `{ items, page, size, totalItems, totalPages }` | 400 MSG-54 (`fieldErrors`: `date`, `status`) · 403 MSG-40 · 5xx MSG-04 | `listStaffAppointments(query, signal?)` · hook `useStaffAppointmentList` | Sắp `startAt` tăng dần (FE không gửi `sort`). Phạm vi theo vai trò (BR-19, BR-21). `q` là dữ liệu cá nhân: không ghi log, không đưa lên URL. `allowedActions` theo mục 3. |
| 2 | `GET /api/v1/appointments/booking-patients/{patientId}` | Path: `patientId` string (mã từ tìm bệnh nhân, ví dụ `pat-an`) | `BookingPatient` = `{ id: string, fullName: string, dateOfBirth: date, relationship: null }` | 403 MSG-29 (bị khóa đặt lịch hoặc ngoài quyền) · 404 MSG-29 (không có hồ sơ) · 5xx MSG-04 | `getBookingPatient(patientId, signal?)` · hook `useBookingPatient` | Trả đúng mã người đặt mà open-times/POST nhận: nếu người đó đã gắn tài khoản thì `id` là `LinkedProfile.id` (UUID) (P03 mục "Quy ước mã người khám"). `relationship` luôn `null` ở khu Lễ tân. |
| 3 | `GET /api/v1/appointments/doctors` **(mới, ngoài §7 phase-02)** | — | `{ items: { id: string, fullName: string, bookable: boolean }[] }` | 5xx MSG-04 | `listStaffDoctors(signal?)` · hook `useStaffDoctorLookup` | Dùng cho bộ lọc "Bác sĩ". Gồm mọi bác sĩ có lịch hoặc ca, không chỉ bác sĩ đang nhận đặt; `bookable` = đang nhận đặt mới. Có thể dùng chung nguồn với danh sách bác sĩ của admin. |
| 4 | `GET /api/v1/appointments/{id}/eligible-doctors` | Path: `id` string | `{ items: { id: string, fullName: string }[] }` (rỗng = AF1) | 404 MSG-29 · 409 MSG-32 (lịch không còn Đã đặt) · 5xx MSG-04 | `listStaffEligibleDoctors(appointmentId, signal?)` · qua `useEligibleDoctors` | Bác sĩ có ca đúng giờ, đang nhận đặt, không có lịch Đã đặt/Đã tiếp nhận khác đúng giờ đó; không gồm bác sĩ hiện tại (BR-20, BR-25). |
| 5 | `POST /api/v1/appointments/{id}/reassign` | Path `id` · body `{ doctorId: string }` | `StaffAppointment` (bản ghi sau khi chuyển) | 400 MSG-12 (`fieldErrors`: `doctorId`) · 404 MSG-29 · 409 MSG-32 · 409 MSG-24 (bác sĩ không còn nhận giờ này) · 5xx MSG-04 | `reassignStaffAppointmentDoctor(appointmentId, doctorId)` · qua `useReassignDoctorFlow` | BR-22: chỉ đổi bác sĩ, **giờ giữ nguyên**. Kiểm quyền, trạng thái và tính khả dụng của bác sĩ trong cùng giao dịch có khóa. Phòng (`roomName`) theo bác sĩ mới. |
| 6 | `POST /api/v1/appointments/{id}/notify-no-replacement` | Path `id` · không body | `StaffAppointment` | 404 MSG-29 · 409 MSG-32 · 5xx MSG-04 | `notifyStaffNoReplacement(appointmentId)` · qua `useReassignDoctorFlow` | Chỉ khi lịch Đã đặt và không có bác sĩ thay. Gửi một lần. Lịch vẫn Đã đặt. BE gửi thông báo cho bệnh nhân (nội dung MSG-89 của R3). |

##### Kiểu dữ liệu

**StaffAppointment** = `{ id: string; startAt: string /* ISO +07:00 */; patient: { id: string; patientCode: string /* BN-000123 */; fullName: string; dateOfBirth: string /* YYYY-MM-DD */; phone: string | null }; bookedFor: { kind: "SELF" } | { kind: "DEPENDENT"; accountHolderName: string }; service: { id: string; name: string }; isFollowUp: boolean; doctor: { id: string; fullName: string }; roomName: string; examFee: { amount: number /* VND */; status: "PAID" | "PENDING_PAYMENT" | "NOT_REQUIRED" }; status: "BOOKED" | "CHECKED_IN" | "CANCELLED" | "NO_SHOW"; allowedActions: ("checkIn" | "reassign" | "notifyNoReplacement")[] }`

Không có trường nào khác (không SĐT người đặt, không tiền sử, không dị ứng: BR-12). `phone` là `null` khi hồ sơ chưa có số.

**DoctorRef** = `{ id: string; fullName: string }`. **BookingPatient** = `{ id: string; fullName: string; dateOfBirth: string; relationship: null }`.

#### 2. Hợp đồng URL giữa màn (query param, FE)

| Từ | Đến | URL | Ghi chú |
| --- | --- | --- | --- |
| Danh sách Lễ tân | chính nó | `/staff/appointments?date=YYYY-MM-DD&doctorId=<id>&status=<STATUS>` | Đổi bộ lọc = replace. Thiếu `date` = hôm nay (giờ VN). `q` không lên URL. Trang đọc: `date`, `doctorId`, `status`. |
| Danh sách | Đặt lịch | `/staff/appointments/new` | Nút "Đặt lịch" và mục menu. |
| `/staff/appointments/new` | chính nó | `?patientId=<id>` | `id` từ `PatientLookup` (`PatientMatch.id`, định danh mờ, ví dụ `pat-an`). Không có tên, SĐT, ngày sinh trên URL. Mã không dùng được (404/403 MSG-29) thì FE xóa param. |
| Danh sách, Sheet, kết quả đặt | Tiếp nhận | `/staff/check-in?appointmentId=<appointment.id>` | Chỉ hiện khi `checkIn` ∈ `allowedActions` (danh sách) hoặc lịch vừa đặt là hôm nay (kết quả đặt). Phase 04 xử lý màn đích. |

#### 3. Quy tắc nghiệp vụ BE phải kiểm

- **BR-19, BR-21 (phạm vi):** Lễ tân chỉ thấy lịch và đặt cho người trong phạm vi phục vụ. Kiểm ở mọi endpoint, không chỉ ở danh sách.
- **BR-22:** Lễ tân không hủy, không đổi giờ. Trên khu Lễ tân chỉ có chuyển bác sĩ cùng giờ và báo bệnh nhân khi không có bác sĩ thay (lịch vẫn Đã đặt). FE không có nút hủy hay đổi giờ.
- **`allowedActions` (BE quyết định; kho giả tính như sau):**
  - `checkIn`: lịch `BOOKED` có ngày là hôm nay (giờ VN).
  - `reassign`: lịch `BOOKED`.
  - `notifyNoReplacement`: lịch `BOOKED`, không có bác sĩ thay (danh sách ứng viên rỗng), chưa báo.
  - `CHECKED_IN`, `CANCELLED`, `NO_SHOW`: không có thao tác (`[]`).
- **BR-20, BR-25:** ứng viên chuyển = bác sĩ có ca đúng giờ, đang nhận đặt, không bận giờ đó.
- **BR-14:** `getBookingPatient` chỉ cho người lễ tân đã tìm được (đi qua tìm kiếm trước).
- **Mã người khám:** `patientId` của `booking-patients/{id}` và `patientId` của open-times/POST phải là một mã cho cùng một người (P03).
- **Chuyển bác sĩ:** khóa lịch và bác sĩ trong một giao dịch. Hai lễ tân cùng chuyển một lịch thì người sau nhận 409 MSG-32 hoặc MSG-24.

#### 4. Câu hỏi mở cho BE / nhóm

1. `GET /api/v1/appointments/doctors` là endpoint mới (không có trong §7 phase-02). Dùng chung nguồn danh sách bác sĩ của admin, hay tạo mới?
2. `BookingPatient` không có mã BN (`patientCode`), nên khối đặt lịch của Lễ tân chỉ hiện tên và ngày sinh. Phase wireframe có hiện BN-000123 ở đây. Nếu cần, thêm `patientCode` vào `BookingPatient` (kiểu P03).
3. Kho giả: chuyển bác sĩ và "báo bệnh nhân" chỉ lưu trong khu Lễ tân (`staffAppointmentMockStore.ts`). Kho lịch chung `bookingMockStore.ts` chưa có lệnh ghi cập nhật, nên lịch đã chuyển chưa hiện ở khu bệnh nhân và chưa chặn đặt trùng bác sĩ mới ở P03. Cần lệnh cập nhật trong kho chung (chủ P03) để hai khu nhất quán. Trên BE thật không có vấn đề này.
4. Ngày sinh của An lệch giữa hai mock: `linkedProfilesMockStore` là 1992-07-21, `patientMockData` là 1958-03-12. Khối đặt lịch của Lễ tân hiện 1992 (theo hồ sơ gắn), còn tìm kiếm ra 1958. Đã nêu ở P03 và S04a, chưa sửa.
5. Seed hôm nay có 4 lịch (P03). Phase-02 §8 yêu cầu ≥ 25 dòng (2 trang) và một lịch AF1 (không bác sĩ thay). Seed chung không thuộc phạm vi S02 nên chưa bổ sung. Test AF1 tạo lịch qua `createAppointment` rồi kiểm.
6. MSG-89 trong `appMessages.ts` ("Chuyển lịch hẹn không thành công…") không khớp R3, trong đó MSG-89 là thông báo BE gửi cho bệnh nhân khi không có bác sĩ thay. Khu Lễ tân không dùng mã này. Chưa sửa vì bảng MSG chỉ thêm dòng mới.
7. `StaffAppointment.patient.phone` là `string | null` (phase ghi `string`), vì kho có hồ sơ chưa có SĐT.
8. Khi `checkIn` vẫn còn ở lịch `BOOKED` đã quá giờ hôm nay: BE quyết định có khóa không.

### I02b · API notes — I02b (thống nhất mock dùng chung + sửa nhỏ sau nghiệm thu)

Không thêm endpoint. Chỉ đổi hành vi của endpoint đã có trong lớp API giả, để hai khu (Lễ tân và Bệnh nhân)
đọc cùng một kho lịch như BE một DB. Kho giả chỉ dùng trong lớp API giả, xóa khi nối BE.

#### 1. Endpoint bị ảnh hưởng (hợp đồng BE phải giữ)

| # | Method · URL | Thay đổi ở FE/mock (I02b) | Ghi chú BE phải kiểm |
| --- | --- | --- | --- |
| 1 | `POST /api/v1/appointments/{id}/reassign` (S02) · body `{ doctorId: string }` → `StaffAppointment` | Bác sĩ và phòng mới được ghi vào lịch chung (`bookingMockStore`). Khu bệnh nhân (`GET /api/v1/appointments`, `GET /api/v1/appointments/{id}`) thấy bác sĩ và phòng mới ngay; giờ trống (`GET /api/v1/appointments/open-times`) bỏ bác sĩ mới và mở lại bác sĩ cũ ở giờ đó. Lỗi không đổi: 400 MSG-12, 404 MSG-29, 409 MSG-32, 409 MSG-24. | Một giao dịch có khóa: kiểm bác sĩ mới còn ca và trống đúng giờ, đổi `doctor` + `roomName` (phòng theo bác sĩ mới), giờ giữ nguyên (BR-22, BR-25). Khu bệnh nhân và giờ trống đọc cùng một bản ghi. |
| 2 | `GET /api/v1/appointments/doctors` (S02) → `{ items: { id, fullName, bookable }[] }` | `bookable` = đang nhận đặt mới **hôm nay** (có ca, không vắng). Trước đây luôn `true`. | BE tính cờ theo lịch làm việc và tình trạng vắng của ngày hôm nay, cùng nguồn với giờ trống. |
| 3 | `GET /api/v1/appointments/open-times` (P03, đọc bởi P03 và đặt lịch Lễ tân) | Bác sĩ vắng hôm nay (mock: BS. Lê Quang Minh) không nằm trong bất kỳ giờ trống nào của hôm nay. | Vắng cả ngày → không là bác sĩ nhận đặt, không tính vào giờ trống. |
| 4 | `GET /api/v1/appointments/{id}/eligible-doctors` (S02) | Không đổi hình trả. Lịch 08:00 hôm nay của bác sĩ vắng (`apt-1041`) trả rỗng: AF1. | Rỗng khi không bác sĩ nào có ca cùng giờ, còn trống, và không phải bác sĩ hiện tại (BR-20, BR-25). |
| 5 | `GET /api/v1/appointments?date=` (S02) và `GET /api/v1/appointments` (P04) | Không đổi hình. Mock thêm 2 dòng hôm nay 08:00 (`apt-1041` AF1, `apt-1042`). | Chỉ là dữ liệu mẫu, không phải hợp đồng. |

#### 3. Quy tắc nghiệp vụ BE phải kiểm (mới rõ hơn)

- **Chuyển bác sĩ là thay đổi lịch chung:** không có "bản sao" riêng ở khu Lễ tân. Sau chuyển, lịch hiện đúng bác sĩ mới cho cả hai khu và giờ trống phản ánh ngay.
- **Bác sĩ vắng:** một bác sĩ vắng cả ngày không nhận đặt mới và không là ứng viên chuyển của ngày đó. Lịch đã có của bác sĩ vắng vẫn còn; Lễ tân chuyển được nếu có ứng viên, nếu không thì chỉ còn "Báo cho bệnh nhân" (AF1).
- **Nhóm ưu tiên ≥ 75 tuổi:** FE chỉ gửi mã nhóm. BE quyết theo ngày sinh và cấu hình (BR-31); demo dùng Nguyễn Thị Lan.

#### 4. Câu hỏi mở cho BE

1. Chuyển bác sĩ lúc đang có ca khám (`CHECKED_IN`): không có trong mock (chỉ chuyển lịch Đã đặt). BE quyết có cho phép không.
2. Cờ `bookable` của bác sĩ vắng: BE trả `false` hay bỏ hẳn bác sĩ khỏi danh sách bộ lọc? Mock trả `false`.
3. Hai dòng AF1 hôm nay (Minh 08:00 và Tuấn 08:00) không có ai thay: BE gửi thông báo "không có bác sĩ thay" (MSG-89 theo R3) một lần; mock chỉ ghi cờ trong khu Lễ tân (`staffAppointmentMockStore`). Cần BE xác nhận nơi lưu cờ đã báo.
4. Màn Tiếp nhận (S04b, `checkInMock*`) và danh sách lịch Lễ tân (S02, `bookingMockStore`) còn hai kho lịch mock tách nhau: lịch hôm nay của hai màn không trùng nhau. Khi nối BE thì một nguồn.
