# OTCMS

Orthopedic Clinic Management System — nền tảng quản lý phòng khám chỉnh hình theo [UC01–UC90](docs/requirements/UC_description.html).

## Công nghệ và phiên bản nền

| Phần | Lựa chọn |
| --- | --- |
| Web | Next.js 16.3.6, React 19.2.8, TypeScript 5.9.3, Node.js 24.21.0 LTS |
| API | Spring Boot 4.1.1, Java 21, Maven Wrapper |
| Dữ liệu và đăng nhập | Supabase PostgreSQL, Supabase Auth; Spring xác thực access token bằng JWKS |
| Ảnh y khoa | Orthanc 26.7.0, OHIF 3.12.18, DICOMweb; Docker Compose cho local |

Backend là một ứng dụng Spring Boot kiểu modular monolith, chia package theo feature và theo lớp (controller → service → repository → entity, xem `backend/CONSTITUTION.md` ARCH-01). Frontend dùng App Router; route chỉ ghép giao diện, nghiệp vụ nằm trong `src/features`. Xem [kiến trúc chi tiết](docs/architecture.md), [quyết định dependency](docs/dependencies.md) và [quy ước làm việc](AGENTS.md).

## Cấu trúc

```text
otcms/
├── AGENTS.md
├── backend/                 Spring Boot REST API
├── frontend/                Next.js App Router
├── infra/imaging/           OHIF config và reverse proxy DICOMweb
├── docs/architecture.md
├── docs/requirements/UC_description.html
└── compose.yaml             Orthanc + OHIF local
```

## Chạy các container trên nhánh final-db

Mở Docker Desktop, sau đó chạy PowerShell tại thư mục gốc repository. Cần Java 21 và Node.js 24 để chạy backend và frontend trên máy.

1. Nếu chưa có cấu hình, tạo file từ mẫu:

   ```powershell
   Copy-Item backend/.env.example backend/.env
   ```

   Điền secret ứng dụng, `MINIO_ROOT_USER` và `MINIO_ROOT_PASSWORD` trong `backend/.env`. Để lưu PDF vào MinIO, đặt `APP_DIAGNOSTIC_REPORT_STORAGE_PROVIDER=minio` (file mẫu mặc định là `local`). Mật khẩu PostgreSQL trong cấu hình ứng dụng phải khớp mật khẩu đã dùng khi tạo database. Không commit file `.env`.

2. Bật PostgreSQL, MinIO, ClamAV, Orthanc, OHIF và imaging gateway cùng lúc:

   ```powershell
   docker compose --env-file backend/.env -f compose.yaml -f docker-compose.final-db.yml up -d
   docker compose --env-file backend/.env -f compose.yaml -f docker-compose.final-db.yml ps
   ```

   File mẫu dùng cổng PostgreSQL 5433; các dịch vụ còn lại dùng MinIO 9000/9001, ClamAV 3310, Orthanc 8042/4242 và gateway OHIF 3001. Nếu cổng đang được container khác sử dụng, dừng container cũ hoặc cấu hình lại cổng. ClamAV có thể cần thời gian khởi tạo trước khi sẵn sàng.

3. Chạy backend trong một PowerShell khác:

   ```powershell
   cd backend
   $env:SPRING_PROFILES_ACTIVE = "final-db"
   .\mvnw.cmd spring-boot:run
   ```

4. Chạy frontend trong một PowerShell khác:

   ```powershell
   cd frontend
   npm ci
   npm run dev
   ```

   Mở `http://localhost:3000`. Cấu hình `frontend/.env.local` theo môi trường của nhóm. Nếu dùng proxy local với `NEXT_BASE_PATH=/otcms`, đặt `OTCMS_DEV_API_PROXY=true`, `NEXT_PUBLIC_API_BASE_URL=` và `NEXT_PUBLIC_OHIF_VIEWER_PATH=/viewer`, rồi khởi động lại frontend.

Docker Compose ở đây dùng cho thử nghiệm local với dữ liệu DICOM mẫu. Không dùng `down -v` nếu cần giữ dữ liệu trong các volume.

## Chuẩn bị Supabase

Mục này mô tả cấu hình Supabase của môi trường cũ. Nhánh `final-db` chạy local theo hướng dẫn Docker ở trên.

1. Tạo Supabase project và lấy Project URL cùng publishable key cho frontend. Không dùng secret/service-role key ở frontend.
2. Dùng chuỗi kết nối PostgreSQL dạng JDBC cho backend (`jdbc:postgresql://.../postgres?sslmode=require`). Nếu máy chỉ có IPv4, dùng **session pooler**, không dùng transaction pooler cho kết nối JPA dài hạn.
3. Dùng asymmetric JWT signing key để Spring có thể kiểm tra token qua JWKS. Đặt issuer là `https://<project-ref>.supabase.co/auth/v1` và JWKS là `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`. Dự án cũ dùng HS256 có thể không có public key trên JWKS; khi đó cần chuyển khóa hoặc thiết kế xác thực khác trước khi gọi API.
4. Tạo file `frontend/.env.local` từ `frontend/.env.example`; tạo `backend/.env` từ `backend/.env.example` và điền giá trị thật. Hai file này đã bị Git bỏ qua.

Chưa có migration nghiệp vụ vì thiết kế bảng bệnh nhân, lượt khám và study phải được chốt theo từng UC. Flyway đã bật, Hibernate ở chế độ `validate`; migration đầu tiên đặt tại `backend/src/main/resources/db/migration/V1__...sql`.

## Chạy local

- Frontend: trong `frontend`, chạy `npm ci` rồi `npm run dev`; mở `http://localhost:3000`.
- Backend và các dịch vụ Docker: làm theo mục **Chạy các container trên nhánh final-db** ở trên. API chạy ở `http://localhost:8080`, trạng thái ở `/api/v1/system/status`, OpenAPI ở `/swagger-ui/index.html`.

Node 24.21.0 là chuẩn cho nhóm; Node 22.11.0 có thể khiến dependency ESLint báo không tương thích. Máy không cần cài Maven toàn cục vì repository có Maven Wrapper.

## Ranh giới bảo mật hiện tại

Compose cho Orthanc/OHIF chỉ phục vụ **thử nghiệm local với dữ liệu giả**. Các cổng chỉ bind vào `127.0.0.1`, nhưng viewer local chưa kiểm tra quyền theo bệnh nhân hoặc study. Trước khi dùng dữ liệu thật hoặc mở ra mạng, phải triển khai `ImagingArchivePort`, xác thực người dùng, kiểm tra quan hệ bệnh nhân–lượt khám–study và trạng thái phát hành tại backend; DICOMweb phải đi qua cổng có kiểm tra quyền. Không coi route `/workspace` của frontend là cơ chế phân quyền. Backend hiện mới bảo vệ các API tương lai bằng JWT; phân quyền nghiệp vụ phải được thêm trong từng use case.

## Tài liệu nguồn

- [Next.js requirements](https://nextjs.org/docs/app/getting-started/installation)
- [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Supabase JWT/JWKS](https://supabase.com/docs/guides/auth/jwts)
- [Orthanc Docker image](https://orthanc.uclouvain.be/book/users/docker-orthancteam.html)
- [OHIF Docker deployment](https://docs.ohif.org/deployment/docker/)
