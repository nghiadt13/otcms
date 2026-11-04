# Doctor Medical Documents — UC30/31/32 — Action Record

## A. Git state, phạm vi và Shadow Plan

Working tree ban đầu sạch trên feat/hung/KAN-XX-doctor-prescription tại e47160e. Sau fetch, origin/product vẫn là 08c8770833cca743470ea8d81a20cbed88036822; UC36 CHƯA merge. Theo yêu cầu lấy product làm base, đã tạo feat/hung/KAN-XX-doctor-medical-documents từ origin/product; giữ nguyên nhánh Prescription đã push. Không merge/rebase/reset/restore/stash/git clean/commit/push. Không tự nhận Human approval.

Shadow Plan: kiểm tra requirements, toàn bộ code/storage/FE; chốt quyền và release; thêm metadata/payload Clinical và CAS; HTTP/validation; test service/security/wiring/architecture/PostgreSQL; regression và ghi exact staging boundary. Không làm frontend, Room/Queue, Billing, Diagnostic, DICOM, Notifications, UC28, UC33 aggregation hay UC34 tổng thể.

## B. Requirement evidence

Nguồn: docs/requirements/UC_description.html UC30/31/32 và các dòng BR/MSG cần thiết trong docs/appendix-rules.md. UC30 cho Doctor/Receptionist thêm referral/outside report vào đúng Patient/Visit; UC31 là Doctor sửa/thay khi còn được phép; UC32 yêu cầu giới hạn theo công việc và own/approved dependent. BR-38 xác định Doctor phụ trách; BR-39 yêu cầu kết quả/report hoàn tất và được clinic cho phép; BR-40 bắt buộc clinic report là PDF, đúng Patient/Visit, chỉ Doctor phụ trách sửa trước patient release. BR-41 giới hạn đọc theo current care và Receptionist chỉ examination reports cần thiết. BR-42/43 vẫn thuộc Advice/Prescription, không bị triển khai lại. MSG-39/40/41/42/44/47 được tái sử dụng.

Spec drift: BR-04 trong HTML là Patient Result Availability, nhưng BR-04 trong appendix hiện là password policy. Không dùng rule password cho tài liệu. Quyền Patient trong implementation dựa trên UC32 và BR-39/40 hiện tại; nội dung supporting trong HTML chỉ làm ngữ cảnh. Không sửa numbering/requirements trong checkpoint.

## C. Existing document/storage audit

Không có MedicalDocument production backend hoặc route/JSON/multipart contract Medical Documents trong frontend. Các schema proposal/action record chỉ là tài liệu; prescription document mocks thuộc UC37, không sao chép sang đây. MultipartFile đang dùng cho ảnh hồ sơ/content, có bytea và kiểm tra JPEG/PNG/WebP. Không tìm thấy generic production storage provider. Diagnostic ReportStoragePort là interface riêng cho study/report, chưa có implementation; không dùng chéo cho Clinical. MinIO là định hướng kiến trúc, chưa có provider để tái sử dụng. Không thêm vendor/dependency.

## D. Workflow placement

Tài liệu hỗ trợ Patient + Visit trong luồng khám, nằm cạnh Examination/Advice/Prescription/imaging links. Upload không đóng Visit, phân phòng, thay queue, tạo order/invoice hay thay imaging status. Clinical completion evidence không thay đổi.

## E. Ownership boundary

Chỉ Clinical sở hữu metadata, payload và document-level release. Đọc qua VisitClinicalContextPort, VisitClinicalCareContextService, VisitDoctorCareRelationQueryService, ClinicalDoctorIdentityResolver, ReceptionistAccountAccessService và PatientProfileAccessService. Không import Visit/Patient/Diagnostic/Billing repository/entity trong production Medical Documents. Không truy cập room/queue persistence.

## F. Persistence model

V100 tạo clinical_medical_documents và clinical_medical_document_payloads. Metadata có UUID Patient/Visit, loại, title, basename, MIME, byte size, version, timestamps, releasedAt/releasedByDoctorProfileId. Payload có document_id PK/FK và bytea. Không UNIQUE(visit_id): nhiều documents/Visit được phép. Ba loại nhỏ, trực tiếp từ requirement: CLINIC_EXAMINATION_REPORT, EXTERNAL_REFERRAL, EXTERNAL_REPORT. Loại và linkage không đổi qua update. Không snapshot diagnosis/names/định danh hành chính.

Version đầu là 1, @Version biểu diễn field; owner repository dùng conditional update tăng version kể cả update chỉ thay payload. Constraints bảo vệ nonblank metadata, MIME, clinic PDF, kích thước, version và cặp release evidence. External UUID linkage theo convention Clinical V073: được xác minh bằng public Visit context; không query persistence của Visit/Patient.

## G. Storage decision

PostgreSQL bytea là storage thật, không phải adapter in-memory. Tách bảng payload để list/metadata không tải binary. Giới hạn 2 MiB khớp application.yml max-file-size=2MB, max-request-size=3MB; không thay cấu hình chung. Không @Lob/OID, path C:/..., URL tạm hay public blob URL. Sau này thay storage nội bộ vẫn giữ document identity/linkage/release/API.

## H. Authorization

Doctor command: JWT accountId -> existing ClinicalDoctorIdentityResolver -> doctorProfileId -> VisitClinicalContextPort -> responsible Doctor + clinicalWorkAllowed + server Patient. Không lấy Patient/Doctor/released state từ JSON.

Doctor read: public Visit context xác định Patient; Doctor phụ trách hiện tại được đọc, hoặc public VisitDoctorCareRelationQueryService phải chứng minh Doctor đang chăm sóc Patient qua OPEN + IN_PROGRESS. Cho phép đọc một tài liệu cũ phục vụ current care theo BR-41, không tạo API tổng hợp UC33. Mutation vẫn đòi Visit gốc hiện tại writable và Doctor phụ trách. Quyền read không mở rộng quyền sửa.

## I. File validation

Clinic report: application/pdf và PDF header/object/startxref/EOF markers; không dựa vào đuôi filename. Đây là kiểm tra nội dung có giới hạn, không phải full PDF parser/malware scanner. External referral/report chấp nhận PDF/JPEG/PNG, dựa vào PDF requirement và định dạng ảnh sẵn có; kiểm tra signature/end marker. Không tự thêm DOCX/EXE/MIME chưa hỗ trợ. File rỗng/quá 2MiB/sai MIME hoặc nội dung trả MSG-47. Basename được loại đường dẫn/control characters.

Download luôn attachment, no-store, nosniff; không render inline hoặc lộ storage path. Byte arrays được defensive copy.

## J. Release semantics

Clinical chưa có release model tổng quát; Diagnostic release thuộc study/report khác context. Chỉ thêm sticky release của từng document. Upload luôn private. Doctor phụ trách hiện tại được release; clinic report còn phải có ClinicalExamination COMPLETED theo BR-39. External file chỉ thành allowed Patient file khi Doctor release rõ ràng. Không suy release từ Visit CLOSED. Patient có thể đọc file đã release của Visit closed nếu ownership/dependent hợp lệ.

Tất cả file đã release đều immutable trong phiên bản này để giữ nguyên bytes/metadata đã công bố; không thêm retract/amendment. Release retry trả state conflict, có thể GET để xem release evidence. Patient result-release composition/UC34 tổng thể vẫn là scope sau.

## K. Concurrency

Upload metadata + payload cùng REQUIRED transaction. Update/release dùng READ_COMMITTED; CAS phải khớp version và releasedAt IS NULL. Chỉ sau CAS thắng mới ghi payload. Payload upsert cũng từ chối header đã release. Metadata-only update không tải/thay binary. Release và replacement cùng khóa header/version nên có một winner. Không REQUIRES_NEW hay swallow lỗi. Lỗi payload phải rollback cả metadata. Read-only REPEATABLE_READ giữ metadata/payload nhất quán. Stale/released => 409 MSG-42.

## L. Endpoints và contract

POST /api/v1/visits/{visitId}/medical-documents — Doctor/Receptionist, 201.
GET /api/v1/visits/{visitId}/medical-documents — trả {items:[]} khi trống.
GET /api/v1/medical-documents/{documentId} — metadata.
GET /api/v1/medical-documents/{documentId}/content — binary attachment.
PUT /api/v1/visits/{visitId}/medical-documents/{documentId} — chỉ Doctor, 200.
POST /api/v1/visits/{visitId}/medical-documents/{documentId}/release — chỉ Doctor, 200.

Upload multipart: metadata là application/json {"documentType":"CLINIC_EXAMINATION_REPORT","title":"Examination report"}; file là binary bắt buộc.
Update multipart: metadata {"title":"Corrected report","version":1}; file optional, bỏ file nghĩa là chỉ sửa title. Không cho đổi type/linkage qua body.
Release JSON: {"version":1}. Version phải integer dương, không coerce string/decimal.

Response metadata: id, visitId, patientProfileId, documentType, title, originalFilename, mediaType, byteSize, released, releasedAt, version, createdAt, updatedAt. Không có payload/storage key, account ID hoặc PII hành chính. Unknown input fields bị bỏ qua theo Clinical convention nhưng không ảnh hưởng identity/state. No-store và shared X-Correlation-ID áp dụng cả lỗi. Domain MSG-39/47 =>400; MSG-40=>403; MSG-41=>404; MSG-42=>409; dependency=>503; unexpected=>500 sanitized. Transport lỗi theo VALIDATION_FAILED/400. MSG-44 là success message hiện có; không thêm success envelope khác Clinical.

## M. Receptionist boundary

Dùng Auth-owned isActiveReceptionistAccount và Visit-owned OPEN context. Scope clinic-wide này đã có precedent trong PatientProfileService (active Receptionist) và reception reads; không bịa assignment riêng cho từng Receptionist. Receptionist được thêm các loại tài liệu được phép và nhận upload receipt. Đọc/list/content được giới hạn clinic examination report của OPEN Visit theo BR-41; không đọc referral/outside report qua GET, không sửa/thay/release. Không nới historical/closed Visit access.

## N. Patient/dependent boundary

Dùng PatientProfileAccessService thật: active own hoặc CONFIRMED dependent; không tạo ownership model khác. List lọc released rows, metadata/content kiểm ownership và release trước đọc payload. File private không khả dụng. Patient không upload/update/release. Các test dùng PatientProfileAccessServiceImpl thật với owner repositories/directories giả ở test boundary; tự thân Clinical không truy cập Patient repository.

## O. Migration

V100__create_clinical_medical_documents.sql được chọn sau khi kiểm tra local/product/toàn bộ fetched remote: V100 chưa dùng; V099 đã nằm trong Prescription e47160e, chưa merge product. V098/V099 nguồn không sửa. Cần phối hợp merge/rollout V099 trước V100 để tránh migration out-of-order trên DB chung; không bật outOfOrder hay auto-merge. Đã xóa duy nhất bản copy V099 cũ trong target/classes/db/migration còn sót từ build nhánh trước, sau kiểm tra absolute generated path; không xóa migration nguồn hoặc user code.

## P. Tests và exact verification

JDK 21.0.9; session JAVA_HOME=C:\Program Files\Java\jdk-21. Cached Maven 3.9.16: C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd. Mọi invocation dùng -Dmaven.repo.local=C:\Users\ad\.m2\repository, ngoài filesystem sandbox để đọc cache hiện có. Không thêm dependency/wrapper.

-DskipTests compile: PASS.
-DskipTests test-compile: PASS; regression cũng compile 6 PostgreSQL test methods mới.
-Dtest=ClinicalMedicalDocumentServiceImplTest,ClinicalMedicalDocumentControllerTest,ClinicalMedicalDocumentArchitectureTest,DoctorCoreProductionWiringTest test: PASS 49, failures=0/errors=0/skips=0.
Final regression: 541 tests, failures=0, errors=0, skips=0; log target/medical-documents-regression.log.

Chi tiết: MedicalDocument service=20, MVC/security=18, architecture=2, production core wiring=9 (đã gồm trong total). Tổng theo feature: Clinical=239, Diagnostic=37, Visit=50, Patient=118, Appointment=72, Auth security=4, Review controller=18, catalog controller=3. Bao gồm toàn bộ Clinical non-Postgres, core mapping/wiring, current-care/dependent authorization và Diagnostic regressions liên quan. Prescription tests NOT RUN trên nhánh này vì UC36 chưa có trong product; không giả vờ kiểm thử code branch chưa merge.

Wiring test khởi tạo Spring MVC context thật với controller/service/access/file validator mới, Auth/Visit/Staff/Patient owner services thật; chỉ persistence doubles và recording transaction manager trong fixture. Không standalone MockMvc để chứng minh registration; không production fake/no-op. Unit race tests chỉ chứng minh CAS control flow; không tính là SQL concurrency PASS.

## Q. PostgreSQL

BLOCKED: TCP 127.0.0.1:5432 không sẵn sàng (probe chỉ đọc ngoài sandbox). 6 integration tests đã thêm/compile: schema+payload, CAS persistence, concurrent replacement, rollback khi payload write lỗi, release immutability ở service/repository, release-vs-replacement. Không claim migration/SQL race/rollback/full DB boot PASS. Không start/install/reset/migrate DB.

## R. Architecture boundary

PASS: Clinical Medical Documents không import Visit/Patient/Diagnostic/Billing persistence hoặc room/queue dependencies. Không đổi Clinic completion, Visit services, Diagnostic workflow, frontend, V098/V099. git diff --check và untracked whitespace: PASS. Metadata không chứa binary/JPA internals; download đã được authorize trước đọc payload.

## S. Files changed

20 files thuộc checkpoint: 11 production files mới, handler scope/message mapping, V100, fixture và 4 suites tests mới, cập nhật production wiring và action record này. Exact manifest ở mục U. Index còn trống, HEAD/product vẫn 08c8770833cca743470ea8d81a20cbed88036822.

## T. Remaining Doctor-flow gaps

UC33 aggregation/authorization tổng thể; Doctor DICOM Study/View; integration imaging result/findings và các Diagnostic Study/Report/Billing/Visit coordination ports còn thiếu; Patient result-release composition tổng thể. Medical Documents chỉ thêm release của từng file. Không tự triển khai các scope đó. UC36 PR/merge order và DB verification là dependency rollout/review còn lại, không bị giấu bằng adapter giả.

## V. Ready to stage?

YES cho code review, với PostgreSQL verification còn BLOCKED và yêu cầu phối hợp V099 trước V100. Không có Human approval/deployment readiness được tự nhận; chưa stage, commit, push, merge hay rebase.

Exact final Maven arguments:

-Dmaven.repo.local=C:\Users\ad\.m2\repository -Dtest=AdminClinicServiceControllerTest,AfterVisitAdviceServiceImplTest,AppointmentBookingControllerValidationTest,AppointmentRequestValidationTest,CancelAppointmentServiceImplT025Test,CareErrorMessageWebTest,CareRelationshipT008Test,CareRelationshipT008WebTest,CheckInAppointmentServiceImplT027Test,CheckInAppointmentServiceImplTest,CheckVisitReservationCapacityServiceT031Test,ClinicalDoctorIdentityResolverTest,ClinicalExaminationServiceImplTest,ClinicalImagingOrderAdapterTest,ClinicalImagingRequestConcurrencyTest,ClinicalImagingRequestControllerTest,ClinicalImagingRequestServiceImplTest,ClinicalMedicalDocumentArchitectureTest,ClinicalMedicalDocumentControllerTest,ClinicalMedicalDocumentServiceImplTest,ClinicalOrderingServiceImplTest,ClinicalVisitCompletionEvidenceServiceImplTest,CreateAppointmentServiceImplTest,CreateFollowUpAppointmentT030Test,CreateWalkInVisitT043Test,DependentAddOtpServiceTest,DependentEmailMaskingTest,DependentProfileServiceTest,DiagnosticControllersT016Test,DiagnosticHistoryT015Test,DiagnosticStatusT015Test,DiagnosticTransitionPolicyTest,DoctorActiveVisitControllerTest,DoctorActiveVisitReadServiceImplTest,DoctorClinicalControllerTest,DoctorCoreArchitectureTest,DoctorCoreMvcMappingTest,DoctorCoreProductionWiringTest,DoctorCurrentCareAuthorizationTest,DoctorImagingRegistrationTest,GetAppointmentAvailabilityServiceImplTest,GetAppointmentChangeAvailabilityServiceImplTest,GetAppointmentsServiceImplT023Test,Kan29MyPatientProfileServiceTest,MarkAppointmentNoShowT028Test,MedicalHistoryErrorWebTest,MedicalHistoryServiceTest,PatientAuthorizationT008Test,PatientClinicalIdentityQueryServiceImplTest,PatientProfileAccessServiceTest,PatientProfileControllerTest,PatientProfileReceptionVerificationT046Test,PatientProfileServiceTest,PublicRequestRateLimiterTest,ReassignAppointmentServiceImplT026Test,RescheduleAppointmentServiceImplT024Test,ReviewControllerTest,ServiceCatalogClinicalAdapterTest,SlotAvailabilityEngineTest,StaffRoomQueryControllerTest,T010CommonSecurityContractTest,TrustedProxyIpResolverTest,UpdateAppointmentStatusT029Test,VisitApiExceptionHandlerTest,VisitCareCapacityT031Test,VisitClinicalCareContextServiceImplTest,VisitClinicalContextAdapterTest,VisitClinicalContextProductionWiringTest,VisitClinicalEvidenceRepairTest,VisitDoctorActiveCareReadServiceImplTest,VisitRoomCapacityT031Test test

## U. Exact safe files to stage

backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/exception/ClinicalApiExceptionHandler.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorCoreProductionWiringTest.java
backend/docs/doctor-medical-documents-uc30-32-action-record.md
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/controller/ClinicalMedicalDocumentController.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/ClinicalMedicalDocumentType.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/dto/MedicalDocumentDtos.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/entity/ClinicalMedicalDocumentJpaEntity.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/entity/ClinicalMedicalDocumentPayloadJpaEntity.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalMedicalDocumentPayloadRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalMedicalDocumentRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/security/ClinicalMedicalDocumentAccess.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/ClinicalMedicalDocumentService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalMedicalDocumentServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/validation/ClinicalMedicalDocumentFileValidator.java
backend/src/main/resources/db/migration/V100__create_clinical_medical_documents.sql
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalMedicalDocumentArchitectureTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalMedicalDocumentControllerTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalMedicalDocumentPostgresIntegrationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalMedicalDocumentServiceImplTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/MedicalDocumentFixture.java
