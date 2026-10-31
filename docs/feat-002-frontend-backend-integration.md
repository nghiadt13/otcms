# FEAT-002 — Theo dõi kết nối Frontend/Backend

Ngày rà soát: 2026-10-28 (Asia/Bangkok)  
Phạm vi: màn hình và API liên quan FEAT-002 Appointment Booking, đối chiếu `backend/sdd/features/feat-002-appointment-booking/`.

## Cập nhật sau khi tích hợp branch product

- Đã merge `origin/product` tại commit `236500a` vào `feat/long/appointment-booking`; merge commit local là `ebacb4e`. Không push lên remote.
- Branch hiện tại đã có Patient portal: `/portal/appointments/new`, `/portal/appointments`, `/portal/appointments/[appointmentId]`, cùng Reception, public site và imaging UI từ product.
- Frontend production build thành công. Runtime HTTP trả `200` cho `/portal/appointments/new` và `/staff-login`.
- Patient booking/list adapters trên product vẫn dùng mock; merge đã mang UI về nhưng chưa nối booking với FEAT-002 backend.
- Các thay đổi làm việc chưa commit đã được giữ trong stash `codex-safety-before-origin-product-merge`; các file untracked không trùng đường dẫn đã được khôi phục. Stash vẫn còn để bảo toàn bản gốc. Route đăng nhập nhân viên từ product được giữ vì nó tích hợp với form/sign-in flow mới hơn.
- Kiểm tra riêng backend sau merge: `git diff ebacb4e^1 ebacb4e -- backend` rỗng; sáu artifact FEAT-002 (`SPEC`, `CONTEXT`, `PLAN`, `TASKS`, `openapi.yaml`, `API-CONTRACT.md`) vẫn còn. Flyway log cùng checksum mismatch đã xuất hiện trong lần chạy trước merge khoảng 18:56, nên lỗi này không do merge product làm mất backend code.

## Cập nhật backend sau khi khôi phục thứ tự migration

- Khôi phục `V026__add_administrator_credentials.sql` và `V027__allow_legacy_administrator_password_reissue.sql` từ snapshot backup `backup/full-workspace-3f8aa30`; checksum lần lượt `1595489762` và `-1130579379`, khớp chính xác giá trị Flyway báo đã áp dụng.
- Đưa lại audit/check-in và queue actor migrations ở version `V028`/`V029`; nội dung và checksum khớp các file trước đó bị đánh số nhầm thành `V026`/`V027`.
- `V030__add_clinic_service_price.sql` vẫn là migration mới nhất. Khởi động backend xác nhận Flyway validated 32 migration, schema đang ở version `030` và up-to-date; không cần ghi migration mới vào database.
- Backend đã chạy thành công (`Spring Boot 4.1.1`, Java 21.0.7). `GET /api/v1/clinic-services` và `GET /v3/api-docs` đều trả `200`. Database cấu hình là remote Supabase; lần khởi động này chỉ đọc/validate lịch sử vì schema đã ở `030`.

## Kết luận

- Hiện chưa thể nói FEAT-002 đã được tích hợp end-to-end. Trang `/admin/appointments` đang dùng dữ liệu mock tại `frontend/src/features/appointments/api/appointmentApi.ts`; phần này không gọi `apiRequest` và chưa gửi bearer token tới backend.
- Backend có các API appointment/availability/arrival/follow-up và API bổ trợ Visit; backend đang expose `/api/v1/appointments` (không có prefix `/admin`).
- Lần kiểm tra runtime ban đầu trước khi merge/migration reconciliation từng gặp lỗi shell Admin và Flyway mismatch; các trạng thái đó đã được xử lý theo cập nhật ở đầu file.
- Để nối an toàn, cần quyết định quyền truy cập theo đặc tả: Clinic Owner không có quyền đọc/quản lý danh sách appointment chung; Admin chỉ có availability và mutation-impact; Receptionist quản lý nghiệp vụ vận hành; Patient tự phục vụ profile hiện liên kết; Doctor chỉ appointment được giao.

## Màn hình Frontend đã tìm thấy

> **Phạm vi so sánh ban đầu:** branch làm việc trước merge ở `ff8bb64`; local branch `product` cũ ở `d9b0483`. Remote mới nhất tại lúc tích hợp là `origin/product` `236500a` (2026-10-28). Các màn Patient từng bị thiếu ở branch làm việc đã được đưa vào merge commit `ebacb4e`.

| Màn/route | Thành phần chính | Actor/phạm vi | Hiện trạng kết nối và việc cần làm |
| --- | --- | --- | --- |
| `/admin/appointments` — Danh sách lịch hẹn | `features/appointments/components/AppointmentListTab.tsx`, `AppointmentFilters.tsx`, `AppointmentTable.tsx`, `AppointmentDetailSheet.tsx` | **Có ở branch hiện tại** và `origin/product`. `origin/product` còn có thêm Patient Appointment screens bên dưới. Hiện đặt dưới khu vực Admin. Theo FEAT-002, danh sách toàn clinic/chi tiết không cấp cho Admin hoặc Owner; danh sách vận hành thuộc Receptionist. Doctor cần màn danh sách riêng chỉ lịch được giao; Patient cần lịch của hồ sơ tự liên kết. | Đang gọi `listAppointments()` trong kho mock. Cần chuyển thành màn Receptionist dùng `GET /api/v1/appointments` và lọc/paging theo DTO backend. Không chỉ đổi URL: cần chuyển route/guard sang khu vực receptionist; định nghĩa lại model, status, search/filter, paging và error mapping. |
| `/admin/appointments` — Chi tiết lịch hẹn (sheet) | `AppointmentDetailSheet.tsx` | Sheet hiển thị tên bệnh nhân, dịch vụ, bác sĩ, trạng thái, phí khám. Không có API riêng gọi khi mở; dữ liệu lấy từ list mock. | Backend có `GET /api/v1/appointments/{id}` nhưng DTO hiện tại không khớp shape mock. Cần màn Receptionist gọi detail endpoint trong scope đã kiểm tra; không mở cho Admin/Owner. Tách dữ liệu phí khám nếu endpoint FEAT-002 không sở hữu/không cung cấp trường đó. |
| `/admin/appointments` — Giờ trống bác sĩ | `AvailabilityTab.tsx`, `AvailabilityGrid.tsx`, `AvailabilityResult.tsx`, `useDoctorAvailability.ts`, `useDoctorLookup.ts` | Phần xem availability theo profile Doctor chỉ được cấp cho Admin để lập lịch; staff booking availability là luồng khác cho Receptionist. | UI đang gọi mock `getDoctorAvailability()` và `lookupDoctors()`; backend có public `GET /api/v1/appointments/availability`, staff `GET /api/v1/staff/appointments/availability`, và `GET /api/v1/doctors`. Cần nối đúng endpoint theo actor, đối chiếu response slots (backend trả danh sách slot có service/doctor/start/end; mock trả ngày/lưới FREE/BOOKED), chọn ngày/slot và service ID. API admin-doctor-availability ghi trong mock chưa tồn tại với route đó. |
| `/admin/appointments` — Dialog đổi bác sĩ | `ReassignDoctorDialog.tsx`, `ReassignCandidateList.tsx`, `DoctorCombobox.tsx` | Receptionist theo D06; Admin không có quyền reassignment. | Đang gọi mock `listEligibleDoctors()` và `reassignAppointmentDoctor()`. Backend có `GET /api/v1/appointments/{appointmentId}/reassignment-availability` và `POST /api/v1/appointments/{id}/reassign` (cần body có `doctorId` và reason). Cần chuyển sang luồng Receptionist, gửi reason, dùng API tìm slot ứng viên thay cho endpoint mock `/admin/.../eligible-doctors`; xử lý response/error thật và scope/idempotency nếu hợp đồng yêu cầu. |
| `/admin/appointments` — Thông báo không có bác sĩ thay thế | `AppointmentListTab.tsx` gọi `notifyNoReplacement()` | Mock hiện gắn vào danh sách admin. | Không tìm thấy endpoint tương ứng trong controller backend hay OpenAPI FEAT-002. Cần xác định nghiệp vụ notification thuộc module nào và public contract nào; nếu chưa có contract, bỏ hành động khỏi FE cho tới khi backend cung cấp use case/API đã duyệt. |
| Patient — đặt lịch khám `/portal/appointments/new` | **Đã merge từ `origin/product` vào branch hiện tại.** Route `src/app/(patient)/portal/appointments/new/page.tsx`; `PatientBookingPage`, `BookingFlow`, booking hooks/components trong `features/appointments`. | Patient đăng nhập; có thể chọn hồ sơ đã liên kết. | UI runtime trả HTTP 200, nhưng `bookingApi.ts` vẫn mock (`mockDelay`, `mockFail`), chưa dùng `apiRequest`. Nối `GET /api/v1/appointments/availability` + `POST /api/v1/appointments`; catalog dùng `GET /api/v1/clinic-services`. Map request backend (`patientProfileId`, `serviceId`, `doctorId`, `startTime`, `purpose`); actor/source do backend lấy từ token. Product mock dùng endpoints/shape cũ (`bookable-services`, `open-times`, `patientId`, `startAt`), cần thay adapter và xác minh API hồ sơ mà `useLinkedProfiles` gọi. |
| Patient — danh sách lịch `/portal/appointments` | **Đã merge từ `origin/product` vào branch hiện tại.** Route `/portal/appointments/page.tsx`, `PatientAppointmentList` và API adapter Patient appointment. | Patient chỉ xem lịch trong scope backend. | `features/appointments/api/patientAppointmentApi.ts` vẫn mock. Nối `GET /api/v1/appointments` với token Patient, khớp response/status thật và dùng backend scope; xác minh UI cần hồ sơ tự liên kết nào vì FEAT-002 giới hạn booking vào profile link hiện tại. |
| Patient — chi tiết lịch `/portal/appointments/[appointmentId]` | **Đã merge từ `origin/product` vào branch hiện tại.** `PatientAppointmentDetail`. | Patient chỉ lịch trong scope backend. | Nối `GET /api/v1/appointments/{id}`; xác nhận backend projection có các trường UI cần (patient/service/doctor/fee/visit); không lấy `allowedActions` từ mock, backend phải quyết định/ủy quyền thao tác. |
| Patient — reschedule/cancel | Dialog/actions đi kèm list/detail trong `origin/product` (`PatientAppointmentActions`, `PatientCancelDialog`). | Patient, deadline và appointment state enforced server-side. | Product API layer hiện mock cho cancel; cần nối `GET /api/v1/appointments/{id}/reschedule-availability`, `PATCH .../{id}/reschedule`, `POST .../{id}/cancel`, gửi reason khi cần, map 409/validation và refresh dữ liệu sau mutation. |
| Patient — tiến độ khám / Visit — **chưa thấy màn chuyên dụng FEAT-002 trên hai branch** | Routes portal visits/results ở product hiện phục vụ workflow/medical records; chưa tìm thấy call `/api/v1/visits/{visitId}/progress` trong frontend. | Patient chỉ Visit của mình; backend Visit API kiểm tra quyền hồ sơ. | Nếu product cần hiển thị queue/stage, thêm call/màn dùng `GET /api/v1/visits/{visitId}/progress`; không suy ra màn này đã có chỉ từ route visits. |
| Receptionist — danh sách/check-in lịch đến — chưa có route/feature | Không thấy appointment-arrivals hoặc reception queue screens. | Receptionist. | Backend có `GET /api/v1/appointment-arrivals`, `POST /api/v1/appointments/{id}/check-in`, `GET /api/v1/reception-queue`; check-in cần `Idempotency-Key` và body profile verification. Cần xây màn worklist/check-in, xử lý replay, lỗi conflict và refresh queue. |
| Receptionist — tạo/đổi/hủy lịch hỗ trợ — chưa có route/feature | Không thấy form receptionist. | Receptionist. | Có API create/reschedule/cancel; cần form có patient profile, service/doctor/slot, reason khi policy yêu cầu, và DTO khớp backend. Không lấy patient profile từ browser tùy ý thay cho kiểm tra backend. |
| Doctor — lịch được giao, cập nhật trạng thái, follow-up — chưa có route/feature | Không thấy appointment/visit workflow screens. | Doctor chỉ appointment có `doctor_id` của chính Doctor; follow-up chỉ từ Visit được phân công và hoàn tất phần examination. | Có `GET /api/v1/appointments`, `PATCH /api/v1/appointments/{id}/status`, `POST /api/v1/visits/{visitId}/follow-up-appointments`. Cần UI riêng theo Doctor scope; không tái sử dụng trang admin hiện tại với quyền rộng. |
| Admin — chỉ xem availability và ảnh hưởng mutation được phép — chưa đủ trong màn appointment | Màn admin hiện gồm cả list, detail, reassignment. | Administrator. | Giữ route admin chỉ cho chức năng availability/mutation-impact được cấp. Chuyển list/detail/reassign sang receptionist; bổ sung đúng endpoint mutation-impact khi feature UI cần. |

## Đối chiếu các API FEAT-002 chính

| Luồng | API backend/controller | FE hiện tại |
| --- | --- | --- |
| Catalog dịch vụ công khai | `GET /api/v1/clinic-services` — `ClinicServiceController` | Đã dùng `publicApiRequest` ở `features/clinic-services/api/publicServiceApi.ts`. |
| Danh mục Doctor công khai | `GET /api/v1/doctors` — `DoctorProfileController` | Appointment mock có `lookupDoctors`; chưa gọi API thật. |
| Availability đặt lịch công khai | `GET /api/v1/appointments/availability` — `AppointmentAvailabilityController` | Chưa có booking flow; availability tab dùng mock. |
| Availability của staff | `GET /api/v1/staff/appointments/availability` — `AppointmentAvailabilityController` | Chưa có staff booking flow; availability tab không dùng endpoint này. |
| Liệt kê/chi tiết appointment theo actor | `GET /api/v1/appointments`, `GET /api/v1/appointments/{id}` — `AppointmentQueryController` | Appointment list/detail đang mock; FE query dùng `q`, `from`, `to`, `doctorId`, `status`, còn backend không nhận `q`. |
| Tạo/cancel/reschedule/reassign | `POST /api/v1/appointments`, `POST /{id}/cancel`, `PATCH /{id}/reschedule`, `POST /{id}/reassign` — `AppointmentBookingController` | Chưa gọi API thật; các màn/flows tương ứng phần lớn chưa có. |
| Reassignment availability | `GET /api/v1/appointments/{appointmentId}/reassignment-availability` — `AppointmentAvailabilityController` | Mock gọi eligible-doctors, contract và shape khác. |
| Check-in/no-show/Doctor status | `POST /{id}/check-in`, `PATCH /{id}/status` — `AppointmentBookingController` | Chưa có workflow UI. |
| Arrivals/QR/manual | `GET /api/v1/arrival-proofs/current`, `POST /{id}/arrivals`, `POST /{id}/arrivals/manual`, `GET /api/v1/appointment-arrivals` — `AppointmentArrivalController` | Chưa thấy UI/calls. |
| Doctor follow-up | `POST /api/v1/visits/{visitId}/follow-up-appointments` — `AppointmentFollowUpController` | Chưa thấy UI/call. |
| Visit queue/progress/WALK_IN | `GET /api/v1/reception-queue`, `GET /api/v1/visits/{visitId}/progress`, `POST /api/v1/visits/walk-ins` — Visit controllers | Chưa thấy UI/calls. |

## Contract và kiến trúc cần đồng bộ

1. **Vai trò màn hình:** thay vị trí/guard hiện tại của `/admin/appointments` vì nội dung list và reassignment đang vượt quyền Admin. Tạo khu vực receptionist; tạo màn patient/doctor với scope riêng nếu product yêu cầu.
2. **Frontend API adapter:** thay mock implementation trong `features/appointments/api/appointmentApi.ts` bằng `apiRequest` / `publicApiRequest` từ `src/lib/api/client.ts`; giữ mock có thể test riêng, không làm runtime default. Gửi Bearer token cho API cần xác thực.
3. **DTO mismatch:** FE mock dùng `BOOKED`, `startAt`, `patientName`, `examFeePaid`, `allowedActions`; backend dùng statuses `CONFIRMED`, `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW` và DTO trường `startTime`, `patientProfileId`, `serviceId`, `doctorId`, v.v. Chốt response projection cho dữ liệu tên người/service/fee trước khi bind UI; không giả định backend trả các trường mock.
4. **Filter/paging mismatch:** FE gửi search `q`; backend list nhận page/size/date/status/doctorId/patientProfileId, không có `q`. Quyết định thêm search API/backend contract nếu đúng policy hoặc bỏ search theo tên/số điện thoại khỏi request và cung cấp API tìm kiếm an toàn khác. Không tải toàn bộ dữ liệu để lọc client.
5. **Availability mismatch:** mock trả availability theo doctor/day với FREE/BOOKED; API trả các slot candidate theo service/doctor. Cập nhật types và UI để chọn dịch vụ/ngày/slot và phản ánh đúng semantics của backend.
6. **Missing operation:** endpoint `notify-no-replacement` trong mock chưa có backend contract. Bỏ nút hoặc bổ sung use case/API ở module sở hữu sau khi yêu cầu được chốt.
7. **Error handling:** dùng API error mapping chung; ánh xạ mã 400/401/403/404/409/429/503 vào thông báo/refresh phù hợp, không suy quyền từ việc ẩn nút.
8. **Backend architecture:** source hiện đặt appointment code dưới package `controller/service/repository/model`, trái convention feature Clean Architecture trong `AGENTS.md`. Nên ghi nhận/tách lớp theo quyết định kiến trúc đã duyệt khi có thay đổi ranh giới; không cản trở HTTP integration ngay lập tức nhưng cần cập nhật tài liệu/kiến trúc khi refactor.
9. **Hợp đồng lỗi:** API-contract addendum hiện chỉ mô tả reception queue/progress; dùng `openapi.yaml` và các controller làm đối chiếu cho phần còn lại. Cập nhật API contract addendum nếu đây là tài liệu chuẩn chung cần duy trì đầy đủ.

## Trạng thái runtime của lượt rà soát ban đầu

- Frontend: Node `v24.19.0`, npm `12.0.2`. Trước merge, trang admin trả 500 do shell import `@/lib/api/staffSession` thiếu. Sau merge, `npm run build` thành công; GET `http://localhost:3000/portal/appointments/new` và `/staff-login` trả HTTP 200. Các response 200 xác nhận route render được, không xác nhận API nghiệp vụ hoạt động.
- Backend lúc rà soát ban đầu: Java `21.0.7`; Spring context dừng tại Flyway do migration names/order không khớp database. Đã xác định và sửa lỗi theo phần cập nhật backend ở trên; không dùng Flyway `repair`.
- Không gọi API mutation và không tạo/đổi dữ liệu nghiệp vụ.

## Danh sách việc theo dõi

| ID | Ưu tiên | Việc | Trạng thái |
| --- | --- | --- | --- |
| INT-01 | P0 | Theo dõi runtime API/DB sau khi sửa Flyway, xác minh các trang thực gọi API được. | Backend và endpoint health pass; nghiệp vụ appointment FE vẫn mock |
| INT-02 | P0 | Điều tra Flyway history/checksum 026–029; đồng bộ source theo lịch sử thực tế, không dùng repair. | Đã khôi phục source đúng; DB validated version 030 |
| INT-03 | P0 | Chuyển appointment list/detail/reassign khỏi Admin; chốt route Receptionist và guard backend/FE theo role scope FEAT-002. | Chưa làm |
| INT-04 | P0 | Thay mock appointment adapter bằng API client chung và đồng bộ DTO/status/filter/paging/availability contract. | Chưa làm |
| INT-05 | P1 | Chốt/bổ sung contract cho notification “không có bác sĩ thay thế”; hiện chỉ có mock. | Chưa làm |
| INT-06 | P1 | Hoàn thiện kết nối Patient booking/list/detail/reschedule/cancel/progress và đối chiếu Reception worklist/check-in/assisted booking với backend. | UI Patient/Reception đã merge; adapters booking/list vẫn mock |
| INT-07 | P1 | Bổ sung Doctor appointment/status/follow-up screens theo quyền sở hữu bản ghi. | Chưa làm |
| INT-08 | P2 | Giới hạn màn Admin appointment về availability/mutation-impact; đồng bộ tài liệu Clean Architecture/API contract nếu thực hiện refactor. | Chưa làm |
| INT-09 | P0 | Nối booking/patient appointment adapters đã merge với backend FEAT-002 và đồng bộ profile scope/DTO. | UI đã có; API adapters vẫn mock |

## File tham chiếu

- Đặc tả: `backend/sdd/features/feat-002-appointment-booking/SPEC.md`, `CONTEXT.md`, `openapi.yaml`, `API-CONTRACT.md`, `TASKS.md`.
- FE: `frontend/src/features/appointments/`, `frontend/src/app/(admin)/admin/appointments/page.tsx`, `frontend/src/lib/api/client.ts`.
- BE: `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/appointment/controller/`, `visit/controller/`, `staff/controller/DoctorProfileController.java`.

## Patient portal — tích hợp backend không đổi trường giao diện (2026-10-28)

Yêu cầu giới hạn: không sửa field/label/interaction của màn Patient. Chỉ triển khai adapter khi backend đã có endpoint và có thể đổi DTO ở lớp API; mọi thiếu hụt bắt buộc thêm field hoặc API mới được báo cáo, chưa thay UI.

### Kết quả rà soát

| Route/màn | Kết quả hiện tại | Bằng chứng/điều kiện |
| --- | --- | --- |
| `/portal/appointments/new` — đặt lịch | **Chưa thể nối end-to-end trong giới hạn hiện tại.** | UI đã chọn hồ sơ, dịch vụ, bác sĩ (không bắt buộc) và giờ; backend yêu cầu thêm `purpose` (lý do khám) bắt buộc nhưng màn không có field. `GET /api/v1/appointments/availability` trả `{slots:[{serviceId,doctorId,doctorDisplayName,startTime,endTime,...}]}`, còn UI yêu cầu `{days:[{date,times:[{startAt,doctors}]}],serverNow,slotMinutes}`. Backend cũng chưa có API self-service list hồ sơ liên kết. Phải duyệt bổ sung field lý do khám, và cần backend cung cấp profile list + thống nhất availability projection trước khi chuyển adapter. |
| `/portal/appointments` — danh sách lịch | **Chưa thể nối không đổi màn.** | GET list có scope Patient nhưng trả DTO `AppointmentResponse` dạng ID/time/status/purpose; bảng đang cần tên người khám, dịch vụ, bác sĩ, room, fee, `visitId`, trạng thái/phép thao tác Patient. Backend query mặc định profile chính hiện tại, không filter danh sách profile liên kết. Cần backend Patient projection + family-link authorization và quyết định dữ liệu/columns/phí/actions trước khi bind. |
| `/portal/appointments/[appointmentId]` — chi tiết/hủy lịch | **Hủy có endpoint; chi tiết không khớp. Chưa bật adapter.** | `POST /api/v1/appointments/{id}/cancel` cần body theo `CancelAppointmentRequest` (reason bắt buộc/điều kiện theo implementation), response DTO thiếu Patient view fields. `GET /{id}` có cùng DTO tối giản. UI detail cần appointment display projection và cancel reason flow tương thích. |
| Đổi lịch trong detail | **Backend có API, UI chưa có flow đổi lịch.** | `GET /{id}/reschedule-availability` trả slots phẳng; `PATCH /{id}/reschedule` nhận `startTime` và `reason`. UI Patient hiện có action cancel, chưa có thao tác đổi lịch. Thêm thao tác/field cần báo duyệt trước. |
| Hồ sơ patient `/portal/profiles`, `/new`, `/[profileId]`, `/history` | **Chưa nối được.** | FE giả định `/api/v1/me/patient-profiles` list/detail/create/update/history/link. Backend hiện có `GET /api/v1/patient-profiles` chỉ Receptionist search, `POST` cũng Receptionist create; không có patient self-service CRUD/list/history/link. Màn create hiện thu nhiều trường hơn backend request/response. Không đổi form hoặc cấp API giả lập quyền. |
| Tiến độ `/portal/visits`, `/portal/visits/[visitId]` | **Chưa nối list; có progress detail endpoint nhưng DTO phải đối chiếu.** | Backend chỉ có `GET /api/v1/visits/{visitId}/progress`, không có Patient visits list. Cần endpoint list theo các profile có quyền và kiểm tra payload progress; giữ lại chỉ nếu khớp trường hiện dùng. |
| Kết quả `/portal/results`, `/portal/results/[visitId]` | **Chưa nối.** | Chưa có controller `/visits/results` hoặc `/{visitId}/results`; FE API đang `USE_MOCK=true`. |
| Đơn thuốc `/portal/prescriptions`, `/[prescriptionId]` | **Chưa nối.** | Không tìm thấy Prescription controller; FE có nhánh API thật nhưng `USE_MOCK=true`. |
| Thanh toán `/portal/payments/**` | **Chưa nối.** | Không có billing/payment controller trong backend hiện tại; các adapter patient billing/payment vẫn mock. Cần contract payment + UI flow phù hợp trước. |
| Review `/portal/reviews`, `/new` | **Chưa nối.** | Không có review controller; eligible visit/list/create đang mock. |
| Ảnh `/portal/images`, `/[studyId]` | **Chưa nối.** | Không tìm thấy imaging controller trong backend hiện tại; FE `USE_MOCK=true`. Không dùng trực tiếp Orthanc/DICOM URL vì cần kiểm tra quyền theo study ở backend. |
| Thông báo `/portal/notifications` | **Chưa nối.** | Không có notifications controller; FE mock. |
| Tài khoản `/portal/account`, `/account/password` | **Chưa nối đầy đủ.** | Change-password backend route đã tồn tại; profile/avatar/email endpoints chưa có trong backend controller. Adapter Account đang mock, nên chỉ có thể cân nhắc nối thao tác đổi mật khẩu nếu request/response khớp và UI dùng cùng contract. Chưa đổi vì chưa đối chiếu đầy đủ DTO/error/CSRF. |
| Public service pages `/services`, `/services/[serviceId]`, trang chủ | **Đã gọi backend thật.** | `features/clinic-services/api/publicServiceApi.ts` gọi `publicApiRequest('/api/v1/clinic-services')`; runtime endpoint đã trả 200. Không sửa field màn Patient. |
| Đăng nhập/đăng ký Patient | **Đã gọi backend thật.** | `features/auth/api/authApi.ts` dùng backend session login/register/verify/resend. Runtime lần trước xác nhận backend khởi động; các luồng xác thực cụ thể cần browser session để kiểm tiếp. |

### Thông tin cần chủ sản phẩm duyệt trước khi đổi màn

1. Thêm field **Lý do khám** bắt buộc vào `/portal/appointments/new` để gửi `purpose` theo backend; hoặc backend phải duyệt mặc định nghiệp vụ có ý nghĩa (không tự gửi chuỗi rỗng/giá trị giả).
2. Bổ sung/duyệt API Patient để lấy các profile liên kết và Patient projection cho danh sách/chi tiết appointment (các field tên người khám, dịch vụ/bác sĩ, phí, visit, action). Backend cần lọc record theo quan hệ Patient ở server.
3. Nếu muốn đổi lịch, thêm thao tác và nhập lý do theo API hiện có.
4. Chốt phạm vi các route profile, visit, result, prescription, billing, review, imaging, notifications và account; phần lớn hiện chưa có API backend, cần thiết kế/triển khai backend trước khi adapter có thể trỏ tới nguồn thật.

**Không sửa các field/UI Patient trong lượt này.** Không gọi mutation hay ghi dữ liệu nghiệp vụ. Xác nhận runtime trước đó chỉ chứng minh backend/catalog và route frontend khởi chạy được; chưa chứng minh Patient APIs vì các API tương ứng chưa tồn tại.
