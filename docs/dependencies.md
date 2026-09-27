# Dependency và quy tắc nâng cấp

## Frontend

`frontend/package.json` khóa phiên bản trực tiếp; `package-lock.json` khóa cả cây phụ thuộc. Cài bằng `npm ci` trên Node 24.21.0.

| Gói | Vai trò |
| --- | --- |
| `next` 16.3.6 + `react`/`react-dom` 19.2.8 | App Router và giao diện |
| `@supabase/supabase-js` 2.117.2 | Đăng nhập Supabase Auth trong trình duyệt; không truy cập bảng hồ sơ lâm sàng trực tiếp |
| `typescript` 5.9.3, `@types/*` | Kiểm tra kiểu |
| `tailwindcss` 4.3.3 + PostCSS | CSS giao diện |
| `eslint-config-next` 16.3.6 + `eslint` 9.39.5 | Lint theo Next |
| `prettier` 3.9.9 | Format code |

ESLint 10 hiện không tương thích với các plugin React/import do `eslint-config-next` 16.3.6 kéo vào; nhóm chỉ nâng khi toàn bộ plugin hỗ trợ. Không thêm `@supabase/ssr` cho scaffold vì hiện tại chỉ dùng Auth client-side; khi cần phiên trên Server Components phải thiết kế lại luồng cookie và xác thực. Không thêm OHIF/Cornerstone vào `frontend` vì viewer chạy độc lập.

## Backend

Spring Boot parent 4.1.1 quản lý phiên bản tương thích cho các Spring starter và driver. Java 17; Maven Wrapper 3.9.16. Không ghi phiên bản riêng cho dependency đã có trong BOM.

| Dependency | Vai trò |
| --- | --- |
| `spring-boot-starter-webmvc` | REST API |
| `spring-boot-starter-data-jpa` | Persistence adapter với PostgreSQL |
| `spring-boot-starter-validation` | Kiểm tra request DTO |
| `spring-boot-starter-security` + `spring-boot-starter-security-oauth2-resource-server` | Xác thực bearer JWT và kiểm tra quyền tại API |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | Migration schema PostgreSQL |
| `org.postgresql:postgresql` | JDBC driver runtime |
| `spring-boot-starter-actuator` | Health endpoint |
| `springdoc-openapi-starter-webmvc-ui` 3.1.1 | OpenAPI/Swagger UI, nhánh phù hợp Spring Boot 4 |

Không dùng Java SDK Supabase cho truy cập dữ liệu: backend kết nối PostgreSQL qua JDBC và chỉ tin JWT sau khi xác minh chữ ký/JWKS. Các test starter do Spring Initializr tạo được giữ cho UC tương lai; hiện repository chưa có test nghiệp vụ.

## Imaging

Compose khóa `orthancteam/orthanc:26.7.0`, `ohif/app:v3.12.18` và `nginx:1.28.0-alpine`. Khi nâng, kiểm tra DICOMweb và viewer cùng nhau. Không dùng tag `latest`; không đưa DICOM thật vào Git hoặc Compose local chưa có phân quyền.
