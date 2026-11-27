# Chạy Orthanc và OHIF local

Hướng dẫn này chạy Orthanc và OHIF bằng Docker Compose, rồi mở viewer từ OTCMS để quyền xem được backend kiểm tra theo study. Chỉ dùng DICOM giả hoặc đã ẩn danh; Compose local không phù hợp để xử lý ảnh bệnh nhân thật.

## 1. Cấu hình backend và frontend

Trong `backend/.env`, bật kho ảnh local:

```dotenv
OTCMS_IMAGING_ENABLED=true
OTCMS_IMAGING_MAX_UPLOAD_BYTES=80000000
OTCMS_IMAGING_MAX_UPLOAD_FILES=128
OTCMS_IMAGING_MAX_CONCURRENT_UPLOADS=1
OTCMS_IMAGING_ORTHANC_BASE_URI=http://localhost:8042
```

Trong `frontend/.env.local`, dùng cùng origin của gateway để cookie cấp quyền DICOM được gửi tới `/dicom-web/`:

```dotenv
NEXT_BASE_PATH=/otcms
NEXT_PUBLIC_API_BASE_URL=http://localhost:3001
NEXT_PUBLIC_OHIF_VIEWER_PATH=/viewer
```

Không bật các cờ bỏ qua kiểm tra định danh DICOM với dữ liệu bệnh nhân thật. Khởi động lại backend và Next.js sau khi đổi biến môi trường.

Định danh DICOM hiện được lưu cùng hồ sơ bệnh nhân và chỉ định hình ảnh (migration V128). Backend tự cấp accession duy nhất cho mỗi chỉ định và chỉ trả các tag đó cho kỹ thuật viên đang được phân công; không cần thêm mapping accession vào cấu hình cho từng order.

## Tạo ZIP DICOM mẫu cho chỉ định

`backend/scripts/rekey-dicom-zip.ps1` có thể lấy PatientID/Issuer và AccessionNumber trực tiếp từ backend. Cấu hình URL API; có thể truyền access token đang hoạt động hoặc để script mở lời nhắc đăng nhập kỹ thuật viên:

```powershell
$env:OTCMS_API_BASE_URL = 'http://localhost:3001'
# Tùy chọn; nếu bỏ qua, script sẽ hỏi đăng nhập kỹ thuật viên.
# $env:OTCMS_ACCESS_TOKEN = '<active access token>'
```

Sau khi chọn chỉ định đang được phân công, truyền `RequestId` cho script bằng UUID gốc hoặc mã hiển thị `CD-XXXXXXXX` (ví dụ `CD-3A3D3C05`). Với mã ngắn, script tìm UUID trong danh sách chỉ định đang được giao cho kỹ thuật viên; nếu có nhiều kết quả trùng 8 ký tự đầu, hãy dùng UUID đầy đủ. Mã `CD-…` chỉ là mã hiển thị, không cần thêm cột vào database. Backend tự tìm lượt chụp hiện hành và định danh DICOM. Mỗi lần chạy cần một đường dẫn đầu ra mới; script tự tạo Study, Series và SOP Instance UID mới:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\rekey-dicom-zip.ps1 `
  -InputZip .\output\TEST-MR-KNEE-demo-12.zip `
  -OutputZip .\output\TEST-MR-KNEE-CD-3A3D3C05.zip `
  -RequestId 'CD-3A3D3C05'
```

Không commit hoặc chia sẻ access token. ZIP được sinh cho đúng chỉ định được chọn và cần upload vào chính chỉ định đó.

## 2. Khởi động Docker

Mở Docker Desktop, từ thư mục gốc repository chạy:

```powershell
docker compose up -d orthanc ohif imaging-gateway
docker compose ps
```

- Orthanc REST: `http://localhost:8042`
- Orthanc Explorer: `http://localhost:8042/ui/app/`
- Cổng OTCMS + OHIF: `http://localhost:3001`

Chạy backend bằng `./scripts/start-backend.ps1` ở thư mục gốc và frontend bằng `npm run dev` trong `frontend`.

## 3. Mở study trong OHIF

Đăng nhập OTCMS tại `http://localhost:3001/otcms`, mở công việc upload của kỹ thuật viên, chọn chỉ định được phân công, tải DICOM lên và gửi study. Nút **Mở OHIF Viewer** chỉ xuất hiện sau khi backend xác nhận study đã liên kết. Bác sĩ mở cùng study từ màn hình cận lâm sàng khi study đã sẵn sàng.

Không mở trực tiếp `http://localhost:3001/viewer` để xem study trong luồng ứng dụng: grant được cấp cho từng tài khoản và study, rồi gateway chuyển DICOMweb qua backend để kiểm tra lại quyền. Upload trực tiếp từ Orthanc Explorer chỉ đưa file vào archive, không liên kết study với chỉ định OTCMS.

## 4. Dừng dịch vụ

```powershell
docker compose down
```

Lệnh trên giữ lại volume `orthanc-data`; không xóa volume nếu muốn giữ study giả đã nạp.
