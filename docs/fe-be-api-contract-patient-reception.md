# Hợp đồng API FE ↔ BE — khu Bệnh nhân, Lễ tân, trang công khai

Cập nhật: 25/10/2026 (gộp khu Lễ tân `/reception` từ nhánh `thien-quang`) · Người tổng hợp: NghiaDT (điều phối FE) · Nguồn: code FE `frontend/src` + ghi chú từng phase (`plans/261024-1610-fe-patient-reception-implementation/api-notes/`).

FE đã dựng xong khu `/portal` (bệnh nhân), `/reception` (Lễ tân: hồ sơ, lịch hẹn, tiếp nhận, hàng đợi, thu phí, tra cứu) và các trang công khai trên **lớp API giả**. Mỗi hàm trong `features/<feature>/api/*Api.ts` đã đúng URL, method, DTO và hình lỗi mà BE cần trả. Tài liệu này là thứ BE làm theo để khi nối chỉ đổi lớp api, không sửa màn hình.

Nguồn nghiệp vụ: R3 SRS bản Drive (số BR/MSG theo bản Drive; `docs/appendix-rules.md` trong repo là bản cũ 19/10, đánh số BR lệch) và đặc tả tách tài khoản/hồ sơ 23/10.

Điều chỉnh được người phụ trách phần lễ tân chấp thuận ngày 27/10/2026: tra cứu hồ sơ bằng tên, SĐT **hoặc** mã bệnh nhân; ngày sinh chỉ là bộ lọc tùy chọn. Tìm kiếm trả về các ứng viên để đối chiếu, không tự xác nhận danh tính. Cho mở form tạo hồ sơ ngay, nhưng vẫn kiểm tra thông tin, xác nhận danh tính và chống trùng trước khi lưu. Quy tắc này thay phần tra cứu cũ của UC10/BR-14 tại quầy; xem `docs/requirements/UC10-create-walk-in-patient-profile.md`. Quyền truy cập và liên kết hồ sơ trong khu bệnh nhân giữ nguyên.

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

  `fieldErrors` → hiện dưới ô; còn lại → toast. 400 dữ liệu sai · 403 không đủ quyền (MSG-40) · 404 không còn hoặc không thuộc tài khoản (MSG-41, không lộ là có tồn tại) · 409 xung đột trạng thái/trùng · 5xx MSG-04.
- **Phạm vi dữ liệu**: tài nguyên của chính người đăng nhập dùng `/api/v1/me/...`; dữ liệu nghiệp vụ dùng `/api/v1/<nghiệp vụ>` và **BE lọc theo tài khoản** (hồ sơ đã gắn qua liên kết, gồm "Bản thân"). FE không gửi id người dùng. Lọc một người khám dùng query `profileId=`.
- **Xác thực**: FE gắn `Authorization: Bearer <access token do backend cấp>` qua `apiRequest`, token lấy từ `lib/auth/authSession.ts` (`lib/api/client.ts`). Trang công khai `/api/v1/public/**` phải gọi được **khi chưa đăng nhập** → BE mở không cần token, FE cần thêm biến thể `apiRequest` không gắn token khi nối.
- Vai trò ứng dụng nằm trong dữ liệu tài khoản (`GET /api/v1/me` trả `kind`/`role`); không dùng claim `role` của Supabase.

## 2. Cách nối BE (FE làm khi BE xong từng nhóm)

1. Trong file `api/*Api.ts` của feature, đổi cờ `USE_MOCK` (hoặc nhánh mock) sang gọi `apiRequest` — nhánh thật đã viết sẵn ở hầu hết hàm.
2. Xóa module chỉ phục vụ mock (đều có comment "xóa khi nối BE"): `lib/mock/*` (registry phí khám dùng chung), adapter lệnh `features/patient-reception/api/receptionApi.ts` + `receptionSeed.ts` (xem R00), `lib/session/mockCurrentActor.ts`, `lib/session/mockSignedInKind.ts`, `features/appointments/components/BookingMockStoreBootstrap.tsx`, `features/notifications/components/PatientNotificationMockBootstrap.tsx`, các file `*MockData.ts`/`*MockStore.ts`.
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
| POST | `/api/v1/appointments` | Đặt lịch (bệnh nhân và Lễ tân) | P03, I01, R00 |
| GET | `/api/v1/appointments` | Danh sách lịch: khu bệnh nhân (`profileId`, `tab`) và Lễ tân (`date`, `doctorId`, `status`, `patientId`, `q`) | P04, R00 |
| GET | `/api/v1/appointments/{id}` | Chi tiết lịch hẹn | P04 |
| POST | `/api/v1/appointments/{id}/cancel` | Bệnh nhân hủy trước check-in | P04 |

### 3.4 Khu Lễ tân `/reception` (UC04, UC05, UC10, UC11, UC13 AF2, UC20–UC22, UC24, UC25, UC29–UC34, UC41, UC52–UC58, UC63, UC64, UC71)
FE hiện gọi **một adapter lệnh** (`features/patient-reception/api/receptionApi.ts`: `loadReception()` + `executeReception(command, version)`). Khi nối, mỗi lệnh thành một endpoint dưới đây và `loadReception` tách thành các GET theo màn. Chi tiết DTO, lỗi, luật kiểm: phụ lục **R00**.

| Method | URL | Lệnh FE / màn | UC |
| --- | --- | --- | --- |
| GET | `/api/v1/patients/lookup?q=&dob=&page=&size=` | Lễ tân tìm bằng tên, SĐT hoặc mã BN; ngày sinh tùy chọn, không tự chọn hồ sơ | UC10, BR-14 (điều chỉnh 27/10) |
| GET | `/api/v1/patients/{id}` | Chi tiết hồ sơ hành chính (không tiền sử, dị ứng) | UC11 |
| POST | `/api/v1/patients` | `save-patient` (tạo, không tạo tài khoản) | UC10 |
| PATCH | `/api/v1/patients/{id}` | `save-patient` (sửa) | UC11 |
| GET | `/api/v1/patients/{id}/care-links?status=PENDING` | Yêu cầu liên kết chờ xác minh tại quầy | UC13 AF2 |
| POST | `/api/v1/care-links/{id}/confirm` | `confirm-link` | UC13 AF2 |
| GET | `/api/v1/appointments?date=&doctorId=&status=&patientId=&q=` | Danh sách `/reception/appointments` (dùng chung URL với khu bệnh nhân, BE lọc theo vai trò) | UC24 |
| GET | `/api/v1/appointments/open-times?date=&serviceId=&doctorId=` | Giờ trống, bác sĩ có ca (dùng chung P03) | UC20 |
| POST | `/api/v1/appointments` | `book` (dùng chung P03; Lễ tân gửi `patientId`) | UC21 |
| GET | `/api/v1/appointments/{id}/eligible-doctors` | Bác sĩ thay thế cùng giờ | UC22 |
| POST | `/api/v1/appointments/{id}/reassign` | `reassign` | UC22 |
| POST | `/api/v1/appointments/{id}/notify-no-replacement` | `notify-unavailable` (AF1) | UC22 |
| GET | `/api/v1/reception/check-in-candidates?date=` | Màn Tiếp nhận hôm nay `/reception` | UC29 |
| GET | `/api/v1/reception/priority-groups` | Nhóm ưu tiên (cấu hình) | UC29, BR-31 |
| POST | `/api/v1/reception/walk-in-fees` | `prepare-walk-in` (lập phí khám khách trực tiếp) | UC29, UC54 |
| POST | `/api/v1/reception/check-ins` | `check-in` → số thứ tự, phòng | UC29 |
| GET | `/api/v1/queue-entries?date=&roomId=&status=` | Danh sách đã vào hàng đợi | UC30 |
| GET | `/api/v1/rooms/{roomId}/queue?kind=EXAM\|REVIEW\|SERVICE` | Hàng đợi một phòng `/reception/queues` | UC32 |
| POST | `/api/v1/rooms/{roomId}/queue/call-next` | `call-next` | UC32 |
| POST | `/api/v1/queue-entries/{id}/arrive` · `/absent` · `/return` | `queue-action` | UC32 |
| POST | `/api/v1/queue-entries/{id}/reorder` | `reorder` | UC33 |
| POST | `/api/v1/queue-entries/{id}/move-room` | `move-room` (chỉ hàng đợi dịch vụ) | UC31 |
| GET | `/api/v1/queue-entries/changes?date=` | Lịch sử điều phối (lý do, trước/sau) | UC31, UC33 |
| GET | `/api/v1/visits?date=&status=&q=` | `/reception/visits` | UC34 |
| GET | `/api/v1/visits/{id}/progress` | Tiến trình lượt (dùng chung P05, không trả nội dung y khoa) | UC34 |
| POST | `/api/v1/visits/{id}/transfer-doctor` | `transfer-doctor` (bác sĩ phụ trách vắng) | UC31 AF1 |
| GET | `/api/v1/service-orders?date=&status=&q=` | `/reception/orders` | UC52 |
| GET | `/api/v1/payments/unpaid-items?patientId=&contextId=` | `/reception/payments` | UC57 |
| POST | `/api/v1/payments/cash` | `pay-cash` | UC54 |
| POST | `/api/v1/payments/counter-qr` | `start-qr` (VNPay QR tại quầy) | UC54 |
| GET | `/api/v1/payments/counter-qr/{id}` | Trạng thái QR (poll) | UC54 |
| POST | `/api/v1/payments/vnpay/ipn` | VNPay gọi BE (thay nút `demo-gateway` của mock) | UC54, UC55 |
| GET | `/api/v1/payments?from=&to=&q=&method=` | `/reception/payment-history` (dùng chung P06, phạm vi toàn phòng khám) | UC56 |
| GET | `/api/v1/payments/{id}` | Biên lai, in | UC53, UC58 |
| GET | `/api/v1/result-sheets?q=&date=` · `/api/v1/result-sheets/{id}` | `/reception/results` (chỉ xem, in) | UC41 |
| GET | `/api/v1/rooms?available=` | `/reception/rooms` (kèm số người chờ) | UC64 |
| GET | `/api/v1/services?active=true` | `/reception/services` | UC71 |
| GET | `/api/v1/me/shifts?from=&to=` | `/reception/schedule` | UC25 |
| GET · POST | `/api/v1/me/notifications` · `/{id}/read` | `read-notice` (dùng chung P08) | UC63 |
| GET · PUT/DELETE · POST | `/api/v1/me` · `/api/v1/me/avatar` · `/api/v1/me/password` | `/reception/account`, `avatar` (dùng chung P01) | UC03–UC05 |

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
- **Tiếp nhận, hàng đợi, thu phí tại quầy**: xem R00 §4 (một lượt hoạt động mỗi bệnh nhân, quay lại ≤ 7 ngày không thu phí khám, một phòng một người đang gọi, đổi thứ tự phải có lý do, chống thu trùng và khóa thu khi VNPay chưa rõ kết quả).
- **Hồ sơ**: lễ tân tra cứu theo điều chỉnh UC10/BR-14 ngày 27/10: tên, SĐT hoặc mã BN; ngày sinh tùy chọn để thu hẹp danh sách trong phạm vi công việc. Đối chiếu họ tên, ngày sinh và SĐT trước thao tác nghiệp vụ; không tự chọn ứng viên đầu tiên. Khu bệnh nhân chỉ truy cập hồ sơ đã được gắn; gắn online chỉ khi email trùng email đã xác minh; tối đa một "Bản thân" mỗi tài khoản và mỗi hồ sơ (MSG-21).
- **Kết quả, ảnh**: chỉ khối được phép hiện mới có trong `visibleBlocks`; `viewerUrl` chỉ cấp khi study đã phát hành, kèm token kiểm theo study.

## 5. Câu hỏi mở cho BE / nhóm (gom từ các phase)

1. ~~Mã 403 khu Lễ tân~~: đã chốt MSG-40 theo UC31–UC34 (MSG-38 chỉ khi không xem được danh sách chờ UC30).
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

### R00 · Khu Lễ tân `/reception` (gộp nhánh `thien-quang` 25/10/2026)

Nguồn: `features/patient-reception` (types.ts, api/receptionApi.ts, schema/receptionRules.ts, schema/receptionMessages.ts) và `docs/receptionist-ui.md`. Mã lỗi đã khớp phần Exceptions của từng UC trong R3 Drive (25/10/2026). Bản `/staff/*` cũ (S00, S02, S04a, S04b, S05, I02b) đã gỡ khi gộp; khu bệnh nhân không đổi.

#### 1. Cách FE gọi hiện tại và khi nối

- `loadReception()` trả cả `ReceptionState` (ảnh chụp toàn bộ dữ liệu mẫu). Khi nối, FE thay bằng các GET ở bảng 3.4, mỗi màn chỉ tải phần mình cần.
- `executeReception(command, expectedVersion)` chạy một lệnh nguyên tử: kiểm lại trạng thái sau độ trễ, sai thì không ghi gì. `version` là số phiên bản của **cả kho mock**; BE dùng phiên bản theo bản ghi (`version` trong DTO hoặc header `If-Match`), sai phiên bản → 409 MSG-42 rồi FE tải lại.
- Lỗi mock là `ApiError` (`lib/api/apiError.ts`) đúng hình ProblemDetail + `code` ở §1, FE hiển thị bằng `messageFromError`. Thông báo thành công lấy theo lệnh trong `schema/receptionMessages.ts`: BE **không** cần trả câu chữ, chỉ cần trả bản ghi mới (và số thứ tự/phòng khi tiếp nhận).
- Hai lệnh chỉ có ở mock: `demo-gateway` (giả lập VNPay gọi về, thay bằng IPN thật) và mật khẩu mẫu `Demo12345` của `changeReceptionPassword` (thay bằng `POST /api/v1/me/password`).

#### 2. DTO (tên trường như FE; enum gửi lên BE dùng `UPPER_SNAKE` theo §1, FE đổi khi nối)

```ts
Patient        { id, name, dob, sex: "Nam"|"Nữ"|"Khác", phone, address, nationalId, email }   // không có tiền sử, dị ứng
PatientInput   = Patient trừ id
CareLink       { id, patientId, accountName, accountEmail, relationship, status: PENDING|CONFIRMED, evidence? }
Appointment    { id, patientId, doctorId, serviceId, date, time: "HH:mm", status: BOOKED|CHECKED_IN|CANCELLED|NO_SHOW, notice? }
Visit          { id, patientId, doctorId, appointmentId?, firstCheckIn: date, status: WAITING|EXAMINING|AWAITING|COMPLETED|INCOMPLETE|LEFT, priority, followUp }
QueueEntry     { id, visitId, roomId, kind: EXAM|SERVICE|REVIEW, number: "101-003", date, joinedAt, status: WAITING|SERVING|ABSENT|DONE|CLOSED,
                 priority, position, calledAt?, callerId? }   // "đang gọi" = WAITING + calledAt, không thêm trạng thái ngoài SRS
Order          { id, visitId, serviceId, roomId, round, status: PENDING|PAID|RESULTS|CANCELLED, resultAt?, cancellationReason? }
Charge         { id, patientId, contextId, serviceId, orderId?, amount, status: PENDING|PAID|CANCELLED, createdAt, paymentId? }
                 // contextId = id lịch hẹn, id lượt khám, hoặc phí khám khách trực tiếp trước khi có lượt
Payment        { id, patientId, patientName, contextId, items: { chargeId, name, amount }[], amount, method: CASH|VNPAY,
                 cashReceived?, createdAt, receivedBy, reference? }   // patientName chụp lúc thu, biên lai bất biến
QrTransaction  { id, chargeIds, amount, status: PENDING|SUCCEEDED|FAILED, expiresAt, needsReconciliation, paymentId? }
ResultSheet    { id, visitId, version, replaced, issuedAt, diagnosis, conclusion, advice, doctorName }
Room           { id, name, category: EXAM|IMAGING|LAB, available, serviceIds, waitingCount }
Doctor         { id, name, roomId, absent }      Shift { id, staffId, date, start, end, roomId }
Service        { id, name, category, price, active }
Priority       NORMAL|EMERGENCY|CHILD|PREGNANT|DISABILITY|ELDERLY|MERIT
QueueChange    { id, at, actor, action, reason, before, after }
```

Danh sách bản ghi có thao tác nên trả `allowedActions` (§1) thay cho việc FE tự suy như mock (ví dụ `["checkIn","collectFee"]`, `["call","markAbsent","return","reorder","moveRoom"]`).

#### 3. Endpoint, body, lỗi

| Endpoint | Body / query | Trả | Lỗi (status · MSG) |
| --- | --- | --- | --- |
| `GET /patients/lookup` | `q` bắt buộc (tên/SĐT/mã BN, có chữ hoặc số); `dob` tùy chọn; `page`, `size` theo §1 | trang `{ items: { id, name, dob, phone, sex }[], page, size, totalItems, totalPages }`; nhiều ứng viên là kết quả hợp lệ, không phải lỗi | 400 MSG-54 (q rỗng/không có chữ hoặc số, ngày sinh không hợp lệ) · 403 MSG-40 (không có quyền tra cứu tại quầy) |
| `POST /patients` | `PatientInput` + `identityConfirmed: true` | 201 `Patient` | 400 MSG-17 (+`fieldErrors`) · trùng họ tên + SĐT + ngày sinh hoặc trùng số định danh → 409 MSG-18 |
| `PATCH /patients/{id}` | như trên | 200 `Patient` | 400 MSG-17 · 404 MSG-19 · 409 MSG-18 |
| `POST /care-links/{id}/confirm` | `{ evidence (≥5 ký tự), identityConfirmed: true }` | 200 `CareLink` | 400 MSG-17 · 404/409 MSG-21 (không còn chờ, hoặc hồ sơ đã đủ email và định danh) |
| `POST /appointments` | `{ patientId, serviceId, date, time, doctorId? }` | 201 `Appointment` + tạo `Charge` phí khám PENDING | 404/409 MSG-29 (bệnh nhân/dịch vụ không đặt được, trùng giờ của chính bệnh nhân) · 400 MSG-31 (giờ đã qua) · 409 MSG-30 (hết chỗ) |
| `POST /appointments/{id}/reassign` | `{ doctorId }` | 200 `Appointment` (giờ giữ nguyên) | 404 MSG-29 · 409 MSG-32 (không còn Booked, bác sĩ hiện tại không vắng) · 409 MSG-24 (bác sĩ mới không trống cùng giờ) |
| `POST /appointments/{id}/notify-no-replacement` | — | 200 `Appointment` (`notice`) | 404 MSG-29 · 409 MSG-32 · còn bác sĩ thay được → 409 MSG-42 |
| `POST /reception/walk-in-fees` | `{ patientId, serviceId }` | 201 `Charge` | 404 MSG-18 · 400 MSG-39 (không phải dịch vụ khám đang cung cấp) · 409 MSG-37 (đang có lượt hoạt động) · 409 MSG-42 (đã lập phí) · 409 MSG-41 (không có bác sĩ trực) |
| `POST /reception/check-ins` | `{ patientId, appointmentId?, returnVisitId?, priority, present: true }` | 201 `{ visit, entries: QueueEntry[] }` | 400 MSG-39 (chưa xác nhận có mặt, nhóm ưu tiên sai) · 404 MSG-18 · 409 MSG-37 (lịch không hợp lệ hôm nay, chưa trả phí khám, trùng lượt, quá hạn 7 ngày, còn chỉ định chưa thu) · 409 MSG-41 (bác sĩ/phòng không sẵn sàng) |
| `POST /rooms/{roomId}/queue/call-next` | `{ kind: EXAM\|REVIEW }` | 200 `QueueEntry` | 409 MSG-42 (không phải phòng khám có bác sĩ trực, phòng đang gọi/đang khám, bệnh nhân đang ở phòng khác) · 404 MSG-41 (hết người chờ) |
| `POST /queue-entries/{id}/arrive\|absent\|return` | — | 200 `QueueEntry` | 403 MSG-40 (không phải lễ tân đã gọi, khác ngày, hàng đợi dịch vụ) · 409 MSG-42 |
| `POST /queue-entries/{id}/reorder` | `{ position, priority, reason (≥5) }` | 200 `QueueEntry[]` của phòng | 400 MSG-39 · 409 MSG-42 (đã được gọi, chen trước người đang gọi) |
| `POST /queue-entries/{id}/move-room` | `{ roomId, reason (≥5) }` | 200 `QueueEntry` mới | 400 MSG-39 · 409 MSG-42 (đã gọi, phòng khám, phòng mới không làm đủ dịch vụ đang chờ) |
| `POST /visits/{id}/transfer-doctor` | `{ doctorId, reason (≥5) }` | 200 `Visit` | 400 MSG-39 · 409 MSG-42 (bác sĩ cũ không vắng, đang phục vụ) · 409 MSG-41 (bác sĩ mới không trực) |
| `POST /payments/cash` | `{ chargeIds, cashReceived, confirmed: true }` | 201 `Payment` | 400 MSG-49 (rỗng, trùng, khác bệnh nhân/ngữ cảnh) · 400 MSG-39 (tiền nhận < tổng, chưa xác nhận) · 404 MSG-50 · 409 MSG-49 (không còn PENDING) · 409 MSG-52 (đang chờ VNPay/cần đối soát) · 409 MSG-41 (chưa có phòng cho dịch vụ đã thu) |
| `POST /payments/counter-qr` | `{ chargeIds }` | 201 `QrTransaction` + payload QR | như `cash` (trừ MSG-39) |
| `GET /payments/counter-qr/{id}` | — | `QrTransaction` | 404 MSG-50 · từ chối → `FAILED` (FE hiện MSG-53) |
| `GET /payments`, `GET /payments/{id}` | `from`, `to`, `q`, `method` | trang `Payment` / `Payment` | 400 MSG-54 · 404 MSG-50 |
| `POST /me/notifications/{id}/read` | — | 204 | 404 MSG-50 |
| `PUT /me/avatar` · `POST /me/password` | multipart ảnh JPG/PNG/WebP ≤ 2 MB · `{ currentPassword, newPassword }` | 200 / 204 | 400 MSG-08 |
| GET chỉ đọc khác (`visits`, `service-orders`, `result-sheets`, `rooms`, `services`, `me/shifts`, `queue-entries`) | lọc theo bảng 3.4 | trang hoặc `{ items }` | 400 MSG-54 · 403 MSG-40 · 404 MSG-41/MSG-50 · 5xx MSG-04 |

Thông báo thành công FE tự hiển thị: MSG-90 (hồ sơ, xác minh liên kết), MSG-26, MSG-27 `{doctor}`, MSG-94 `{number}` `{room}`, MSG-43 (mọi thao tác hàng đợi/chuyển phòng/chuyển bác sĩ), MSG-48 `{amount}`, MSG-52 (tạo QR, chưa rõ kết quả), MSG-53, MSG-60, MSG-05, MSG-07.

#### 4. Luật BE bắt buộc kiểm (mock đã làm, BE phải làm lại phía server)

- **Hồ sơ**: `q` tìm tên không phân biệt hoa/thường, dấu tiếng Việt (các từ có thể không liên tiếp); SĐT chuẩn hóa `+84` thành `0` và bỏ dấu cách/dấu phân tách, cho tìm một phần; mã BN không phân biệt hoa/thường, dấu cách và dấu gạch nối. `dob` nếu có là điều kiện bổ sung chính xác. Query rỗng không trả toàn bộ kho hồ sơ. Chỉ lễ tân được tìm trong phạm vi công việc; danh sách chỉ trả thông tin hành chính để chọn, GET chi tiết phải kiểm tra quyền lại. Nhiều ứng viên cần đối chiếu, không tự liên kết/chọn hộ. Cho mở form tạo hồ sơ mà chưa tìm kiếm; POST vẫn bắt buộc `identityConfirmed` và kiểm tra trùng trước khi ghi. Hồ sơ mới không tạo tài khoản. Dữ liệu trả cho Lễ tân không có tiền sử, dị ứng. Xác minh liên kết chỉ khi yêu cầu đang chờ và hồ sơ thiếu email hoặc số định danh; lưu căn cứ giấy tờ vào lịch sử.

FE prototype hiện lọc/phân trang trên dữ liệu giả đã nạp. Khi nối API thật, dùng lookup có phân trang phía server theo hợp đồng trên, rồi gọi `GET /patients/{id}` khi mở hồ sơ; không tải toàn bộ kho bệnh nhân xuống trình duyệt. Endpoint này không cung cấp tìm kiếm hồ sơ tùy ý cho Patient/Guest.
- **Lịch hẹn**: giờ trống theo ca, bước 20 phút; chỉ dịch vụ khám đang cung cấp; khóa khung giờ khi ghi. Lễ tân không đổi giờ, không hủy lịch (BR-22). Đổi bác sĩ chỉ khi bác sĩ hiện tại vắng, giữ nguyên giờ, gửi MSG-96/MSG-27; không có người thay thì giữ lịch Booked và gửi MSG-89. Lịch Booked quá giờ kết thúc ca → NO_SHOW (không hủy, không hoàn phí).
- **Tiếp nhận (UC29)**: bệnh nhân có mặt và phí khám đã trả (lịch hẹn hoặc phí khách trực tiếp) trước khi tạo lượt; một bệnh nhân một lượt hoạt động. Khách trực tiếp không tự tạo lịch hẹn; phí khám gắn sang lượt mới. Quay lại trong ngày 1–7 của lượt đang chờ kết luận: tiếp tục cùng lượt, không thu lại phí khám; ngày 8 lượt chuyển INCOMPLETE, hủy chỉ định chưa có kết quả, không hoàn tiền. Bác sĩ phụ trách vắng thì chặn, phải chuyển bác sĩ trước.
- **Hàng đợi (UC30–UC33, BR-29)**: sắp theo nhóm ưu tiên cấu hình (cấp cứu trước, các nhóm ưu tiên ngang nhau, rồi thông thường) và thời điểm vào; số thứ tự theo phòng theo ngày. Một phòng chỉ một người đang gọi/đang khám; một bệnh nhân không bị gọi ở hai phòng cùng lúc. Chỉ lễ tân đã gọi mới ghi vắng/quay lại trong cùng ngày; quay lại xếp theo thời điểm quay lại. Lễ tân chỉ gọi ở phòng khám; phòng dịch vụ do kỹ thuật viên gọi. Đổi vị trí và chuyển phòng phải có lý do, lưu trước/sau (`QueueChange`).
- **Chuyển phòng/bác sĩ (UC31, BR-34)**: chuyển phòng dịch vụ chỉ sang phòng làm được mọi dịch vụ đang chờ của bệnh nhân. Chuyển phòng khám = chuyển bác sĩ phụ trách sang bác sĩ đang trực, chỉ khi bác sĩ cũ vắng; gửi MSG-88 cho bác sĩ mới.
- **Thu phí (UC54, BR-48–BR-51)**: một giao dịch cho các khoản cùng bệnh nhân và cùng lịch/lượt, trả trọn từng khoản, giá theo khoản; chống thu trùng; tiền mặt phải xác nhận và ≥ tổng, tính tiền thừa. Một payment một biên lai bất biến mang tên bệnh nhân lúc thu. Thu xong chỉ định: đưa bệnh nhân vào hàng đợi phòng phù hợp ít người chờ (dùng lại hàng đợi đang có nếu phòng đó làm được), không tự quay về bác sĩ khi mới xong một dịch vụ.
- **VNPay tại quầy**: PENDING không đánh dấu PAID; IPN thành công idempotent và phải xác minh chữ ký; quá hạn 15 phút hoặc chưa rõ kết quả → khóa thu lại (`needsReconciliation`) đến khi đối soát; giao dịch bị từ chối phải tạo yêu cầu mới.
- **Giới hạn vai trò**: Lễ tân không đóng lượt, không sửa kết quả, không ra chỉ định, không kê đơn, không sửa ca/phòng/danh mục, chỉ đổi ảnh đại diện (thông tin liên hệ nhân viên chỉ đọc theo UC04).

#### 5. URL giữa các màn (query param)

| Màn | URL | Ghi chú |
| --- | --- | --- |
| Tiếp nhận | `/reception?patientId=<id>` | Mở hộp tiếp nhận cho bệnh nhân; từ hồ sơ, lịch hẹn, tiến trình lượt |
| Lịch hẹn | `/reception/appointments?patientId=<id>` | Lọc lịch của bệnh nhân (bỏ lọc ngày) |
| Thu phí | `/reception/payments?patientId=<id>&contextId=<lịch hẹn \| lượt>` | Chọn sẵn nhóm khoản thu |
| Hàng đợi | `/reception/queues?roomId=<id>` | Thiếu hoặc sai thì mở phòng đầu tiên |
| Tiến trình | `/reception/visits?visitId=<id>` | Mở chi tiết lượt |

Chỉ chứa id, không chứa tên hay SĐT.

#### 6. Câu hỏi mở cho BE / nhóm

1. Phiên bản bản ghi để chống ghi đè (mock dùng một `version` cho cả kho): BE trả `version` trong DTO hay dùng `ETag`/`If-Match`?
2. Trùng hồ sơ khi tạo/sửa: R3 không có câu "đã có hồ sơ", FE đang dùng MSG-18. Nhóm có muốn thêm mã riêng?
3. R3 không có MSG thành công cho "lập phí khám khách trực tiếp" và "đã báo bệnh nhân không có bác sĩ thay"; FE không hiện toast, chỉ cập nhật trạng thái trên màn.
4. Tiền mặt không đủ/chưa xác nhận đang dùng MSG-39 (R3 không có mã riêng cho thanh toán tại quầy).
5. Cấu hình nhóm ưu tiên và thứ tự (mock cố định) lấy từ đâu; đồng hồ máy chủ dùng giờ Việt Nam.
6. Mã bệnh nhân/số thứ tự: mock dùng `BN0001`, `101-003`. BE chốt định dạng.

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
| 1 | `POST /api/auth/patient/login` (nhân viên: `/api/auth/staff/login`) | `email: string`, `password: string` | `{accessToken, tokenType, expiresIn, accountId, accountType}` + cookie `refresh_token` | 401 → MSG-01; còn lại (429, 5xx, mạng) → MSG-04 | `features/auth/api/authApi.ts#signIn` | BR-01, BR-02: chỉ tài khoản bệnh nhân đủ điều kiện đăng nhập qua `/login`. Khóa theo chính sách (BR-02). FE không phân biệt lý do từ chối. |
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
| 12 | `getSession()` (`authSession.ts`: bộ nhớ, hết hạn thì `GET /api/auth/csrf` + `POST /api/auth/refresh`) | — | session hoặc null | lỗi mạng → MSG-04 | `features/my-account/components/RoleHomeRedirect.tsx` (`/workspace`) | Chỉ dùng để biết có phiên; quyền do `/me` và backend. |
| 13 | `POST /api/auth/logout` (header `X-CSRF-TOKEN`) | — | 204 | bỏ qua lỗi, xoá phiên phía trình duyệt, về `/login` | menu header bệnh nhân (P00) | — |

#### Hợp đồng URL giữa màn (query)
- `/login?next=<đường dẫn>`: FE chỉ nhận `next` là đường dẫn bắt đầu bằng `/portal` (không `//`, không `\`, không ký tự điều khiển, không URL tuyệt đối); còn lại về `/portal`. CTA "Đặt lịch khám": `/login?next=/portal/appointments/new`.
- `/workspace`: không tham số. Chuyển theo tài khoản (`homeRouteForAccount`): PATIENT → `/portal`; ADMIN và CLINIC_OWNER → `/admin`; nhân viên khác → `/reception`.
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
- Khu Lễ tân `/reception/appointments?patientId=` dùng form đặt lịch riêng của `patient-reception` (R00), gọi cùng `POST /api/v1/appointments` với `patientId`.

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
3. **Hàng đợi:** chỉ trả `WAITING`, `BEING_SERVED`, `ABSENT` của lượt. Không trả `DONE`/`CLOSED`. `peopleAhead` = số `WAITING` đứng trước trong cùng phòng theo thứ tự BE (cùng quy tắc với hàng đợi Lễ tân, xem R00 §4).
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
