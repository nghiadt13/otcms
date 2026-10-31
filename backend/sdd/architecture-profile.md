# Architecture Profile
# Version: 1.3.0
# Status: APPROVED
# Mục đích: Nguồn sự thật tech stack và kiến trúc canonical cho artifact do SDD/ADD sinh ra.

> Repository hiện đã có bootstrap Java 21/Spring Boot/Maven và dependency cho Spring Security, Spring Data JPA, Flyway, PostgreSQL, validation và Web MVC. RFC-001 đã được Human reviewer Long duyệt ngày 2026-09-25 và các quyết định được áp dụng vào `CONSTITUTION.md` v2.0.0. Profile v1.3.0 bổ sung modular-monolith và authentication binding từ FEAT-001 Context v0.5.4; Human reviewer Long đã phê duyệt profile ngày 2026-09-29 với các follow-up được ghi bên dưới.

---

## 1. Thứ tự chọn binding

Skill resolve quyết định công nghệ theo thứ tự:

1. Profile này khi `Status: APPROVED`.
2. Repository evidence rõ ràng: manifest, lockfile, runtime config, source layout.
3. Input explicit của command, ví dụ `/sdd-init --stack="..."`.
4. Core-only starter baseline bên dưới.

Mâu thuẫn giữa các nguồn là **blocking configuration gap**. Skill phải lưu AI recommendation và yêu cầu Human reviewer có thẩm quyền quyết định; không được chọn một bên hoặc sinh artifact adapter-specific.

---

## 2. Binding OTCMS và repository evidence

| Mối quan tâm | Binding OTCMS | Status | Evidence / thiếu hụt |
| :--- | :--- | :--- | :--- |
| Ngôn ngữ và runtime | Java 21 | APPROVED / OBSERVED | `pom.xml` khai báo `java.version=21`; có Java bootstrap source |
| Framework / transport | Spring Boot 4.1.1, Spring Web MVC REST API | APPROVED / OBSERVED | `pom.xml` parent `spring-boot-starter-parent:4.1.1` và `spring-boot-starter-webmvc`; chưa có controller/API contract |
| Build / dependency | Maven Wrapper | OBSERVED / UNVERIFIED | Có `pom.xml`, `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd`; chưa có persisted command result |
| Kiến trúc | Spring Boot modular monolith; package theo business capability; mỗi module dùng controller → service interface → service.impl → repository → entity và DTO/mapper ở API boundary | APPROVED / NOT IMPLEMENTED | Một backend deployable và một transaction boundary chính; RFC-001 và Profile v1.3.0 đã được duyệt |
| Package gốc | `fu.fa26.sep490.g58.otcms_be` | OBSERVED | `src/main/java/fu/fa26/sep490/g58/otcms_be/OtcmsBeApplication.java` |
| Xác thực và phân quyền | Spring Security; access JWT 15 phút; opaque rotating refresh token 7 ngày; Google Authorization Code + PKCE | APPROVED / NOT IMPLEMENTED | Architecture Profile v1.3.0 và FEAT-001 Context v0.5.4 đã được duyệt; chưa có security implementation |
| Session persistence | Chỉ lưu refresh-token hash trong PostgreSQL; rotate/revoke/reuse detection; tối đa 5 active sessions/account | POLICY SELECTED / NOT IMPLEMENTED | FEAT-001 Context v0.5.4; schema và migration chưa được tạo |
| Cookie / browser security | Refresh cookie `HttpOnly`, `Secure`, host-only, `Path=/api/auth`; exact-origin CORS allowlist; double-submit CSRF cho refresh/logout | POLICY SELECTED / FRONTEND VALUES PENDING | Exact frontend origins và `SameSite` là typed deployment configuration |
| Portal boundary | Patient Portal public; Staff Portal email/password chỉ qua internal network/VPN; screen và auth entry point tách biệt | POLICY SELECTED / TOPOLOGY PENDING | Gateway/reverse proxy và backend cùng enforce; exact origin, CIDR/VPN và trusted proxy list chưa cấu hình |
| Email verification | Gmail SMTP qua email-delivery adapter; email OTP 6 chữ số, hash-at-rest, single-use, 10 phút, 5 attempts, 3 resends/account/hour | POLICY SELECTED / NOT IMPLEMENTED | FEAT-001 Context v0.5.4; Gmail quota, TLS, timeout/retry và delivery evidence chưa có |
| Secret configuration | JWT signing material, Gmail credential/App Password và audit-HMAC secret chỉ lấy từ environment/secret mechanism | POLICY SELECTED / CONFIG PENDING | Không hardcode, log, trả client hoặc commit secret; production secret provider chưa chọn |
| Persistence / schema | Spring Data JPA, Flyway, PostgreSQL; account/session/identity/OTP/consent/audit persistence do backend sở hữu | DEPENDENCIES OBSERVED / CONFIG PENDING | Có JPA, Flyway và PostgreSQL dependencies; chưa có datasource, entity, repository hoặc migration |
| Validation | Jakarta Bean Validation | DEPENDENCY OBSERVED | Có `spring-boot-starter-validation`; chưa có request DTO/validation behavior |
| Test / verification | Maven test infrastructure; Testcontainers cho DB khi được duyệt | PARTIALLY OBSERVED / UNVERIFIED | Có test starters và `OtcmsBeApplicationTests.java`; chưa có Testcontainers dependency hoặc persisted test result |
| DICOM/object storage/viewer | Orthanc, DICOMweb, MinIO, OHIF | APPROVED ARCHITECTURE / NOT IMPLEMENTED | Đã mô tả boundary ở Section 6; binding triển khai cụ thể thuộc feature tương ứng |
| Cache, broker, deployment | Chưa chọn | OPTIONAL | Chỉ quyết định khi SPEC có yêu cầu |

### Hiện trạng cần migration

Một số legacy policy/skills/docs và thư mục placeholder của starter template vẫn có thể chứa assumption TypeScript hoặc Clean Architecture. Java bootstrap không tự làm các governance artifact này hết hiệu lực; Constitution v2.0.0 ARCH-01 và Profile v1.3.0 là chuẩn Java/Spring layered hiện hành. Không xóa placeholder hoặc coi governance cleanup hoàn tất nếu chưa có scope/review phù hợp.

---

## 3. Exact command và binding còn thiếu

| Mối quan tâm | Status | Evidence / Human Decision cần có |
| :--- | :--- | :--- |
| Spring Boot, JPA, Flyway, PostgreSQL, validation và test dependencies | APPROVED / COMPATIBILITY UNVERIFIED | Dependencies có trong `pom.xml`; RFC/profile review đã hoàn tất, compatibility verification chưa có persisted evidence |
| Maven Wrapper test command | EVIDENCE OBSERVED / PENDING HUMAN APPROVAL | `.\mvnw.cmd test` nằm trong successful `clean verify` evidence ngày 2026-09-29: 1 test, 0 failure, 0 error, 0 skipped |
| Maven Wrapper build command | EVIDENCE OBSERVED / PENDING HUMAN APPROVAL | `.\mvnw.cmd clean verify` successful ngày 2026-09-29; JAR repackaged thành công; cần persist command approval |
| Authentication/session policy | APPROVED / NOT IMPLEMENTED | Spring Security, access JWT 15 phút, opaque rotating refresh token 7 ngày, refresh hash trong PostgreSQL; Profile v1.3.0 và Context v0.5.4 đã được duyệt |
| Browser security values | PARTIALLY SELECTED | Baseline cookie/CORS/CSRF đã chọn; exact frontend origin và `SameSite` chốt bằng typed deployment configuration trước integration |
| Gmail SMTP | SELECTED / UNVERIFIED | Cần App Password/secret provisioning, TLS, quota, timeout/retry, fake sender cho test và persisted delivery evidence |
| Datasource/Flyway configuration | CONFIGURED / LOCAL EVIDENCE OBSERVED | `src/main/resources/application.yml` đọc Supabase/local PostgreSQL environment; evidence 2026-09-29 kết nối `localhost:5432/otcms_db`; chưa có migration |
| MinIO/Orthanc/OHIF integration binding | ARCHITECTURE APPROVED / IMPLEMENTATION BINDING PENDING | Client mechanism, runtime topology, authorization gateway và exact verification command sẽ do feature ảnh tương ứng chốt |
| Cache, broker, deployment | OPTIONAL | Chỉ chọn khi feature/operations yêu cầu |

---

## Proposed verification commands

Các lệnh dưới đây đã có successful repository evidence do Human cung cấp ngày 2026-09-29 nhưng chưa trở thành exact approved commands cho đến khi Human Final Review của Profile được ghi lại.

- Command: `.\mvnw.cmd test`
  - Status: APPROVED
  - Evidence: 1 test, 0 failures, 0 errors, 0 skipped; Spring context kết nối PostgreSQL local `localhost:5432/otcms_db`.
- Command: `.\mvnw.cmd clean verify`
  - Status: APPROVED
  - Evidence: `BUILD SUCCESS`; test 1/0/0/0; JAR được package và Spring Boot repackage thành công.

---

## 4. Modular monolith boundary

### 4.1 Deployment model

OTCMS SHALL được triển khai như một modular monolith:

- Một Spring Boot application và một Maven build chính.
- Một backend deployment unit phục vụ Patient Portal và Staff Portal.
- Một PostgreSQL database cho dữ liệu nghiệp vụ có cấu trúc và transaction chính.
- MinIO, Orthanc, Gmail SMTP và Google OIDC vẫn là external services được gọi qua adapter rõ ràng; chúng không biến OTCMS thành microservices.
- Không yêu cầu service discovery, distributed transaction hoặc message broker cho MVP nếu feature SPEC chưa chứng minh nhu cầu.

### 4.2 Module organization

Package SHALL được tổ chức theo business capability; layered architecture áp dụng bên trong từng module:

```text
fu.fa26.sep490.g58.otcms_be
├── common
│   ├── constant
│   ├── exception
│   ├── response
│   ├── util
│   └── validation
├── config
├── security
├── auth
│   ├── controller
│   ├── dto
│   │   ├── request
│   │   └── response
│   ├── mapper
│   ├── service
│   │   ├── AuthService.java
│   │   └── impl
│   │       └── AuthServiceImpl.java
│   ├── repository
│   └── entity
├── patient
├── staff
├── appointment
├── encounter
├── imaging
└── notification
```

Tên module ngoài `auth` là capability map định hướng và chỉ được tạo khi feature tương ứng có SPEC/PLAN được duyệt.

### 4.3 Dependency rules

- Controller nhận/trả DTO, thực hiện transport-level validation và chỉ gọi service interface; không gọi repository hoặc service implementation trực tiếp.
- `service` chứa interface mô tả use case/application boundary; `service.impl` chứa implementation, orchestration, business rule và transaction boundary.
- Service implementation dùng constructor injection, được đăng ký bằng Spring stereotype phù hợp và không được expose như API contract.
- Repository/entity thuộc module sở hữu dữ liệu đó.
- Module không được truy cập trực tiếp repository hoặc entity nội bộ của module khác; giao tiếp qua public service/facade hoặc contract đã duyệt.
- Request/response DTO thuộc API boundary; DTO không được dùng thay entity persistence và entity không được trả trực tiếp qua API.
- Mapper chịu trách nhiệm chuyển DTO ↔ service/domain input-output khi việc mapping không còn đơn giản; controller/service không được chứa mapping phức tạp lặp lại.
- `common` chỉ chứa primitive/cross-cutting code ổn định như exception contract, response wrapper, generic validation, constant và utility không mang business ownership; không được trở thành nơi chứa business logic của mọi module.
- Root `config` chứa Spring/application configuration dùng chung như CORS, Jackson, persistence và typed configuration binding; feature-specific configuration vẫn thuộc module sở hữu khi cần.
- Root `security` chứa Spring Security configuration, authentication filters, JWT/CSRF/network-boundary components và authenticated-principal support; business login/register rules vẫn thuộc `auth.service`/`auth.service.impl`.
- Mỗi module sở hữu `mapper` của chính nó; mapper của module này không được truy cập repository/entity nội bộ của module khác.
- Dependency cycle giữa modules bị cấm và phải được kiểm tra bằng architecture test khi implementation bắt đầu.

### 4.4 Authentication placement

- Patient Portal và Staff Portal dùng chung `auth` module trong một backend monolith.
- Hai portal có controller/entry point và security policy riêng, nhưng có thể dùng chung account/session/token persistence.
- Network restriction của Staff Portal được enforce tại gateway và security boundary của cùng backend; không cần tách staff authentication thành microservice.
- Auth module chỉ cung cấp authenticated principal/account context; resource authorization vẫn thuộc module sở hữu resource.

---

## 5. Kiến trúc authentication và account persistence

### 5.1 Trust boundary và luồng chính

```text
Patient Portal (public)              Staff Portal (internal/VPN)
  ├─ email/password ─────────┐        └─ email/password ─────────┐
  ├─ email OTP verification ┤                                  │
  └─ Google Auth Code + PKCE┤                                  │
                             ▼                                  ▼
                       Gateway / reverse proxy network policy
                                      │
                                      ▼
                            Spring Security / Java 21
                                   │
              ┌────────────────────┼───────────────────┐
              │                    │                   │
              ▼                    ▼                   ▼
        PostgreSQL             Google OIDC         Gmail SMTP
   account/session/hash      identity provider   OTP/notification
```

- Patient Portal và Staff Portal dùng screen/authentication entry point riêng. Patient entry point chỉ nhận patient account; staff entry point chỉ nhận staff account.
- Patient Portal là Internet-facing và hỗ trợ registration, email/password, Google login; Staff Portal chỉ hỗ trợ email/password trong approved internal network/VPN.
- Tách UI hoặc ẩn staff URL không phải security boundary. Gateway/reverse proxy và backend phải cùng enforce staff network policy.
- Backend chỉ tin `Forwarded`/`X-Forwarded-For` từ trusted proxy đã cấu hình; client-supplied forwarding header không được dùng để vượt network restriction.
- React chỉ gọi OTCMS backend cho authentication; không giữ database, Gmail hoặc Google client secret.
- Spring Security là authentication boundary. Google, Gmail và PostgreSQL không tự quyết định authorization nghiệp vụ của OTCMS.
- Login thành công trả access JWT trong response body; refresh token chỉ truyền bằng protected cookie.
- Authorization theo patient, encounter, DICOM, MinIO hoặc OHIF vẫn thuộc policy của từng feature/resource.
- Patient và staff có thể dùng chung token/session persistence, nhưng authenticated principal phải chứa account type đáng tin cậy; cross-portal login trả failure chung chống enumeration.

### 5.2 Token và session lifecycle

| Thành phần | Binding |
| :--- | :--- |
| Access token | JWT, lifetime 15 phút; signing key/secret lấy từ approved secret configuration |
| Refresh token | Opaque cryptographically random token, lifetime 7 ngày, rotate sau mỗi lần dùng |
| Persistence | Chỉ lưu refresh-token hash và session metadata trong PostgreSQL; không lưu plaintext refresh token |
| Revocation | Reuse, password change, account disable/archive hoặc security revoke thu hồi session liên quan |
| Session limit | Tối đa 5 active sessions/account; vượt giới hạn thì thu hồi session cũ nhất theo SPEC |
| Logout | Logout current session idempotent, revoke refresh token hiện tại và clear cookie |

Account/session persistence tối thiểu phải hỗ trợ account status, external Google identity, refresh-token rotation/revocation, email-verification challenge, consent evidence và safe authentication audit. Flyway là migration owner; entity không được trả trực tiếp qua API.

### 5.3 Cookie, CORS và CSRF baseline

- Refresh cookie: `HttpOnly=true`, `Secure=true`, `Path=/api/auth`, không khai báo `Domain` để mặc định host-only.
- `SameSite` là typed deployment configuration và phải được chốt theo topology thực tế của React/backend trước integration.
- CORS dùng exact-origin allowlist từ typed configuration; wildcard origin `*` bị cấm khi bật credentials.
- Patient origin và Staff origin được allowlist riêng; Staff origin allowlist không thay thế internal network/VPN enforcement.
- Endpoint tối thiểu `refresh` và `logout` dùng double-submit CSRF: backend cấp random CSRF token bằng cookie riêng không `HttpOnly`, client gửi lại bằng header `X-CSRF-TOKEN`, backend xác minh trước khi thay đổi authentication state.
- Thay đổi làm yếu `HttpOnly`, `Secure`, host-only default, exact-origin allowlist hoặc CSRF validation cần security review.
- Exact internal CIDR/VPN, trusted proxy list và development override phải được cấu hình trước integration; production không được mặc định allow-all cho staff entry point.

### 5.4 Google authentication

- Dùng Authorization Code Flow với PKCE cho patient login.
- Backend kiểm tra issuer, audience, expiration và `email_verified=true`; dùng `sub` làm provider identity ổn định.
- Chỉ link vào patient account khi verified normalized Google email khớp duy nhất với registered normalized email và không có conflict.
- Không log authorization code/token và không tự link Google identity trực tiếp với clinical record cũ.

### 5.5 Gmail SMTP và email OTP

- Gmail SMTP được truy cập qua `EmailDeliveryAdapter`; business service không phụ thuộc trực tiếp Gmail client/configuration.
- OTP gồm 6 chữ số từ cryptographically secure random source, chỉ lưu hash, single-use và hết hạn sau 10 phút.
- Mỗi OTP tối đa 5 lần nhập sai; resend tạo OTP mới, vô hiệu OTP cũ và giới hạn 3 lần/account/giờ.
- Gmail credential/App Password lấy từ environment hoặc approved secret mechanism; không commit, log hoặc trả client.
- Adapter phải có connect/read timeout, bounded retry cho lỗi transient và failure outcome rõ ràng. Không báo account activated trước khi OTP hợp lệ và consent bắt buộc đã hoàn tất.
- Local/integration test dùng fake email sender; test không gửi Gmail thật hoặc log OTP.

### 5.6 Secret boundary

Các secret sau chỉ tồn tại ở backend runtime configuration:

- JWT signing key/secret.
- Gmail username và App Password/credential.
- Key dùng HMAC normalized email trong safe authentication audit.
- Database credential và Google OAuth client secret.

Secret phải được inject qua environment/secret mechanism, được redact khỏi logs/diagnostics và có rotation strategy trước production. React không nhận bất kỳ secret nào.

---

## 6. Kiến trúc lưu trữ và hiển thị ảnh đề xuất

### 6.1 Sơ đồ thành phần

```text
                 React
                   │
                   ▼
          Spring Boot / Java 21
                   │
      ┌────────────┼────────────┐
      │            │            │
      ▼            ▼            ▼
 PostgreSQL       MinIO        Orthanc
      │            │            │
 Structured      Files         DICOM
   Data        PDF/JPG/...    X-Ray/CT/MRI
                                │
                                ▼
                           OHIF Viewer
```

### 6.2 Trách nhiệm lưu trữ

| Thành phần | Trách nhiệm | Không dùng cho |
| :--- | :--- | :--- |
| PostgreSQL | Bệnh nhân, lịch hẹn, lần khám, chỉ định chẩn đoán hình ảnh, kết quả, audit và tham chiếu `StudyInstanceUID`/Orthanc resource ID | Nội dung nhị phân DICOM hoặc tài liệu dung lượng lớn |
| MinIO | PDF, JPG/PNG thông thường, giấy chỉ định và tài liệu đính kèm không phải DICOM | Bản sao DICOM đã được Orthanc quản lý |
| Orthanc | DICOM study/series/instance và DICOM metadata cho X-Ray, CT, MRI | Dữ liệu nghiệp vụ phòng khám |
| OHIF Viewer | Hiển thị DICOM từ Orthanc qua DICOMweb, gồm zoom, pan, window/level, đo đạc và duyệt series khi dữ liệu hỗ trợ | Lưu trữ dữ liệu nguồn hoặc quyết định authorization |

### 6.3 Luồng MVP: upload DICOM thủ công

```text
Máy X-Ray/CT/MRI
        │ export .dcm hoặc ZIP
        ▼
Máy tính kỹ thuật viên / thư mục mạng nội bộ
        │ người dùng chủ động chọn hoặc kéo thả tệp
        ▼
React → Spring Boot → Orthanc → DICOMweb → OHIF Viewer
                │
                └→ PostgreSQL lưu liên kết nghiệp vụ và audit
```

1. Kỹ thuật viên chọn đúng bệnh nhân hoặc chỉ định chụp và tải `.dcm`, nhiều tệp `.dcm` hoặc ZIP chứa một study.
2. Spring Boot xác thực người dùng, kiểm tra quyền, giới hạn dung lượng/loại tệp và kiểm tra metadata DICOM cần thiết trước khi gửi sang Orthanc.
3. Sau khi Orthanc nhận thành công, backend lưu liên kết tối thiểu giữa chỉ định/bệnh nhân với `StudyInstanceUID`, Orthanc resource ID, trạng thái, người tải và thời điểm tải trong PostgreSQL.
4. Người có quyền mở study bằng OHIF; OHIF đọc ảnh qua DICOMweb. Orthanc không được public trực tiếp ra Internet và authorization phải đi qua gateway/policy đã được SPEC phê duyệt.
5. Tệp trên máy kỹ thuật viên chỉ là bản tạm; việc dọn tệp phải theo retention policy của phòng khám sau khi xác minh upload thành công.

### 6.4 Ranh giới và quyết định còn mở

- MVP không tích hợp trực tiếp máy chụp hoặc PACS bằng DICOM C-STORE; đây là extension cần SPEC, network/security review và test riêng.
- Trình duyệt không tự quét thư mục cục bộ; kỹ thuật viên phải chủ động chọn hoặc kéo thả tệp.
- Không dùng Google Drive làm kho trung gian mặc định cho DICOM.
- Không lưu trùng cùng một DICOM trong MinIO và Orthanc.
- DICOM demo/test phải được ẩn danh; không ghi patient data, DICOM payload, token hoặc URL truy cập có credential vào log.
- Quy tắc đối soát Patient ID trong DICOM với hồ sơ OTCMS, xử lý upload trùng, sửa liên kết, xóa/retention và quyền xem study phải được chốt trong feature SPEC.
- MPR/3D phụ thuộc modality, chất lượng study và cấu hình OHIF; không được cam kết cho mọi ảnh.

---

## 7. Artifact generation gate

Architecture Profile v1.3.0 và RFC-001 đã được Human duyệt. `CONTEXT.md`/`SPEC.md` có thể tiếp tục theo review gate tương ứng; `PLAN.md` cần feature Spec `APPROVED & LOCKED`. `TASKS.md` và execution kỹ thuật vẫn phải thỏa artifact gate, governance sync và exact verification-command evidence trước khi được coi là implementation-ready.

| Artifact / skill | Có thể dùng core-only baseline? | Điều kiện |
| :--- | :--- | :--- |
| `CONTEXT.md` / `/sdd-context` | Có | Reference profile, chỉ ghi unknown liên quan feature. |
| `SPEC.md` / `/sdd-spec` | Có | Requirement technology-neutral; không dùng ORM decorator hoặc framework schema. |
| `PLAN.md` / `/sdd-plan` | Không, nếu feature cần HTTP, persistence, validation hoặc test command | Binding bắt buộc phải được chọn và có evidence. |
| `TASKS.md` / `/sdd-tasks` | Không | Cần Plan approved và test/build command đã chọn. |
| `/add-execute`, `/sdd-layer-edit` | Không | Cần binding đã chọn và verification command runnable. |
| Technical skill adapter guidance | Không, nếu adapter-specific | Báo `CONFIGURATION GAP`; chỉ đưa ra architecture check framework-neutral. |

---

## 8. Quyết định bắt buộc khi bị block

```markdown
### Example AI Agent Recommendation
- Status: PENDING HUMAN REVIEW
- Scope: `.sdd/architecture-profile.md` — <feature or requested task>
- Recommendation: Select <HTTP framework / database / ORM / test command> before technical planning or execution.
- Evidence: <files checked and no/conflicting evidence found>
- Risks and assumptions: Generating framework-specific files or commands now would be speculative.
- Alternatives considered: <supported stack options relevant to the project>
- Required human decision: Approve exact technology binding(s) and verification command(s).

### Example Human Final Review
- Status: PENDING
- Decision:
- Reviewer:
- Reviewed at:
- Follow-up:
```

Dùng `/sdd-review --target=.sdd/architecture-profile.md ...` để ghi approval. Sau approval, cập nhật `PLAN.md` và `TASKS.md` bị ảnh hưởng; technical recommendation cũ mất hiệu lực.

---

## 9. Governance liên quan

- `CONSTITUTION.md` là thẩm quyền cho hard security và architecture constraint.
- `CLAUDE.md` là bộ nhớ kiến trúc dành cho con người; phải phản ánh profile đã approved.
- `.sdd/constraints/` quản lý global, business và safety constraint. Constraint chỉ áp dụng adapter-specific khi Architecture Profile đã chọn binding tương ứng.
- `.sdd/mcp-config.yaml` kiểm soát quyền tool, không chọn công nghệ.

---

## Change Impact Record

- **Changed intent, requirement, architecture, or task assumption:** Bổ sung local PostgreSQL/Supabase-compatible datasource evidence và hai exact Maven Wrapper verification command.
- **Feature Lock boundary affected:** Không đổi feature behavior; verification-command binding và datasource evidence được bổ sung.
- **Material state-change classification:** runtime configuration; dependency/build verification.
- **Downstream artifacts invalidated:** FEAT-001 `PLAN.md` blocker cần refresh; chưa có implementation/tasks.
- **Required trace, test, and sync actions:** Human review Profile command entries; sau approval gọi lại `/sdd-plan --feature=feat-001-auth-login`.
- **Review follow-up:** Exact command approval bắt buộc trước Plan implementation mapping.

## AI Agent Recommendation
- Status: APPROVED
- Scope: `.sdd/architecture-profile.md` — Java/Spring Boot, authentication/account persistence và kiến trúc quản lý ảnh OTCMS
- Recommendation: Duyệt profile v1.3.0 gồm Java 21, Spring Boot modular monolith, Maven Wrapper, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Jakarta Bean Validation và layered architecture trong từng business module; Patient Portal public và Staff Portal internal/VPN với entry point riêng trong cùng `auth` module; access JWT 15 phút, opaque rotating refresh token 7 ngày, refresh hash trong PostgreSQL, protected refresh cookie, exact-origin CORS, double-submit CSRF, Google Authorization Code + PKCE, Gmail SMTP qua email-delivery adapter, email OTP baseline và secret boundary; giữ MinIO/Orthanc/OHIF theo kiến trúc ảnh đã mô tả.
- Evidence: RFC-001 được Human reviewer Long duyệt ngày 2026-09-25 và áp dụng vào `CONSTITUTION.md` v2.0.0; FEAT-001 Context v0.5.4 ghi các package-boundary, modular-monolith, authentication và dual-portal decisions; repository có Java 21 bootstrap, Maven Wrapper và dependencies Web MVC, Security, JPA, Flyway, PostgreSQL, validation. Security/account/email/network-policy implementation và MinIO/Orthanc/OHIF chưa có implementation evidence.
- Risks and assumptions: Spring Boot 4.1.1 và dependency set chưa có persisted compatibility/test result; exact patient/staff origins, `SameSite`, internal CIDR/VPN và trusted proxy list còn là deployment configuration; Gmail có quota/rate limit và App Password secret; token rotation/reuse, CSRF, session concurrency và network restriction cần integration tests; MinIO/Orthanc/OHIF chưa triển khai; dữ liệu bệnh nhân và DICOM vẫn cần authorization/audit/retention theo feature SPEC.
- Alternatives considered: Giữ TypeScript/Node.js nhưng Human Director đã chọn Java/Spring Boot.
- Required human decision: SATISFIED — Long approved exact Maven Wrapper commands and local PostgreSQL datasource evidence on 2026-09-29.

## Human Final Review
- Status: APPROVED
- Decision: APPROVED exact Maven verification commands and local PostgreSQL datasource evidence
- Reviewer: Long
- Reviewed at: 2026-09-29T22:26:04.2115440+07:00
- Follow-up: `/sdd-plan --feature=feat-001-auth-login`
