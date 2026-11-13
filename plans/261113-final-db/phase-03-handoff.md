# Phase 03a — bàn giao (13/11, Claude làm phần lõi)

## BÀN GIAO CHO LONG — 13/11 tối (đọc phần này trước)

Codex đã dừng sau bước 1. Phần còn lại của 03a giao cho Long qua Jira; NghiaDT/Claude giữ luồng D2, 03b, phase 10.

**Trạng thái code (đã push):**
- `final-db` @ `bbb9ad7c` — đã có P05 (`queue_entries`, audit visit vào `audit_logs`). P04, P05, P07 đều sửa package `appointment`/`visit` của Long: xem `git log --oneline origin/product..origin/final-db -- backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit backend/src/main/java/fu/fa26/sep490/g58/otcms_be/appointment`.
- `final-db-p03-diagnostic-imaging` = `final-db` + commit bàn giao P03: V118 (mục 1–4, bảng cũ còn), `ClinicalServiceOrderExecutionPort` (có `execution_version`), entity mới `DiagnosticReportJpaEntity`, `ImagingStudyJpaEntity`, 2 cột đọc trên `ClinicVisitJpaEntity`. Đã kiểm: compile, 93 migration trên DB trống, V118_1 chạy tiếp được (còn 36 bảng), `OpenApiContractSnapshotTest` 1/0/0, OpenAPI no differences. Chưa chạy full suite trên nhánh này.

**Môi trường:** làm đúng `team-setup-docker-db.md` (cùng thư mục) tới mục 6.

**Cách làm song song (không ai sửa file của người kia):**
- Long: nhánh con từ `final-db-p03-diagnostic-imaging`, mỗi ticket một nhánh `final-db-p03-long-<việc>`, PR vào `final-db-p03-diagnostic-imaging` (không vào `final-db`). PR phải compile và giữ app khởi động được.
- Claude: luồng D2 + e2e trong 12 file "Claude giữ" (mục 2) commit thẳng lên `final-db-p03-diagnostic-imaging`. Long KHÔNG sửa 12 file đó; cần đổi gì ở đó thì ghi vào ticket.
- Bước 5 (audit) gộp vào ticket của package tương ứng: audit diagnostic đi theo bước 2, audit imaging đi theo bước 4. Bước 6 (sửa test) cũng theo package. Bước 7 = ticket kiểm tra cuối.
- Thứ tự gợi ý: bước 2 → bước 4 → bước 3 (bước 3 độc lập, làm lúc nào cũng được); ticket kiểm tra cuối làm sau cùng.

**Quyết định đã chốt trong lúc làm (bắt buộc theo):** xem 2 khối "ĐÃ CHỐT" ở mục 4 (18:40 cột `ingest_operation_id`; 19:30 `execution_version` thay version API). Các quy tắc port ở mục 1. Mục "Tiến độ" (mục 4): Long tick + ghi chú sau mỗi ticket, thay chỗ "Codex".

Worktree: `D:\Education\SEP490-FALL2026\otcms-p03`, nhánh `final-db-p03-diagnostic-imaging` (từ `final-db` @ 6122c6e0). Chưa commit. Đọc kèm `phase-03-diagnostic-imaging-orders.md` (mục 5, và khối **ĐÃ CHỐT 13/11** ở cuối — khối đó GHI ĐÈ các mặc định trong file) và `agent-rules.md`.

**Bắt buộc:** cập nhật mục "Tiến độ" cuối file sau MỖI bước. Không commit, không push, không merge. Phase 05 (`otcms-p05`) merge TRƯỚC phase này; cả hai sửa `ClinicVisitJpaEntity` → khi rebase giữ dòng walk-in của 05 và dòng imaging của 03.

## 0. Chia 03a / 03b (quyết định đã chốt)

- **03a (đợt này):** `V118` = mục 1–4 (thêm cột, chép dữ liệu, ràng buộc, trigger mới). Bảng cũ VẪN CÒN. Toàn bộ code chuyển sang đọc/ghi cột mới; class của bảng cũ còn nằm đó nhưng không ai dùng.
- **03b (sau khi 03a xanh):** `migration-staged/V118_1__final_db_diagnostic_imaging_drop_old.sql` (mục 5 cũ: DROP bảng/hàm) chuyển sang `migration/`; xoá entity/repository của 18 bảng; grep "phải rỗng" của plan.
- Vì V118 sửa tại chỗ `diagnostic_reports` (đổi tên cột) và `imaging_studies` (đổi PK) nên app chỉ khởi động lại được khi entity của hai bảng này đã viết lại (bước 1 dưới).

## 1. Claude đã làm

| Việc | File | Đã kiểm |
|---|---|---|
| `V118` vào `migration/`, chỉ mục 1–4; mục 5 tách sang `migration-staged/V118_1…` | `db/migration/V118__final_db_diagnostic_imaging_orders.sql`, `db/migration-staged/V118_1__…drop_old.sql` | DB trống: V001→V119 OK, rồi V118_1 OK, không sót hàm đọc bảng cũ, còn 43 bảng (sau P05: 36) |
| **Port cột thực hiện chụp** trên `clinical_service_orders` (Q7: không map entity thứ hai) | `clinical/service/ClinicalServiceOrderExecutionPort.java`, `clinical/service/impl/ClinicalServiceOrderExecutionPortImpl.java` | Chạy tay toàn bộ state machine trên DB sau V118: các bước đúng = 1 dòng, thoả mọi CHECK mới; bước sai thứ tự = 0 dòng; `version` không đổi |

Quy tắc của port (đừng phá):
- `requestId` trong API = `clinical_service_orders.id` = `ImagingExecution.orderId()`.
- Mọi hàm ghi là UPDATE có điều kiện, trả số dòng; 0 = không đúng trạng thái → đọc lại rồi quyết replay hay `STATE_CONFLICT` (như code cũ). Không SELECT-rồi-INSERT.
- Port KHÔNG tăng `version` (cột đó của `ServiceOrderJpaEntity`). Chỗ code cũ khoá `diagnostic_requests` (`findByIdForUpdate`) thì gọi `port.findForUpdate`.
- Mọi hàm `MANDATORY`: caller phải ở trong `@Transactional`.
- Thiếu hàm đọc nào thì THÊM vào port (cùng kiểu native + `execution_status is not null`), không viết SQL vào bảng clinical từ package `diagnostic`.

## 2. Phân file (để làm song song, không ai sửa file của người kia)

**Claude giữ (luồng D2 — bỏ outbox, cùng transaction), Codex KHÔNG sửa:**
`diagnostic/service/impl/DiagnosticEvidenceServiceImpl.java`, `diagnostic/service/impl/DiagnosticFindingsServiceImpl.java`, `diagnostic/service/impl/DiagnosticResultEventQueryServiceImpl.java`, `diagnostic/integration/VisitDiagnosticPort.java`, `diagnostic/integration/ImagingStudyPort.java`, `visit/service/impl/VisitImagingCoordinationServiceImpl.java`, `visit/service/VisitImagingCoordinationService.java`, `visit/integration/diagnostic/VisitDiagnosticReadAdapter.java`, `imaging/service/impl/ImagingConfirmationDispatcher.java`, `imaging/service/impl/ImagingConfirmationOutboxPersistenceService.java`, `imaging/integration/diagnostic/ImagingStudyContractAdapter.java`, `imaging/service/impl/ImagingSendService.java`, và test e2e mới `ImagingResultLifecyclePostgresIntegrationTest`.

**Codex làm (theo plan mục 5.11 và "Related Code Files"):**
1. **Entity theo cấu trúc V118** (làm TRƯỚC, Claude cần để viết D2):
   - `diagnostic_reports` mới (một dòng = một phiên bản kết quả và/hoặc PDF của phiên bản đó; cột ở plan mục 5.3): viết lại `DiagnosticReportJpaEntity` thành entity duy nhất của bảng (gộp `DiagnosticFindingsVersionJpaEntity` vào), repository tương ứng. `findingsVersionId` = `diagnostic_reports.id`, `reportId` = `report_id`.
   - `imaging_studies` mới (PK `id` = operationId cũ; cột ở plan mục 5.4; `dicom_index` jsonb): viết lại `ImagingStudyJpaEntity` + `ImagingStudyRepository`, gộp `ImagingIngestOperationJpaEntity` vào.
   - `visits`: thêm `imaging_result_ready_at`, `findings_released_at` vào `ClinicVisitJpaEntity` (map, `insertable/updatable` theo plan) — chỉ thêm field, Claude dùng.
   - Sau bước này: app khởi động được (`ddl-auto: validate`). Báo trong "Tiến độ" kèm tên method public của 2 entity mới.
2. `diagnostic` (ngoài file của Claude): đổi `DiagnosticRequestJpaRepository` / `DiagnosticAttemptJpaRepository` sang `ClinicalServiceOrderExecutionPort` ở: `DiagnosticRequestServiceImpl` (cancel → `port.cancel`, transfer → `port.transferRoom`, review key ở mục 5.6), `DiagnosticImagingRequestRegistrationImpl` (snippet H → `port.assignImagingRoom`), `DiagnosticStatusServiceImpl`, `DiagnosticHistoryServiceImpl`, `DiagnosticTechnicianRequestQueryServiceImpl`, `DiagnosticViewerAuthorizationServiceImpl`, `DiagnosticVisitCompletionEvidenceServiceImpl`, `DiagnosticPatientHistoryQueryServiceImpl` (query `findReleasedPatientHistory` viết lại trên `diagnostic_reports` mới + port), `DiagnosticOrderStatusQueryServiceImpl`. `DiagnosticRequestJpaEntity` được thay bằng `ImagingExecution` (record của port) trong các file này.
3. `clinical`: snippet A (submit replay trên dòng `round_head`, constant `uk_clinical_orders_submit_key`, `uk_clinical_orders_round_number`) và G (danh sách đợt cho lễ tân đọc dòng `round_head`). Q1: GIỮ `invoices.clinical_ordering_round_id` (chỉ bỏ FK) — không sửa billing ngoài `invoice_items.service_order_id`.
4. `imaging` (ngoài file của Claude): snippet E (`ImagingUploadRegistrationService`: INSERT + bắt `uk_imaging_studies_upload_scope` ở bean không transactional), F (`ImagingDicomWebScopeResolver` dùng `dicom_index @>`), reconciliation trỏ `imaging_studies(id)`.
5. Audit 3 nguồn cũ (`diagnostic_audit_events`, `diagnostic_history_access_audits`, `imaging_audit_events`) → `AuditLogWriter` theo plan mục 5.9 (ở file của Claude thì Claude làm).
6. Test: sửa SQL vào bảng cũ; 3 file test mới của plan mục 5.13 trừ e2e (Claude); đổi `RELEASE_PENDING` → `RELEASED` ở 3 test.
7. `./mvnw.cmd test` đầy đủ (`OTCMS_TEST_DB_NAME=otcms_test_db_p03`), grep, OpenAPI.

## 3. Luồng D2 (Claude làm sau bước 1 của Codex) — để Codex biết đừng đụng

- Liên kết ảnh: `ImagingConfirmationDispatcher` gọi MỘT transaction: `linkConfirmedStudy` (port `findForUpdate` + `linkStudy`) → `ClinicalImagingOrderPort.markResultAvailable` → `VisitDiagnosticPort.publishResultReady` (`visits.imaging_result_ready_at`, MANDATORY) → `imaging_studies.state = LINKED`, `acknowledged_at` → audit. Thất bại: rollback, rồi ghi retry/đối soát ở transaction `REQUIRES_NEW` riêng (như hiện tại).
- Phát hành: `DiagnosticFindingsServiceImpl.complete` trong MỘT transaction: ghi dòng `diagnostic_reports` (COMPLETED + `released_at`) → `publishFindingsReleased` → `acceptFindingsRelease`; trả `releaseState = "RELEASED"`. Gỡ `noRollbackFor` ở `acceptFindingsRelease` và `send`.
- `dispatchDueOutbox`, `DiagnosticOutboxJpaEntity` và mọi `enqueue(...)` bị bỏ.

## 4. Tiến độ (Codex cập nhật)

- [x] 1 entity `diagnostic_reports`, `imaging_studies`, 2 cột `visits`; app khởi động được (kiểm chứng 18:47, xem checkpoint dưới)
- [ ] 2 diagnostic → port
- [ ] 3 clinical A, G
- [ ] 4 imaging E, F, reconciliation
- [ ] 5 audit
- [ ] 6 test
- [ ] 7 full test, grep, OpenAPI
- [ ] (Claude) D2 + e2e
- [ ] 03b: V118_1 vào `migration/`, xoá class 18 bảng, grep rỗng, full test

### Checkpoint Codex — 13/11/2026, trước bước 1

- [x] Đã đọc handoff → plan (gồm khối ĐÃ CHỐT 13/11) → agent-rules, quy ước repo; xác nhận đúng worktree/nhánh. Chưa sửa Java, migration hoặc file D2; chưa chạy Maven. Docker `otcms-final-db` đang healthy tại 5433; DB riêng `otcms_test_db_p03` chưa tồn tại.
- [x] **ĐÃ GIẢI QUYẾT 18:40 — ràng buộc còn sót khi tách 03a/03b.** `V060__create_imaging_persistence.sql:68` tạo `imaging_studies.ingest_operation_id UUID NOT NULL UNIQUE REFERENCES imaging_ingest_operations(id)`. V118 giữ nguyên cả NOT NULL lẫn FK này; chỉ `migration-staged/V118_1__final_db_diagnostic_imaging_drop_old.sql:24` mới DROP cột. Trong khi handoff bước 4 / snippet E yêu cầu INSERT operation mới chỉ vào `imaging_studies`, không ghi bảng cũ. Không map cột cũ → INSERT lỗi NOT NULL; map cùng operationId → lỗi FK nếu không INSERT bảng cũ. Đây là kiểm tra tĩnh từ migration, chưa chạy test hay kết luận app đã khởi động.
- **ĐÃ CHỐT (Claude, 13/11 18:40):** V118 chưa merge nên được sửa. Đã thêm vào mục 2.3 của V118: `DROP CONSTRAINT imaging_studies_ingest_operation_id_fkey` + `ALTER COLUMN ingest_operation_id DROP NOT NULL`; cột và dữ liệu cũ giữ tới V118_1. Đã chạy lại DB trống V001→V119 rồi V118_1: OK, 43 bảng. Đã dò toàn bộ FK/NOT NULL từ bảng mới sang bảng cũ: chỉ còn cột này bị chặn (`clinical_service_orders.ordering_round_id` NOT NULL là cố ý, cột giữ lâu dài, không FK; `imaging_studies.attempt_id` NOT NULL đúng thiết kế). Entity `ImagingStudyJpaEntity` KHÔNG map `ingest_operation_id` (nó bị bỏ ở 03b). Lưu ý: DB test `otcms_test_db_p03` nếu đã chạy V118 bản cũ thì xoá/tạo lại. → Tiếp tục bước 1.
- (Câu hỏi gốc) **Câu hỏi cần NghiaDT/Claude chốt:** có cho phép sửa tối thiểu V118 để `ALTER COLUMN ingest_operation_id DROP NOT NULL` và bỏ FK cũ `imaging_studies_ingest_operation_id_fkey`, vẫn giữ cột/dữ liệu cũ đến 03b không? Cách này cho phép toàn bộ code 03a chỉ ghi bảng mới, không cần ghi song song bảng cũ. Hiện yêu cầu bảo toàn phần V118 của Claude nên Codex chưa áp dụng; V118_1 vẫn ở staged, mọi bước 1–7 chưa được tick hoàn tất. Sau khi chốt, tiếp tục bước 1 theo đúng thứ tự user yêu cầu.

### Checkpoint Codex — bước 1 đang kiểm chứng, 13/11 sau 18:40

- Đã tiếp nhận quyết định 18:40; V118 đã được Claude sửa, Codex không sửa migration. Tạo DB riêng `otcms_test_db_p03` mới trên Docker 5433 (trước đó chưa tồn tại).
- Đã sửa `DiagnosticReportJpaEntity`, `DiagnosticReportJpaRepository`, `ImagingStudyJpaEntity`, `ImagingStudyRepository`, thêm 2 field chỉ đọc (`insertable=false, updatable=false`) và getter vào `ClinicVisitJpaEntity`. Không map `ingest_operation_id`; getter `getIngestOperationId()` tạm trả PK `id` cho caller D2 cũ, không phải field/cột. Repository tạm giữ overload `findById(String)` chuyển sang `findByStudyRef` để adapter D2 của Claude còn biên dịch trước khi đổi; cần gỡ khi D2 đã đổi caller.
- Đang chạy DUY NHẤT `./mvnw.cmd test -Dtest=OpenApiContractSnapshotTest`, log `backend/p03-step1-test.log`; main compile đã qua. Chưa tick bước 1, chưa chạy full suite. `git diff --check` chỉ báo dòng trống EOF có sẵn trong V118 của Claude; không chỉnh file đó.
- Lỗi môi trường công cụ: lệnh `python` là Windows Store alias, chưa chạy script; đã chạy lại thành công bằng `C:\Users\123bi\.claude\skills\.venv\Scripts\python.exe`. Không ảnh hưởng DB/code nghiệp vụ.


### Checkpoint Codex — HOÀN TẤT bước 1, 13/11 18:48

- App profile `final-db` chạy thật trên `jdbc:postgresql://127.0.0.1:5433/otcms_test_db_p03`, HTTP `127.0.0.1:18003`; Flyway validate 92 migration tới V119, Hibernate `ddl-auto: validate` qua, Tomcat/app started lúc 18:47:34. `GET /v3/api-docs` HTTP 200. Đã dừng đúng app P03 sau kiểm tra, Docker giữ nguyên.
- Targeted `OpenApiContractSnapshotTest`: **1 test / 0 failure / 0 error / 0 skip**, BUILD SUCCESS, log `backend/p03-step1-test-retry.log`. Lần đầu 1 error do Codex map nhầm `report_sha256` thành CHAR; đã sửa về VARCHAR(64) theo DB (không đổi migration), chạy lại đạt. Đây là lỗi mới đã xử lý, không gán baseline. Chưa chạy full suite (để bước 7 chạy duy nhất một lần).
- OpenAPI từ snapshot test và từ app thật `.snapshots/openapi-step1-running.json`: đều **no differences**, dùng đúng allow path/schema của agent-rules. Chưa chạy grep nghiệm thu toàn phase vì bước 2–7 và D2 chưa làm; class/bảng cũ cố ý giữ cho 03b.
- Chỉ sửa 5 file Java của bước 1 như checkpoint trước; không sửa file D2, V118/V118_1 hay migration khác. `ImagingStudyJpaEntity` không map cột `ingest_operation_id`; `dicom_index` dùng record lồng có kiểu. Hai cột visit chỉ đọc để cập nhật native của D2 không bị JPA ghi đè.
- Lưu ý caller cũ: constructor PDF cũ và constructor study metadata cũ còn để main/test/D2 biên dịch trước các bước chuyển luồng. Chưa được coi chúng là luồng ghi mới hoạt động hoàn chỉnh; study mới phải tạo bằng `(ImagingOperationRecord, uploadKey)` để đủ dữ liệu bắt buộc, PDF mới gắn vào dòng findings bằng `attachReport` (API reportId lấy `getReportId`, findingsVersionId lấy `getId`). Gỡ overload `ImagingStudyRepository.findById(String)` khi adapter D2 đã chuyển sang `findByStudyRef`.

**Public API entity để Claude viết D2 (đã compile):**

- `DiagnosticReportJpaEntity`: constructor draft `(UUID id, UUID requestId, UUID visitId, String studyRef, int versionNumber, String findingsText, UUID recordedByDoctorId, Instant occurredAt)`; constructor PDF cũ vẫn có. `markCompleted(UUID reportId, Instant now)` set state COMPLETED + completedAt + releasedAt trong cùng lần flush; gọi `recordCompleteKey(key, fingerprint)` trước flush đó. `attachReport(UUID reportId, String storageRef, String sha256, DiagnosticReportValidationStatus status, UUID doctorId, Instant uploadedAt, Instant validatedAt)` chỉ ở DRAFT. Các method còn lại:
  `getId`, `getRequestId`, `getVisitId`, `getStudyRef`, `getReportVersion`, `getStorageRef`, `getContentSha256`, `getValidationStatus`, `getUploadedByDoctorId`, `getUploadedAt`, `getValidatedAt`, `getReleasedAt`, `setValidationStatus`, `setValidatedAt`, `setReleasedAt`, `getVersionNumber`, `getServiceOrderId`, `getReportId`, `getResultType`, `getState`, `getFindingsText`, `getCorrectionReason`, `getRecordedByDoctorId`, `getOccurredAt`, `getRecordedAt`, `getCompletedAt`, `getDraftIdempotencyKey`, `getDraftFingerprint`, `getCorrectionIdempotencyKey`, `getCorrectionFingerprint`, `getUploadIdempotencyKey`, `getUploadFingerprint`, `getCompleteIdempotencyKey`, `getCompleteFingerprint`, `reviseDraft`, `setCorrectionReason`, `attachReport`, `markCompleted`, `recordDraftKey`, `recordCorrectionKey`, `recordUploadKey`, `recordCompleteKey`.

- `ImagingStudyJpaEntity`: constructor `(ImagingOperationRecord operation, String uploadIdempotencyKey)`; `toModel()` trả projection UPLOAD. `prepareConfirmation(UUID eventId, String key, String fingerprint, Instant now)`, `markAcknowledged(Instant now)`, `scheduleRetry(Instant nextAt, String safeCode)`, `markReconciliationRequired(String safeCode)`, `acceptFindingsRelease(UUID eventId, String payloadSha256, UUID findingsId, UUID reportId, String reportSha256, Instant now)`. Record lồng `DicomIndex(List<DicomSeries>)`, `DicomSeries(uid, orthancRef, instances)`, `DicomInstance(sopUid, orthancRef, frames)`; JSON giữ tên `orthanc_ref`, `sop_uid`. Danh sách method:
  `toModel`, `getId`, `getState`, `getVersion`, `applyValidation`, `setArchiveStudyRef`, `setStudyRef`, `getIngestOperationId`, `getServiceOrderId`, `getVerificationStatus`, `getStudyRef`, `getRequestId`, `getAttemptId`, `getVisitId`, `getPatientId`, `getActorAccountId`, `getArchiveStudyRef`, `getStudyInstanceUid`, `getModality`, `getSeriesCount`, `getInstanceCount`, `getVerificationEvidence`, `getPayloadSha256`, `getCreatedAt`, `getUpdatedAt`, `getUploadIdempotencyKey`, `getDicomIndex`, `getSendIdempotencyKey`, `getSendFingerprint`, `getConfirmationEventId`, `getConfirmationAttemptCount`, `getNextConfirmationAttemptAt`, `getLastSafeErrorCode`, `getAcknowledgedAt`, `getReleaseEventId`, `getReleasePayloadSha256`, `getReleasedFindingsId`, `getReleasedReportId`, `getReleasedReportSha256`, `getFindingsReleasedAt`, `setDicomIndex`, `prepareConfirmation`, `markAcknowledged`, `scheduleRetry`, `markReconciliationRequired`, `acceptFindingsRelease`, `DicomIndex`, `DicomSeries`, `DicomInstance`.

**ĐÃ CHỐT (Claude, 13/11 19:30) — version / expectedVersion của diagnostic:**
- Cột mới `clinical_service_orders.execution_version bigint NOT NULL DEFAULT 0` (thêm vào V118, chép từ `diagnostic_requests.version`, CHECK `>= 0`). Đây là `@Version` cũ của diagnostic_requests. KHÔNG dùng `clinical_service_orders.version` (của `ServiceOrderJpaEntity`) để hai bên không đá nhau.
- Port: `ImagingExecution.executionVersion()`; `assignImagingRoom` để 0 (như INSERT cũ); 5 hàm ghi còn lại nhận `long expectedVersion` ngay sau `orderId`, UPDATE có `WHERE execution_version = :expectedVersion` và `execution_version + 1`. Trả 0 dòng = sai trạng thái hoặc sai version → đọc lại, quyết replay / `VERSION_CONFLICT` / `STATE_CONFLICT` như code cũ.
- Code gọi: chỗ cũ dùng `request.getVersion()` → `execution.executionVersion()`; `requireVersion` và `DiagnosticTransitionPolicyImpl` (`expectedVersion != request.version()`) giữ nguyên ý nghĩa. Response trả `version` = `executionVersion` (sau khi ghi thì đọc lại hoặc +1). Luồng hệ thống không có expectedVersion từ API (first use, link study) truyền version vừa đọc bằng `findForUpdate`.
- Thao tác chỉ ghi `diagnostic_reports` (draft, correction, upload PDF) không tăng `execution_version` — giống cũ (cũ chỉ ghi bảng findings, không sửa request). Nếu test cũ chứng minh khác thì DỪNG báo lại.
- Đã kiểm: DB trống V001→V119 rồi V118_1 OK; `mvnw compile` OK. Chưa chạy test hành vi guard version (Codex có test T012/T014 sẽ phủ).
- **`otcms_test_db_p03` đã chạy V118 bản cũ → checksum lệch: xoá và tạo lại DB trước khi chạy test.** → Tiếp tục bước 2.

(Câu hỏi gốc) **Câu hỏi trước bước 2 — chờ chốt, chưa tự chọn:** API diagnostic vẫn trả `version`, nhận `expectedVersion` (cancel/transfer/review/findings), code cũ so với `DiagnosticRequestJpaEntity.@Version` và tăng khi sửa request. `ImagingExecution` hiện không chứa version; port cố ý không tăng `clinical_service_orders.version`. Có dùng version của `clinical_service_orders` làm nguồn kiểm tra API (thêm hàm đọc qua port), hay Claude cung cấp cơ chế khác để giữ ý nghĩa optimistic locking? Không gán cứng 0, không bỏ kiểm tra expectedVersion, không tự tăng version trong port. Đã gửi câu hỏi cho user; phần phụ thuộc bước 2–7 đang dừng theo quy tắc gặp chỗ không chắc. Bước 1 đã hoàn tất độc lập.
