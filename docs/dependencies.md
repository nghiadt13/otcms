# Dependency và quy tắc nâng cấp

## Frontend

`frontend/package.json` khóa phiên bản trực tiếp; `package-lock.json` khóa cả cây phụ thuộc. Cài bằng `npm ci` trên Node 24.21.0.

| Gói | Vai trò |
| --- | --- |
| `next` 16.3.6 + `react`/`react-dom` 19.2.8 | App Router và giao diện |
| `@supabase/supabase-js` 2.117.2 | Đăng nhập Supabase Auth trong trình duyệt; không truy cập bảng hồ sơ lâm sàng trực tiếp |
| `typescript` 5.9.3, `@types/*` | Kiểm tra kiểu |
| `tailwindcss` 4.3.3 + PostCSS | CSS giao diện |
| `@base-ui/react` 1.9.0, `class-variance-authority` 0.7.1, `cn` 0.4.0, `lucide-react` 1.57.0, `tw-animate-css` 1.4.0, `shadcn` 4.21.5 | shadcn/ui (style `base-nova`): component chép vào `src/components/ui`, thêm bằng `npx shadcn@latest add <tên>`. CLI mặc định ghi phiên bản `^`; bỏ dấu `^` để khớp quy ước khóa phiên bản. |
| `sonner` 2.0.8, `cmdk` 1.1.1, `next-themes` 0.4.6 | Toast (`lib/messages/notify.ts`), ô lệnh và chuyển theme của shadcn `sonner`/`command`; thêm ở phase 00 màn Admin. |
| `eslint-config-next` 16.3.6 + `eslint` 9.39.5 | Lint theo Next |
| `prettier` 3.9.9 | Format code |
| `vitest` 4.1.12, `@vitejs/plugin-react`, `vite-tsconfig-paths`, `happy-dom` 21.0.3, `@testing-library/react`/`dom`/`jest-dom`/`user-event` | Unit và component test (`npm test`) |

Test dùng `happy-dom` thay vì `jsdom`: `jsdom` 27 kéo ESM-only dependency và không khởi động trên Node 22.11 (cần ≥ 22.12), trong khi `happy-dom` chạy được trên cả Node 22 lẫn 24. Khi cài thêm gói trên Node 22.11, npm 10.9 có thể lỗi `Cannot read properties of null (reading 'edgesOut')`; dùng npm 11 (đi kèm Node 24, hoặc `npx npm@11 install ...`) để lockfile khớp với nhóm.

ESLint 10 hiện không tương thích với các plugin React/import do `eslint-config-next` 16.3.6 kéo vào; nhóm chỉ nâng khi toàn bộ plugin hỗ trợ. Không thêm `@supabase/ssr` cho scaffold vì hiện tại chỉ dùng Auth client-side; khi cần phiên trên Server Components phải thiết kế lại luồng cookie và xác thực. Không thêm OHIF/Cornerstone vào `frontend` vì viewer chạy độc lập.

## Backend

Spring Boot parent 4.1.1 quản lý phiên bản tương thích cho các Spring starter và driver. Java 21; Maven Wrapper 3.9.16. Không ghi phiên bản riêng cho dependency đã có trong BOM.

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
