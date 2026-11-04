# Khu bác sĩ (`/doctor`) — luồng khám bệnh

Căn cứ: R3 UC35–UC45, UC49, UC50, UC52 (mục 1.4.2.5, BR-13, BR-32–BR-47, BR-65). Đối chiếu R3 với BE `product`: `plans/reports/doctor-fe-261104-2148-r3-vs-be-coverage.md` (thư mục `plans/` ở gốc workspace, không nằm trong repo).

## Cấu trúc

- Shell và menu: `src/features/doctor-shell/` (sidebar, header, danh sách tab của workspace lượt khám).
- Màn khám: `src/features/doctor-exam/` (`types`, `schema`, `api`, `hooks`, `components`). Route mỏng ở `src/app/(doctor)/doctor/...`.
- Route: `/doctor` (bảng điều khiển), `/doctor/today` (bệnh nhân hôm nay), `/doctor/visits/[visitId]/{,records,examination,treatment,paraclinical,closing}`.
- `/doctor/imaging`, `/doctor/schedule`, `/doctor/account`, `/doctor/notifications` còn là trang chờ của shell.

## Lớp dữ liệu

Mọi màn gọi qua `DoctorExamApi` (`api/doctorExamApi.ts`), có hai bản:

| Bản | Khi nào | Ghi chú |
| --- | --- | --- |
| live (`doctorExamLive*.ts`) | mặc định | `apiRequest` tới BE; hợp đồng viết từ source BE, khóa bằng `doctorExamLiveApi.test.ts` |
| demo (`doctorExamDemo*.ts`) | `NEXT_PUBLIC_DOCTOR_DEMO=true`, hoặc công tắc "Dữ liệu mẫu" ở header (không phải production) | dữ liệu trong bộ nhớ, mất khi tải lại; chỉ để xem/chạy thử giao diện khi BE thiếu |

Nguồn bệnh nhân hôm nay: `GET /appointments` (hẹn, kể cả chưa đến) ghép `GET /doctor/visits/active` (lượt đang khám, gồm vãng lai, kèm họ tên/tuổi/giới; xem `api/doctorExamLiveActive.ts`). Endpoint thứ hai lỗi thì danh sách vẫn dựng từ lịch hẹn.

`capabilities` của mỗi bản cho biết việc nào BE đã có. Việc BE chưa có thì màn vẫn dựng, nút lưu bị khóa và có khung "Backend chưa hỗ trợ" (`BackendGapNotice`). Khi BE có endpoint: thay thân hàm trong bản live, đổi cờ `capabilities`, không đổi màn.

Render cần biết bản API (ví dụ đọc `capabilities`) phải dùng `useDoctorExamApi()`, không gọi `getDoctorExamApi()` trong lúc render, vì server không có `localStorage` và sẽ lệch lúc hydrate.

## Việc còn chờ BE

Xét nghiệm (chỉ định + kết quả), đơn thuốc, hồ sơ lượt khám cũ, phiếu kết quả/sửa kết quả, lượt chờ kết luận (Awaiting Conclusion) và vãng lai chưa bắt đầu khám, mã bệnh nhân, liệt kê chỉ định lâm sàng theo lượt, hoàn tất khám đúng BR-33, danh mục ICD-10 (đang là hằng số `schema/icd10Catalog.ts`), "hướng điều trị" (BR-40), link xem ảnh DICOM cho bác sĩ (`NEXT_PUBLIC_OHIF_DEV_URL` chỉ dùng khi dev).

## Kiểm thử

`npm test -- src/features/doctor-exam` (schema, hợp đồng live qua `apiRequest` giả, luật bản demo, màn hình chạy trên bản demo).
