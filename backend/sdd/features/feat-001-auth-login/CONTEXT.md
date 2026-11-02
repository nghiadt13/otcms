# Feature Context — FEAT-001 Authentication Login

# Version: 0.6.0
# Status: DRAFT
# Feature slug: `feat-001-auth-login`
# Architecture Profile: v1.3.0 — APPROVED; modular-monolith/authentication bindings selected

---

## Intent Packet

- **WHAT:** Cung cấp hai login entry point tách biệt: Patient Portal public cho bệnh nhân đăng nhập Google hoặc email/password, và Staff Portal chỉ khả dụng từ mạng nội bộ/VPN cho nhân viên đăng nhập email/password. Cung cấp luồng đăng ký bệnh nhân với các trường bắt buộc đã chọn.
- **WHY:** OTCMS xử lý hồ sơ khám và dữ liệu hình ảnh y khoa nhạy cảm; hệ thống cần biết ai đang truy cập trước khi áp dụng quyền theo action/resource và tạo audit phù hợp.
- **Definition of Done:** Staff hợp lệ chỉ đăng nhập qua Staff Portal từ approved internal network/VPN bằng email/password; bệnh nhân hợp lệ chỉ đăng nhập qua Patient Portal bằng Google hoặc verified email/password; bệnh nhân có thể đăng ký, xác minh email, yêu cầu/hoàn tất password reset khi đủ điều kiện và đổi local password sau khi xác thực; account/linkage/token state nhất quán; cross-portal/network access bị từ chối an toàn; lỗi theo error contract; sự kiện bảo mật được audit/log an toàn; behavior được kiểm chứng bằng success/failure/security tests.
- **Boundaries:** Separate patient/staff login entry points; Staff Portal network restriction; employee email/password login; patient Google login; patient self-registration/onboarding và email OTP verification; forgot/reset/change password cho local credential đủ điều kiện; kiểm tra account type/status; tạo authentication outcome; Google identity-to-patient-account linkage; rate limiting; xử lý thất bại, duplicate/conflict và anti-enumeration; audit; contract tối thiểu để client dùng kết quả.
- **Exclusions:** MFA; quản trị role/permission; DICOM/MinIO authorization; tự đăng ký tài khoản nhân viên; identity provider ngoài Google; tạo local password cho Google-only account; logout mọi thiết bị theo yêu cầu người dùng và giao diện quản lý session.
- **Decision owner:** Human Product Owner phối hợp Tech Lead/Security reviewer có thẩm quyền.

---

## 1. Problem Statement

OTCMS chưa có behavior xác thực/onboarding được đặc tả. Human đã quyết định nhân viên dùng email/password; bệnh nhân dùng Google hoặc email/password và được tự đăng ký bằng bộ trường bắt buộc. Khi email Google đã verified và trùng email đăng ký đã normalize, hai phương thức xác thực dùng cùng patient account.

## 2. Desired Business Outcome

1. Nhân viên được phép sử dụng có thể đăng nhập bằng email qua Staff Portal trong mạng nội bộ/VPN; bệnh nhân có thể xác thực qua Patient Portal public bằng Google hoặc email/password.
2. Bệnh nhân chưa có account có thể đăng ký bằng dữ liệu tối thiểu đã được duyệt và được liên kết đúng với Google identity khi áp dụng.
3. Client nhận một outcome nhất quán để tiếp tục phiên truy cập hoặc hiển thị lỗi an toàn.
4. Người ngoài không thể dùng response để thu thập account hoặc dữ liệu bệnh nhân.
5. Hệ thống có evidence an toàn cho đăng nhập/đăng ký thành công hoặc thất bại mà không log password, Google token hoặc dữ liệu lâm sàng.
6. Identity/context sau đăng nhập đủ để feature sau thực thi authorization; login/register không tự trao quyền vượt account policy.

## 3. Stakeholders

| Stakeholder | Mối quan tâm |
| :--- | :--- |
| Người dùng OTCMS được cấp tài khoản | Đăng nhập rõ ràng, phản hồi an toàn và có thể sử dụng ổn định |
| Nhân viên phòng khám | Truy cập đúng tài khoản và phạm vi công việc |
| Quản trị viên được ủy quyền | Quản lý trạng thái tài khoản ngoài phạm vi feature này; cần login tôn trọng trạng thái đó |
| Product Owner | Chốt nhóm người dùng, định danh đăng nhập và phạm vi MVP |
| Tech Lead / Security reviewer | Chốt session/token, password, abuse controls, audit và public endpoint policy |
| Nhóm vận hành | Quan sát lỗi/abuse mà không lộ credential hoặc PII |

## 4. Domain Glossary

| Thuật ngữ | Nghĩa trong feature |
| :--- | :--- |
| Account | Bản ghi định danh đã được OTCMS cho phép dùng để xác thực; không đồng nghĩa hồ sơ bệnh nhân |
| Employee login identifier | Email của nhân viên dùng để tìm account; Human đã chọn |
| Google identity | Subject/identity do Google xác thực dùng để liên kết với patient account; token và claim trust rules chưa được chốt |
| Patient registration | Luồng bệnh nhân tự cung cấp bộ dữ liệu cơ bản để tạo account/onboarding |
| Patient account linkage | Quan hệ duy nhất giữa Google identity, patient account và hồ sơ bệnh nhân khi có |
| Credential | Nhân viên và bệnh nhân local login dùng password; Google login dùng provider identity đã được xác minh |
| Normalized email | Biểu diễn email canonical dùng cho lookup/uniqueness; normalization rule cụ thể cần Spec chốt |
| Terms consent | Evidence đồng ý Terms & Privacy Policy, gồm actor type, version và thời điểm chấp thuận; nhóm dưới 18 tuổi hoặc trên 70 tuổi cần cả patient consent và guardian consent |
| Guardian phone | Số điện thoại liên hệ của người giám hộ được ghi cho bệnh nhân dưới 18 tuổi hoặc trên 70 tuổi; không được xác minh và không tạo guardian account hoặc quyền truy cập hồ sơ trong feature này |
| Email OTP | Mã dùng một lần gửi tới registered email để xác minh email và kích hoạt local patient account |
| Authentication | Xác minh người dùng là chủ thể của account |
| Authorization | Quyết định chủ thể được làm gì với resource nào; nằm ngoài login outcome cơ bản |
| Account status | Trạng thái quyết định account có được đăng nhập, ví dụ active/locked/disabled; tập trạng thái chưa chốt |
| Authentication outcome | Kết quả thành công hoặc thất bại an toàn của một login attempt |
| Session/token | Cơ chế duy trì authenticated context sau login; chưa được chọn |
| Account enumeration | Suy ra account tồn tại thông qua message, status, timing hoặc response khác biệt |
| Login attempt | Một yêu cầu xác thực được correlation/audit an toàn, không chứa raw credential |

## 5. Business Rules Known at Context Stage

1. Patient login, Google authentication callback/exchange và patient registration là public entry points có phạm vi hẹp; public policy phải ghi rõ trong security configuration và Spec. Employee login chỉ đi qua Staff Portal internal/VPN entry point và không được public như patient endpoints.
2. Password, token/session secret và raw credential không được log, audit hoặc trả lại client.
3. Failure response không được xác nhận identifier có tồn tại, password sai hay account bị vô hiệu hóa nếu điều đó tạo account-enumeration risk.
4. Account không ở trạng thái được phép sử dụng phải bị từ chối.
5. Thành công chỉ tạo authenticated identity/context; quyền nghiệp vụ vẫn được kiểm tra tại service/policy boundary của từng feature.
6. API error dùng schema đã duyệt:

   ```json
   {
     "error": {
       "code": "...",
       "message": "...",
       "timestamp": "..."
     }
   }
   ```

7. Login audit/log chỉ dùng safe identifier/correlation ID và sanitized outcome; không chứa password, token hoặc clinical data.
8. Business/security decisions trong implementation phải trace đến requirement bằng `@ears`/`EARS[...]` theo Constitution.
9. Employee email phải được normalize/compare theo rule được Spec chốt và có database uniqueness constraint phù hợp.
10. Google identity linkage phải dùng stable provider subject và verified email. Chỉ link cùng patient account khi normalized verified Google email trùng normalized registered email duy nhất và không có conflict.
11. Patient registration bắt buộc: full name, email, phone number, date of birth, gender, password, confirm password và patient Terms & Privacy consent. Với bệnh nhân dưới 18 tuổi hoặc trên 70 tuổi, guardian Terms & Privacy consent cũng bắt buộc.
12. Confirm password chỉ dùng kiểm tra request khớp password và SHALL không được persist hoặc log.
13. Mỗi Terms consent phải lưu evidence tối thiểu gồm actor type (`PATIENT` hoặc `GUARDIAN`), terms/privacy version và accepted timestamp. Guardian consent trong FEAT-001 là self-attested registration evidence, không chứng minh danh tính hoặc quan hệ pháp lý của người giám hộ.
14. Patient registration không tự tạo quyền nhân viên. Duplicate normalized email SHALL bị từ chối. Duplicate phone không tự chặn registration vì có thể dùng chung trong gia đình, nhưng MAY được ghi nhận như risk signal an toàn. Conflicting Google linkage phải fail an toàn theo Spec.
15. Email/password account SHALL hoàn tất email verification trước khi chuyển sang trạng thái cho phép đăng nhập đầy đủ. Với MVP, account chưa verified không được login.
16. Password SHALL có ít nhất 12 ký tự, hỗ trợ passphrase/khoảng trắng và không áp arbitrary composition rule như bắt buộc chữ hoa, số hoặc ký tự đặc biệt.
17. Raw password SHALL có tối đa 64 ký tự và đồng thời không vượt quá 72 UTF-8 bytes khi BCrypt là encoder thực tế. API boundary SHALL từ chối input vượt một trong hai giới hạn và implementation không được âm thầm truncate trước khi hash.
18. Password SHALL được hash bằng Spring Security `DelegatingPasswordEncoder` với BCrypt làm baseline encoder. Cột lưu encoded password SHALL cho phép tối đa 255 ký tự (ví dụ `VARCHAR(255)`) và stored hash phải giữ encoder prefix để hỗ trợ migration.
19. Plaintext password và confirm password SHALL không được persist hoặc log; password hash SHALL không xuất hiện trong response DTO, API response, application log hoặc client.
20. Register, login, verify-email, forgot/reset-password và change-password endpoint SHALL có rate limiting phù hợp theo Spec.
21. Kiểm tra password phổ biến hoặc đã bị lộ được deferred khỏi MVP và SHALL không block FEAT-001. Khi bổ sung, cơ chế phải privacy-preserving, không gửi plaintext password ra ngoài và phải được review dependency/failure policy trước khi triển khai.
22. Password-reset token SHALL có tối thiểu 256-bit entropy, single-use, hết hạn sau 15 phút và chỉ lưu hash trong PostgreSQL. Token mới làm mất hiệu lực token cũ; token/plaintext SHALL không được log hoặc audit.
23. OTCMS SHALL dùng access JWT sống 15 phút và opaque refresh token sống 7 ngày. Refresh token được rotate mỗi lần dùng, chỉ lưu hash trong PostgreSQL và truyền bằng cookie `HttpOnly`, `Secure`; SameSite/CSRF policy phải khớp deployment topology.
24. Refresh-token reuse, password change, account disable/archive hoặc security revoke SHALL thu hồi session liên quan. Mỗi account tối đa 5 active sessions; khi vượt giới hạn, session cũ nhất bị thu hồi theo behavior được Spec mô tả.
25. Refresh và logout phiên hiện tại thuộc feature này. Logout SHALL idempotent, revoke refresh token hiện tại và clear cookie; “logout all devices” được deferred.
26. Account status baseline: `PENDING_VERIFICATION`, `ACTIVE`, `TEMPORARILY_LOCKED`, `DISABLED`, `ARCHIVED`. Chỉ `ACTIVE` được login; client nhận failure chung chống enumeration.
27. Sau 5 lần credential failure liên tiếp, account bị `TEMPORARILY_LOCKED` 15 phút. Rate limiting SHALL kết hợp account/safe normalized-identifier reference và IP/network signal để giảm brute force lẫn lockout abuse; threshold nằm trong typed configuration.
28. Login audit SHALL ghi timestamp, method, sanitized outcome, account UUID khi xác định được, correlation ID và network/client evidence đã sanitize. Với unknown identifier, dùng keyed HMAC của normalized email; retention baseline 90 ngày và có thể bị policy cao hơn override.
29. Login success response chỉ trả access token, expiry và user summary tối thiểu gồm account ID, display name, account type. Refresh token không xuất hiện trong JSON; permission graph/clinical profile không nằm trong login response.
30. Google login SHALL dùng Authorization Code Flow với PKCE. Backend kiểm tra issuer, audience, expiration, `email_verified=true` và dùng `sub` làm provider identity. Google authorization code/token SHALL không được log.
31. Google identity SHALL link vào account trước; không tự link trực tiếp vào clinical patient record chỉ vì email trùng. Clinical-record matching/recovery là approved workflow riêng.
32. Email/provider subject conflict SHALL không auto-merge hoặc chuyển linkage. Operation fail an toàn, audit bằng safe reference và chuyển sang manual recovery/review ngoài feature này.
33. Patient registration tạo account và patient profile cơ bản mới; không tự nhận hoặc gắn hồ sơ khám cũ. Normalized email SHALL unique; phone được normalize nhưng không mặc định unique vì có thể dùng chung trong gia đình.
34. Full name hỗ trợ Unicode và được trim/giới hạn độ dài; email được normalize; phone dùng format canonical; date of birth không được ở tương lai. Gender chỉ nhận một trong ba giá trị `MALE`, `FEMALE`, `OTHER`.
35. Email verification SHALL dùng OTP 6 chữ số được sinh bằng cryptographically secure random source, single-use, chỉ lưu hash và hết hạn sau 10 phút. OTP mới làm mất hiệu lực OTP cũ; tối đa 5 lần nhập sai/OTP và 3 lần resend/account/giờ theo typed configuration.
36. Bệnh nhân ở mọi nhóm tuổi đăng ký và đăng nhập bằng chính patient account của mình. Với bệnh nhân dưới 18 tuổi hoặc trên 70 tuổi, trường phone SHALL là số điện thoại liên hệ của người giám hộ, nhưng FEAT-001 không xác minh số điện thoại này.
37. Account chỉ được kích hoạt sau khi registered email được xác minh bằng OTP và các consent bắt buộc theo nhóm tuổi đã được ghi nhận. Email OTP verification không tạo guardian account, guardian-patient linkage, consent delegation hoặc quyền truy cập hồ sơ.
38. Email OTP và các email thông báo thuộc scope tương ứng SHALL được gửi qua Gmail SMTP bằng email-delivery adapter. Gmail credential/App Password SHALL được đọc từ secret/environment configuration, không được commit, log hoặc trả cho client; timeout, delivery failure và retry policy phải được Spec/Plan mô tả.
39. Refresh-token cookie baseline SHALL là `HttpOnly=true`, `Secure=true`, `Path=/api/auth` và không khai báo `Domain` để tạo host-only cookie. `SameSite` giữ configurable theo deployment topology và không được đặt giá trị làm mất CSRF protection.
40. CORS SHALL dùng exact-origin allowlist từ typed configuration. Khi cho phép credential, wildcard origin `*` bị cấm; chỉ các method/header cần thiết được cho phép.
41. Các endpoint dùng refresh cookie để thay đổi authentication state, tối thiểu `refresh` và `logout`, SHALL yêu cầu CSRF token theo double-submit pattern: backend cấp random CSRF token bằng cookie riêng không `HttpOnly`, client gửi lại bằng header `X-CSRF-TOKEN`, backend kiểm tra hai giá trị trước khi xử lý. CSRF token không được dùng làm authentication credential.
42. Cookie domain/path, CORS allowlist và `SameSite` MAY thay đổi bằng typed deployment configuration sau khi phối hợp frontend, nhưng các baseline `HttpOnly`, `Secure`, host-only mặc định, exact-origin allowlist và CSRF validation không được hạ thấp nếu không qua security review.
43. Patient Portal và Staff Portal SHALL có login screen và authentication entry point riêng. Patient entry point chỉ chấp nhận patient account; staff entry point chỉ chấp nhận staff account. Cross-portal credential attempt phải trả failure chung, không tiết lộ account type hoặc account existence.
44. Patient Portal là Internet-facing và hỗ trợ patient registration, email/password và Google login. Staff Portal chỉ hỗ trợ email/password; không có self-registration hoặc Google login cho staff.
45. Tách màn hình hoặc ẩn URL không phải security boundary. Staff authentication entry point SHALL bị giới hạn tại gateway/reverse proxy và backend theo approved internal network/VPN policy.
46. Backend SHALL chỉ tin `Forwarded`/`X-Forwarded-For` từ reverse proxy đã cấu hình tin cậy; header do client Internet tự cung cấp không được dùng để vượt network restriction.
47. Patient và staff MAY dùng chung account/session persistence và token infrastructure, nhưng token/authenticated principal phải mang account type đáng tin cậy để resource authorization phân biệt hai nhóm.
48. Exact Staff Portal origin, internal CIDR/VPN topology, trusted proxy list và hành vi development environment SHALL nằm trong typed deployment configuration và được chốt trước integration/deployment.
49. Patient Portal và Staff Portal SHALL dùng chung `auth` module trong một Spring Boot modular monolith. Hai entry point có policy riêng nhưng dùng chung account/session/token infrastructure; FEAT-001 không yêu cầu tách authentication thành microservice.
50. Auth module SHALL dùng request/response DTO và module-owned mapper tại API boundary, đồng thời tách service interface khỏi implementation trong `service.impl`. Controller chỉ phụ thuộc service interface; `*ServiceImpl` sở hữu orchestration/business rules/transaction boundary và truy cập repository theo dependency direction đã duyệt. Cross-cutting exception/response/validation nằm trong root `common`, Spring configuration dùng chung nằm trong root `config`, và framework authentication filters/JWT/CSRF/network components nằm trong root `security`.
51. Forgot/reset password SHALL áp dụng cho patient và staff `ACTIVE` hoặc `TEMPORARILY_LOCKED` có local password; patient dùng public patient endpoint, staff dùng endpoint riêng chịu internal network/VPN policy. Google-only account không được dùng flow này để tạo local password.
52. Forgot-password SHALL luôn trả cùng `202 Accepted` và generic message dù email không tồn tại, account không đủ điều kiện, Google-only hoặc email delivery thất bại. Gmail delivery outcome chỉ được ghi nội bộ bằng safe audit.
53. Forgot-password SHALL giới hạn 3 requests/account/hour và 5 requests/IP/hour; reset token tối đa 5 failed attempts; change-password giới hạn 5 requests/account/15 minutes.
54. Reset/change password SHALL dùng cùng password policy hiện hành, từ chối password mới trùng current password và không yêu cầu password history trong MVP. Change-password yêu cầu access JWT hợp lệ và current password đúng.
55. Reset/change password thành công SHALL atomically update password hash, revoke toàn bộ refresh sessions của account và yêu cầu đăng nhập lại. Access JWT đã phát có thể còn hiệu lực tối đa 15 phút; FEAT-001 không thêm access-token denylist.
56. Chỉ `ACTIVE` account có local credential được forgot/reset/change password. `TEMPORARILY_LOCKED` account MAY hoàn tất reset; reset thành công chuyển account về `ACTIVE` và xóa credential-failure state. `PENDING_VERIFICATION`, `DISABLED` và `ARCHIVED` không được reset.
57. Reset token SHALL được client gửi trong request body; reset email SHALL đặt token trong URL fragment thay vì query parameter để giảm nguy cơ token xuất hiện trong backend/proxy request log.

## 6. Candidate User Journey

### Nhân viên

1. Nhân viên truy cập Staff Portal từ approved internal network hoặc VPN.
2. Nhân viên nhập email và password đã được cấp; Staff Portal không cung cấp registration hoặc Google login.
3. Gateway/backend kiểm tra network policy, request, credential, account type và status mà không tiết lộ account existence.
4. Khi thành công, OTCMS tạo staff authentication outcome theo cơ chế đã duyệt.

### Bệnh nhân

1. Bệnh nhân truy cập Patient Portal public và chọn Google hoặc registered email/password.
2. OTCMS xác minh Google identity theo approved provider configuration.
3. Nếu provider subject đã link hoặc verified Google email trùng duy nhất normalized registered email mà không có conflict, OTCMS dùng cùng patient account và hoàn tất linkage theo approved workflow.
4. Nếu chưa có account và policy cho phép, bệnh nhân đi qua registration/onboarding với các trường cơ bản đã duyệt.
5. Duplicate hoặc conflicting identity/account linkage bị từ chối an toàn và được audit.
6. Mọi failure trả lỗi thống nhất, không lộ token, account existence hoặc dữ liệu bệnh nhân.

Sau login thành công, backend phát access JWT và rotating refresh cookie theo các business rules đã chốt; endpoint shape cụ thể được đặc tả ở Spec.

### Xác minh đăng ký bằng email OTP

1. Bệnh nhân đăng ký patient account bằng registered email và các trường bắt buộc đã duyệt.
2. OTCMS sinh OTP và gửi tới registered email qua email-delivery adapter.
3. Bệnh nhân nhập OTP còn hiệu lực; backend xác minh hash, attempt limit và single-use state.
4. OTCMS kích hoạt account khi OTP hợp lệ, patient consent tồn tại và guardian consent cũng tồn tại nếu bệnh nhân dưới 18 tuổi hoặc trên 70 tuổi. Phone chỉ là dữ liệu liên hệ và không được đánh dấu verified.

### Forgot, reset và change password

1. Patient hoặc staff yêu cầu forgot-password qua entry point tương ứng; staff entry point vẫn chịu internal network/VPN policy.
2. OTCMS luôn trả response generic; chỉ gửi reset instruction khi account `ACTIVE`, có local credential và request nằm trong rate limit.
3. Người dùng gửi reset token còn hiệu lực cùng password mới; backend consume token, cập nhật hash và revoke toàn bộ refresh sessions atomically.
4. Người dùng đã đăng nhập có thể change-password bằng current password và password mới; thành công cũng revoke toàn bộ refresh sessions và yêu cầu đăng nhập lại.

## 7. Constraints and Evidence

| Loại | Constraint / evidence | Ảnh hưởng |
| :--- | :--- | :--- |
| Governance | `CONSTITUTION.md` v2.0.0 đã duyệt Spring Security, safe error contract, clinical-data protection và resource authorization | Spec phải tuân `SEC-01`, `SEC-02`, `API-01`, `ENG-02`, `TEST-01`, `OBS-01` |
| Architecture | Repository có Java 21, Spring Boot 4.1.1 và Spring Security dependency | Là evidence kỹ thuật; không tự chọn authentication mechanism |
| Profile | Architecture Profile v1.3.0 đã `APPROVED`; authentication, modular-monolith và package baselines đã được duyệt | Spec có thể được tạo; Plan cần Spec `APPROVED & LOCKED`, còn execution cần verification evidence theo Profile |
| Data | Chưa có datasource/account schema/migration được duyệt | Spec phải định nghĩa observable behavior trước khi Plan chọn persistence |
| Email delivery | Human chọn Gmail SMTP cho OTP và thông báo thuộc scope | Plan phải dùng adapter, secret configuration, TLS, timeout/retry và kiểm tra quota/compatibility theo dependency gates |
| Verification | Maven Wrapper tồn tại nhưng exact command chưa có persisted successful evidence | Chưa được claim implementation-ready |
| Privacy | OTCMS xử lý dữ liệu nhạy cảm | Login log/audit phải tránh credential, PII và clinical data |

## 8. Scope Boundaries

### In scope

- Xác thực người dùng đã có account.
- Hai login screen/entry point riêng cho Patient Portal và Staff Portal.
- Employee email login qua Staff Portal giới hạn trong mạng nội bộ/VPN.
- Patient Google login và identity linkage.
- Patient self-registration/onboarding với bộ trường được duyệt.
- Email OTP verification và activation cho local patient account.
- Patient local email/password login trên cùng account với Google login khi linkage hợp lệ.
- Email verification/activation cho account đăng ký bằng email/password.
- Rate limiting cho register, login và verify-email.
- Refresh access token và logout phiên hiện tại.
- Rotating refresh-token session lifecycle và giới hạn active sessions.
- Kiểm tra trạng thái account ảnh hưởng login.
- Authentication success/failure outcome.
- Anti-enumeration behavior.
- Login attempt audit và safe observability.
- Contract tối thiểu để client duy trì authenticated context sau khi cơ chế được duyệt.
- Unit/integration/security tests cho behavior đã chốt.
- Forgot/reset password cho patient và staff local-password account đủ điều kiện.
- Authenticated change-password với current-password verification.

### Out of scope

- Tạo, mời hoặc phê duyệt account nhân viên.
- Role/permission administration.
- MFA, biometric hoặc social login.
- Identity provider bên ngoài khác Google.
- Authorization chi tiết cho patient, encounter, DICOM, MinIO hoặc OHIF.
- Guardian account, guardian-patient linkage, consent delegation và quyền truy cập hồ sơ của người giám hộ.
- Logout mọi thiết bị và giao diện quản lý session.
- UI styling ngoài behavior cần thiết để nhập credential và xử lý outcome.

## 9. Material Decisions and Disposition

| ID | Câu hỏi cần quyết định | Tại sao material | Disposition |
| :--- | :--- | :--- | :--- |
| Q-LOGIN-01 | Nhóm nào được đăng nhập ở MVP? | Quyết định account model, identity scope và authorization context | **RESOLVED — Nhân viên và bệnh nhân** |
| Q-LOGIN-02 | Login identifier/mechanism theo nhóm là gì? | Ảnh hưởng uniqueness, privacy, validation và UX | **RESOLVED — Nhân viên: email/password; bệnh nhân: Google hoặc registered email/password** |
| Q-LOGIN-03 | Duy trì authenticated context bằng server session/cookie hay access/refresh token? | Ảnh hưởng API contract, CSRF, storage, rotation và logout | **RESOLVED — access JWT 15 phút + rotating opaque refresh token 7 ngày trong HttpOnly/Secure cookie; refresh hash lưu DB** |
| Q-LOGIN-04 | Account status nào tồn tại và status nào được phép login? | Quyết định invariant và error/audit behavior | **RESOLVED — PENDING_VERIFICATION, ACTIVE, TEMPORARILY_LOCKED, DISABLED, ARCHIVED; chỉ ACTIVE login** |
| Q-LOGIN-05 | Password policy và password encoder baseline là gì? | Security control và migration compatibility | **RESOLVED — minimum 12, maximum 64 characters và 72 UTF-8 bytes, passphrase/spaces allowed, no composition rule, DelegatingPasswordEncoder + BCrypt baseline, encoded-password column length 255; breached-password check deferred khỏi MVP** |
| Q-LOGIN-06 | Có lockout/rate limit không; threshold/window/unlock behavior thế nào? | Chống brute force nhưng có denial-of-service/UX trade-off | **RESOLVED — 5 credential failures khóa 15 phút; layered identifier/account + IP signal; public auth endpoints rate-limited qua typed config** |
| Q-LOGIN-07 | Concurrent sessions và session/token lifetime thế nào? | Ảnh hưởng security, usability và revocation | **RESOLVED — access 15 phút, refresh 7 ngày, tối đa 5 active sessions; rotate/revoke theo rule** |
| Q-LOGIN-08 | Logout/refresh có thuộc feature login không? | Ảnh hưởng boundary và Definition of Done | **RESOLVED — refresh và logout current session in scope; logout all devices deferred** |
| Q-LOGIN-09 | Audit login failure lưu safe account reference nào và retention bao lâu? | Quan sát abuse nhưng có privacy/storage risk | **RESOLVED — account UUID khi biết, keyed HMAC normalized email khi unknown, correlation ID, sanitized network/client evidence; baseline 90 ngày** |
| Q-LOGIN-10 | Response thành công cần trả profile/role nào, nếu có? | Có nguy cơ biến login thành authorization/profile endpoint | **RESOLVED — access token, expiry, account ID, display name, account type; refresh ở cookie; không trả permission graph/clinical profile** |
| Q-LOGIN-11 | Patient registration gồm chính xác trường nào và trường nào bắt buộc? | Ảnh hưởng data minimization, validation và patient matching | **RESOLVED — Full name, email, phone number, date of birth, gender, password, confirm password, patient Terms & Privacy consent; thêm guardian consent nếu tuổi dưới 18 hoặc trên 70** |
| Q-LOGIN-12 | Sau đăng ký, bệnh nhân có local password/email login hay chỉ đăng nhập bằng Google? | Quyết định credential model và recovery behavior | **RESOLVED — Cả Google và registered email/password trên cùng patient account** |
| Q-LOGIN-13 | Google identity được liên kết với patient account/hồ sơ hiện có bằng rule nào? | Sai linkage có thể lộ hồ sơ y tế cho nhầm người | **RESOLVED — link provider subject vào account qua verified normalized email match duy nhất; không tự link clinical record cũ** |
| Q-LOGIN-14 | Nếu email Google trùng account/patient record hiện có nhưng chưa link thì xử lý thế nào? | Nguy cơ account takeover hoặc duplicate patient | **RESOLVED — link account khi match duy nhất/no conflict; conflict không auto-merge, fail an toàn và chuyển manual recovery/review** |
| Q-LOGIN-15 | Google OAuth/OIDC consent, verified-email requirement và provider claim nào được tin cậy? | Quyết định trust boundary | **RESOLVED — Authorization Code + PKCE; backend verify issuer, audience, expiry, email_verified; dùng sub làm identity** |
| Q-LOGIN-16 | Password maximum length là 64, 128 hay giới hạn khác; xử lý byte-length với BCrypt ra sao? | Tránh DoS, truncation và behavior không nhất quán với Unicode | **RESOLVED — raw password tối đa 64 ký tự và 72 UTF-8 bytes; reject nếu vượt giới hạn, không truncate; encoded-password column tối đa 255 ký tự** |
| Q-LOGIN-17 | Dùng nguồn/cơ chế nào để kiểm tra password phổ biến/bị lộ và xử lý thế nào khi nguồn không khả dụng? | Có privacy, dependency và availability trade-off | **RESOLVED BY DEFERRAL — không thuộc MVP; quay lại bằng security/dependency review riêng nếu còn thời gian** |
| Q-LOGIN-18 | Email verification OTP có lifetime, single-use, attempt, resend và invalidation behavior thế nào? | Account activation là security state transition | **RESOLVED — secure random 6 digits, hash-at-rest, single-use, 10 phút, tối đa 5 lần nhập sai; resend invalidates old OTP; tối đa 3 lần/giờ** |
| Q-LOGIN-19 | Tập gender value được hỗ trợ là gì? | Ảnh hưởng API enum, inclusivity và reporting | **RESOLVED — `MALE`, `FEMALE`, `OTHER`** |
| Q-LOGIN-20 | Bệnh nhân dưới 18 tuổi hoặc trên 70 tuổi đăng ký và xác minh bằng cách nào? | Ảnh hưởng account ownership, verification và privacy | **RESOLVED — bệnh nhân dùng chính patient account và xác minh registered email bằng OTP; phone là số liên hệ người giám hộ nhưng không được verify trong FEAT-001** |
| Q-LOGIN-21 | Cookie/CORS/CSRF baseline và origin topology cụ thể là gì? | Phụ thuộc cách frontend được triển khai và ảnh hưởng CSRF/cookie delivery | **RESOLVED BASELINE — refresh cookie `HttpOnly`, `Secure`, host-only, `Path=/api/auth`; exact-origin CORS allowlist, không wildcard với credentials; double-submit CSRF cho refresh/logout; SameSite và origin values là typed deployment configuration** |
| Q-LOGIN-22 | Registration verification dùng phone/SMS/Zalo hay email? | Ảnh hưởng external dependency, chi phí và activation contract | **RESOLVED — dùng email OTP; phone/SMS/Zalo verification không thuộc FEAT-001** |
| Q-LOGIN-23 | Provider nào gửi email OTP và thông báo? | Ảnh hưởng dependency, secret handling, delivery và vận hành | **RESOLVED — Gmail SMTP qua email-delivery adapter; credential/App Password lấy từ secret/environment configuration** |
| Q-LOGIN-24 | Patient và staff dùng chung hay tách login entry point; Staff Portal được bảo vệ mạng thế nào? | Ảnh hưởng attack surface, mechanism confusion và deployment topology | **RESOLVED BASELINE — hai screen/entry point riêng; Patient Portal public, Staff Portal email/password chỉ qua internal network/VPN; gateway và backend cùng enforce; exact origins/CIDR/VPN/trusted proxies là typed deployment configuration trước integration** |
| Q-LOGIN-25 | Hai portal cần backend/service riêng hay dùng chung backend? | Ảnh hưởng deployment, transaction, module ownership và operational complexity | **RESOLVED — một Spring Boot modular monolith; Patient/Staff entry point riêng trong cùng `auth` module, dùng chung account/session/token infrastructure** |
| Q-LOGIN-26 | Auth module và cross-cutting package được tổ chức thế nào? | Ảnh hưởng dependency direction, testability và API/persistence boundary | **RESOLVED — request/response DTO và mapper thuộc module; `service` chứa interface, `service.impl` chứa `*ServiceImpl`; root `common`, `config`, `security` chứa concern dùng chung với boundary đã định nghĩa** |
| Q-LOGIN-27 | Forgot/reset/change password áp dụng cho ai và có security lifecycle nào? | Ảnh hưởng account enumeration, token storage, session compromise và portal boundary | **RESOLVED — patient/staff ACTIVE local-password account; random hashed 15-minute single-use token; generic forgot response; current password cho change; revoke all refresh sessions; Google-only local-password creation deferred** |

Không còn material Context question ở trạng thái blocking. Architecture Profile v1.3.0 đã được duyệt; Context v0.6.0 và Spec v1.1.0 cần Human Final Review trước khi chuyển sang `PLAN.md`.

## 10. Risks and Edge Cases to Carry into Spec

- Identifier không tồn tại, password sai và account bị vô hiệu hóa tạo response/timing khác nhau.
- Staff credential được thử ở Patient Portal hoặc patient credential được thử ở Staff Portal.
- Staff endpoint bị truy cập từ Internet, giả mạo `X-Forwarded-For` hoặc cấu hình trusted proxy/CIDR sai.
- Credential rỗng, quá dài, malformed hoặc Unicode/case normalization không nhất quán.
- Brute force, credential stuffing, distributed attempts và lockout abuse.
- Concurrent login trong khi account status/password thay đổi.
- Audit/log failure trong login path.
- Clock skew nếu token/time-based policy được chọn.
- Token/session bị replay, fixation, theft hoặc lưu không an toàn ở client.
- Google token giả, sai audience/issuer, hết hạn hoặc email chưa verified.
- Google identity bị link nhầm với patient record; account takeover qua email trùng.
- Hai request đăng ký đồng thời tạo duplicate account/patient linkage.
- Thu thập dư dữ liệu cá nhân ở form đăng ký.
- Confirm password hoặc password vô tình được persist/log.
- Patient/guardian consent thiếu actor type, version/timestamp hoặc guardian consent bị bỏ qua cho nhóm tuổi yêu cầu.
- Email normalization khác nhau giữa local registration và Google linkage.
- Password Unicode dài vượt giới hạn byte của encoder bị truncate hoặc xử lý không nhất quán.
- Breached-password lookup tương lai làm lộ password/email hoặc trở thành availability dependency nếu được bổ sung thiếu review.
- Verify-email token bị replay, hết hạn, resend nhiều lần hoặc account được kích hoạt trước verification.
- Refresh-token reuse, concurrent rotation và revoke race.
- Cookie SameSite/domain/path, CORS allowlist hoặc CSRF policy không khớp deployment topology.
- HMAC audit identifier dùng secret yếu hoặc không có rotation policy.
- Email OTP bị brute-force/replay, resend abuse, delayed delivery, Gmail quota/rate limit hoặc SMTP failure.
- Gmail credential/App Password bị commit, log hoặc cấu hình sai TLS.
- Phone chưa xác minh bị implementation hoặc feature khác hiểu nhầm là verified contact.
- Database/service unavailable trong lúc login.
- Error handler của Spring Security không khớp error contract.
- Test vô tình log hoặc fixture chứa credential/PII thật.
- Forgot-password response hoặc email-delivery outcome làm lộ account existence/eligibility.
- Reset token bị đoán, replay, lưu plaintext, dùng sau expiration hoặc không bị supersede.
- Password reset/change thành công nhưng refresh session cũ chưa bị revoke atomically.

## Methodology Profile

- **Depth:** FORMAL
- **Rationale:** Login là security/public contract feature, tạo authenticated identity dùng bởi toàn hệ thống và có nguy cơ account enumeration, credential abuse, session/token compromise.
- **Risk posture:** high
- **High-risk review route:** Product Owner quyết định business scope; Tech Lead/Security reviewer duyệt authentication mechanism/controls; Clinical owner duyệt gender/minor-registration policy; persisted Context/Spec review bắt buộc.
- **Unresolved-decision owner:** Product Owner, Tech Lead/Security reviewer và Clinical owner theo bảng Q-LOGIN.

## Describe-back record

- **WHAT understood:** Xây dựng employee email login, patient Google login, patient self-registration/onboarding và local-password recovery/change an toàn, trả authentication outcome nhất quán và tạo identity/context cho feature được bảo vệ.
- **WHY understood:** OTCMS xử lý dữ liệu lâm sàng nên phải xác minh chủ thể trước authorization, đồng thời chống lộ account/credential và có audit an toàn.
- **Definition of Done understood:** Login/register/verify-email/forgot/reset/change-password/refresh/logout behavior được chốt; response không enumeration; password-reset và rotating-session lifecycle, error contract và safe audit thống nhất; tests bao phủ business/security paths.
- **Boundaries and exclusions understood:** Bao gồm employee/patient login, Google authentication/linkage, patient registration, email verification, forgot/reset/change password cho local credential, refresh và current-session logout; không bao gồm employee provisioning, Google-only local-password creation, MFA, provider khác Google, logout-all UI, role administration hoặc resource authorization của feature khác.
- **Assumptions and unknowns:** Authentication/session, account status, lockout, audit, response, Google OIDC/linkage, password policy/storage, gender values, consent rules, email-OTP lifecycle, Gmail SMTP, cookie/CORS/CSRF và dual-portal baselines đã được Human chấp nhận. Breached-password check được deferred; exact patient/staff origins, SameSite, internal CIDR/VPN và trusted proxies được điền bằng typed deployment configuration khi các nhóm phối hợp.
- **Material questions and disposition:** Q-LOGIN-01..27 resolved hoặc deferred có boundary; không còn blocking Context question.
- **Consistency check:** Intent khớp Constitution v2.0.0 và Architecture Profile v1.3.0, bao gồm dual-portal/internal-network và password-recovery baseline. Profile review đã hoàn tất; Context v0.6.0/Spec v1.1.0 review và Maven verification evidence vẫn phải hoàn tất trước downstream execution.

## Change Impact Record

- **Changed intent, requirement, architecture, or task assumption:** Mở rộng FEAT-001 để gồm forgot/reset/change password cho patient và staff có local credential; bổ sung reset-token lifecycle, generic forgot response, rate limit và session revocation.
- **Feature Lock boundary affected:** Spec draft phải chuyển password recovery/change từ deferred work vào locked scope.
- **Material state-change classification:** shared/public contract; persistence schema; permission/security behavior; external email side effect.
- **Downstream artifacts invalidated:** FEAT-001 Spec v1.0.0 draft cần revision v1.1.0; chưa có Plan, Tasks hoặc implementation để invalidate.
- **Required trace, test, and sync actions:** Bổ sung EARS/BDD/persistence/error coverage trong Spec; `/sdd-trace` và exact Maven verification được thực hiện ở downstream artifact phù hợp.
- **Review follow-up:** Context v0.6.0 và Spec v1.1.0 cần Human review; **Spec revision required: yes** vì scope và public API thay đổi.

## AI Agent Recommendation

- **Status:** PENDING HUMAN REVIEW
- **Scope:** `.sdd/features/feat-001-auth-login/CONTEXT.md`
- **Recommendation:** Duyệt Context v0.6.0 với forgot/reset/change password nằm trong FEAT-001, giữ portal/network boundary, anti-enumeration, hashed reset token và revoke-all-refresh-session behavior đã chốt.
- **Evidence:** Constitution v2.0.0 quy định Spring Security, anti-sensitive-data logging, safe error contract và tests; repository có Spring Security dependency; Architecture Profile v1.3.0 đã được Human reviewer Long phê duyệt ngày 2026-09-29.
- **Risks and assumptions:** Access JWT cũ có residual window tối đa 15 phút sau reset/change; Google-only account không được tạo local password; frontend topology vẫn quyết định exact origin và SameSite; Gmail quota, App Password và delivery behavior cần được kiểm tra ở Plan.
- **Alternatives considered:** Cho Google-only account tự tạo local password qua forgot flow bị loại để tránh mở rộng credential model; trả lỗi forgot chi tiết bị loại do account-enumeration risk; giữ refresh sessions sau reset/change bị loại do session-compromise risk.
- **Required human decision:** Human reviewer duyệt Context v0.6.0 sau thay đổi phạm vi; approval Clarification-First của Long ngày 2026-09-29 là evidence cho rule mới nhưng không tự phê duyệt artifact đã sửa.

## Human Final Review

- **Status:** APPROVED
- **Decision:** APPROVED FEAT-001 Context v0.6.0
- **Reviewer:** Long
- **Reviewed at:** 2026-09-29T17:52:11.1239211+07:00
- **Follow-up:** Review FEAT-001 Spec v1.1.0
