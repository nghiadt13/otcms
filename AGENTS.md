# OTCMS — Quy ước kiến trúc và mã nguồn

Đây là quy ước làm việc của repository OTCMS. Cập nhật tài liệu khi thay đổi kiến trúc hoặc cách đặt tên.

## Nguồn yêu cầu

- Dùng `docs/requirements/UC_description.html` (bản UC01–UC90) làm căn cứ cho phạm vi hiện tại. Khi tài liệu khác mâu thuẫn, xác nhận lại với nhóm trước khi triển khai.
- Phạm vi UC01–UC90 loại quản lý kho/vật tư, nhân sự, lịch làm việc, nghỉ phép, lương và màn hình tự gán quyền. Không khôi phục các tính năng này chỉ vì chúng xuất hiện trong tài liệu cũ.
- Vai trò phòng khám là cố định theo SRS. Quyền phải được kiểm tra tại backend cho từng thao tác và bản ghi; việc ẩn nút hoặc trang ở frontend chỉ hỗ trợ giao diện.

## Kiến trúc backend

- Xây một ứng dụng Spring Boot REST theo Clean Architecture trong modular monolith. Dùng một Maven module ban đầu; chia Java package theo **feature nghiệp vụ** như `appointment`, `visit`, `clinical`, `imaging`, `billing`. Không chia package nghiệp vụ theo actor như `doctor` hay `receptionist` vì nhiều actor cùng dùng một feature.
- Bên trong feature, dùng `domain`, `application`, `adapter/in/web`, `adapter/out/persistence` và adapter tích hợp khác khi cần. Một UC là một thao tác/use case, không mặc định là một package riêng.
- `domain` chứa mô hình và luật nghiệp vụ thuần Java; không import Spring, JPA, HTTP hay SDK bên ngoài. `application` điều phối use case và khai báo port. Adapter nhận/trả HTTP, lưu dữ liệu hoặc gọi dịch vụ ngoài và phụ thuộc vào port. Chiều phụ thuộc đi vào `application`/`domain`.
- Một Maven module chưa tự ngăn import sai giữa các package. Khi tạo mã nguồn, bảo vệ chiều phụ thuộc bằng code review và kiểm tra kiến trúc tự động nếu nhóm thống nhất dùng công cụ đó.
- Controller gọi use case qua port đầu vào; không gọi trực tiếp JPA repository hoặc Orthanc. Feature khác dùng API/use case được công khai của feature sở hữu dữ liệu, không truy cập adapter hay bảng của feature đó như một lối tắt.
- Đặt kiểm tra quyền truy cập theo vai trò và quyền sở hữu hồ sơ ở backend. Vai trò ứng dụng nằm trong dữ liệu tài khoản/quy tắc nghiệp vụ; không đồng nhất nó với claim `role` PostgreSQL của Supabase Auth.
- PostgreSQL/Supabase lưu dữ liệu nghiệp vụ và tham chiếu study. File DICOM nằm trong kho ảnh sau `ImagingArchivePort`; backend kiểm tra bệnh nhân, lượt khám, study và trạng thái phát hành trước khi cấp quyền xem.

## Kiến trúc frontend

- Dùng NextJS App Router. `src/app` sở hữu URL, `page.tsx`, `layout.tsx` và trạng thái route. `src/features/<feature>` sở hữu component, hook, schema và lời gọi API của nghiệp vụ đó; component dùng chung đặt trong `src/components`.
- Route có thể theo khu vực sử dụng (`patient`, `staff`, `admin`); code tái sử dụng chia theo feature. Giữ `page.tsx` mỏng: ghép giao diện và gọi feature, không đặt quy tắc nghiệp vụ trong route.
- Gọi Spring Boot qua một API client chung. Không nhân đôi workflow nghiệp vụ trong Next Route Handlers hoặc truy cập trực tiếp bảng hồ sơ bệnh nhân, hóa đơn, study từ trình duyệt.
- Khi tích hợp viewer, chạy OHIF độc lập với dependency của NextJS. Mọi đường truy cập DICOMweb có dữ liệu người dùng phải đi qua cơ chế xác thực và kiểm tra quyền theo study. Compose local hiện chỉ dùng ảnh giả trên `127.0.0.1`, chưa có phân quyền.

## Đặt tên

| Thành phần | Quy ước | Ví dụ |
| --- | --- | --- |
| Java package | chữ thường, theo feature, tên đơn | `appointment`, `imaging` |
| Java class/interface | `PascalCase`, tên nêu trách nhiệm | `Appointment`, `ReleaseStudyUseCase` |
| Use case đầu vào và thực thi | `<Action><Entity>UseCase` và `<Action><Entity>Handler` | `CreateAppointmentUseCase`, `CreateAppointmentHandler` |
| Port đầu ra và adapter | `<Entity>Repository` hoặc `<Purpose>Port`; adapter nêu công nghệ | `ImagingArchivePort`, `OrthancImagingArchiveAdapter` |
| HTTP và JPA | `<Entity>Controller`, `<Action><Entity>Request`, `<Entity>Response`, `<Entity>JpaEntity` | `AppointmentController`, `CreateAppointmentRequest` |
| React component | file và export dùng `PascalCase` | `AppointmentForm.tsx` |
| React hook | `use` + `PascalCase` | `useRoomQueue.ts` |
| Frontend feature/folder | `kebab-case`; route file theo quy ước NextJS | `clinical-records/`, `page.tsx` |
| Utility/TypeScript module | `camelCase` hoặc tên rõ chức năng, nhất quán trong feature | `formatVisitTime.ts`, `appointmentApi.ts` |
| PostgreSQL | bảng/cột `snake_case`; migration Flyway `V<version>__<description>.sql` | `imaging_studies`, `patient_id`, `V1__create_accounts.sql` |

Tên class phải phản ánh hành động hoặc khái niệm nghiệp vụ. Tránh các tên chung như `CommonService`, `Utils`, `DataManager` khi có thể đặt tên cụ thể.

## API, cấu hình và thay đổi schema

- URL REST dùng `/api/v1/` và danh từ số nhiều; request/response là DTO riêng, không trả JPA entity trực tiếp. Khi triển khai endpoint nghiệp vụ đầu tiên, thống nhất kiểu lỗi chung và áp dụng nhất quán.
- Dùng Flyway để thay đổi schema. Migration đã áp dụng chỉ được sửa bằng migration mới; Hibernate `ddl-auto=validate` khi kết nối database dùng chung.
- Giữ bí mật trong biến môi trường; `.env.example` chỉ có tên biến và giá trị mẫu. Database credential, Supabase secret key và Orthanc credential chỉ ở backend/infra; `NEXT_PUBLIC_*` chỉ dành cho giá trị được phép công khai.
- `frontend/package-lock.json`, Maven Wrapper và phiên bản image trong Compose là nguồn phiên bản đã khóa. Khi nâng cấp dependency, nâng theo một nhóm tương thích và cập nhật lockfile; không dùng tag `latest` cho dịch vụ trong Compose.
- `.editorconfig` là chuẩn thụt dòng cho toàn repository; frontend dùng ESLint/Prettier. Đối với Java, giữ định dạng 4 spaces và tên theo bảng trên; chỉ thêm formatter tự động khi nhóm chốt công cụ/phiên bản tương thích. Không đặt quy tắc thụt dòng trái với cấu hình tool.

## Khi hoàn thành một thay đổi

- Với thay đổi nghiệp vụ, đối chiếu UC liên quan và kiểm tra quyền của các actor có liên quan, nhất là truy cập hồ sơ và phát hành ảnh.
- Với thay đổi ranh giới module, quy tắc đặt tên hoặc cách quản lý dependency, cập nhật tài liệu này trong cùng thay đổi.
- Trong `frontend`, dùng `npm ci`, `npm run dev`, `npm run lint`, `npm run build`. Node.js 24 LTS là phiên bản chuẩn của nhóm.
- Trong `backend`, dùng `./mvnw spring-boot:run` hoặc `./mvnw package -DskipTests` (PowerShell: `./mvnw.cmd`). Java 17 là phiên bản chuẩn của nhóm.
- Xem `README.md` để cấu hình môi trường local và `docs/architecture.md` để biết cây thư mục theo feature.
