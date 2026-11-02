# OTCMS — Quy ước kiến trúc và mã nguồn

Đây là quy ước làm việc của repository OTCMS. Cập nhật tài liệu khi thay đổi kiến trúc hoặc cách đặt tên.

## Nguồn yêu cầu

- Căn cứ cho phạm vi hiện tại là bản UC01–UC78: mỗi UC là một file `docs/requirements/UC<số>-<tên>.md`, còn nội dung Business Rule (BR) và Application Message (MSG) mà UC tham chiếu nằm duy nhất trong `docs/appendix-rules.md`. Các UC đang được bổ sung dần; với UC chưa có file trong `docs/requirements/`, số UC ghi trong code (ví dụ `ucs` ở `adminNavigation.ts`) chỉ là tạm: không triển khai luật nghiệp vụ dựa vào chúng khi chưa xác nhận với nhóm, và cập nhật lại số UC khi file được thêm. Khi tài liệu khác mâu thuẫn, xác nhận lại với nhóm trước khi triển khai.
- Phạm vi UC01–UC78 bao gồm quản lý hồ sơ giới thiệu bác sĩ, ca làm việc của bác sĩ/lễ tân/kỹ thuật viên và giờ nhận lịch khám của bác sĩ; loại quản lý kho/vật tư, hồ sơ nhân sự, nghỉ phép, lương và màn hình tự gán quyền. Không khôi phục các tính năng bị loại chỉ vì chúng xuất hiện trong tài liệu cũ.
- Vai trò phòng khám là cố định theo SRS. Quyền phải được kiểm tra tại backend cho từng thao tác và bản ghi; việc ẩn nút hoặc trang ở frontend chỉ hỗ trợ giao diện.

### Tra cứu UC, BR, MSG nhanh

UC spec nằm ở `docs/requirements/UC<số>-<tên>.md`, mỗi file có frontmatter `id`, `name`, `br: [...]`, `msg: [...]`; trong thân chỉ ghi mã `[BR-xx]`/`[MSG-xx]`. Nội dung BR/MSG chỉ có trong `docs/appendix-rules.md`, mỗi mã là một dòng bảng bắt đầu bằng `| BR-xx |` hoặc `| MSG-xx |`. **Không đọc cả `appendix-rules.md`**; grep đúng mã cần dùng (chạy từ thư mục `otcms`, dùng Bash/Git Bash):

```bash
# Danh mục UC kèm mã BR/MSG (không mở từng file)
grep -H -E '^(id|name|br|msg):' docs/requirements/*.md

# Nội dung BR/MSG cụ thể (escape dấu | cho đúng dòng bảng)
grep -E '^\| (BR-02|MSG-01) \|' docs/appendix-rules.md

# Toàn bộ BR/MSG của một UC, một lệnh
codes=$(grep -hE '^(br|msg):' docs/requirements/UC01-*.md | grep -oE '(BR|MSG)-[0-9]+' | paste -sd'|')
grep -E "^\| ($codes) \|" docs/appendix-rules.md

# Ngược lại: UC nào dùng một BR/MSG (-w để BR-2 không khớp BR-25)
grep -lw 'BR-25' docs/requirements/*.md
```

File UC ngắn (~50 dòng) nên đọc trọn khi cần luồng; chỉ grep appendix. Mã luôn có 2 chữ số (`BR-02`, không phải `BR-2`).

## Kiến trúc backend

- Xây một ứng dụng Spring Boot REST theo **modular monolith + Spring layered architecture**, nguồn duyệt là `backend/CONSTITUTION.md` mục ARCH-01 và ARCH-02 (RFC-001). Dùng một Maven module; chia Java package theo **feature nghiệp vụ** như `appointment`, `visit`, `billing`, `review`, `notification`. Không chia package nghiệp vụ theo actor như `doctor` hay `receptionist` vì nhiều actor cùng dùng một feature.
- Bên trong feature, chia theo lớp: `controller`, `service` (interface) cùng `service/impl`, `repository`, `model/entity` (JPA entity), `model/dto` (request/response), `exception`; thêm `mapper`, `config`, `integration` (client dịch vụ ngoài), `security`, `validation` khi cần. Package mẫu để làm theo: `review`, `notification`. Một UC là một thao tác trong service, không mặc định là một package riêng.
- Chiều phụ thuộc là `controller → service → repository → entity`. Controller chỉ xử lý HTTP (nhận request, lấy người dùng đăng nhập, validate/map, gọi service, trả DTO); không gọi repository hoặc `EntityManager`, không chứa luật nghiệp vụ, không trả entity. Service giữ điều phối, luật nghiệp vụ, kiểm tra quyền trên bản ghi, idempotency và ranh giới transaction; không biết mã HTTP (lỗi nghiệp vụ là exception trong `exception/`, `@RestControllerAdvice` ánh xạ sang HTTP). Repository chỉ làm persistence bằng Spring Data JPA hoặc truy vấn có tham số; entity không phụ thuộc web, DTO hay security. Bean dùng constructor injection.
- Một Maven module chưa tự ngăn import sai giữa các package; bảo vệ chiều phụ thuộc bằng code review và test cấu trúc đơn giản (JUnit + reflection). Không thêm công cụ kiểm tra kiến trúc (ví dụ ArchUnit) khi nhóm chưa duyệt.
- Feature khác dùng service được công khai của feature sở hữu dữ liệu, không truy cập repository hay bảng của feature đó như một lối tắt. Nếu có lý do đọc chéo (ví dụ báo cáo), ghi rõ trong PR để NghiaDT duyệt.
- Dùng cấu trúc layered này cho mọi package backend. Muốn dùng cấu trúc khác phải có RFC được duyệt (theo `backend/CONSTITUTION.md`).
- Đặt kiểm tra quyền truy cập theo vai trò và quyền sở hữu hồ sơ ở backend. Vai trò ứng dụng nằm trong dữ liệu tài khoản/quy tắc nghiệp vụ; không đồng nhất nó với claim `role` PostgreSQL của Supabase Auth.
- PostgreSQL/Supabase lưu dữ liệu nghiệp vụ và tham chiếu study. File DICOM nằm trong kho ảnh sau một interface riêng (ví dụ `ImagingArchivePort`) đặt ở `integration/`; backend kiểm tra bệnh nhân, lượt khám, study và trạng thái phát hành trước khi cấp quyền xem.

## Kiến trúc frontend

- Dùng NextJS App Router. `src/app` sở hữu URL, `page.tsx`, `layout.tsx` và trạng thái route. `src/features/<feature>` sở hữu component, hook, schema và lời gọi API của nghiệp vụ đó; component dùng chung đặt trong `src/components`.
- Route theo khu vực sử dụng (`public`, `patient`, `staff`, `admin`) nằm trong route group `(khu-vực)` có root layout và CSS riêng, để thay đổi giao diện của khu này không lan sang khu khác (xem `docs/architecture.md`); code tái sử dụng chia theo feature. Giữ `page.tsx` mỏng: ghép giao diện và gọi feature, không đặt quy tắc nghiệp vụ trong route.
- **Rào chắn phụ thuộc frontend** (ESLint `no-restricted-imports` trong `eslint.config.mjs`, `npm run lint` sẽ báo lỗi): một feature chỉ import chính nó và mã dùng chung (`src/components`, `src/lib`, `src/hooks`); không import feature khác hay `src/app`. Muốn kết hợp nhiều feature thì ghép ở `app/` hoặc ở `admin-shell` / `imaging-shell` (các lớp ghép khung giao diện, được import feature nghiệp vụ). Mã dùng chung không import feature hay `app/`. Import ngoài feature dùng alias `@/`. Feature mới tự được bảo vệ vì danh sách lấy từ thư mục `src/features`; khi thêm một lớp ghép khác, thêm tên vào `COMPOSITION_FEATURES` trong file cấu hình. Feature nghiệp vụ không import ngược lớp shell.
- **Trang đặc biệt của Next**: mỗi khu có `error.tsx` (Client Component; prop là `error` và `retry`, **không** phải `reset` như Next cũ) và, khi màn dùng dữ liệu bất đồng bộ phía server, `loading.tsx` cùng `not-found.tsx` đặt trong thư mục khu. Vì có nhiều root layout nên 404 cho URL không khớp route nào do `src/app/global-not-found.tsx` xử lý (bật bằng `experimental.globalNotFound` trong `next.config.ts`); lỗi ở chính root layout do `src/app/global-error.tsx`. Hai file `global-*` tự dựng `<html>` và tự nạp CSS.
- **Test frontend**: Vitest + Testing Library, môi trường `happy-dom`, file `*.test.ts(x)` đặt cạnh mã nguồn, thiết lập chung ở `src/test/setup.ts`. Test hook/component đi qua lớp API giả như màn hình thật; khi nối backend thì mock ở ranh giới `apiRequest`, không mock hook. Async Server Component không test được bằng Vitest, dùng E2E (chưa thiết lập).
- **Giao diện**: mọi màn hình mới theo [`docs/ui-guidelines.md`](docs/ui-guidelines.md) (bảng màu Teal + Coral, font Be Vietnam Pro, thang chữ và khoảng cách, bố cục `PageHeader` → lọc → nội dung, đủ trạng thái tải/trống/lỗi). Khu admin chỉ dùng token màu ngữ nghĩa, không viết màu Tailwind thô hay mã hex trong component. Đổi màu/font thì sửa `src/styles/brand-palette.css` và cập nhật tài liệu đó trong cùng thay đổi.
- **Form và validation**: chưa chọn thư viện vì chưa có màn nào cần. Khi màn đầu tiên cần form nhiều trường hoặc validation dùng chung với backend, chọn **một** giải pháp cho cả dự án, đặt schema ở `src/features/<feature>/schema/`, rồi cập nhật mục này và `docs/dependencies.md` trong cùng thay đổi. Cho đến lúc đó giữ state cục bộ như `RoomFormDialog`.
- Gọi Spring Boot qua một API client chung. Không nhân đôi workflow nghiệp vụ trong Next Route Handlers hoặc truy cập trực tiếp bảng hồ sơ bệnh nhân, hóa đơn, study từ trình duyệt.
- Khi tích hợp viewer, chạy OHIF độc lập với dependency của NextJS. Mọi đường truy cập DICOMweb có dữ liệu người dùng phải đi qua cơ chế xác thực và kiểm tra quyền theo study. Compose local hiện chỉ dùng ảnh giả trên `127.0.0.1`, chưa có phân quyền.

## Đặt tên

| Thành phần | Quy ước | Ví dụ |
| --- | --- | --- |
| Java package | chữ thường, theo feature, tên đơn | `appointment`, `imaging` |
| Java class/interface | `PascalCase`, tên nêu trách nhiệm | `ReviewService`, `NotificationPublisher` |
| Service | interface `<Entity>Service` (hoặc tên theo trách nhiệm) ở `service/`, cài đặt `<Entity>ServiceImpl` ở `service/impl/` | `ReviewService`, `ReviewServiceImpl` |
| Repository và entity | `<Entity>Repository` (Spring Data JPA) ở `repository/`, `<Entity>JpaEntity` ở `model/entity/` | `PatientReviewRepository`, `PatientReviewJpaEntity` |
| HTTP và DTO | `<Entity>Controller` ở `controller/`; DTO là record gom trong `<Feature>Dtos` hoặc `<Action><Entity>Request`, `<Entity>Response` ở `model/dto/` | `AdminReviewController`, `ReviewDtos` |
| Exception | `<Feature>OperationException` mang mã MSG; `<Feature>ApiExceptionHandler` ánh xạ sang HTTP, đặt ở `exception/` | `ReviewOperationException`, `ReviewApiExceptionHandler` |
| Client dịch vụ ngoài | tên nêu nhà cung cấp, đặt ở `integration/<nhà-cung-cấp>/` | `PayOsPaymentGateway` |
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
- Trong `frontend`, dùng `npm ci`, `npm run dev`, `npm run lint`, `npm test` (chạy một lần; `npm run test:watch` để theo dõi), `npm run build`. Node.js 24 LTS là phiên bản chuẩn của nhóm.
- Trong `backend`, dùng `./mvnw spring-boot:run` hoặc `./mvnw package -DskipTests` (PowerShell: `./mvnw.cmd`). Java 21 là phiên bản chuẩn của nhóm; build dừng nếu chạy bằng JDK khác (Maven Enforcer).
- Xem `README.md` để cấu hình môi trường local và `docs/architecture.md` để biết cây thư mục theo feature.
