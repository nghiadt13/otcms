# RFC-001 — Chuyển OTCMS sang Java/Spring Boot và điều chỉnh governance

- RFC Status: APPROVED
- Date: 2026-09-18
- Primary scope: `.sdd/architecture-profile.md`, `CONSTITUTION.md`, `AGENTS.md`, `CLAUDE.md`
- Downstream scope: `.sdd/mcp-config.yaml`, `.claude/skills/`, `.sdd/constraints/`, `README.md`, `docs/`, `scripts/`
- Decision owner: Tech Lead / Architecture Board hoặc Human reviewer có thẩm quyền

## Motivation

Human Director chọn stack Java/Spring Boot như RWM cho Orthopedic Trauma Clinic Management System. OTCMS hiện là TypeScript/Node.js starter template với Clean Architecture và chưa có manifest, bootstrap, database hoặc test runner. Sao chép nguyên bộ RWM sẽ tạo xung đột với `CONSTITUTION.md` ARCH-01, Architecture Profile và quy trình SDD/ADD của OTCMS.

## Proposed Change

### Architecture Profile — thực hiện trước

Chọn Java 21, Spring Boot REST API, Maven Wrapper, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Jakarta Bean Validation và Spring layered architecture. Giữ JWT là quyết định của auth SPEC; chưa mặc định chọn token policy. Chỉ chuyển binding sang `APPROVED` khi Human Final Review được lưu. Chỉ ghi exact test/build command sau khi `pom.xml`, Maven Wrapper và test source có bằng chứng trong repo.

Kiến trúc lưu trữ và hiển thị ảnh đề xuất:

```text
React → Spring Boot
                ├─ PostgreSQL: structured business data, audit và external-resource reference
                ├─ MinIO: PDF/JPG/PNG và tài liệu không phải DICOM
                └─ Orthanc: DICOM study/series/instance
                              └─ DICOMweb → OHIF Viewer
```

MVP dùng upload DICOM thủ công từ máy kỹ thuật viên. Tích hợp trực tiếp modality/PACS qua DICOM C-STORE hoặc DICOMweb inbound nằm ngoài RFC này và cần feature SPEC, network/security review cùng execution plan riêng.

### Spring Architecture Decision

- **Decision status:** `PROPOSED`; chỉ có hiệu lực sau khi RFC và Architecture Profile được Human reviewer có thẩm quyền phê duyệt.
- **Architecture style:** Spring layered architecture, tổ chức theo feature hoặc business capability bên trong package gốc `fu.fa26.sep490.g58.otcms_be`; mỗi feature vẫn phải giữ dependency direction và layer responsibility bên dưới.
- **Dependency direction:** `controller → service → repository → entity`. DTO, mapper, validator, policy và external adapter được đặt tại boundary tương ứng; dependency ngược chiều bị cấm nếu không có RFC/Plan exception được duyệt.
- **Controller boundary:** Controller chỉ xử lý HTTP mapping, authentication context đã xác thực, request validation, DTO mapping và gọi service. Controller không truy cập repository/`EntityManager`, không chứa business rule hoặc transaction orchestration, và không trả JPA entity trực tiếp.
- **Service boundary:** Service sở hữu use-case orchestration, business invariant, resource authorization, idempotency decision và transaction boundary. Write operation dùng `@Transactional`; read-only operation dùng `@Transactional(readOnly = true)` khi phù hợp. Không dựa vào Spring self-invocation để kích hoạt transaction.
- **Repository boundary:** Repository chỉ xử lý persistence bằng Spring Data JPA hoặc query parameterized. Repository không quyết định HTTP response, không chứa authorization policy và không trả API DTO trực tiếp cho controller.
- **Entity boundary:** JPA entity biểu diễn persistence state và không phụ thuộc controller, request/response DTO, security filter, MinIO/Orthanc client hoặc web concerns. Relationship ownership, fetch strategy, cascade và optimistic locking phải được Plan/SPEC xác định khi có rủi ro.
- **DTO and mapper boundary:** Request/response dùng DTO riêng với Jakarta Bean Validation. Entity-to-DTO mapping nằm trong mapper/service boundary; không dùng entity làm public contract. Mapping thuần kỹ thuật không cần EARS tag, nhưng transformation có business decision phải trace tới requirement.
- **Authorization boundary:** Spring Security xác thực request và cung cấp identity/context; service hoặc policy component thực thi authorization theo action/resource. Role string không hardcode rải rác; JWT/session mechanism và role policy chỉ được chọn trong approved auth SPEC/Profile.
- **Configuration boundary:** Bean dùng constructor injection. Runtime configuration bind bằng typed/validated `@ConfigurationProperties`; production secret không có source-code default. Security, CORS, OpenAPI, serialization và exception handling được cấu hình tập trung.
- **External integration boundary:** MinIO và Orthanc được truy cập qua dedicated client/adapter do service điều phối. Controller và entity không gọi external system trực tiếp. Adapter phải có timeout, sanitized error mapping và safe observability; retry/idempotency/reconciliation chỉ áp dụng theo approved Plan.
- **DICOM viewing boundary:** OHIF đọc study từ Orthanc qua DICOMweb sau authorization gateway/policy; OHIF và Orthanc không tự thay thế business authorization của OTCMS.
- **Async boundary:** Upload/processing chỉ chuyển sang background job khi SPEC có latency/reliability requirement và Architecture Profile đã duyệt mechanism. Không mặc định message broker hoặc `@Async`.
- **Cross-cutting boundary:** Global exception handling, correlation ID, audit, security, configuration và integration observability dùng component tập trung, tránh implementation trùng giữa feature.
- **Exception process:** Bất kỳ thay đổi dependency direction, bypass service, expose entity, đưa authorization xuống controller/repository hoặc cho frontend truy cập credential/external admin API đều cần revised Plan/RFC và Human review phù hợp.

### DICOM Security Boundary Decision

- Orthanc REST/admin API SHALL không public trực tiếp ra Internet hoặc cho React gọi bằng administrative credential. Network route chỉ mở qua trusted internal boundary và gateway/backend policy đã duyệt.
- OHIF chỉ là viewer; OHIF SHALL không được coi là authorization boundary hoặc nguồn quyết định người dùng được xem study nào.
- Trước khi cấp viewer session/URL hoặc proxy DICOMweb request, backend/gateway SHALL xác thực người dùng và kiểm tra authorization theo action, `StudyInstanceUID`/external study reference, patient/imaging-order linkage và organizational scope do SPEC xác định.
- MinIO bucket/object SHALL private mặc định. Presigned/temporary access chỉ được cấp sau authorization, có bounded expiry và không chứa administrative credential.
- React SHALL không nhận MinIO access/secret key, Orthanc username/password, service token hoặc administrative endpoint credential.
- Application log, audit detail, metric label và error response SHALL không chứa patient name/identifier, raw clinical payload, raw DICOM payload hoặc raw DICOM metadata có khả năng định danh. Chỉ dùng safe/correlation identifier đã được policy cho phép.
- Development, automated test và demo SHALL dùng synthetic hoặc anonymized DICOM; dữ liệu bệnh nhân thật cần explicit approved data-use policy và controlled environment.
- DICOM binary SHALL chỉ được lưu chính thức trong Orthanc; MinIO SHALL không lưu bản sao DICOM mặc định. Ngoại lệ cần revised architecture decision với lifecycle, consistency và deletion policy.
- Upload SHALL enforce configurable size limit, supported content/type validation, archive safety khi dùng ZIP, DICOM parsing/required metadata check và duplicate detection dựa trên DICOM UID/idempotency evidence trước khi active linkage.
- Patient/imaging-order linkage, mismatch handling, re-link/correction permission, audit và conflict resolution SHALL do feature SPEC quyết định. Kỹ thuật viên hoặc Agent không được tự sửa liên kết ngoài approved workflow.

### Constitution — Layer 1

- `SEC-01`: Kế thừa không hardcode secret, mask PII; bổ sung dữ liệu khám chữa bệnh, chẩn đoán, tài liệu/hình ảnh y khoa vào phạm vi dữ liệu nhạy cảm. Không ghi payload bệnh án trong log.
- `SEC-02`: Yêu cầu policy theo SPEC cho từng vai trò, từng hành động và từng tài nguyên hồ sơ; kiểm tra quyền ở service/policy boundary. Spring Security là cơ chế sau khi profile được duyệt; không mặc định JWT, đăng ký công khai hay webhook.
- `API-01` mới: DTO ở biên API, Bean Validation, lỗi an toàn và contract test; pagination/giới hạn cho collection; thay đổi breaking cần kế hoạch version/deprecation.
- `DATA-01`: SPEC xác định retention, recovery, sửa/xóa và audit cho hồ sơ, lịch hẹn, phiếu khám, chỉ định và tài liệu. Không áp soft-delete đồng loạt; chính sách từng loại dữ liệu cần Human quyết định.
- `DATA-02/03` mới: JPA ownership/fetch, tránh N+1; Flyway migration đã áp dụng immutable; migration destructive cần backup/rollback/recovery theo safety constraint.
- `REL-01` mới: Orthanc/MinIO call có timeout hữu hạn; retry có maximum attempts, backoff/time budget và chỉ áp dụng cho transient failure. Validation/auth/business conflict/non-retryable 4xx fail fast; `429` tôn trọng server guidance. Upload dùng idempotency key kết hợp DICOM UID hoặc checksum/key theo SPEC; uniqueness/integrity được enforce tại DB.
- `MED-01` mới: Hồ sơ khám, chẩn đoán, đơn thuốc, tài liệu và DICOM metadata được xem là dữ liệu lâm sàng nhạy cảm; cấm log raw payload và cấm dùng dữ liệu bệnh nhân thật cho test/demo nếu chưa có quy trình ẩn danh được duyệt.
- `IMG-01` mới: Orthanc là kho DICOM chính thức; MinIO không lưu bản sao DICOM mặc định; PostgreSQL chỉ lưu business reference/identifier; OHIF không phải authorization boundary; Orthanc admin/REST API không public trực tiếp.
- `EXT-01` mới: Tích hợp PostgreSQL, MinIO và Orthanc phải định nghĩa durable operation state, partial-failure behavior, compensation/reconciliation và orphan handling. Orthanc thành công nhưng PostgreSQL finalization thất bại phải được tìm lại bằng correlation/idempotency evidence; database thành công nhưng external upload thất bại không được tạo active linkage. Reconciliation phải idempotent, auditable, bounded và không tự xóa clinical object khi thiếu retention/authorization/recovery policy.

### Constitution — Layer 2

- Thay `ARCH-01` Clean Architecture TypeScript bằng Spring layered architecture `controller → service → repository → entity`; controller không truy cập persistence trực tiếp hoặc trả JPA entity; service giữ business rule, authorization và transaction boundary. DTO/mapper ở API boundary.
- Đặt `ARCH-02` cho Spring dependency injection và configuration: constructor injection; configuration qua typed/validated `@ConfigurationProperties`; production secret không có default; security, CORS, OpenAPI, serialization và exception handling được cấu hình tập trung.
- Đổi rule async hiện hành thành `ARCH-03`; giữ yêu cầu async/reliability do SPEC quyết định. Không mặc định message broker, SLA hoặc circuit breaker.

### Constitution — Layer 3

- Chuyển JSDoc `@ears` thành Java comment/Javadoc cho business decision, không tag boilerplate.
- Dùng centralized Spring exception/security handlers và chốt error contract `{ "error": { "code", "message", "timestamp" } }`. `code` là stable machine-readable code, `message` an toàn cho client, `timestamp` dùng ISO-8601 có timezone. Mọi thay đổi schema phải đồng bộ DTO, handlers, Spring Security handlers, OpenAPI và integration tests.
- Request DTO dùng Jakarta Bean Validation; write transaction đặt tại service bằng `@Transactional`; read-only operation dùng `@Transactional(readOnly = true)` khi phù hợp; query phải parameterized; schema change phải có Flyway migration và evidence.
- Dùng Maven Wrapper sau khi có evidence. Exact Maven command chỉ được ghi sau khi wrapper tồn tại, command chạy thành công trên repository và result được persist trong Architecture Profile/CI; sự tồn tại của wrapper không tự chứng minh verification.
- Bổ sung `TEST-01`: unit test cho business rule/invariant/authorization; integration test cho controller, validation, error contract và security; persistence test cho repository/JPA constraint/Flyway; regression test cho bug fix; success/failure path; test isolation và execution-order independence.
- Khi H2 không phản ánh đúng PostgreSQL, dùng Testcontainers/PostgreSQL hoặc production-compatible database được duyệt; không thay đổi production query chỉ để làm H2 pass.
- Orthanc/MinIO integration test phải bao phủ timeout, retryable/non-retryable error, duplicate/idempotency, external-success/database-failure, database-success/external-failure và reconciliation theo risk. Test dùng controlled fake/stub/container, không phụ thuộc public/uncontrolled service.
- Bổ sung `OBS-01`: production-relevant event dùng structured logs; correlation/request ID được generate/validate tại boundary và propagate qua outbound call nhưng không chứa PII. External call ghi service/operation, duration, sanitized outcome, retry attempt và safe identifier; không log request/response body, DICOM payload/metadata, hồ sơ khám, diagnosis, prescription, credential, token hoặc presigned URL. Critical clinical/imaging/access state change tạo audit theo SPEC mà không sao chép clinical payload.
- Bổ sung `ENG-05`: dependency/upgrade cần purpose và justification; tương thích approved Java/Spring Boot BOM; review CVE, maintenance, license và transitive risk bằng approved process. Không thêm deprecated API; exception cần justification, migration owner/follow-up. MinIO/Orthanc/DICOM/security/serialization/file parser cần review credential, untrusted-input và CVE risk riêng. Dependency change phải đồng bộ `pom.xml`, profile/plan khi binding đổi, config, tests và evidence.

### AGENTS.md

- Đổi vai trò sang Senior Java Spring Boot Backend Engineer và scope sang `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`, `.sdd`, `docs`, `scripts` trong task.
- Thay các tham chiếu `package.json`, `tsconfig.json`, `npm publish`, `node_modules`, `dist` bằng `pom.xml`, Maven Wrapper, `target` và lệnh Maven có evidence.
- Giữ nguyên Human Final Review, Shadow Plan, Action Record, `/add-execute`, `Project Ownership: team`, `Agent Execution: orchestrated`, no self-approval, no push và review gate. Không sao chép quy tắc RWM cho phép thi công task nếu trái gate OTCMS.
- Bổ sung cấm đọc/ghi secret và bảo vệ dữ liệu bệnh nhân trong log/report; không tự xác định quyền truy cập hồ sơ.
- Cập nhật allowed/read-only/forbidden paths cho Maven/Spring; `target/` là generated output; dependency addition, security change và external integration vẫn cần Human gate theo governance.

### CLAUDE.md

- Đổi Project Identity sang OTCMS Backend, cấu trúc Java package theo tên package thực tế sau khi bootstrap (chưa suy đoán package name).
- Mô tả Spring layers, DTO/mapper, Spring Security, JPA/Flyway/PostgreSQL và Maven theo profile đã duyệt.
- Giữ nguồn chuẩn `.sdd/architecture-profile.md`, `.claude/skills/_shared/ai-review-protocol.md`, `.sdd/shared_context.md` và workflow SDD/ADD; bỏ cây TypeScript `src/domain/usecase/interface/infra` sau phê duyệt.
- Các quyết định nghiệp vụ phòng khám nằm trong feature SPEC, không điền giả định về vai trò, retention hoặc quy trình khám.
- Ghi rõ boundary PostgreSQL/MinIO/Orthanc/OHIF, upload DICOM thủ công cho MVP và các quyết định còn mở phải đi qua feature SPEC.

### Downstream governance, skills, docs và scripts

- Cập nhật `.sdd/mcp-config.yaml` để thay quyền theo `src/domain`, `src/usecase`, `src/interface` bằng boundary Java thực tế; không mở rộng quyền ngoài phạm vi cần thiết.
- Audit `.claude/skills/` cho ví dụ TypeScript/JSDoc, đường dẫn `src/usecase`, lệnh npm và assumption Clean Architecture; thay bằng profile-aware Java guidance hoặc đánh dấu generic khi chưa có repository evidence.
- Đồng bộ `.sdd/constraints/` với Flyway, medical-data/DICOM safety và no-direct-production-operation; không làm yếu các DB/Git/secret gate hiện có.
- Cập nhật `README.md` và `docs/` để không giới thiệu starter TypeScript như kiến trúc OTCMS đã chọn.
- Cập nhật `scripts/` chỉ khi script chứa assumption Node/TypeScript; giữ script generic nếu không bị ảnh hưởng. Mọi exact Maven command phải có repository evidence trước khi được coi là executable binding.

## Risk Assessment

- Đây là thay đổi Layer 1/2 và đảo kiến trúc đã ghi trong Constitution; ba tệp governance và profile phải đồng bộ trong cùng lần phát hành.
- Template scripts/skills/docs hiện có thể vẫn giả định TypeScript layout hoặc architecture profile cũ; cần audit downstream trước khi tuyên bố migration hoàn tất.
- Chưa có Java source/Maven Wrapper, nên chưa thể chứng minh build/test. `APPROVED` stack không đồng nghĩa repository đã chạy được.
- Dữ liệu y tế nhạy cảm; quyền truy cập, retention, audit và xử lý tài liệu cần SPEC cùng Human review riêng.
- Orthanc, MinIO và OHIF là external integrations mới; authorization bypass, duplicate storage và partial failure có thể tạo rò rỉ hoặc sai liên kết dữ liệu nếu boundary không được enforce.
- Mở rộng path/tool policy sai có thể cấp Agent quyền vượt quá package/task boundary; `.sdd/mcp-config.yaml` cần review riêng trong cùng migration.
- Downstream skills/docs/scripts có thể drift nếu chỉ đổi ba governance file; migration không được báo hoàn tất khi còn reference TypeScript có tính điều khiển execution.

## Migration Plan

1. Tech Lead/Architecture Board review RFC; chỉ tiếp tục sau persisted `APPROVED` với reviewer, timestamp và follow-up cụ thể.
2. Bump `CONSTITUTION.md` từ v1.1.0 lên v2.0.0 vì thay Clean Architecture/TypeScript bằng Spring layered architecture là breaking governance change.
3. Cập nhật đồng bộ `CONSTITUTION.md`, `AGENTS.md`, `CLAUDE.md` và `.sdd/architecture-profile.md`; giữ changelog và kiểm tra rule/reference chéo.
4. Cập nhật `.sdd/mcp-config.yaml` theo Java path boundary và least privilege.
5. Audit `.claude/skills/`, `.sdd/constraints/`, `README.md`, `docs/` và `scripts/`; sửa reference TypeScript/Node.js có ảnh hưởng execution hoặc ghi follow-up artifact/task có owner rõ ràng.
6. Chạy governance/template validation command đã có repository evidence; ghi exact command, exit status và gap chưa kiểm chứng.
7. Tạo approved task riêng để bootstrap Java project; không gộp dependency/config implementation vào governance approval.
8. Sau khi có `pom.xml` và Maven Wrapper, chạy Maven command thực tế, cập nhật exact verification binding trong Architecture Profile và review lại profile.
9. Chỉ tạo adapter-specific `PLAN.md`/`TASKS.md` khi profile, feature SPEC, command và external-integration boundary đã được duyệt.

## Rollback Plan

- Trước khi apply: nếu RFC bị `REJECTED` hoặc `REVISE`, không sửa Constitution/AGENTS/CLAUDE và giữ Architecture Profile ở `DRAFT`.
- Sau khi apply governance nhưng trước bootstrap: mọi phục hồi hoặc đổi lại kiến trúc phải qua RFC mới hoặc revision được quy trình cho phép; không âm thầm sửa Constitution đã khóa.
- Sau bootstrap nhưng trước shared data: rollback source/config theo approved migration task và đồng bộ lại profile/governance bằng review bền vững.
- Sau khi có Flyway/shared data: không sửa migration đã áp dụng; dùng migration phục hồi mới cùng backup, compatibility và recovery plan đã review.
- Không xóa file placeholder hoặc generated artifact trong rollback nếu chưa có explicit Human confirmation theo repository policy.

## Acceptance Criteria

- [ ] RFC có `APPROVED` Human Final Review với decision, authorized reviewer, timestamp và follow-up không phải placeholder.
- [ ] `CONSTITUTION.md` v2.0.0 không còn rule TypeScript/JSDoc/Clean Architecture xung đột với Spring layered architecture.
- [ ] Constitution có rule precedence và các rule `API-01`, `DATA-02`, `DATA-03`, `REL-01`, `MED-01`, `IMG-01`, `EXT-01`, Spring configuration, testing, observability và dependency gates.
- [ ] `AGENTS.md`, `CLAUDE.md` và Architecture Profile mô tả cùng Java/Spring architecture và cùng source-of-truth hierarchy.
- [ ] PostgreSQL/MinIO/Orthanc/OHIF responsibilities và authorization boundary nhất quán; DICOM không bị quy định lưu trùng trong MinIO.
- [ ] `.sdd/mcp-config.yaml` dùng Java path boundary với least privilege và không còn quyền điều khiển execution dựa trên layout TypeScript cũ.
- [ ] Reference TypeScript/Node.js có tính điều khiển execution trong `.claude/skills/`, constraints, README, docs và scripts đã được sửa hoặc có follow-up task/artifact được review với owner rõ ràng.
- [ ] JWT/session, role policy, retention duration, upload limits, patient matching, retry count, broker và MPR/3D không bị tự suy đoán; chúng được để cho approved profile/SPEC/Plan tương ứng.
- [ ] Governance/template validation đạt hoặc mọi check chưa chạy được được ghi rõ command, nguyên nhân và residual risk.
- [ ] Maven verification vẫn `BLOCKED` cho đến khi `pom.xml` và wrapper tồn tại; sau bootstrap, exact command/result được lưu trong Architecture Profile.

## AI Agent Recommendation
- Status: PENDING HUMAN REVIEW
- Scope: RFC-001, Architecture Profile, ba governance file và downstream policy/skills/docs/scripts bị ảnh hưởng
- Recommendation: Sau khi reviewer xác nhận rule set, external-integration boundary, migration và acceptance criteria, duyệt chuyển OTCMS sang Java/Spring Boot cùng PostgreSQL/MinIO/Orthanc/OHIF; thực hiện governance migration trước và bootstrap code bằng task riêng.
- Evidence: Human Director chọn Java/Spring Boot ngày 2026-09-18; Architecture Profile đã ghi kiến trúc upload DICOM thủ công; repository vẫn chưa có `pom.xml`, Maven Wrapper hoặc Java source; governance/downstream policy còn reference TypeScript layout.
- Risks and assumptions: Chưa có command executable; MinIO/Orthanc/OHIF chưa có implementation evidence; JWT, role policy, retention, patient matching và các nghiệp vụ dữ liệu y tế chưa được SPEC quyết định.
- Alternatives considered: Giữ TypeScript starter; không phù hợp lựa chọn stack vừa được Human Director xác nhận.
- Required human decision: Tech Lead/Architecture Board persist `APPROVED`, `REVISE` hoặc `REJECTED` cho thay đổi Layer 1/2 và binding architecture.

## Human Final Review
- Status: APPROVED
- Decision: Áp dụng RFC-001 vào `CONSTITUTION.md` v2.0.0.
- Reviewer: Long
- Reviewed at: 2026-09-25T08:50:16+07:00
- Follow-up: Đồng bộ các rule đã duyệt vào `CONSTITUTION.md` v2.0.0, sau đó kiểm tra rule ID, tham chiếu và phạm vi migration còn lại.
