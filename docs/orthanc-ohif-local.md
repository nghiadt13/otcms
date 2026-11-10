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
