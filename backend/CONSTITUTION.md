# OTCMS Backend Constitution — Spring Boot

# Version: 2.0.0 (LOCKED)
# Status: APPROVED — Requires RFC Process to Modify
# Owner: Tech Lead & Architecture Board (@arch-board)
# Target Stack: Java 21, Spring Boot, Maven, Spring Security, Spring Data JPA, Flyway, PostgreSQL
# Architecture RFC: `.sdd/rfcs/RFC-001-java-spring-otcms-governance.md`

---

## RULE PRECEDENCE & CONFLICT HANDLING

1. User request và constraints an toàn/pháp lý bắt buộc.
2. `CONSTITUTION.md`.
3. `AGENTS.md` và `CLAUDE.md`.
4. Approved Architecture Profile và feature `CONTEXT.md`, `SPEC.md`, `PLAN.md`, `TASKS.md` theo gate tương ứng.
5. Convention đang tồn tại trong codebase.
6. Assumption của Agent.

Nếu xung đột không thể giải quyết theo thứ tự trên, Agent SHALL dừng phần việc bị ảnh hưởng, lưu evidence và yêu cầu Human decision.

---

## LAYER 1: HARD RULES

Các quy tắc bảo mật, toàn vẹn dữ liệu và quyền quyết định tối thiểu. Vi phạm phải block delivery hoặc execution liên quan.

### SEC-01: Zero Hardcoded Secrets, PII and Clinical Data Exposure

- **Rule**: System SHALL NOT lưu credentials, API key, private key, connection string chứa password hoặc PII dưới dạng plaintext trong source code, configuration, artifact hay log.
- **Rule**: PII trong log/error response phải được mask; không trả stack trace, provider detail hoặc secret cho client/caller không được phép.
- **Enforcement**: Chạy security scan, secret scan hoặc review command đã được Architecture Profile/CI chọn; không tự suy đoán tool hoặc command.

### SEC-02: Access Control by Feature Policy

- **Rule**: Feature có action thay đổi state hoặc truy cập dữ liệu nhạy cảm SHALL xác định identity, authorization ownership và behavior unauthorized trong `SPEC.md`.
- **Rule**: Mechanism xác thực, token/session, role/scope policy, middleware và HTTP/event mapping chỉ dùng khi binding tương ứng trong Architecture Profile đã `APPROVED`.
- **Rule**: Spring Security cung cấp authenticated identity/context; service hoặc policy component thực thi business authorization theo action/resource đã được Spec xác định.
- **Rule**: Role/authority được quản lý tập trung; không hardcode role string rải rác. Public endpoint phải được liệt kê trong approved security policy/spec và configuration.

### DATA-01: Core Business Data Protection

- **Rule**: Feature thay đổi hoặc xóa core business data SHALL xác định retention, recovery, authorization và audit behavior trong `SPEC.md`.
- **Rule**: Soft-delete policy, field representation, query default, hard-delete exception, migration và cascade behavior chỉ dùng khi persistence binding đã `APPROVED` và safety review cho phép.
- **Rule**: Không dùng destructive DB operation trái `.sdd/constraints/safety.md`.

### API-01: Stable and Safe API Contract

- Controller SHALL nhận/trả DTO; không expose JPA entity qua API.
- Request DTO SHALL dùng Jakarta Bean Validation.
- Collection endpoint SHALL có pagination và maximum page size.
- API contract change SHALL cập nhật DTO, OpenAPI và integration tests; breaking change cần versioning hoặc migration/deprecation plan được duyệt.
- Upload/download SHALL định nghĩa authorization, size limit, content validation và failure behavior trong SPEC/Plan.

### DATA-02: JPA Persistence Safety

- JPA relationship SHALL có ownership, fetch và cascade rõ ràng; `FetchType.EAGER` cần justification.
- Collection query SHALL kiểm soát N+1; không sửa `LazyInitializationException` bằng cách chuyển toàn bộ relationship sang EAGER.
- Concurrent business update SHALL dùng optimistic locking, database constraint hoặc strategy được duyệt.
- Collection không giới hạn SHALL phân trang; API SHALL không serialize entity trực tiếp.

### DATA-03: Flyway Migration Safety

- Flyway migration đã chạy trên shared environment SHALL immutable.
- Destructive migration SHALL có backup, rollout và rollback/recovery strategy.
- Data migration lớn SHOULD tách khỏi schema migration khi cần kiểm soát deploy time/lock.
- Applied migration chỉ được khắc phục bằng migration mới có test/evidence.

### REL-01: Idempotency, Concurrency and External Calls

- Mọi call đến Orthanc/MinIO SHALL có connect/read/request timeout hữu hạn, bind qua typed configuration và có safe default cho từng môi trường; cấm timeout vô hạn.
- Retry SHALL có maximum attempts hữu hạn, backoff khi phù hợp và tổng time budget; giá trị cụ thể phải được approved Plan/config xác định bằng evidence.
- Chỉ lỗi transient như connection reset, timeout, throttling hoặc server unavailable/5xx phù hợp mới được retry. Validation, authentication/authorization, unsupported media, payload quá lớn, business conflict và non-retryable 4xx SHALL fail fast. `429` chỉ retry khi tôn trọng server guidance/backoff.
- Retryable state change và upload SHALL có idempotency strategy. Upload SHOULD kết hợp operation/idempotency key với DICOM UID hoặc object checksum/key theo approved SPEC; retry không được tạo duplicate study/object/business linkage.
- Database uniqueness và integrity SHALL được enforce bằng unique/foreign-key/check/version constraint phù hợp, không chỉ bằng Java validation.
- Workflow qua PostgreSQL, MinIO hoặc Orthanc SHALL định nghĩa partial failure, compensation/reconciliation và orphan-resource handling trước implementation.

### MED-01: Clinical Data Protection

- Hồ sơ khám, chẩn đoán, đơn thuốc, tài liệu và DICOM metadata là dữ liệu lâm sàng nhạy cảm.
- Development, automated test và demo SHALL dùng synthetic hoặc anonymized DICOM/data; dữ liệu bệnh nhân thật cần explicit approved data-use policy và controlled environment.
- Application log, audit detail, metric label và error response SHALL không chứa patient name/identifier, raw clinical payload, raw DICOM payload hoặc raw DICOM metadata có khả năng định danh; chỉ dùng safe/correlation identifier đã được policy cho phép.
- Truy cập và thay đổi clinical data SHALL authorization theo action/resource và audit theo SPEC.
- Retention, correction, anonymization và disclosure SHALL do Human-approved policy/SPEC quyết định.

### IMG-01: DICOM and Medical Imaging Boundary

- Orthanc là kho DICOM chính thức; MinIO SHALL không lưu bản sao DICOM mặc định; PostgreSQL chỉ lưu business reference/identifier.
- OHIF là viewer, không phải storage hoặc authorization boundary; OHIF không tự quyết định người dùng được xem study nào.
- Orthanc REST/admin endpoint SHALL không public trực tiếp ra Internet hoặc cho React gọi bằng administrative credential; access chỉ qua trusted network boundary và approved backend/gateway policy.
- Trước viewer session/URL hoặc DICOMweb proxy request, backend/gateway SHALL xác thực user và authorize theo action, study reference/`StudyInstanceUID`, patient/imaging-order linkage và organizational scope do SPEC xác định.
- Upload DICOM SHALL enforce configurable size limit, supported content/type validation, archive safety khi dùng ZIP, DICOM parsing/required metadata check, idempotency và duplicate detection trước active linkage.
- Patient/imaging-order mismatch, re-link/correction permission, audit và conflict resolution SHALL do feature SPEC quyết định; không sửa liên kết ngoài approved workflow.
- Direct modality/PACS integration cần feature SPEC, network/security review và Plan riêng.

### EXT-01: External Storage Consistency

- MinIO và Orthanc SHALL được truy cập qua dedicated adapter; controller/entity không gọi external service trực tiếp.
- MinIO bucket/object SHALL private mặc định; temporary URL cần authorization và bounded expiry.
- System SHALL không trả trạng thái upload thành công khi required PostgreSQL business linkage và external resource reference chưa hoàn tất; trạng thái trung gian phải được biểu diễn rõ như `PENDING`, `PROCESSING`, `RECONCILIATION_REQUIRED` hoặc equivalent do SPEC định nghĩa.
- Nếu Orthanc nhận DICOM thành công nhưng PostgreSQL finalization thất bại, system SHALL giữ correlation/idempotency evidence đủ để tìm lại study, không upload mù lần nữa, và đưa operation vào reconciliation/compensation path đã duyệt.
- Orphan DICOM hoặc MinIO object SHALL được phát hiện bằng scheduled/manual reconciliation dựa trên operation record, external identifier/checksum và age threshold. Không tự hard-delete clinical object nếu chưa có retention, authorization và recovery policy.
- Reconciliation SHALL idempotent, auditable và bounded; mỗi lần chạy phải phân loại `LINKED`, `RETRYABLE`, `ORPHANED`, `CONFLICT` hoặc equivalent, ghi safe evidence và hỗ trợ Human intervention cho conflict.
- Nếu database commit thành công nhưng external upload thất bại, operation SHALL ở trạng thái failure/retryable rõ ràng; không tạo active clinical linkage đến resource chưa tồn tại.
- React/frontend SHALL không nhận MinIO access/secret key, Orthanc username/password, service token hoặc administrative endpoint credential.

---

## LAYER 2: ARCHITECTURAL CONSTRAINTS

Cấu trúc hệ thống và ranh giới module. Mọi ngoại lệ cần RFC `APPROVED` bởi Tech Lead hoặc Architecture Board.

### ARCH-01: Spring Layered Architecture Boundaries

- Dependency direction SHALL là `controller → service → repository → entity` trong package gốc `fu.fa26.sep490.g58.otcms_be`.
- Controller chỉ xử lý HTTP, authenticated context, validation/mapping và gọi service; không truy cập repository/`EntityManager`, chứa business rule hoặc trả entity.
- Service sở hữu orchestration, business invariant, resource authorization, idempotency và transaction boundary.
- Repository chỉ xử lý persistence bằng Spring Data JPA/parameterized query; entity không phụ thuộc web, DTO, security filter hoặc external client.
- DTO/mapper/validator/policy/external adapter SHALL nằm tại boundary phù hợp; dependency ngược chiều cần exception được duyệt.

### ARCH-02: Spring Dependency Injection and Configuration

- Bean SHALL dùng constructor injection; field injection cần framework-specific justification.
- Configuration SHALL bind qua typed/validated `@ConfigurationProperties`; production secret không có default.
- Security, CORS, OpenAPI, serialization và exception handling SHALL được cấu hình tập trung.

### ARCH-03: Async and Reliability by Specification

- **Rule**: Feature có latency, reliability, retry, ordering, idempotency hoặc delivery requirement SHALL mô tả behavior và acceptance criteria trong `SPEC.md`.
- **Rule**: Message broker, queue, scheduler, timeout, retry/backoff và persistence mechanism chỉ dùng khi Architecture Profile đã `APPROVED` binding/evidence tương ứng.
- **Rule**: Không tự áp threshold thời gian, broker hoặc implementation mechanism từ template.

---

## LAYER 3: ENGINEERING STANDARDS

Tiêu chuẩn code quality và bảo trì. Exception cần được nêu trong recommendation, có evidence và được reviewer có thẩm quyền chấp thuận.

### ENG-01: Spec-to-Code Traceability

- **Rule**: Business decision, invariant, authorization rule hoặc non-obvious behavior trong service, policy, validator hay security decision SHALL trace tới requirement trong Spec bằng Java comment/Javadoc:

  ```java
  /**
   * @ears .sdd/features/{slug}/SPEC.md#REQ-XXX
   */
  ```

- **Rule**: Không gắn EARS tag cho getter/setter, boilerplate, mapping thuần kỹ thuật hoặc method không chứa business decision.

### ENG-02: Safe Error Contract

- **Rule**: Error không được leak stack trace, secret, PII, clinical payload hoặc provider/driver detail qua application boundary.
- **Rule**: API/security error SHALL đi qua centralized Spring exception/security handler và dùng thống nhất schema:

  ```json
  {
    "error": {
      "code": "...",
      "message": "...",
      "timestamp": "..."
    }
  }
  ```

- **Rule**: `code` SHALL là stable machine-readable code; `message` SHALL an toàn cho client; `timestamp` SHALL dùng ISO-8601 với timezone.
- **Rule**: Thay đổi error schema SHALL cập nhật DTO, centralized handlers, Spring Security handlers, OpenAPI và integration tests trong cùng delivery.
- **Rule**: External-service error SHALL được map sang safe domain/application error; không trả raw MinIO/Orthanc payload cho client.

### ENG-03: Evidence-Based Verification

- **Rule**: Chỉ chạy test, lint, typecheck, build, security scan hoặc migration command vừa có `APPROVED` binding vừa có repository/CI evidence trong Architecture Profile.
- **Rule**: Không thay bằng Maven/framework command hoặc test filter suy đoán ngoài approved exact command.
- **Rule**: `N/A` chỉ hợp lệ khi check thực sự không áp dụng và phải ghi lý do; khi command không chạy được phải báo command, nguyên nhân và phần chưa xác nhận.
- **Rule**: Maven command chỉ được ghi là exact approved verification command sau khi Maven Wrapper tồn tại, command đã chạy trên repository với exit code thành công và evidence được persist trong Architecture Profile/CI. Wrapper chỉ tồn tại trên filesystem không chứng minh command đã verify.
- **Rule**: Completion report SHALL nêu exact command, result, số test/failure khi output cung cấp, check bị skip và residual risk; cấm claim `PASS` khi command chưa chạy hết.

### ENG-04: Validation, Transactions and Query Safety

- Request DTO SHALL dùng Jakarta Bean Validation.
- Write transaction SHALL đặt tại service bằng `@Transactional`; read-only operation SHOULD dùng `@Transactional(readOnly = true)`.
- Không dựa vào Spring self-invocation cho transaction; query SHALL parameterized.
- Schema change SHALL có Flyway migration và evidence.

### TEST-01: Testing Standards

- Business rule, invariant và authorization decision SHALL có unit test ở boundary phù hợp.
- Controller, request validation, error contract và Spring Security behavior SHALL có integration test.
- Custom repository query, JPA mapping, database constraint và Flyway migration SHALL có persistence/integration test khi áp dụng.
- Bug fix SHALL có regression test tái hiện failure cũ và chứng minh behavior đã sửa, trừ khi report ghi rõ lý do không thể test.
- Test SHALL bao phủ relevant success path và failure path, gồm unauthorized/forbidden/invalid input khi feature có boundary tương ứng.
- Test SHALL không phụ thuộc execution order, shared mutable state, wall-clock không kiểm soát hoặc dữ liệu môi trường không được quản lý.
- Khi H2 không phản ánh đúng PostgreSQL behavior, test SHALL dùng Testcontainers/PostgreSQL hoặc production-compatible database đã được duyệt; không sửa production query chỉ để làm H2 pass.
- Orthanc/MinIO integration SHALL có test cho timeout, retryable và non-retryable failure, duplicate/idempotency, external-success/database-failure, database-success/external-failure và reconciliation behavior theo feature risk.
- Test external service SHOULD dùng controlled fake/stub/container phù hợp; test SHALL không phụ thuộc public/uncontrolled Orthanc hoặc MinIO instance.

### OBS-01: Observability and Audit

- Application SHALL dùng structured logs cho production-relevant event; không ghép raw user/external payload vào log message.
- Mỗi inbound request SHOULD có correlation/request ID được validate/generate tại boundary và propagate an toàn qua service cùng outbound Orthanc/MinIO call. Correlation ID SHALL không chứa PII hoặc clinical identifier.
- Log/metric/audit chỉ dùng safe identifier đã được policy cho phép. Patient name, medical-record content, diagnosis, prescription, raw DICOM payload/metadata, password, token, API key, MinIO/Orthanc credential và presigned URL SHALL không xuất hiện trong application log hoặc metric label.
- External call SHALL ghi service/operation name, duration, sanitized outcome/status, retry attempt khi áp dụng và safe correlation identifier; không log request/response body hoặc administrative endpoint credential.
- Business-critical state change như patient-link correction, DICOM upload/link/re-link/delete request, document access-policy change và authorization-sensitive action SHALL tạo audit record khi SPEC yêu cầu.
- Audit record SHALL immutable theo application behavior, timestamped và chứa actor/action/resource-safe-reference/outcome cần thiết; audit detail không được trở thành bản sao clinical payload.
- Observability failure SHALL không làm lộ dữ liệu nhạy cảm hoặc thay đổi business outcome, trừ khi approved SPEC xác định audit persistence là điều kiện bắt buộc của operation.

### ENG-05: Dependency and Quality Gates

- Dependency mới hoặc version upgrade SHALL có documented purpose, owner/use location và lý do không dùng JDK/Spring capability hiện có.
- Dependency SHALL tương thích với approved Java và Spring Boot version/BOM; cấm override managed version nếu không có compatibility evidence và Human-approved Plan.
- Trước khi chấp nhận dependency/upgrade, SHALL review known vulnerability, maintenance status, license và transitive dependency risk bằng approved tool/process; unresolved critical/high risk phải block hoặc có documented Human disposition.
- Deprecated API SHALL không được thêm mới. Existing deprecated API chỉ được giữ khi có documented justification, migration owner và follow-up; suppression không được dùng để che compatibility/security problem.
- MinIO, Orthanc/DICOM, security, serialization và file-processing dependency cần review riêng về credential handling, untrusted-input parsing và CVE exposure.
- Dependency change SHALL cập nhật `pom.xml`, relevant configuration, Architecture Profile/Plan khi binding thay đổi, tests và dependency evidence trong cùng delivery.
- Compilation, approved static/security checks và relevant tests SHALL pass trước completion; nếu check không chạy được phải báo exact command, nguyên nhân và residual risk.

---

## RFC PROCESS

Sau khi template được phát hành, muốn sửa Layer 1 hoặc Layer 2 trong `CONSTITUTION.md`:

1. Tạo `.sdd/rfcs/RFC-XXX-<title>.md` bằng `/sdd-rfc`.
2. Ghi `Motivation`, `Proposed Change`, `Risk Assessment`, `Migration Plan`, `Rollback Plan`, AI recommendation và Human Final Review.
3. Tech Lead/Human Director ghi `APPROVED` qua quy trình RFC, rồi dùng `/sdd-rfc --approve=<rfc-number>` để đồng bộ Constitution và bump version.

Agent không tự sửa Constitution, suy approval từ hội thoại hoặc bypass RFC. RFC-001 được Reviewer `Long` phê duyệt ngày 2026-09-25 cho Constitution v2.0.0.

---

## AI AGENT SELF-CHECK PROTOCOL

Trước khi báo hoàn thành task, Agent phải kiểm tra:

1. [ ] Không có plaintext secret/key hoặc PII leak (`SEC-01`).
2. [ ] Feature cần access control đã có identity, authorization ownership và unauthorized behavior trong Spec (`SEC-02`).
3. [ ] Persistence/data change có retention, recovery, deletion policy và safety review phù hợp (`DATA-01`).
4. [ ] Dependency direction khớp `ARCH-01`; controller không truy cập persistence trực tiếp.
5. [ ] Spring configuration tuân `ARCH-02`; async/reliability tuân `ARCH-03`.
6. [ ] Business method có `@ears .sdd/features/{slug}/SPEC.md#REQ-XXX` (`ENG-01`).
7. [ ] Error boundary không leak sensitive detail; HTTP mapping, nếu có, khớp `ENG-02`.
8. [ ] Architecture Profile, Shadow Plan và Human Final Review đã `APPROVED` trước technical execution.
9. [ ] Đã chạy exact approved verification command hoặc ghi `N/A` với lý do hợp lệ (`ENG-03`).
10. [ ] DTO/transaction/query/Flyway tuân `ENG-04`; JPA và migration tuân `DATA-02`, `DATA-03`.
11. [ ] Idempotency, external partial failure và DB constraints đã được kiểm tra (`REL-01`, `EXT-01`).
12. [ ] Clinical/DICOM data, Orthanc/MinIO/OHIF boundary và authorization đúng (`MED-01`, `IMG-01`).
13. [ ] Tests, logs/audit và dependency gates tuân `TEST-01`, `OBS-01`, `ENG-05`.

---

## Changelog

### v2.0.0 (2026-09-25)

- BREAKING: Chuyển từ starter TypeScript/Clean Architecture sang Java 21/Spring Boot layered architecture theo RFC-001.
- Thêm API, JPA/Flyway, reliability, clinical-data, DICOM, MinIO/Orthanc/OHIF, testing và observability rules.

### v1.1.0 (2026-08-24)

- Chuyển Constitution của starter template sang profile-aware governance.
- Loại bỏ hardcode JWT/OAuth2/RBAC, `deleted_at`, Message Queue 1.5 giây, HTTP-only error mapping và `npm test`.
- Sửa dependency direction theo Clean Architecture/Hexagonal Architecture canonical.
- Giữ RFC gate cho mọi thay đổi Constitution sau khi phát hành template.

### v1.0.0

- Phát hành Constitution ban đầu cho SDD + ADD Starter Template.
