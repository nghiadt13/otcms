# Feature Specification — FEAT-001 Authentication Login/Register

# Version: 1.1.0
# Status: APPROVED & LOCKED
# Feature slug: `feat-001-auth-login`
# Architecture Profile: v1.3.0 — APPROVED
# Context: v0.6.0 — APPROVED

---

## Methodology Profile

- **Depth:** FORMAL
- **Rationale:** Feature tạo authenticated identity cho hệ thống xử lý dữ liệu y tế, mở public authentication endpoints, quản lý credential, token rotation, external identity và security state transitions.
- **Risk posture:** high
- **High-risk review route:** Product Owner duyệt business scope; Tech Lead/Security reviewer duyệt authentication, session, abuse controls và API contract; Clinical owner duyệt age/guardian/gender policy; persisted Spec review bắt buộc.
- **Unresolved-decision owner:** Product Owner và Tech Lead/Security reviewer cho các deployment value được deferred trước integration.

## Clarification-First Record

- **Status:** APPROVED
- **Decision:** Tất cả khuyến nghị Clarification-First được chấp nhận; cho phép đồng bộ Architecture Profile và tạo FEAT-001 Spec.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-29
- **Approved assumptions:** Google onboarding, Google activation của matching pending account, guardian-data boundary, age calculation, server-owned consent version, endpoint set, normalization/field limits, error mapping, rate limits, refresh-token reuse handling, idempotent logout, Gmail failure behavior, standalone backend staff-network enforcement và data-retention baselines.
- **Deferred deployment values:** Exact Patient/Staff frontend origins, `SameSite`, internal CIDR/VPN và trusted proxies phải được chốt trước integration.
- **Password recovery scope approval:** Long approved toàn bộ khuyến nghị Forgot/Reset/Change Password ngày 2026-09-29: eligible account, endpoint separation, 256-bit hashed token, 15-minute lifetime, rate limits, password rules, revoke-all-refresh-sessions, status handling và generic forgot response.

---

## 1. Context & Goal

OTCMS cần xác thực bệnh nhân và nhân viên trước khi các feature khác thực thi authorization trên hồ sơ và dữ liệu y tế. Feature này cung cấp Patient Portal public và Staff Portal internal/VPN với entry point tách biệt trong cùng Spring Boot modular monolith.

Mục tiêu kiểm chứng được:

1. Bệnh nhân đăng ký, xác minh email và đăng nhập bằng email/password hoặc Google trên cùng một patient account khi linkage hợp lệ.
2. Nhân viên chỉ đăng nhập bằng email/password qua Staff Portal từ approved internal network/VPN.
3. Backend phát access JWT 15 phút và opaque rotating refresh token 7 ngày, quản lý session/revocation an toàn.
4. Public authentication contract chống account enumeration, brute force, token reuse và lộ credential/PII.
5. Authentication outcome cung cấp account context; quyền truy cập resource vẫn do feature sở hữu resource quyết định.
6. Patient và staff có local credential đủ điều kiện có thể forgot/reset/change password mà không làm lộ account existence hoặc giữ refresh session cũ.

### 1.1 API surface

| Method | Path | Access | Mục đích |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/patient/register` | Public patient | Đăng ký local patient account và gửi email OTP |
| `POST` | `/api/auth/patient/verify-email` | Public patient | Xác minh OTP và kích hoạt account đủ điều kiện |
| `POST` | `/api/auth/patient/resend-otp` | Public patient | Vô hiệu OTP cũ và gửi OTP mới |
| `POST` | `/api/auth/patient/login` | Public patient | Đăng nhập patient bằng email/password |
| `POST` | `/api/auth/patient/google/exchange` | Public patient | Đổi Google authorization code, login/link/onboard patient |
| `POST` | `/api/auth/staff/login` | Internal/VPN staff | Đăng nhập staff bằng email/password |
| `GET` | `/api/auth/csrf` | Public, no authentication effect | Cấp double-submit CSRF token |
| `POST` | `/api/auth/refresh` | Refresh cookie + CSRF | Rotate refresh token và cấp access token mới |
| `POST` | `/api/auth/logout` | Refresh cookie + CSRF | Revoke current session và clear cookie |
| `POST` | `/api/auth/patient/forgot-password` | Public patient | Yêu cầu gửi password-reset instruction bằng response chống enumeration |
| `POST` | `/api/auth/patient/reset-password` | Public patient | Đặt password mới bằng reset token |
| `POST` | `/api/auth/staff/forgot-password` | Internal/VPN staff | Yêu cầu reset staff password qua entry point giới hạn mạng |
| `POST` | `/api/auth/staff/reset-password` | Internal/VPN staff | Reset staff password qua entry point giới hạn mạng |
| `POST` | `/api/auth/change-password` | Authenticated patient/staff | Đổi local password bằng current password |

Chỉ các endpoint trên được public hoặc network-exposed theo policy của feature này. Staff endpoint không trở thành public endpoint chỉ vì nằm cùng deployment unit.

---

## 2. Actors & Roles

| Actor | Khả năng trong feature | Giới hạn |
| :--- | :--- | :--- |
| Patient registrant | Đăng ký, cung cấp consent, xác minh email | Không tạo staff account hoặc quyền lâm sàng |
| Active patient | Login local/Google, refresh và logout current session | Chỉ dùng Patient Portal entry point |
| Active staff | Login local, refresh và logout current session | Chỉ dùng Staff Portal và approved network/VPN |
| Eligible local-password account | Yêu cầu reset hoặc đổi password | Chỉ account/status/portal đủ điều kiện; Google-only account không tạo local credential qua flow này |
| Guardian consent actor | Đồng ý Terms/Privacy cho patient dưới 18 hoặc trên 70 | Không có account, identity proof, linkage hoặc quyền truy cập hồ sơ trong FEAT-001 |
| React clients | Gọi backend qua exact-origin CORS allowlist | Không nhận secret, refresh-token plaintext hoặc persistence credential |
| Google OIDC | Xác minh Google identity | Không quyết định OTCMS authorization hoặc clinical-record linkage |
| Gmail SMTP | Gửi OTP/thông báo qua adapter | Không quyết định account activation |
| Operator/Security reviewer | Cấu hình secrets, origins, CIDR và xem safe audit | Không được xem raw password, token hoặc OTP |

---

## 3. Functional Requirements

### 3.1 Registration and validation

- **REQ-AUTH-001 — Ubiquitous:** The OTCMS SHALL normalize email bằng cách trim và lowercase toàn bộ; hệ thống SHALL không xóa dấu chấm hoặc phần `+tag`.
- **REQ-AUTH-002 — Ubiquitous:** The OTCMS SHALL normalize phone về E.164 tối đa 15 chữ số và SHALL dùng `+84` làm country code mặc định cho số Việt Nam nhập ở local format.
- **REQ-AUTH-003 — WHEN:** WHEN một patient gửi registration hợp lệ gồm full name, email, phone, date of birth, gender, password, confirm password và patient consent, the OTCMS SHALL tạo một `PENDING_VERIFICATION` patient account, patient profile cơ bản, consent evidence và email-verification challenge trong một business transaction.
- **REQ-AUTH-004 — IF:** IF normalized email đã thuộc bất kỳ account nào, THEN the OTCMS SHALL từ chối registration với `EMAIL_ALREADY_REGISTERED` và SHALL không tạo account/profile/credential thứ hai.
- **REQ-AUTH-005 — WHEN:** WHEN phone trùng phone đã lưu nhưng normalized email chưa tồn tại, the OTCMS SHALL cho phép registration và MAY ghi safe risk signal mà không đánh dấu phone là unique hoặc verified.
- **REQ-AUTH-006 — IF:** IF password và confirm password không khớp, password dưới 12 hoặc trên 64 ký tự, hoặc password vượt 72 UTF-8 bytes, THEN the OTCMS SHALL từ chối request trước khi persistence và SHALL không truncate password.
- **REQ-AUTH-007 — Ubiquitous:** The OTCMS SHALL cho phép letters, numbers, symbols và spaces trong password mà không áp composition rule bắt buộc.
- **REQ-AUTH-008 — Ubiquitous:** The OTCMS SHALL hash local password bằng Spring Security `DelegatingPasswordEncoder` với BCrypt baseline, giữ encoder prefix và SHALL không persist/log plaintext password hoặc confirm password.
- **REQ-AUTH-009 — IF:** IF full name sau trim không nằm trong 2–100 Unicode characters, email dài hơn 254 characters, phone không thể normalize hợp lệ, date of birth ở tương lai, hoặc gender không thuộc `MALE|FEMALE|OTHER`, THEN the OTCMS SHALL từ chối registration với `VALIDATION_FAILED`.
- **REQ-AUTH-010 — WHEN:** WHEN tuổi hoàn chỉnh tại ngày đăng ký theo `Asia/Ho_Chi_Minh` dưới 18 hoặc trên 70, the OTCMS SHALL yêu cầu patient consent, guardian consent và SHALL xem phone là guardian contact phone.
- **REQ-AUTH-011 — WHEN:** WHEN tuổi hoàn chỉnh từ 18 đến 70 inclusive, the OTCMS SHALL chỉ yêu cầu patient consent và SHALL xem phone là patient contact phone.
- **REQ-AUTH-012 — IF:** IF consent bắt buộc không được đồng ý, THEN the OTCMS SHALL từ chối registration và SHALL không tạo account.
- **REQ-AUTH-013 — Ubiquitous:** The OTCMS SHALL lấy Terms/Privacy version hiện hành từ typed server configuration và lưu actor type, version và accepted timestamp; client SHALL không quyết định version được lưu.

### 3.2 Email OTP activation

- **REQ-AUTH-014 — WHEN:** WHEN registration transaction hoàn tất, the OTCMS SHALL sinh OTP 6 chữ số bằng cryptographically secure random source, chỉ lưu hash, đặt expiration 10 phút và gửi OTP qua `EmailDeliveryAdapter`.
- **REQ-AUTH-015 — IF:** IF Gmail delivery thất bại, THEN the OTCMS SHALL giữ account ở `PENDING_VERIFICATION`, ghi sanitized delivery outcome, trả `EMAIL_DELIVERY_UNAVAILABLE` và cho phép resend theo rate limit.
- **REQ-AUTH-016 — WHEN:** WHEN patient cung cấp OTP còn hiệu lực, khớp hash, chưa dùng và consent bắt buộc tồn tại, the OTCMS SHALL atomically đánh dấu OTP consumed và chuyển account sang `ACTIVE`.
- **REQ-AUTH-017 — IF:** IF OTP sai, hết hạn, đã dùng hoặc đã bị thay thế, THEN the OTCMS SHALL trả `OTP_INVALID_OR_EXPIRED`, không kích hoạt account và tăng failed-attempt counter khi challenge còn hiệu lực.
- **REQ-AUTH-018 — IF:** IF một OTP đã có 5 lần nhập sai, THEN the OTCMS SHALL vô hiệu challenge đó và yêu cầu resend trước lần thử tiếp theo.
- **REQ-AUTH-019 — WHEN:** WHEN resend được phép, the OTCMS SHALL vô hiệu mọi OTP chưa dùng trước đó, sinh challenge mới và gửi OTP mới.
- **REQ-AUTH-020 — IF:** IF account đã đạt 3 lần resend trong rolling 1-hour window, THEN the OTCMS SHALL từ chối resend với `RATE_LIMIT_EXCEEDED`.

### 3.3 Patient and staff local login

- **REQ-AUTH-021 — WHEN:** WHEN một `ACTIVE` patient cung cấp đúng normalized email và password tại patient login endpoint, the OTCMS SHALL tạo session và trả authentication success contract.
- **REQ-AUTH-022 — WHEN:** WHEN một `ACTIVE` staff cung cấp đúng normalized email/password tại staff login endpoint từ approved internal network/VPN, the OTCMS SHALL tạo session và trả authentication success contract.
- **REQ-AUTH-023 — IF:** IF identifier không tồn tại, password sai, account type sai portal, hoặc status không cho phép login, THEN the OTCMS SHALL trả cùng HTTP `401`, code `AUTHENTICATION_FAILED` và generic message.
- **REQ-AUTH-024 — IF:** IF staff login request không đến từ approved internal CIDR/VPN, THEN the OTCMS SHALL từ chối trước credential authentication với `STAFF_NETWORK_ACCESS_DENIED`.
- **REQ-AUTH-025 — IF:** IF request tự cung cấp `Forwarded` hoặc `X-Forwarded-For` nhưng direct peer không thuộc trusted proxy list, THEN the OTCMS SHALL bỏ qua forwarding headers khi xác định network origin.
- **REQ-AUTH-026 — WHEN:** WHEN một known account có 5 credential failures liên tiếp trong 15 phút, the OTCMS SHALL chuyển account sang `TEMPORARILY_LOCKED` trong 15 phút.
- **REQ-AUTH-027 — WHEN:** WHEN lock duration kết thúc và account không bị `DISABLED` hoặc `ARCHIVED`, the OTCMS SHALL cho phép credential authentication lại và reset consecutive-failure state sau login thành công.
- **REQ-AUTH-028 — IF:** IF account đang `PENDING_VERIFICATION`, `TEMPORARILY_LOCKED`, `DISABLED` hoặc `ARCHIVED`, THEN the OTCMS SHALL không phát token/session.

### 3.4 Google authentication and onboarding

- **REQ-AUTH-029 — WHEN:** WHEN patient gửi Google authorization code, PKCE verifier và redirect context hợp lệ, the OTCMS SHALL exchange code ở backend và kiểm tra issuer, audience, expiration và `email_verified=true`.
- **REQ-AUTH-030 — IF:** IF Google code/token/claim không hợp lệ hoặc email chưa verified, THEN the OTCMS SHALL từ chối với `AUTHENTICATION_FAILED` và SHALL không tạo/link account.
- **REQ-AUTH-031 — WHEN:** WHEN Google provider `sub` đã link duy nhất với một `ACTIVE` patient account, the OTCMS SHALL login account đó.
- **REQ-AUTH-032 — WHEN:** WHEN Google `sub` chưa link nhưng normalized verified Google email trùng duy nhất một patient account hoàn chỉnh và không có conflict, the OTCMS SHALL atomically link provider identity vào account đó.
- **REQ-AUTH-033 — WHEN:** WHEN account ở REQ-AUTH-032 là `PENDING_VERIFICATION` nhưng đã có đủ profile và consent, the OTCMS SHALL xem verified Google email là email verification và chuyển account sang `ACTIVE` trước khi login.
- **REQ-AUTH-034 — WHEN:** WHEN không có matching account và request cùng lần exchange có đủ registration data/consent ngoại trừ password, the OTCMS SHALL tạo `ACTIVE` patient account, profile, consent và Google linkage atomically rồi login.
- **REQ-AUTH-035 — IF:** IF không có matching account và Google exchange request thiếu onboarding data bắt buộc, THEN the OTCMS SHALL trả `GOOGLE_ONBOARDING_REQUIRED` và SHALL không tạo partial account/linkage.
- **REQ-AUTH-036 — IF:** IF provider subject, normalized email hoặc existing linkage tạo conflict, THEN the OTCMS SHALL trả `IDENTITY_LINK_CONFLICT`, không auto-merge và không link clinical record.

### 3.5 Token, session, refresh and logout

- **REQ-AUTH-037 — WHEN:** WHEN login thành công, the OTCMS SHALL phát access JWT lifetime 15 phút trong response body và opaque cryptographically random refresh token lifetime 7 ngày trong protected cookie.
- **REQ-AUTH-038 — Ubiquitous:** The OTCMS SHALL chỉ persist refresh-token hash cùng session metadata và SHALL không persist/log plaintext refresh token.
- **REQ-AUTH-039 — Ubiquitous:** The OTCMS SHALL giới hạn tối đa 5 active sessions/account.
- **REQ-AUTH-040 — WHEN:** WHEN tạo session thứ sáu, the OTCMS SHALL revoke session active cũ nhất theo `createdAt`, dùng stable session ID làm tie-breaker.
- **REQ-AUTH-041 — WHEN:** WHEN valid refresh cookie và valid double-submit CSRF token được gửi tới refresh endpoint, the OTCMS SHALL atomically revoke token cũ, phát token mới trong cùng token family và trả access JWT mới.
- **REQ-AUTH-042 — IF:** IF refresh token expired, revoked, malformed, không khớp hash, hoặc account không còn `ACTIVE`, THEN the OTCMS SHALL từ chối refresh với `AUTHENTICATION_FAILED`, revoke session liên quan khi xác định được và clear refresh cookie.
- **REQ-AUTH-043 — IF:** IF một refresh token đã rotate bị dùng lại, THEN the OTCMS SHALL revoke toàn bộ token family/current session và SHALL không revoke các session độc lập khác của account.
- **REQ-AUTH-044 — WHEN:** WHEN client gọi logout với CSRF hợp lệ, the OTCMS SHALL revoke current session nếu xác định được, clear refresh cookie và trả `204 No Content`.
- **REQ-AUTH-045 — IF:** IF logout không có token, token hết hạn hoặc đã revoke nhưng CSRF request hợp lệ, THEN the OTCMS SHALL vẫn clear cookie và trả `204 No Content`.
- **REQ-AUTH-046 — WHEN:** WHEN client gọi CSRF endpoint, the OTCMS SHALL sinh random token, đặt token trong cookie riêng không `HttpOnly` và trả cùng giá trị cho client dùng ở header `X-CSRF-TOKEN`.
- **REQ-AUTH-047 — IF:** IF refresh/logout thiếu CSRF cookie/header hoặc hai giá trị không khớp, THEN the OTCMS SHALL từ chối với `CSRF_VALIDATION_FAILED` và SHALL không thay đổi session state.

### 3.6 Audit, privacy and abuse controls

- **REQ-AUTH-048 — Ubiquitous:** The OTCMS SHALL ghi authentication audit gồm timestamp, method, sanitized outcome, correlation ID, sanitized network/client evidence và account UUID khi xác định được.
- **REQ-AUTH-049 — WHEN:** WHEN identifier không map được tới account, the OTCMS SHALL dùng keyed HMAC của normalized email làm safe audit reference.
- **REQ-AUTH-050 — Ubiquitous:** The OTCMS SHALL không log password, confirm password, password hash, OTP, Google code/token, access/refresh token, Gmail credential, JWT secret hoặc clinical data.
- **REQ-AUTH-051 — IF:** IF endpoint vượt rate limit, THEN the OTCMS SHALL trả `429 RATE_LIMIT_EXCEEDED` và SHALL không thực hiện authentication state transition hoặc external email call.
- **REQ-AUTH-052 — Ubiquitous:** The OTCMS SHALL áp typed rate limits: login 10 requests/IP/minute và 5 credential failures/account/15 minutes; register 5 requests/IP/hour; resend 3 requests/account/hour; refresh 30 requests/session/minute; Google exchange 10 requests/IP/minute.

### 3.7 Forgot, reset and change password

- **REQ-AUTH-053 — WHEN:** WHEN patient hoặc staff gửi forgot-password tới đúng portal endpoint và đã qua portal/network admission, the OTCMS SHALL normalize identifier và luôn trả `202 Accepted` với cùng generic message dù account không tồn tại hoặc không đủ điều kiện.
- **REQ-AUTH-054 — WHEN:** WHEN forgot-password identifier map duy nhất tới `ACTIVE` hoặc `TEMPORARILY_LOCKED` account có local password và request nằm trong rate limit, the OTCMS SHALL sinh reset token có tối thiểu 256-bit entropy, chỉ lưu hash, đặt expiration 15 phút, vô hiệu token cũ và gửi instruction qua `EmailDeliveryAdapter`.
- **REQ-AUTH-055 — IF:** IF forgot-password identifier không tồn tại, account là Google-only, status không đủ điều kiện hoặc email delivery thất bại, THEN the OTCMS SHALL không tiết lộ disposition đó trong response và SHALL chỉ ghi safe internal outcome.
- **REQ-AUTH-056 — IF:** IF staff forgot/reset/change-password request đến ngoài approved internal network/VPN hoặc qua untrusted forwarding headers, THEN the OTCMS SHALL từ chối theo cùng staff network policy trước khi xử lý credential.
- **REQ-AUTH-057 — WHEN:** WHEN một `ACTIVE` local-password account gửi reset token hợp lệ, chưa dùng, chưa hết hạn cùng password mới hợp lệ và khác current password, the OTCMS SHALL atomically consume token, cập nhật encoded password và revoke toàn bộ refresh sessions của account.
- **REQ-AUTH-058 — WHEN:** WHEN một `TEMPORARILY_LOCKED` local-password account hoàn tất reset hợp lệ, the OTCMS SHALL atomically chuyển account về `ACTIVE`, xóa credential-failure state, consume token, cập nhật password và revoke toàn bộ refresh sessions.
- **REQ-AUTH-059 — IF:** IF reset token sai, hết hạn, đã dùng, bị supersede hoặc vượt 5 failed attempts, THEN the OTCMS SHALL trả `PASSWORD_RESET_TOKEN_INVALID`, không thay đổi password/session và tăng failed-attempt counter khi challenge còn hiện hành.
- **REQ-AUTH-060 — IF:** IF reset được yêu cầu cho `PENDING_VERIFICATION`, `DISABLED`, `ARCHIVED` hoặc Google-only account, THEN the OTCMS SHALL không phát reset token và forgot response SHALL vẫn generic.
- **REQ-AUTH-061 — WHEN:** WHEN authenticated `ACTIVE` local-password account gửi đúng current password và password mới hợp lệ, khác current password, the OTCMS SHALL atomically cập nhật encoded password và revoke toàn bộ refresh sessions của account.
- **REQ-AUTH-062 — IF:** IF current password không đúng, THEN the OTCMS SHALL trả `CURRENT_PASSWORD_INVALID`, không đổi password và không revoke session.
- **REQ-AUTH-063 — IF:** IF password mới trùng current password, THEN the OTCMS SHALL trả `PASSWORD_REUSE_NOT_ALLOWED` và SHALL không thay đổi credential/session.
- **REQ-AUTH-064 — Ubiquitous:** The OTCMS SHALL áp cùng password length/byte/encoding policy cho registration, reset và change password; password history ngoài current-password comparison SHALL không được yêu cầu trong MVP.
- **REQ-AUTH-065 — Ubiquitous:** The OTCMS SHALL giới hạn forgot-password ở 3 requests/account/hour và 5 requests/IP/hour, reset ở 5 failed attempts/token và change-password ở 5 requests/account/15 minutes.
- **REQ-AUTH-066 — WHEN:** WHEN reset/change password thành công, the OTCMS SHALL yêu cầu đăng nhập lại; access JWT đã phát trước đó MAY còn hiệu lực tối đa 15 phút và FEAT-001 SHALL không tạo access-token denylist.

---

## 4. Non-functional Requirements

- **NFR-AUTH-001 — Security:** Password hashing SHALL dùng BCrypt qua `DelegatingPasswordEncoder`; encoded value column SHALL hỗ trợ ít nhất 255 characters và giữ `{bcrypt}` prefix.
- **NFR-AUTH-002 — Browser security:** Refresh cookie SHALL có `HttpOnly=true`, `Secure=true`, `Path=/api/auth`, không khai báo `Domain`; `SameSite` SHALL là typed configuration được chốt trước integration.
- **NFR-AUTH-003 — CORS:** Credentialed CORS SHALL chỉ chấp nhận exact origins trong allowlist; wildcard `*` SHALL bị cấm; patient/staff origins SHALL được cấu hình riêng.
- **NFR-AUTH-004 — Token entropy:** Opaque refresh token và CSRF token SHALL được tạo từ cryptographically secure random source với tối thiểu 256 bits entropy trước encoding.
- **NFR-AUTH-005 — Time:** Persistence timestamps SHALL được lưu UTC; age calculation SHALL dùng `Asia/Ho_Chi_Minh`; validation tests SHALL dùng controllable clock.
- **NFR-AUTH-006 — Transaction integrity:** Account/profile/credential/consent creation, Google account/link creation, OTP consumption/activation và refresh rotation SHALL có atomic database transaction hoặc database constraints chứng minh không tồn tại partial committed business state.
- **NFR-AUTH-007 — Database integrity:** Database SHALL enforce unique normalized email, unique `(provider, providerSubject)`, unique refresh-token hash và một active current OTP challenge/account theo schema strategy được Plan duyệt.
- **NFR-AUTH-008 — Retention:** Authentication audit SHALL có baseline retention 90 days; consumed/expired OTP và password-reset challenges SHALL được purge sau 24 hours; expired/revoked sessions SHALL được purge sau 30 days; unverified pending account SHALL được archive hoặc anonymize sau 30 days.
- **NFR-AUTH-009 — Availability boundary:** Gmail/Google failure SHALL không tạo active account, issued session hoặc identity linkage không hoàn tất; retry SHALL không nhân đôi account, linkage, consent hoặc challenge hiện hành.
- **NFR-AUTH-010 — Test isolation:** Automated tests SHALL không gọi Gmail/Google thật, không phụ thuộc execution order/shared mutable state và SHALL dùng fake adapter/controlled clock cho external/time behavior.
- **NFR-AUTH-011 — Observability:** Mỗi auth/external operation SHALL ghi structured outcome, correlation ID và duration milliseconds; retention/log content SHALL tuân REQ-AUTH-048..050.
- **NFR-AUTH-012 — Deployment fail-closed:** Trong integration/production, Staff Portal SHALL từ chối mọi request nếu approved internal CIDR/VPN configuration bị thiếu hoặc invalid; development override SHALL chỉ cho phép loopback được khai báo rõ.
- **NFR-AUTH-013 — Reset-token security:** Password reset token SHALL có tối thiểu 256 bits entropy trước encoding, chỉ lưu hash, hết hạn sau 15 minutes và SHALL được consume trong cùng transaction với password update/session revocation.
- **NFR-AUTH-014 — Recovery privacy:** Forgot-password response status, body shape và generic message SHALL giống nhau cho unknown, ineligible, Google-only và eligible identifiers; automated contract tests SHALL kiểm tra equality của các response này.
- **NFR-AUTH-015 — Reset-link transport:** Reset email SHALL đặt token trong URL fragment, không đặt trong query parameter; frontend SHALL gửi token tới reset endpoint trong request body để token không xuất hiện trong backend/proxy request URL.

Exact Gmail connect/read timeout, bounded transient retry values, frontend origins, `SameSite`, internal CIDR/VPN và trusted proxies là typed deployment bindings phải được chốt và kiểm thử trước integration; chúng không thay đổi business contract trong Spec này.

---

## 5. Data Model

Đây là logical model; ORM mapping, migration ordering và index names thuộc `PLAN.md`.

### 5.1 Account and profile

| Entity | Required logical fields | Constraint/lifecycle |
| :--- | :--- | :--- |
| `Account` | id, normalizedEmail, accountType, status, passwordHash nullable, emailVerifiedAt, failedCredentialCount, lockedUntil, createdAt, updatedAt | normalizedEmail unique; accountType `PATIENT|STAFF`; status `PENDING_VERIFICATION|ACTIVE|TEMPORARILY_LOCKED|DISABLED|ARCHIVED` |
| `PatientProfile` | accountId, fullName, phoneCanonical, phoneOwnerType, dateOfBirth, gender | one-to-one patient account; phone not unique; phoneOwnerType `PATIENT|GUARDIAN` |
| `ExternalIdentity` | accountId, provider, providerSubject, normalizedEmailAtLink, linkedAt | unique `(provider, providerSubject)`; provider baseline `GOOGLE` |
| `ConsentEvidence` | accountId, actorType, termsVersion, privacyVersion, acceptedAt | actor `PATIENT|GUARDIAN`; immutable evidence for accepted version |

### 5.2 Verification and session

| Entity | Required logical fields | Constraint/lifecycle |
| :--- | :--- | :--- |
| `EmailVerificationChallenge` | id, accountId, otpHash, expiresAt, failedAttempts, consumedAt, supersededAt, createdAt | plaintext OTP forbidden; one current challenge/account |
| `AuthSession` | id, accountId, tokenFamilyId, refreshTokenHash, createdAt, expiresAt, lastRotatedAt, revokedAt, revokeReason, safe client/network metadata | refresh hash unique; max 5 active/account enforced transactionally |
| `AuthenticationAudit` | id, occurredAt, method, outcome, accountId nullable, identifierHmac nullable, correlationId, sanitized network/client evidence, durationMs | no raw credential/token/clinical data; 90-day baseline retention |
| `PasswordResetChallenge` | id, accountId, tokenHash, expiresAt, failedAttempts, consumedAt, supersededAt, createdAt | token hash unique; plaintext token forbidden; một current challenge/account; 15-minute lifetime |

### 5.3 State transitions and invariants

```text
Registration ──> PENDING_VERIFICATION ──OTP/verified Google──> ACTIVE
ACTIVE ──5 credential failures──> TEMPORARILY_LOCKED ──15 minutes──> ACTIVE
ACTIVE/PENDING/TEMPORARILY_LOCKED ──authorized administration──> DISABLED/ARCHIVED
```

Invariants:

1. Chỉ `ACTIVE` account được phát login/refresh authentication outcome.
2. Một normalized email chỉ thuộc một account.
3. Một Google `(provider, sub)` chỉ link một account.
4. Password hash, refresh-token hash và OTP hash không xuất hiện trong DTO response.
5. Account activation yêu cầu verified email và đầy đủ consent theo tuổi.
6. Guardian consent không tạo guardian identity hoặc authorization.
7. Google linkage không tự tạo clinical-record linkage.
8. Rotation thành công không để cả refresh token cũ và mới cùng hợp lệ.
9. Reset token mới làm mọi reset challenge cũ chưa dùng của account mất hiệu lực.
10. Reset/change password commit thành công đồng nghĩa toàn bộ refresh sessions của account đã bị revoke trong cùng transaction.
11. Google-only account không nhận local password qua forgot/reset/change flow.

---

## 6. Error Handling

### 6.1 Canonical error envelope

```json
{
  "error": {
    "code": "AUTHENTICATION_FAILED",
    "message": "Authentication failed.",
    "timestamp": "2026-09-29T10:15:30Z"
  }
}
```

`timestamp` dùng ISO-8601 UTC. Validation details nếu được trả SHALL không chứa credential value hoặc xác nhận account tồn tại.

### 6.2 Status mapping

| Condition | HTTP | Stable code | Client-visible behavior |
| :--- | :---: | :--- | :--- |
| Malformed/invalid field | 400 | `VALIDATION_FAILED` | Safe field errors |
| Wrong credential, non-active status, wrong portal, invalid Google/refresh | 401 | `AUTHENTICATION_FAILED` | Generic message |
| Invalid/missing CSRF | 403 | `CSRF_VALIDATION_FAILED` | Không mutate session |
| Staff request outside approved network | 403 | `STAFF_NETWORK_ACCESS_DENIED` | Không xử lý credential |
| Duplicate registration email | 409 | `EMAIL_ALREADY_REGISTERED` | Không tạo duplicate |
| Google identity conflict | 409 | `IDENTITY_LINK_CONFLICT` | Không auto-merge |
| Google new patient lacks required profile/consent | 422 | `GOOGLE_ONBOARDING_REQUIRED` | Client thu thập registration fields rồi khởi động exchange hợp lệ mới |
| OTP invalid/expired/consumed | 400 | `OTP_INVALID_OR_EXPIRED` | Không activate |
| Rate limit exceeded | 429 | `RATE_LIMIT_EXCEEDED` | Có thể trả safe retry metadata |
| Gmail unavailable before delivery | 503 | `EMAIL_DELIVERY_UNAVAILABLE` | Account vẫn pending; cho phép resend |
| Forgot-password accepted | 202 | N/A | Luôn generic, kể cả unknown/ineligible/delivery failure |
| Reset token invalid/expired/consumed | 400 | `PASSWORD_RESET_TOKEN_INVALID` | Không đổi credential/session |
| Current password incorrect | 400 | `CURRENT_PASSWORD_INVALID` | Không đổi credential/session |
| New password equals current password | 400 | `PASSWORD_REUSE_NOT_ALLOWED` | Không đổi credential/session |
| Unexpected internal failure | 500 | `INTERNAL_ERROR` | Correlation ID ở safe response metadata/log context, không lộ stack trace |

### 6.3 Success contracts

Successful login/Google exchange/refresh trả tối thiểu:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "account": {
    "id": "<account-id>",
    "displayName": "<display-name>",
    "accountType": "PATIENT"
  }
}
```

Refresh token chỉ đi qua cookie. Response không trả permission graph, clinical profile, password hash, refresh token hoặc provider token.

Registration gửi OTP thành công trả `202 Accepted` với trạng thái `PENDING_VERIFICATION`. Verify-email thành công trả `204 No Content`. Logout idempotent trả `204 No Content`.

Forgot-password luôn trả `202 Accepted` với message `If the account is eligible, password reset instructions will be sent.` Reset/change password thành công trả `204 No Content`, revoke toàn bộ refresh sessions và clear refresh cookie của request hiện tại khi có.

---

## 7. Acceptance Criteria

### AC-01 — Local patient registration and activation

```gherkin
Given normalized email chưa tồn tại và dữ liệu đăng ký hợp lệ
When patient đăng ký và Gmail adapter nhận yêu cầu gửi OTP
Then account ở PENDING_VERIFICATION, password được hash, consent đúng actor/version được lưu
And response là 202, raw password/OTP không xuất hiện trong persistence response hoặc log
When patient gửi OTP hợp lệ trước 10 phút
Then OTP được consume đúng một lần và account chuyển sang ACTIVE
```

### AC-02 — Age and guardian rule

```gherkin
Given ngày hiện tại được cố định theo Asia/Ho_Chi_Minh
When patient có tuổi dưới 18 hoặc trên 70 đăng ký thiếu guardian consent
Then registration bị từ chối và không có partial account
When cùng patient cung cấp cả patient và guardian consent
Then registration được chấp nhận và phoneOwnerType là GUARDIAN
```

### AC-03 — Duplicate and validation

```gherkin
Given một account đã có normalized email patient@example.com
When đăng ký bằng email có case/whitespace khác nhưng normalize cùng giá trị
Then response là 409 EMAIL_ALREADY_REGISTERED và chỉ có một account
When phone trùng nhưng email khác hợp lệ
Then registration không bị chặn chỉ vì phone trùng
```

### AC-04 — Anti-enumeration and lockout

```gherkin
Given các trường hợp unknown email, wrong password, wrong portal, pending, locked, disabled và archived
When login thất bại
Then tất cả trả 401 AUTHENTICATION_FAILED với generic message và không phát token
And known account bị khóa 15 phút sau 5 credential failures liên tiếp
```

### AC-05 — Staff network boundary

```gherkin
Given request không đến từ approved CIDR hoặc giả mạo X-Forwarded-For qua untrusted peer
When gọi staff login
Then response là 403 STAFF_NETWORK_ACCESS_DENIED trước password authentication
Given trusted proxy và resolved client IP thuộc approved CIDR
When ACTIVE staff cung cấp đúng credential
Then login thành công
```

### AC-06 — Google login/link/onboarding

```gherkin
Given Google claims hợp lệ và provider subject đã link ACTIVE patient
When backend exchange authorization code với PKCE
Then patient nhận authentication success và không tạo account mới
Given verified Google email match duy nhất một complete PENDING_VERIFICATION account
When exchange không có linkage conflict
Then identity được link, email được xem là verified, account ACTIVE và login thành công atomically
Given Google identity chưa có account và onboarding payload đầy đủ
When exchange thành công
Then một ACTIVE patient account cùng Google linkage được tạo mà không cần local password
```

### AC-07 — Google conflict/failure

```gherkin
Given issuer, audience, expiration hoặc email_verified claim không hợp lệ
When Google exchange được gọi
Then response là 401 AUTHENTICATION_FAILED và không tạo/link account
Given provider subject hoặc email linkage conflict
When exchange được gọi
Then response là 409 IDENTITY_LINK_CONFLICT và không auto-merge hoặc link clinical record
```

### AC-08 — Refresh rotation and reuse

```gherkin
Given ACTIVE account có valid refresh cookie và matching CSRF token
When refresh được gọi
Then old refresh hash bị revoke và access/new refresh được phát atomically
When old token được replay
Then current token family bị revoke, request bị từ chối và session khác của account không bị revoke
```

### AC-09 — Session limit and logout

```gherkin
Given account có 5 active sessions
When login tạo session thứ sáu
Then session cũ nhất theo createdAt/session ID bị revoke và chỉ còn 5 active sessions
Given logout có valid CSRF nhưng refresh token thiếu, expired hoặc revoked
When logout được gọi
Then response là 204 và refresh cookie bị clear
```

### AC-10 — OTP abuse and delivery failure

```gherkin
Given current OTP
When resend thành công
Then OTP cũ không còn hợp lệ và chỉ OTP mới có thể activate account
When OTP sai lần thứ năm
Then challenge bị vô hiệu
When resend lần thứ tư trong một giờ
Then response là 429 RATE_LIMIT_EXCEEDED
Given Gmail adapter thất bại
When registration đã commit
Then account vẫn PENDING_VERIFICATION, response là 503 và resend có thể thử lại mà không tạo duplicate
```

### AC-11 — CSRF and CORS

```gherkin
Given refresh/logout request thiếu hoặc mismatch CSRF cookie/header
When endpoint được gọi
Then response là 403 và session state không đổi
Given credentialed cross-origin request từ origin không nằm trong exact allowlist
When browser preflight hoặc request được xử lý
Then origin không được cấp CORS permission
```

### AC-12 — Persistence and audit safety

```gherkin
Given success và failure paths của register, login, Google, OTP, refresh và logout
When integration tests kiểm tra database và captured logs
Then uniqueness/state invariants được giữ
And không có password, OTP, access/refresh token, Google token, Gmail credential hoặc clinical data trong response/log
And audit chứa correlation ID, sanitized outcome và safe account/HMAC reference
```

### AC-13 — Forgot-password anti-enumeration

```gherkin
Given identifiers lần lượt thuộc ACTIVE local account, Google-only account, disabled account và unknown account
When forgot-password được gọi trong rate limit
Then mọi response đều là 202 với cùng body/message
And reset challenge/email chỉ được tạo cho ACTIVE local-password account
Given Gmail delivery thất bại cho eligible account
When forgot-password được xử lý
Then response vẫn là generic 202 và failure chỉ xuất hiện trong safe internal outcome
```

### AC-14 — Password reset lifecycle

```gherkin
Given ACTIVE local-password account và reset token hợp lệ chưa hết 15 phút
When password mới hợp lệ và khác current password được gửi
Then token được consume, password hash được cập nhật và mọi refresh session bị revoke atomically
When cùng token bị replay
Then response là 400 PASSWORD_RESET_TOKEN_INVALID và credential không đổi
Given TEMPORARILY_LOCKED account hoàn tất reset hợp lệ
Then account trở về ACTIVE và credential-failure state được xóa
```

### AC-15 — Authenticated password change

```gherkin
Given ACTIVE local-password account có access JWT hợp lệ
When current password đúng và password mới hợp lệ, khác current password
Then password được cập nhật, mọi refresh session bị revoke và response là 204
When current password sai hoặc password mới trùng current password
Then response dùng stable error code tương ứng và credential/session không đổi
```

### 7.1 Verification coverage required

1. Unit tests cho age/consent, normalization, password limits/reuse, account state, lockout, reset-token lifecycle, token-family reuse và session eviction.
2. Controller/security integration tests cho validation, public endpoint policy, CORS, CSRF, portal/account type và staff network boundary.
3. Persistence/migration tests cho unique constraints, atomic activation/linkage/rotation và concurrent duplicate attempts.
4. Adapter integration tests bằng fake Google/email adapters cho success, timeout/failure, forgot-response indistinguishability, retry-safe behavior và partial-failure prevention.
5. Regression test cho mỗi security/behavior bug fix.
6. Success và failure paths không phụ thuộc execution order; dùng controllable clock.
7. PostgreSQL/Testcontainers khi H2 không phản ánh constraint, locking hoặc transaction semantics production.
8. Exact Maven command chỉ được ghi là verified sau khi Maven Wrapper chạy thành công và có persisted evidence.

---

## 8. Out of Scope

- Breached/common-password external checking trong MVP.
- Tạo local password cho Google-only account qua password recovery.
- MFA, SMS OTP, Zalo OTP hoặc phone verification.
- Staff self-registration hoặc employee provisioning.
- Guardian account, guardian identity verification, legal relationship, patient linkage hoặc delegated record access.
- Role/permission administration và resource authorization cho patient, encounter, DICOM, MinIO hoặc OHIF.
- Automatic clinical-record matching/merge dựa trên email hoặc Google identity.
- Provider ngoài Google.
- Logout all devices và session-management UI.
- Nginx/reverse-proxy provisioning, VPN provisioning và production deployment.
- Exact frontend origins, `SameSite`, internal CIDR/VPN, trusted proxies và Gmail timeout/retry values cho đến integration planning.

---

## Adversarial Quality Pass

| Finding | Incident/impact giả định | Disposition |
| :--- | :--- | :--- |
| Account enumeration | Attacker phân biệt unknown/locked/disabled account | Clarified: REQ-AUTH-023 dùng cùng 401/code/message; safe audit tách nội bộ |
| Refresh replay race | Hai request đồng thời cùng token tạo hai token hợp lệ | Clarified: REQ-AUTH-041/043 yêu cầu atomic rotation và revoke token family khi reuse |
| Google account takeover | Trùng email hoặc provider subject được auto-merge sai | Clarified: unique subject/email, verified claim, atomic linkage; conflict fail closed |
| Staff proxy spoofing | Internet client giả `X-Forwarded-For` để vào staff login | Clarified: chỉ tin forwarding headers từ trusted proxy; missing config fail closed |
| OTP replay/brute force | OTP cũ hoặc đoán mã kích hoạt account | Clarified: hash, 10 phút, single-use, 5 attempts, supersede và resend limit |
| Gmail partial failure | Account tạo xong nhưng email không gửi | Approved assumption: giữ pending, 503, resend idempotent, không duplicate |
| Consent boundary sai | Minor/elder account active thiếu guardian consent | Clarified: deterministic age/timezone, activation invariant và transaction |
| BCrypt silent truncation | Hai password khác nhau sau byte 72 xác thực như nhau | Clarified: reject trên 64 characters hoặc 72 UTF-8 bytes trước hashing |
| Session eviction nondeterministic | Concurrent sixth login vượt session limit | Clarified: max 5, transactional oldest `createdAt` + ID tie-breaker |
| Sensitive logs | Credential/token/OTP xuất hiện trong exception hoặc test output | Clarified: REQ-AUTH-050 và AC-12; fake adapters, captured-log verification |
| Pending data tồn tại vô hạn | Unverified account/PII tích lũy | Approved assumption: pending archive/anonymize sau 30 days |
| Deployment topology chưa chốt | Cookie hoặc network policy hoạt động sai khi tích hợp FE/proxy | Deferred item: exact origins, SameSite, CIDR/VPN, trusted proxies là integration blocker |
| Gmail timing chưa chốt | External call treo hoặc retry quá mức | Deferred item: exact timeout/retry values là Plan/integration blocker; business outcome vẫn theo REQ-AUTH-015 |
| Forgot-password enumeration | Response hoặc Gmail failure cho biết account có tồn tại/eligible | Clarified: generic 202/body cho mọi disposition; safe outcome chỉ ở internal audit |
| Reset-token theft/replay | Attacker đổi password hoặc tái dùng token | Clarified: 256-bit random, hash-at-rest, 15 phút, single-use, supersede và 5-attempt limit |
| Session còn sống sau password compromise | Attacker tiếp tục refresh sau reset/change | Clarified: password update và revoke-all-refresh-sessions trong cùng transaction; access JWT residual window tối đa 15 phút được Human chấp nhận |

Domain walkthrough đã bao phủ normal/error flow, state transition, actor/portal boundary, duplicate/concurrency, external partial failure và data lifecycle. Không còn business question blocking bản Spec draft; các deployment binding được ghi rõ là blocker trước integration.

---

## Feature Lock

- **Locked scope:** Patient registration, consent, email OTP activation, patient local/Google login, staff internal-network login, forgot/reset/change password cho eligible local-password accounts, access/refresh session lifecycle, CSRF, rate limits, safe audit và API/error contracts trong FEAT-001.
- **Deferred work:** Các mục ở Out of Scope; exact frontend origins, `SameSite`, CIDR/VPN, trusted proxies và Gmail timeout/retry values phải được chốt trước integration.
- **Change path:** `/sdd-update --feature=feat-001-auth-login --artifact=spec --bump=<patch|minor|major> --reason="..."`
- **Lock boundary:** Feature/sprint only; this does not lock unrelated project work.

---

## Definition of Done Check

- [x] Clarification-First có persisted Human decision và không còn material business question thiếu disposition.
- [x] Methodology Profile kế thừa `FORMAL`, high-risk route và decision owner.
- [x] Có đủ tám thành phần Spec.
- [x] Functional requirements dùng EARS và happy/error paths có cặp tương ứng.
- [x] NFR trong phạm vi Spec có giá trị kiểm chứng; deployment values chưa chốt được đánh dấu integration blocker.
- [x] State transitions, invariants, adversarial pre-mortem và domain walkthrough đã ghi disposition.
- [x] Feature Lock, deferred work và change path đã được ghi.
- [ ] Human Final Review cho Spec chưa `APPROVED & LOCKED`.
- [ ] Exact Maven verification commands chưa có successful persisted evidence.

## Changelog

### v1.1.0 — 2026-09-29

- Mở rộng scope với patient/staff forgot-password, reset-password và authenticated change-password cho local credential.
- Bổ sung 14 EARS requirements, reset-token persistence/invariants, error contracts, rate limits và ba acceptance groups.
- Ghi approval Clarification-First của Reviewer Long ngày 2026-09-29 và chuyển Google-only local-password creation sang deferred work.

### v1.0.0 — 2026-09-29

- Tạo SPEC đầu tiên từ Context v0.5.4 và Architecture Profile v1.3.0 đã duyệt.
- Áp dụng toàn bộ Clarification-First assumptions được Reviewer Long duyệt ngày 2026-09-29.
- Định nghĩa API, EARS requirements, state/invariant, error contract, BDD acceptance criteria, adversarial findings và Feature Lock.

## Change Impact Record

- **Changed intent, requirement, architecture, or task assumption:** Thêm forgot/reset/change password; reset token 256-bit/hash/15 phút/single-use; generic forgot response; current-password verification; revoke toàn bộ refresh sessions sau reset/change.
- **Feature Lock boundary affected:** Password recovery/change được chuyển từ Out of Scope vào locked scope; Google-only local-password creation vẫn deferred.
- **Material state-change classification:** shared/public contract; persistence schema; permission/security behavior; external email side effect.
- **Downstream artifacts invalidated:** Spec v1.0.0 draft được thay bằng v1.1.0; Context v0.5.4 approval không còn bao phủ scope mới và đã được refresh thành v0.6.0 DRAFT; chưa có Plan/Tasks/code/tests để invalidate.
- **Required trace, test, and sync actions:** Trace REQ-AUTH-053..066 vào Plan/Tasks/code/tests; persistence/concurrency/security integration tests; `/sdd-sync` sau implementation khi contract thay đổi; exact Maven command vẫn cần successful evidence.
- **Review follow-up:** Human review Context v0.6.0 và Spec v1.1.0; chỉ tạo Plan sau khi Context được approved và Spec được `APPROVED & LOCKED`.

## AI Agent Recommendation

- **Status:** PENDING HUMAN REVIEW
- **Scope:** `.sdd/features/feat-001-auth-login/SPEC.md` v1.1.0
- **Recommendation:** Duyệt Context v0.6.0 và lock FEAT-001 Spec v1.1.0 làm behavior/public contract cho bước planning; giữ exact origins, `SameSite`, network/proxy values và Gmail timeout/retry là planning/integration blockers trước integration.
- **Evidence:** Architecture Profile v1.3.0 đã được duyệt; Clarification-First cho login/register và password recovery đều được Long duyệt ngày 2026-09-29; Spec có 66 EARS requirements, measurable baselines, state invariants, 15 acceptance groups và adversarial disposition.
- **Risks and assumptions:** Access JWT cũ có residual window tối đa 15 phút sau password reset/change; Google-only account chưa được tạo local password; explicit reset-token link delivery cần tránh query/server logs; exact deployment/external timeout values vẫn chưa chốt.
- **Alternatives considered:** Auto-create Google account thiếu profile/consent bị loại; tự link clinical record bị loại; revoke mọi device khi refresh reuse bị loại vì vượt current-session scope; giữ stale Profile statuses bị loại vì mâu thuẫn persisted approval.
- **Required human decision:** Product Owner/Tech Lead/Security reviewer duyệt Context v0.6.0 và `SPEC.md` v1.1.0 thành `APPROVED & LOCKED`, hoặc yêu cầu revise các contract cụ thể.

## Human Final Review

- **Status:** APPROVED
- **Decision:** APPROVED FEAT-001 Spec v1.1.0
- **Reviewer:** Long
- **Reviewed at:** 2026-09-29T17:53:05.9736805+07:00
- **Follow-up:** `/sdd-plan --feature=feat-001-auth-login`
