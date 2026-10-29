# Ghi chú merge: thay đổi chưa commit trên `nghiadt13`

Viết ngày 28/10/2026. Nhánh `nghiadt13` ở `d4296ff`, working tree còn **115 mục chưa commit** (80 sửa, 2 xóa, 1 thêm, phần còn lại là file mới chưa theo dõi). Số liệu so sánh với nhánh khác lấy từ ref cục bộ, **chưa `git fetch`**, nên nhánh remote có thể đã đi xa hơn.

## 0. Đọc nhanh

- **Đã commit thành 3 phase** (mục 7), nên không còn vướng chuyện "thay đổi chưa commit" khi merge. Từ đây, "mình" nghĩa là 3 commit đó.
- Nhánh dễ phải merge nhất và mức chồng với working tree này:

| Nhánh | Hơn `HEAD` | File trùng |
| --- | --- | --- |
| `origin/feat/long/appointment-booking` | 8 commit | 30 (BE chỉ còn SPEC và openapi) |
| `origin/product` | 2 commit | 16 |
| `origin/thien-quang` (Quang) | 17 commit | 2 (SPEC và openapi, **là commit của Long mà Quang đã merge vào nhánh**; 11 commit riêng của Quang đều là `reception` phía FE) |
| `fix/booking-connect-phase1` (DTN, nhánh cục bộ chưa push; **không phải của Long**) | 15 commit | 2 (`MyAccountController`, test giá), **kèm xung đột về ý nghĩa ở mục 1** |
| `try/nghiadt13-with-booking-be` | 1 commit, thay cả `backend/` | 24 |

- Ba quy tắc chọn: (1) file **giống hệt** upstream (mục 2) thì lấy bản nào cũng được. (2) file **cả hai cùng sửa** (mục 3) thì lấy upstream rồi áp lại phần của mình theo bảng. (3) file **chỉ mình có** (mục 4) thì giữ hết.

## 1. Rủi ro lớn nhất: hai bản `GET /api/v1/me`

`fix/booking-connect-phase1` (và `try/…`) có `auth/controller/MyAccountController.java` **cùng đường dẫn** với file mới của mình, nhưng khác hẳn:

| | Của mình | Của nhánh `fix/booking-connect-phase1` (DTN) |
| --- | --- | --- |
| Service | `MyAccountQueryService` (+`Impl`, +`MyAccountQueryServiceImplTest`) | `CurrentAccountService` (+`Impl`) |
| Phản hồi | `{id, accountType, email, displayName}` | `{id, kind, displayName, avatarUrl, status, allowedActions, role, username, mustChangePassword, email}` |
| Test | không có test tích hợp | `MyAccountEndpointPostgresIntegrationTest` |

Hướng xử lý: **giữ bản của nhánh `fix/booking-connect-phase1`** (commit do DTN viết ngày 26/10, không phải Long; `origin/feat/long/appointment-booking` chưa có `/api/v1/me`) (đầy đủ hơn, khớp kiểu `MyAccount` mà FE giả đang dùng), bỏ `MyAccountQueryService`, `MyAccountQueryServiceImpl`, `MyAccountQueryServiceImplTest` của mình để không còn hai chỗ cùng map `/api/v1/me`. Rồi sửa FE: [currentAccountApi.ts](../frontend/src/lib/auth/currentAccountApi.ts) đang đọc `accountType`, phải map từ `kind` (và `role` nếu cần). Các file dùng `accountType`/`AccountType` (ngoài khu admin `features/accounts`): `authSession.ts` (định nghĩa kiểu), `currentAccountApi.ts` (+test), `homeRouteForAccount.ts` (+test), `RoleHomeRedirect.tsx` (+test), `PatientAuthGate.tsx` (+test), `SiteAccountLinks.test.tsx`, `SignInForm.test.tsx`, `PatientBookingPage.test.tsx`, `PatientPortalHeader.test.tsx`. Tìm lại bằng `grep -rln accountType frontend/src`.

## 2. Giống hệt upstream: không phải chọn

- **Giá dịch vụ** = commit `ff8bb64` của `origin/feat/long/appointment-booking`, 13 file y hệt: `ClinicServiceController`, `ClinicServiceMapper`, `ClinicService`, `ServiceRequest`, `ServiceResponse`, `ClinicServiceJpaEntity`, `CreateClinicServiceService`, `UpdateClinicServiceService`, `ClinicServiceValidation`, `CreateClinicServiceServiceImpl`, `UpdateClinicServiceServiceImpl`, `V030__add_clinic_service_price.sql`, `ClinicServicePostgresIntegrationTest`.
- Riêng `fix/booking-connect-phase1` là bản **cũ hơn, chưa có giá**: khi merge nhánh đó, `ClinicServicePostgresIntegrationTest` (và `AppointmentBookingController` thiếu reschedule) cứ giữ bản mình/Long.
- **Đăng nhập nhân viên** = `origin/product`, y hệt: `(public)/staff-login/page.tsx`, `AdminAccountMenu.tsx` (+test), `SignInForm.tsx`, `authFieldRules.ts`.
- `d4296ff` (nhánh mình) và `f974431` (`origin/product`) là **cùng một thay đổi** "đăng nhập bằng JWT của backend" với hai mã khác nhau. Expect conflict ở các file auth nhưng ý định giống nhau.

## 3. Cả hai cùng sửa: lấy upstream, áp lại phần của mình

### Backend

Phần tên bác sĩ, tên dịch vụ, giá trong lịch hẹn **sửa tại chỗ vào code của Long, không thêm class mới**, nên các controller lịch hẹn và test của chúng không còn bị sửa.

| File | Việc phải áp lại |
| --- | --- |
| `…/feat-002-appointment-booking/SPEC.md` | Long sửa nhiều (nhánh Quang chỉ mang lại bản của Long). Lấy bản upstream, chỉ thêm lại **1 dòng** `AppointmentResponse:` có thêm `nullable serviceName/servicePrice` (giá hiện hành, không phải giá chụp lúc đặt) và `nullable doctorDisplayName`, kèm ghi chú "chỉ GET danh sách/chi tiết điền, endpoint ghi trả null". |
| `…/feat-002-appointment-booking/openapi.yaml` | Lấy upstream, thêm lại 3 dòng vào schema `Appointment`: `serviceName`, `servicePrice`, `doctorDisplayName` (đều nullable, kèm `description` như trên). |
| `appointment/service/AppointmentSummary.java` | Thêm 3 trường cuối constructor: `doctorDisplayName`, `serviceName`, `servicePrice` (+getter). |
| `appointment/service/impl/GetAppointmentsServiceImpl.java` | Constructor nhận thêm `StaffAvailabilityProviderQueryService doctors` và `ClinicServiceBookingQueryService services` (trước `clock`); hàm `summaries(...)` tra tên từng bác sĩ, dịch vụ một lần cho cả trang. |
| `appointment/mapper/AppointmentMapper.java`, `model/dto/response/AppointmentResponse.java` | `AppointmentResponse` thêm `serviceName`, `servicePrice`, `doctorDisplayName` (19 tham số). `toResponse(AppointmentSummary)` điền 3 trường; `toResponse(CreatedAppointment)` truyền `null`. |
| `servicecatalog/service/ClinicServiceBookingQueryService.java` và `impl/ServiceCatalogQueryServiceImpl.java` | Thêm method `findServiceLabel(UUID)` trả `Optional<ServiceLabel(name, price)>`, không lọc dịch vụ đã đóng. |
| `appointment/GetAppointmentsServiceImplT023Test.java` | Constructor thêm 2 mock; thêm 2 test (có tên, và null khi bác sĩ/dịch vụ không còn). |
| `auth/config/DemoAccountSeeder.java` | Gộp tay. Giữ: tham số `PatientProfileRepository`, hàm `ensurePatientProfile` (mọi tài khoản bệnh nhân demo có hồ sơ với họ tên thật), `seed(...)` trả về `AccountJpaEntity` và đổi `private` thành package-private `static`. |
| `resources/application-demo.yml` | `patient-origins` và `staff-origins` thành `http://localhost:3000,http://127.0.0.1:3000` (mở FE bằng 127.0.0.1 vẫn qua CORS/CSRF). |

### Frontend

| File | Việc phải áp lại |
| --- | --- |
| `lib/api/client.ts`, `lib/api/apiError.ts`, `lib/auth/authSession.ts` | **CSRF**, không được bỏ: request ghi (POST/PUT/PATCH/DELETE) gửi `X-CSRF-TOKEN` và `credentials: "include"`; gặp 403 thì lấy token mới và thử lại đúng 1 lần; `ApiError` mang `errorCode` đọc từ `error.code` của BE; `authSession` thêm `getCsrfToken(refresh)` và `peekSession()`. |
| `features/auth/api/authApi.ts` | Giữ `signInStaff` (POST `/api/auth/staff/login`) và việc `signIn` gọi `fetchCurrentAccount()` ngay sau khi đăng nhập để header có tên. |
| `features/auth/safePortalNext.ts` (+test) | `next` thiếu hoặc không hợp lệ thì về **`/`** (trang chủ hiện tên người dùng), không phải `/portal`. |
| `(public)/login/page.tsx`, `(public)/register/page.tsx`, `RegisterPatientFlow.tsx` | Truyền `next` qua lại giữa đăng nhập và đăng ký. |
| `components/brand/PublicBookAppointmentLink.tsx` (+test), `PublicServiceDetail.test.tsx` | Nút "Đặt lịch khám" dẫn thẳng `/portal/appointments/new` (bước 1 của wizard lo đăng nhập). Bản upstream còn "đọc phiên rồi rẽ qua `/login?next=…`". Giữ của mình **nếu giữ wizard**; nếu không thì phải đổi lại cả test. |
| `my-account/components/RoleHomeRedirect.tsx` (+test), `homeRouteForAccount.ts` (+test) | Dùng `fetchCurrentAccount()` (backend thật) và `AccountType` thay cho `getMyAccount` giả. Xem mục 1 nếu đổi sang bản `/me` của Long. |
| `patient-shell/components/PatientAccountMenu.tsx` | Chỉ đổi kiểu dáng nút tài khoản (hình viên thuốc, avatar nền màu chính). Giữ của mình. |
| `patient-shell/components/PatientPortalHeader.test.tsx` | Test dựng tài khoản theo hình `accountType` của `/me`. Đổi theo quyết định ở mục 1. |

## 4. Chỉ mình có: giữ hết

- **Đặt lịch bệnh nhân (wizard):** `features/appointments/components/booking/wizard/`, `schema/bookingWizard.ts`, `api/bookingLiveApi.ts`, `bookingLiveMapping.ts` (+test), và các file sửa kèm: `BookingFlow`, `BookingPatientChoice`, `DateStrip`, `TimeSlotGrid`, `bookingSizes`, `PatientBookingPage`, `useBookableServices`, `useOpenTimes`, `types/booking.ts`, `bookingRequestRules`, `portal/appointments/new/page.tsx`.
- **Khu "Thông tin cá nhân":** `components/patient/PersonalAreaShell.tsx`, `portal/account/_personal/`, `patient-profiles/components/PersonalInfoForm.tsx`, `hooks/useOwnPatientProfile.ts`, `my-account/components/AccountEmailField.tsx`, `PatientChangePasswordPage.test.tsx`; sửa `ProfileTextField`, `ProfileSexField`, `AvatarEditor`, `account/page.tsx`, `portal/layout.tsx`, `patientNavigation`. **Đã xóa** `PatientAccountProfile.tsx` và test của nó. Tài liệu: `docs/ui-guidelines.md`, `docs/fe-be-api-patient-personal-info.md`.
- **Header, trang chủ, cổng:** `SiteAccountLinks` (+test), `SiteHeader`, `SiteTopBar`, `SiteFooter`, `Home*`, `siteNavigation`, `portal/_home/PortalHomeHeader.tsx`, `PortalHomeSections`, `portal/page.tsx`, `PatientPortalHeader`, `PatientPortalNav`, `PatientAuthGate` (+test), `hooks/useCurrentAccount.ts`, `lib/auth/currentAccountApi.ts` (+test).
- **Khác:** `auth/config/DemoDoctorSeeder.java`, `scripts/start-backend-local.ps1`.

## 5. Sau khi merge

```bash
# không để hai nơi cùng map /api/v1/me
grep -rn '"/api/v1/me"\|@GetMapping("/api/v1/me' backend/src/main

cd backend && ./mvnw.cmd test-compile
cd backend && ./mvnw.cmd test -Dtest='AppointmentBookingControllerValidationTest,GetAppointmentsServiceImplT023Test'
cd frontend && npm run lint && npx tsc --noEmit && npm test
```

Mình chưa chạy được các test tích hợp Postgres (Docker tắt) nên cần chạy lại sau merge, nhất là `AppointmentQueryT023…`, `AppointmentBookingT022…`.

## 6. Việc còn treo (không liên quan merge)

- Màn Lịch hẹn của bệnh nhân (`patientAppointmentApi.ts`) vẫn chạy mock; chưa nối `GET /api/v1/appointments` và `/{id}`. Trạng thái BE là `CONFIRMED` còn FE dùng `BOOKED`.
- Tên bác sĩ, tên dịch vụ, giá chỉ có ở `GET /api/v1/appointments` và `/{id}`; phản hồi của POST đặt lịch (và hủy, đổi giờ...) trả `null` các trường đó. Sau khi đặt, FE gọi `GET /api/v1/appointments/{id}` (địa chỉ nằm trong header `Location`) để lấy kết quả đầy đủ.
- BE chưa có phòng khám và trạng thái thanh toán trong lịch hẹn; `servicePrice` là giá hiện hành, đổi giá thì lịch cũ đổi theo.
- Các khoảng trống BE khác của khu cá nhân (thiếu cột, CORS thiếu PATCH, avatar) ghi ở `docs/fe-be-api-patient-personal-info.md`.

## 7. Ba phase đã commit lên `nghiadt13`

Các mục 0 đến 6 viết **trước** khi commit (lúc đó còn là thay đổi chưa commit). Sau đó toàn bộ được chia thành 3 commit liên tiếp, mỗi phase là một chủ đề độc lập để dễ `revert` hoặc chọn bên khi merge:

| Phase | Chủ đề | Nội dung chính |
| --- | --- | --- |
| 1 | `feat(backend)` | `GET /api/v1/me`; GET lịch hẹn kèm tên bác sĩ, tên dịch vụ, giá; giá dịch vụ (V030, trùng commit `ff8bb64` của Long); seed demo (hồ sơ bệnh nhân, 4 bác sĩ, ca làm 30 ngày); CORS thêm `127.0.0.1:3000`; SPEC và openapi; script `scripts/start-backend-local.ps1`. |
| 2 | `feat(frontend)` | CSRF và lỗi trong API client; đăng nhập và đăng ký giữ `next`; đăng nhập nhân viên; tài khoản hiện tại (`/me`) cho header và điều hướng theo vai trò; trang chủ và header công khai; khung `portal/layout.tsx` (cổng đăng nhập `PatientAuthGate`, kèm khung rộng `max-w-6xl` cho màn cá nhân); wizard đặt lịch 3 bước nối BE thật. |
| 3 | `feat(frontend)` | Màn "Thông tin cá nhân" (`/portal/account`): khung hai thẻ, form theo UC03/UC04, đổi email bằng OTP; xóa `PatientAccountProfile`; tài liệu `ui-guidelines`, danh sách API BE cần, và file ghi chú merge này. |

Thứ tự phụ thuộc: phase 2 cần BE ở phase 1 để chạy với dữ liệu thật; phase 3 chạy trong khung cổng bệnh nhân của phase 2 (`portal/layout.tsx`), nên không đứng riêng được nếu thiếu phase 2.

## 8. Kết quả merge thử `origin/feat/long/appointment-booking` vào `nghiadt13` (28/10)

Merge thử trong worktree riêng (đã xóa), không đụng nhánh thật. Long hơn 8 commit, ngoài phần chia sẻ còn 137 file tự gộp được và **32 file conflict**.

| Nhóm | File | Bản chất | Cách xử lý |
| --- | --- | --- | --- |
| Backend | `DemoAccountSeeder.java`, `application-demo.yml` | Long **bỏ tài khoản nhân viên demo** (`app.demo.staff-email/password`) và đổi tên bean. Nhưng `DemoDoctorSeeder` của mình **bắt buộc** `app.demo.staff-password`, thiếu là BE ném lỗi lúc khởi động. | Giữ phần nhân viên demo của mình, cộng thêm `cookie-secure: false` của Long. Đã thử: BE biên dịch được. |
| Dịch vụ công khai | 14 file `features/clinic-services/**`, `(site)/services/page.tsx` | Hai bên cùng **thêm** các file này (add/add): bản của DTN 25/10 và bản có giá của Long. | Lấy bản Long. |
| Đăng nhập, đăng ký | 9 file `features/auth/**`, `login/page.tsx`, `lib/api/client.ts`, `lib/session/mockSignedInKind.ts`, `PublicBookAppointmentLink*` | Hai bản cài song song "đăng nhập bằng BE". Long còn **xóa** `staff-login/page.tsx`. | Quyết định hướng đi trước, rồi áp lại: `next` ở đăng ký, CSRF trong `client.ts`, `signInStaff`, nút "Đặt lịch khám" dẫn thẳng. |

Thử lấy bản Long cho toàn bộ file conflict phía FE, kết quả:
- Backend: biên dịch được, 0 test thất bại (152 lỗi đều là test tích hợp không đăng nhập được Postgres, như trước).
- `tsc`: 1 lỗi thật: `register/page.tsx` truyền `next` cho `RegisterPatientFlow` nhưng bản Long không nhận `next`.
- `eslint`: không lỗi.
- `vitest`: 8 test fail trên 1190. 7 ở `SignInForm.test.tsx` (file này y hệt bản Long: test mock Supabase nhưng code gọi thẳng `POST /api/auth/patient/login`, nên rất có thể cũng fail trên nhánh Long; chưa kiểm riêng) và 1 ở `SiteHeader.test.tsx` (mong nút đặt lịch dẫn `/portal/appointments/new`, bản Long còn `/login?next=…`).

## 9. Merge thật vào nhánh `merge/long-into-nghiadt13` (29/10)

Worktree `tmp/wt-merge-long`, chưa push, `nghiadt13` chưa đổi. Hai commit:
- `f50d60c` merge phần dễ: BE (giữ nhân viên demo), dịch vụ công khai lấy bản Long, nút đặt lịch và `/staff-login` giữ bản mình, `client.ts` của mình thêm `publicApiRequest`.
- `a214f49` auth: **giữ phiên đăng nhập của mình** (`lib/auth/authSession`), chỉ lấy ba hàm đăng ký thật của Long (`register`, `verify-email`, `resend-otp`; BE miễn CSRF cho cả ba) và test `RegisterPatientFlow`. Bỏ `signIn` và `lib/session/otcmsSession.ts` của Long (token trong `sessionStorage`, không refresh, không gửi CSRF nên các request ghi dưới `/api/v1` sẽ 403).

Còn mở: đăng ký xong chưa thấy chỗ nào tạo hồ sơ bệnh nhân (`RegistrationServiceImpl` chỉ tạo tài khoản, đồng ý, OTP), chưa kiểm bằng chạy thật. Khác nhỏ để nguyên theo bản mình: `safePortalNext` về `/` (Long về `/portal`), `validateStaffEmail` tách riêng.

Muốn đưa `nghiadt13` lên nhánh này (fast-forward) thì phải commit hoặc cất file ghi chú này trước, vì nhánh đó cũng sửa `docs/` và file này đang có thay đổi chưa commit ở thư mục chính.

(Mục 9 là bản gộp một lần, nay chỉ còn làm bản dự phòng ở nhánh `merge/long-into-nghiadt13`. Bản đã vào `nghiadt13` được chia nhóm ở mục 10.)

## 10. Đã merge vào `nghiadt13`, chia ba nhóm (29/10, chưa push)

Cây cuối giống hệt cây gộp một lần đã kiểm tra đầy đủ (`git diff a214f49 nghiadt13` rỗng). Thứ tự từ ổn nhất đến nghi ngờ nhất:

| Nhóm | Commit | Nội dung | Rủi ro | Hoàn tác |
| --- | --- | --- | --- | --- |
| 1 | `6fea0d4` (merge `9512d3e`) | BE của Long: check-in có xác minh, hàng đợi vãng lai, đọc hàng đợi, `V026`, `V027`, tài liệu SDD | Thấp. Gộp sạch, 0 test thất bại | `git revert -m 1 6fea0d4` |
| 2 | `654ddb2` (merge `ff8bb64`) | Giá dịch vụ, trang dịch vụ công khai của Long, `publicApiRequest`; nút đặt lịch và `/staff-login` giữ bản mình | Thấp đến vừa. FE 1192 test qua | `git revert -m 1 654ddb2` |
| 3 | `6bc0611` (commit thường) | Đăng ký bệnh nhân nối BE thật: BE `register` còn 5 trường, FE gọi `register`, `verify-email`, `resend-otp`; giữ phiên đăng nhập của mình | Cao nhất. Đổi hợp đồng đăng ký, chưa chạy thật, chưa rõ chuyện tạo hồ sơ | `git revert 6bc0611` |

Lưu ý khi hoàn tác: nhóm 1 và 2 là merge commit, hoàn tác bằng `-m 1`. Sau đó muốn merge lại nhánh Long phải "revert của revert" trước, vì Git coi các commit của Long đã được merge. Nhóm 3 nên hoàn tác trước (nó nằm trên cùng), rồi mới tới nhóm 2 và nhóm 1.
