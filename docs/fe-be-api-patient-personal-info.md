# API cần cho màn "Thông tin cá nhân" của bệnh nhân

Màn: `/portal/account` (UC03 Xem tài khoản, UC04 Cập nhật tài khoản; BR-01, BR-05, BR-12).
Code FE: `frontend/src/app/(patient)/portal/account/_personal/PersonalInfoScreen.tsx`.
Hiện FE đọc/ghi qua **lớp API giả**; tài liệu này là danh sách việc BE cần làm để FE đổi sang API thật mà không sửa giao diện.

Đối chiếu BE ngày 28/10/2026 (nhánh `nghiadt13`): chỉ có `GET /api/v1/me`; các API còn lại và cột dữ liệu chưa có.

## 1. Trường trên màn và nguồn dữ liệu

Theo đặc tả (UC03/UC04, từ điển dữ liệu "Patient"): họ tên, ngày sinh, giới tính, số điện thoại, địa chỉ, CCCD/mã định danh (tùy chọn), email đăng nhập (đổi bằng OTP), ảnh đại diện. Không có dân tộc, nghề nghiệp, quốc gia, tỉnh/xã tách riêng.

| Trên màn | Nguồn | Sửa được | Ghi chú |
| --- | --- | --- | --- |
| Ảnh đại diện | `GET /api/v1/me` → `avatarUrl` | Có | Upload qua `PUT /api/v1/me/avatar` |
| Họ và tên | hồ sơ Bản thân → `fullName` | Chỉ khi `editableFields` có | Khóa khi hồ sơ đã có lượt khám |
| Ngày sinh | hồ sơ Bản thân → `dateOfBirth` (`YYYY-MM-DD`) | Như họ tên | FE hiện `dd/mm/yyyy` |
| Giới tính | hồ sơ Bản thân → `sex` (`MALE`/`FEMALE`) | Có | BE đang cho cả `OTHER`, xem mục 6 |
| CCCD / Mã định danh | hồ sơ Bản thân → `citizenId` hoặc `null` | Có | Không bắt buộc (BR-14) |
| Số điện thoại | hồ sơ Bản thân → `phone` | Có | Không xác minh (BR-05) |
| Địa chỉ | hồ sơ Bản thân → `address` | Có | Một ô văn bản, bắt buộc |
| Email đăng nhập | `GET /api/v1/me` → `email` | Chỉ qua OTP | Email mới hiệu lực sau khi xác minh (BR-05) |
| Cột trái: tên, "Nam, 21/07/1992" | `fullName`, `sex`, `dateOfBirth` của hồ sơ Bản thân | Không | Chưa có hồ sơ thì hiện email |

## 2. Danh sách API

| # | Method và URL | Trạng thái BE | Dùng để |
| --- | --- | --- | --- |
| 1 | `GET /api/v1/me` | Có, thiếu `avatarUrl` | Email, ảnh, tên hiển thị của tài khoản |
| 2 | `GET /api/v1/me/patient-profiles` | Chưa có | Tìm id hồ sơ có `relationship = "SELF"` |
| 3 | `GET /api/v1/me/patient-profiles/{id}` | Chưa có | Đổ dữ liệu vào form, kèm `editableFields` |
| 4 | `PATCH /api/v1/me/patient-profiles/{id}` | Chưa có | Lưu thay đổi |
| 5 | `POST /api/v1/me/email-change` | Chưa có | Gửi OTP tới email mới |
| 6 | `POST /api/v1/me/email-change/verify` | Chưa có | Xác minh OTP, đổi email |
| 7 | `PUT /api/v1/me/avatar` | Chưa có | Đổi ảnh đại diện |
| 8 | `DELETE /api/v1/me/avatar` | Chưa có | Gỡ ảnh |
| 9 | `POST /api/auth/change-password` | Có (`PasswordRecoveryController`) | Trang đổi mật khẩu, nút "Đổi mật khẩu" trên màn dẫn tới đó |

Chi tiết 2, 3, 4 đã chốt trong [`fe-be-api-contract-patient-reception.md`](fe-be-api-contract-patient-reception.md) (mục hồ sơ đã gắn). Dưới đây là phần màn này dùng.

### 2.1 `GET /api/v1/me` (bổ sung)

Hiện trả `{ id, accountType, email, displayName }`. Thêm:

```json
{
  "id": "…", "accountType": "PATIENT", "email": "an@example.com",
  "displayName": "Nguyễn Văn An",
  "avatarUrl": "https://…/avatars/….jpg"
}
```

`avatarUrl` là `null` khi chưa có ảnh. `displayName` của bệnh nhân luôn là `fullName` của hồ sơ Bản thân (xem 2.4).

### 2.2 `GET /api/v1/me/patient-profiles`

`200 { "items": [{ "id", "patientCode", "fullName", "dateOfBirth", "sex", "relationship" }] }`, Bản thân đứng đầu. Chỉ hồ sơ gắn với tài khoản trong token (BR-01, BR-16); FE không gửi id người dùng. Màn này lấy phần tử có `relationship = "SELF"`; không có thì hiện lời mời "Thêm hồ sơ của bạn".

Đề xuất tùy chọn: thêm `GET /api/v1/me/patient-profile` (số ít) trả luôn chi tiết hồ sơ Bản thân, hoặc `404` khi chưa có, để màn mất một lượt gọi. FE đổi một dòng trong `useOwnPatientProfile`.

### 2.3 `GET /api/v1/me/patient-profiles/{id}`

```json
{
  "id": "…", "patientCode": "BN-000123", "relationship": "SELF",
  "fullName": "Nguyễn Văn An", "dateOfBirth": "1992-07-21", "sex": "MALE",
  "phone": "0912345678", "address": "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
  "citizenId": null, "email": "an@example.com",
  "hasVisits": true,
  "editableFields": ["sex", "phone", "address", "citizenId", "email"],
  "allowedActions": ["update", "unlink", "editHistory"]
}
```

`editableFields` do BE quyết định, FE chỉ mở ô có trong danh sách và hiện chỉ đọc các ô còn lại. Quy tắc hiện FE giả lập: hồ sơ **chưa có lượt khám** sửa được mọi trường; **đã có lượt khám** khóa `fullName` và `dateOfBirth` (tránh lệch hồ sơ y tế, người dùng nhờ lễ tân sửa tại quầy; BR-12 cho Lễ tân sửa dữ liệu hành chính).

Hồ sơ không thuộc tài khoản: `404` mã `MSG-41`.

### 2.4 `PATCH /api/v1/me/patient-profiles/{id}`

Body chỉ gồm các trường người dùng **đã đổi** (FE so với giá trị đã lưu), mỗi trường tùy chọn:

```json
{ "phone": "0900111222", "address": "…", "citizenId": null }
```

`citizenId` rỗng gửi `null` để xóa. Trả `200` với chi tiết hồ sơ như 2.3 (FE dùng luôn để làm mới màn). Quy tắc kiểm tra (FE đã kiểm trước, BE **phải tự kiểm lại**, không tin FE):

| Trường | Luật | Lỗi |
| --- | --- | --- |
| `fullName` | 2 đến 100 ký tự, chỉ chữ (có dấu), khoảng trắng, `. ' -`, không có số | `MSG-17` tại trường |
| `dateOfBirth` | `YYYY-MM-DD`, đúng ngày, không ở tương lai | `MSG-17` |
| `sex` | `MALE` hoặc `FEMALE` | `MSG-17` |
| `phone` | 10 số, bắt đầu 0 (bỏ khoảng trắng, `.`, `-` trước khi kiểm) | `MSG-17` |
| `address` | bắt buộc, tối đa 255 ký tự | `MSG-17` |
| `citizenId` | tùy chọn, 8 đến 12 chữ hoặc số | `MSG-17` |

- Trường ngoài `editableFields` mà vẫn gửi: `400` `MSG-17` gắn đúng trường đó.
- Lỗi theo trường trả dạng `fieldErrors: [{ "field": "phone", "code": "MSG-17" }]` để FE gắn dưới ô.
- Sửa `phone` hoặc `dateOfBirth` phải kiểm trùng như tạo hồ sơ (BR-14, BR-18); trùng thì `409`.
- **Đồng bộ tên:** sửa `fullName` phải cập nhật luôn `displayName` của tài khoản (`GET /api/v1/me` và header khu bệnh nhân đọc từ đó).
- Thành công: FE hiện toast `MSG-05` ("Thông tin tài khoản đã được cập nhật.").
- Sự cố: `5xx` mã `MSG-04`.

### 2.5 Đổi email bằng OTP (BR-05)

- `POST /api/v1/me/email-change` body `{ "newEmail" }` → `204`, gửi OTP tới email mới, email cũ vẫn dùng được. Email sai định dạng: `400` `MSG-12` tại `newEmail`. Email trùng email đang dùng hoặc đã thuộc tài khoản khác: `400` `MSG-08` (một câu chung, không lộ email nào đã tồn tại, BR-03).
- `POST /api/v1/me/email-change/verify` body `{ "otp" }` → `200` tài khoản mới (`email` đã đổi). OTP sai hoặc hết hạn: theo đặc tả UC04 E3 là `MSG-82`. FE hiện đang hiện `MSG-08` cho trường hợp này; xem mục 6.
- Chỉ tài khoản `PATIENT` được gọi; tài khoản khác `403` `MSG-14`.

### 2.6 Ảnh đại diện

- `PUT /api/v1/me/avatar` `multipart/form-data`, part `file`. Chấp nhận `image/jpeg`, `image/png`, `image/webp`, tối đa 2 MB (FE đã chặn trước). Sai loại hoặc quá cỡ: `400` `MSG-08` kèm `fieldErrors: [{ "field": "file" }]`. Trả `200` tài khoản có `avatarUrl` mới.
- `DELETE /api/v1/me/avatar` → `204`.
- Cần nơi lưu file (Supabase Storage hoặc tương đương) và `avatar_url` trên tài khoản; **chưa có cột nào** trong schema.

## 3. Dữ liệu BE còn thiếu so với đặc tả

Bảng `patient_profiles` hiện có: `full_name`, `phone_canonical`, `phone_owner_type`, `date_of_birth`, `gender` (`MALE`/`FEMALE`/`OTHER`). Cần thêm (migration Flyway mới, V030 đã dùng cho giá dịch vụ):

| Cần thêm | Kiểu gợi ý | Ghi chú |
| --- | --- | --- |
| `address` | `VARCHAR(255)` | Bắt buộc theo màn; hồ sơ cũ cho phép rỗng tới khi người dùng bổ sung |
| `citizen_id` | `VARCHAR(12)` | Tùy chọn, có thể cần mã hóa và chính sách lưu (`otcms-schema-design.md` mục 6 còn để ngỏ) |
| `patient_code` | `VARCHAR` duy nhất | `BN-000123`, hiện trong chi tiết hồ sơ |
| (suy ra) `hasVisits` | tính từ bảng lượt khám | Quyết định `editableFields` |
| `accounts.avatar_url` | `TEXT` | Ảnh đại diện |

`patient_profiles.account_id` đang `UNIQUE` nên một tài khoản chỉ có **một** hồ sơ Bản thân, khớp với việc màn này chỉ sửa hồ sơ đó. Hồ sơ người thân nằm ở màn "Hồ sơ và gia đình", không thuộc màn này.

## 4. Quy ước chung BE cần giữ

- Id người dùng lấy từ token, không nhận từ client (BR-01). Mọi API trên kiểm quyền sở hữu: hồ sơ không thuộc tài khoản thì `404` `MSG-41`, không phải `403`.
- Thân lỗi chuẩn `{ "error": { "code", "message" } }` (FE đọc `error.code`), thêm `fieldErrors` cho lỗi theo trường.
- Ghi (`POST`, `PUT`, `PATCH`, `DELETE`) cần header `X-CSRF-TOKEN` (cookie `XSRF-TOKEN`, lấy bằng `GET /api/auth/csrf`). FE client đã tự gắn và thử lại một lần khi `403`.
- **CORS phải thêm `PATCH`.** `SecurityConfig` đang cho `GET, HEAD, POST, PUT, DELETE, OPTIONS`; thiếu `PATCH` thì trình duyệt chặn lệnh lưu hồ sơ (API 4) ở bước preflight, không có log nào ở BE. Thiếu CSRF ở luồng đặt lịch cũng im lặng đúng kiểu này.
- Không ghi CCCD, số điện thoại, địa chỉ vào log.

## 5. Khi BE xong: FE đổi những gì

Giữ nguyên chữ ký hàm, chỉ đổi thân sang `apiRequest`:

| File | Hàm | Đổi sang |
| --- | --- | --- |
| `lib/patient-scope/linkedProfilesApi.ts` | `listLinkedProfiles` | API 2 |
| `features/patient-profiles/api/patientProfileApi.ts` | `getPatientProfile`, `updatePatientProfile` | API 3, 4 |
| `features/my-account/api/myAccountApi.ts` | `getMyAccount`, `updateMyAvatar`, `removeMyAvatar` | API 1, 7, 8 |
| `features/my-account/api/myAccountProfileApi.ts` | `requestMyEmailChange`, `verifyMyEmailChange` | API 5, 6 |

`getMyAccount` có thể dùng chung `fetchCurrentAccount` (`lib/auth/currentAccountApi.ts`, đã gọi `GET /api/v1/me` thật) nếu BE trả đủ `avatarUrl`. Sau đó xóa kho giả `myAccountMockStore`, `patientProfileMockStore`, `linkedProfilesMockStore`.

## 6. Điểm lệch cần chốt với nhóm

1. **Giới tính `OTHER`:** BE cho `MALE/FEMALE/OTHER`, FE và đặc tả chỉ có Nam/Nữ. Chốt bỏ `OTHER` ở BE hay thêm lựa chọn ở FE.
2. **OTP đổi email sai:** đặc tả UC04 E3 ghi `MSG-82`, lớp giả FE đang trả `MSG-08`. Theo đặc tả thì BE trả `MSG-82`, FE sửa `EmailChangeErrorNotice` tương ứng.
3. **Tên hiển thị:** đặc tả UC03/UC04 của bệnh nhân chỉ nói họ tên; màn không còn ô "Tên hiển thị" riêng (`DisplayNameRow` không dùng nữa trên khu bệnh nhân, `PATCH /api/v1/me` không cần cho màn này).
4. **Email của hồ sơ và email tài khoản:** hồ sơ có trường `email` (nhận OTP gắn hồ sơ), màn này chỉ hiện email tài khoản. Chốt hai email có luôn bằng nhau với hồ sơ Bản thân hay không.
5. **Địa chỉ một ô:** đặc tả chỉ có "address". Nếu cần tỉnh/xã (như mẫu tham khảo) thì đặc tả và schema phải thêm trước.

## 7. Kiểm thử khi nối BE

- Hồ sơ chưa có lượt khám: sửa được họ tên, ngày sinh; có lượt khám: hai ô đó chỉ đọc và hiện chú thích.
- Sửa SĐT thành chuỗi sai: lỗi dưới ô, focus về ô, không gọi API (kiểm FE) và `400` `MSG-17` khi gọi thẳng API (kiểm BE).
- Gửi `PATCH` có `fullName` cho hồ sơ đã khám: `400` `MSG-17`.
- Sửa `fullName` (hồ sơ chưa khám): header khu bệnh nhân đổi tên theo sau lần tải tiếp.
- Tài khoản chưa có hồ sơ Bản thân: màn hiện lời mời thêm hồ sơ, không lỗi.
- Đổi email: OTP đúng/sai/hết hạn, email trùng; email cũ vẫn đăng nhập được cho tới khi xác minh.
- Ảnh: sai định dạng, trên 2 MB, gỡ ảnh.
- Gọi `PATCH` từ `http://localhost:3000`: không bị chặn CORS.
