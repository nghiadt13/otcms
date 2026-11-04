# UC35 — Review Action Record

- Scope: hoàn thiện UC35 trên `feat/hung/KAN-XX-doctor-examination-flow`; không commit, stage, reset hoặc xóa code; không sửa Imaging/Visit/Appointment/Billing, UC36 hoặc Complete Visit.
- Authorization: yêu cầu trực tiếp của người dùng cho review, sửa UC35 có điều kiện và exact Maven invocation trong lượt này. Record không thay Human Final Review hoặc approve SDD artifact. `.claude/skills/_shared/ai-review-protocol.md` không có trong checkout.
- Shadow Plan: kiểm tra Git baseline; đọc UC35/BR-42 và workflow; giữ require-existing nếu không có evidence cho auto-create; tăng kiểm chứng identity và dữ liệu examination; compile, focused tests, Clinical regression, diff check; liệt kê file stage.
- Baseline: HEAD `a644361`; một tracked change ở ClinicalExaminationJpaEntity và bốn UC35 untracked files. Khi đọc lần đầu, service đã dùng `findByVisitId(...).orElseThrow(MSG-41)` và test đã có missing-examination rejection. Không tìm thấy auto-create trong service hiện tại.
- File boundary: ClinicalExaminationJpaEntity; AfterVisitAdviceService; AfterVisitAdviceServiceImpl; V074; AfterVisitAdviceServiceImplTest; record này. Lượt này chỉ sửa test và thêm record, giữ các UC35 implementation files đã có.

## Exact repo evidence và behavior

- `../docs/requirements/UC_description.html:125–127`: UC35 Record After-Visit Advice, năm nhóm advice cho current visit. Chưa có file UC35 riêng trong requirements.
- `../docs/appendix-rules.md:57`: BR-42 giới hạn Orthopedic Doctor và advice thuộc examination record của visit. Câu này không mô tả trực tiếp thứ tự tạo record; không có evidence đã tìm được cho phép UC35 tự tạo examination.
- HTML `:111–113` gọi Record Orthopedic Examination là UC29; `:130–131` gọi UC37 là View e-Prescriptions. Không đổi số UC hoặc mở rộng workflow trong lượt này; tên UC37/UC45 từ yêu cầu được dùng để chọn Clinical regression hiện có.
- `../docs/fe-be-api-contract-patient-reception.md:749`: advice và diagnosis thuộc visit; đây là read contract, không cấp quyền tự tạo examination.
- Existing implementation: missing examination → ClinicalOperationException MSG-41, không save; existing DRAFT/COMPLETED → cập nhật advice trên cùng entity, giữ clinical fields và status. Không thêm completed-state lock khi repo chưa quy định.
- `V073__create_clinical_core.sql`: UNIQUE visit_id; V074 chỉ thêm năm nullable advice columns, không đổi migration cũ.
- Test additions: explicit empty repository result; same entity instance cho hai lần save cùng visit; cập nhật advice cũ trên COMPLETED giữ clinical fields/status và kiểm updatedAt.

## Exact verification

Environment: `JAVA_HOME=C:\Program Files\Java\jdk-21`; prepend `%JAVA_HOME%\bin` vào process PATH.

Maven executable: `C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd`.

Mọi Maven command bên dưới dùng argument `-Dmaven.repo.local=C:\Users\ad\.m2\repository`.

- `-DskipTests compile`: PASS, BUILD SUCCESS; Maven Enforcer xác nhận JDK 21.
- `-Dtest=AfterVisitAdviceServiceImplTest test`: PASS, 8 tests, 0 failures/errors/skipped sau khi chạy ngoài sandbox. Lần đầu trong sandbox dừng tại testCompile khi truy cập cached spring-boot-starter-jackson JAR; không sửa dependency để xử lý.
- `-Dtest=ClinicalExaminationServiceImplTest,ClinicalOrderingServiceImplTest test`: PASS ngoài sandbox, 24 tests (8 examination + 16 ordering), 0 failures/errors/skipped, BUILD SUCCESS. Lần đầu trong sandbox gặp cùng lỗi truy cập cached JAR tại testCompile.
- `git diff --check`: PASS sau regression và cập nhật record.

Sync-back: không approve hoặc sửa SDD artifact; không đổi workflow/public API; không stage/commit. Không chạy PostgreSQL integration hoặc áp migration vào database trong lượt review này.

## Follow-up — Current Clinical integration review

Lượt tiếp theo được người dùng cho phép review toàn Clinical và thêm catalog adapter. Giữ nguyên behavior UC35; bổ sung guard null command/visit (MSG-39), null doctor (MSG-40) và bốn test: missing visit, null doctor, unassigned doctor, toàn bộ fields whitespace. UC35 hiện PASS 12/12 trong suite 55 tests. V074 không sửa. Record chi tiết: `clinical-integration-review-action-record.md`.

Context/PostgreSQL đã được thử trong lượt follow-up nhưng BLOCKED bởi connection refused 127.0.0.1:5432, SQLSTATE 08001. Chưa xác minh migration/version bằng database; không coi unit test là integration PASS. Production VisitClinicalContextPort vẫn thiếu. Không stage/commit.
