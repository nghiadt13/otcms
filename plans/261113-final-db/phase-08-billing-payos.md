---
phase: 8
title: "Hoá đơn, thanh toán payOS: gộp payment_gateway_events vào payment_attempts"
status: pending
priority: P1
effort: "3h"
owner: Hùng
dependencies: [1]
migration: V119__final_db_billing_payos.sql
---

# Phase 08: Hoá đơn, thanh toán payOS

## Overview
Bỏ bảng `payment_gateway_events` (nhật ký webhook payOS). Chống gửi trùng chuyển sang hai chỗ: cột `last_event_fingerprint` trên `payment_attempts` và khoá hoá đơn trước khi đọc lại attempt. Nhật ký chuyển sang `audit_logs` qua `AuditLogWriter` (phase 01).

Tên vật lý giữ nguyên: `payment_attempts` (tên logic `payments`), `invoices`, `invoice_items`.

**Phụ thuộc:** phase 01 đã merge. V119 chép nhật ký vào `audit_logs` (V109), và test dùng `AuditLogWriter`.

**Phát hiện bàn giao từ kiểm phase 01:** đọc `phase-01-existing-billing-race.md` trước khi sửa phần khóa invoice. Race webhook đối đầu release đã được tái hiện ở code cũ, ngoài 5 lỗi baseline lịch sử. Giữ nguyên test và xử lý trong phần billing hoặc PR sửa riêng trước nghiệm thu phase 10; không bỏ qua lỗi chỉ vì migration payOS đã chạy được.

## Requirements
- `POST /api/v1/payment-webhooks/payos` giữ hành vi cũ: sự kiện đã xác thực trả 200; chữ ký sai hoặc payload không đọc được trả 400 `INVALID_WEBHOOK`.
- Gửi lại đúng sự kiện đã xử lý: không đổi trạng thái, không ghi thêm dòng nhật ký, trả 200.
- Sự kiện khác cho đơn đã `CONFIRMED`: không settle lần hai, ghi `DUPLICATE`.
- Đơn `PENDING` nhận đủ tiền: `APPLIED`, settle hoá đơn, xác nhận lịch hẹn giữ chỗ (như cũ).
- Tiền về sau khi hoá đơn `VOID` hoặc giữ chỗ đã nhả: `RECONCILIATION_REQUIRED`, không tự hoàn tiền.
- Không sửa code module `appointment` (giữ 15 phút `AWAITING_PAYMENT`).
- API không đổi: URL, DTO, mã MSG. Không thêm cột mới vào `PaymentAttemptResponse`.
- Không ghi payload webhook vào `audit_logs` (có số tài khoản và tên người chuyển). Chỉ ghi các trường đang có trong `payment_gateway_events`.

## Architecture

### 1. `payment_attempts` (V119, ALTER)
| Cột mới | Kiểu | Ghi chú |
|---|---|---|
| `last_event_fingerprint` | varchar(64) NULL | SHA-256 của sự kiện đã áp gần nhất, `CHECK ~ '^[0-9a-f]{64}$'` |
| `signature_verified` | boolean NULL | TRUE khi có giá trị (chỉ sự kiện đã xác thực mới được ghi) |

- `CHECK ck_payment_attempt_event_pair`: `(last_event_fingerprint IS NULL) = (signature_verified IS NULL)`.
- Đã có, không tạo lại: `uq_payment_provider_order UNIQUE (provider, provider_order_code)`, `uq_payment_provider_transaction`, `uk_payment_attempt_active_online`, `ix_payment_attempts_invoice_state`, `ix_payment_attempts_reconcile`.
- **Không chạm** (phase 03 sửa): `invoices.clinical_ordering_round_id`, `ck_invoices_source`, `uk_invoices_clinical_round_source`, `invoices_clinical_ordering_round_id_fkey`, `invoice_items.service_order_id`.

### 2. Nhật ký webhook trong `audit_logs`
Ghi bằng `AuditLogWriter` trong cùng transaction xử lý:

| Trường | Giá trị |
|---|---|
| `source` | `payment_gateway_events` |
| `category` | `PAYMENT_GATEWAY` |
| `action` = `outcome` | `APPLIED` / `DUPLICATE` / `RECONCILIATION_REQUIRED` / `REJECTED` |
| `record_type` | `PAYMENT_WEBHOOK` |
| `entity_id` | id `payment_attempts` nếu khớp đơn, NULL nếu không |
| `visible_in_activity_log` | `false` |
| `actor_type` | `PAYOS` |
| `before_state`, `after_state` | NULL (bảng cũ không lưu) |
| `metadata` | `{provider, fingerprint, orderCode, transactionReference, signatureVerified, detail}` |

Mỗi sự kiện được xử lý ghi một dòng. Replay không ghi gì. Webhook sai chữ ký ghi một dòng `REJECTED` với `signatureVerified=false`.

### 3. Quy tắc xử lý webhook (thay `UNIQUE(provider, event_fingerprint)`)
1. Tìm đơn: `attempts.findFirstByProviderOrderCode(orderCode)`. Không có: ghi `REJECTED` / `UNKNOWN_ORDER_CODE` rồi kết thúc.
2. Có đơn: gọi `settlement.lockInvoiceFresh(invoiceId, payment)` (đã có: khoá hoá đơn rồi làm mới attempt). **Chỉ sau bước này** mới kiểm replay. Webhook trùng chạy song song sẽ bị khoá chờ, rồi đọc được kết quả đã commit của lần đầu.
3. Replay: `fingerprint.equals(payment.getLastEventFingerprint())` thì return.
4. Còn lại: giữ các nhánh cũ. Sự kiện khớp đơn, trừ `DUPLICATE`, gọi `recordVerifiedEvent(fingerprint)`.
5. `CONFIRMED` là trạng thái cuối đối với webhook (đã có, outcome `DUPLICATE`). Không thêm quy tắc mới.

### 4. Java: trước → sau
**`PaymentServiceImpl`**: tham số thứ 3 của constructor, `PaymentGatewayEventRepository events`, đổi thành `AuditLogWriter audit` (cùng vị trí).

**`PaymentAttemptJpaEntity`** thêm:
```java
@Column(name = "last_event_fingerprint", length = 64) private String lastEventFingerprint;
@Column(name = "signature_verified") private Boolean signatureVerified;
public void recordVerifiedEvent(String fingerprint) { this.lastEventFingerprint = fingerprint; this.signatureVerified = Boolean.TRUE; }
public String getLastEventFingerprint() { return lastEventFingerprint; }
```

**`handleVerifiedGatewayEvent`**: bỏ `try/catch (DataIntegrityViolationException)` và lệnh `events.existsBy…`. Giữ công thức fingerprint và `transactions.executeWithoutResult(status -> applyGatewayEvent(event, fingerprint))`. Lý do: không còn UNIQUE trên sự kiện, trùng đồng thời đã do khoá hoá đơn xử lý.

**`applyGatewayEvent`** (thay toàn bộ thân hàm, hiện ở dòng 209-243):
```java
private void applyGatewayEvent(PaymentGateway.VerifiedGatewayEvent event, String fingerprint) {
    PaymentAttemptJpaEntity payment = attempts.findFirstByProviderOrderCode(event.orderCode()).orElse(null);
    if (payment == null) {
        writeWebhookAudit(event.provider(), fingerprint, event.orderCode(), event.transactionReference(),
                true, null, "REJECTED", "UNKNOWN_ORDER_CODE");
        return;
    }
    InvoiceJpaEntity invoice = settlement.lockInvoiceFresh(payment.getInvoiceId(), payment).orElse(null);
    if (fingerprint.equals(payment.getLastEventFingerprint())) return; // replay: bỏ qua, không ghi gì
    String outcome = "REJECTED";
    String detail = "ATTEMPT_OR_AMOUNT_MISMATCH";
    if (event.successful() && matches(payment, event)) {
        if (invoice == null) {
            detail = "INVOICE_NOT_FOUND";
        } else if (payment.getStatus() == PaymentAttemptStatus.CONFIRMED) {
            outcome = "DUPLICATE"; detail = null;
        } else if (invoice.getStatus() == InvoiceStatus.VOID) {
            settlement.holdForReconciliation(payment, PAID_AFTER_VOID, event.transactionReference());
            outcome = "RECONCILIATION_REQUIRED"; detail = "INVOICE_VOID";
        } else if (settlement.outstanding(payment.getInvoiceId()).compareTo(payment.getAmount()) != 0) {
            payment.requireReconciliation("Provider reports payment but invoice balance changed", event.transactionReference());
            outcome = "RECONCILIATION_REQUIRED"; detail = "OUTSTANDING_BALANCE_CHANGED";
        } else {
            payment.confirm(event.transactionReference(), Instant.now(clock));
            boolean applied = settlement.settleInvoice(invoice, payment);
            outcome = applied ? "APPLIED" : "RECONCILIATION_REQUIRED";
            detail = applied ? null : "APPOINTMENT_HOLD_RELEASED";
        }
    } else if (!event.successful() && payment.getStatus() == PaymentAttemptStatus.PENDING) {
        payment.fail(PaymentAttemptStatus.FAILED, "PayOS reported an unsuccessful payment");
        outcome = "APPLIED"; detail = "UNSUCCESSFUL_PROVIDER_PAYMENT";
    }
    if (!"DUPLICATE".equals(outcome)) payment.recordVerifiedEvent(fingerprint);
    writeWebhookAudit(event.provider(), fingerprint, event.orderCode(), event.transactionReference(),
            true, payment.getId(), outcome, detail);
}
```
Khác với code cũ: lệnh `lockInvoiceFresh` trong nhánh cũ đã chuyển lên đầu hàm (không gọi lại); mọi sự kiện khớp đơn đều khoá, kể cả sự kiện thất bại.

**`recordRejectedGatewayEvent`** (hiện ở dòng 245-254):
```java
@Override
public void recordRejectedGatewayEvent(String provider, Object payload) {
    String fingerprint = hash(provider + "|UNVERIFIED|" + String.valueOf(payload));
    transactions.executeWithoutResult(status -> writeWebhookAudit(provider, fingerprint, null, null,
            false, null, "REJECTED", "INVALID_SIGNATURE_OR_PAYLOAD"));
}
```
Không còn kiểm tồn tại theo fingerprint (xem Risk).

**`writeWebhookAudit`** (mới, private):
```java
private void writeWebhookAudit(String provider, String fingerprint, Long orderCode, String reference,
        boolean signatureVerified, UUID attemptId, String outcome, String detail) {
    Map<String, Object> metadata = new HashMap<>(); // HashMap: giá trị có thể null, Map.of không nhận
    metadata.put("provider", provider);
    metadata.put("fingerprint", fingerprint);
    metadata.put("orderCode", orderCode);
    metadata.put("transactionReference", reference);
    metadata.put("signatureVerified", signatureVerified);
    metadata.put("detail", detail);
    audit.record(AuditEntry.builder("payment_gateway_events", "PAYMENT_GATEWAY", outcome, "PAYMENT_WEBHOOK")
            .entityId(attemptId).actorType("PAYOS").outcome(outcome).visibleInActivityLog(false)
            .metadata(metadata).build());
}
```

### 5. Migration V119 (theo thứ tự)
```sql
-- V119__final_db_billing_payos.sql (phase 08). Yêu cầu: V109 đã chạy (audit_logs).
-- 1. Cột chống trùng webhook. UNIQUE(provider, provider_order_code) đã có: uq_payment_provider_order.
ALTER TABLE payment_attempts
    ADD COLUMN last_event_fingerprint varchar(64),
    ADD COLUMN signature_verified boolean,
    ADD CONSTRAINT ck_payment_attempt_event_fingerprint
        CHECK (last_event_fingerprint IS NULL OR last_event_fingerprint ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_payment_attempt_event_pair
        CHECK ((last_event_fingerprint IS NULL) = (signature_verified IS NULL));

-- 2. Chép nhật ký cũ sang audit_logs (dữ liệu demo; entity_id nối qua provider_order_code)
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id,
                        visible_in_activity_log, actor_type, metadata, outcome)
SELECT e.id, e.received_at, 'payment_gateway_events', 'PAYMENT_GATEWAY', e.outcome, 'PAYMENT_WEBHOOK', pa.id,
       false, 'PAYOS',
       jsonb_build_object('provider', e.provider, 'fingerprint', e.event_fingerprint,
                          'orderCode', e.provider_order_code,
                          'transactionReference', e.provider_transaction_reference,
                          'signatureVerified', e.signature_verified, 'detail', e.outcome_detail),
       e.outcome
FROM payment_gateway_events e
LEFT JOIN payment_attempts pa ON pa.provider = e.provider AND pa.provider_order_code = e.provider_order_code;

-- 3. Bỏ bảng cũ. Đã kiểm trên V108: không trigger, không hàm nào, không FK nào tham chiếu.
DROP TABLE payment_gateway_events;
```
(13/11: V119 CÓ backfill `last_event_fingerprint` từ sự kiện đã xác thực mới nhất, bỏ DUPLICATE — giữ đúng hành vi chống trùng cũ cho dữ liệu demo. Câu cũ bên dưới không còn áp dụng.) Không backfill `last_event_fingerprint` cho dữ liệu cũ: replay sau migration vẫn không đổi trạng thái vì `CONFIRMED` là trạng thái cuối.

## Related Code Files
Đường dẫn Java tương đối với `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/`. Test tương đối với `backend/src/test/java/fu/fa26/sep490/g58/otcms_be/`. Migration tương đối với `backend/src/main/resources/`.
- Modify:
  - `billing/model/entity/PaymentAttemptJpaEntity.java`: thêm 2 cột, `recordVerifiedEvent`, getter.
  - `billing/service/impl/PaymentServiceImpl.java`: constructor, `handleVerifiedGatewayEvent`, `applyGatewayEvent`, `recordRejectedGatewayEvent`, thêm `writeWebhookAudit`. Bỏ import `PaymentGatewayEventJpaEntity`, `PaymentGatewayEventRepository`, `DataIntegrityViolationException`; thêm `common.audit.AuditEntry`, `common.audit.AuditLogWriter`, `java.util.HashMap`, `java.util.Map`.
- Delete:
  - `billing/model/entity/PaymentGatewayEventJpaEntity.java`
  - `billing/repository/PaymentGatewayEventRepository.java`
- Move: `db/migration-staged/V119__final_db_billing_payos.sql` → `db/migration/` (file staged do NghiaDT tạo ở phase 00).
- Modify (test):
  - `billing/ClinicalRoundBillingTest.java`: L42 `events` → `audit = mock(AuditLogWriter.class)`; L69 và L152 truyền `audit` thay `events`.
  - `billing/HoldBillingTestSupport.java`: L107-108 bỏ lệnh xoá `payment_gateway_events`. L180-183 `eventOutcomes(long orderCode)` đọc `audit_logs` với `source = 'payment_gateway_events' AND metadata->>'orderCode' = ?` (tham số `String.valueOf(orderCode)`), trả `action || ':' || coalesce(detail, '')` đã sắp theo chữ cái. Thêm `postRawWebhook(String body)`.
  - `billing/AppointmentHoldPaymentFlowPostgresTest.java`: L259-266 đổi mã `987_654_321L` thành mã đơn ngẫu nhiên, bỏ lệnh xoá ở L266. Thêm `rejectedWebhookIsAuditedAndNeverApplied()`.
  - `billing/AppointmentHoldSettlementPostgresTest.java`: thêm `identicalWebhookDeliveriesRacingSettleOnce()` (`@RepeatedTest(8)`, dùng `race()` sẵn có).
  - `appointment/PaymentHoldFixtureSupport.java`: L89-90 bỏ lệnh xoá `payment_gateway_events`. L96 (`appointment_audits`) là việc của phase 04.

## Implementation Steps
1. Tạo nhánh `final-db-p08-billing-payos` từ `final-db` mới nhất (sau khi phase 01 đã merge).
2. `docker compose -f docker-compose.final-db.yml down -v && docker compose -f docker-compose.final-db.yml up -d`. Chạy app profile `final-db` để xác nhận V001–V109 chạy được.
3. `git mv` V119 từ `migration-staged/` sang `db/migration/`. So nội dung với mục 5. Nếu staged khác thì sửa staged trong PR này và ghi "Đổi migration" (agent-rules §5).
4. Sửa `PaymentAttemptJpaEntity` (mục 4).
5. Sửa `PaymentServiceImpl` theo mục 4. Không đổi interface `PaymentService` và `PaymentWebhookController`.
6. Xoá hai file `PaymentGatewayEvent*`.
7. Sửa test theo "Modify (test)", thêm hai test mới (xem Success Criteria).
8. Chạy grep và truy vấn `pg_proc` trong Success Criteria. Ghi kết quả vào PR.
9. `./mvnw test` trong `otcms/backend`, env theo `otcms/AGENTS.md`, với `APP_PAYMENT_PAYOS_ENABLED` khác `true`. Ghi số chạy và số lỗi; lỗi có sẵn theo AGENTS.md.
10. Chạy app `final-db`, `curl -s http://localhost:8080/v3/api-docs > /tmp/api-after.json`, rồi `node plans/261113-final-db/tools/compare-openapi.mjs plans/261113-final-db/api-baseline.json /tmp/api-after.json`.
11. Mở PR theo mẫu agent-rules §8.

## Success Criteria
- [ ] `grep -rn "payment_gateway_events\|PaymentGatewayEvent\|existsByProviderAndEventFingerprint" backend/src/main/java backend/src/test` → rỗng.
- [ ] Trên DB sau V119: `SELECT proname FROM pg_proc WHERE prosrc ILIKE '%payment_gateway_events%';` → 0 dòng; `\d payment_gateway_events` → không tìm thấy.
- [ ] `\d payment_attempts` có 2 cột mới và 2 CHECK mới. `INSERT` có `last_event_fingerprint` mà `signature_verified` NULL bị từ chối.
- [ ] `webhookPaidConfirmsAppointmentInTheSameTransactionAndDuplicateDoesNotConfirmAgain` pass: nhật ký là `APPLIED:` và `DUPLICATE:`.
- [ ] `successfulGatewaySettlementMarksRoundPaidAndRepeatedEventDoesNotRepeatSettlement` (unit, `ClinicalRoundBillingTest`) pass.
- [ ] `identicalWebhookDeliveriesRacingSettleOnce` pass 8/8: đúng 1 dòng `APPLIED` cho attempt, `confirmedTransitions == 1`, không ngoại lệ.
- [ ] `rejectedWebhookIsAuditedAndNeverApplied` pass: body `{}` → HTTP 400 `INVALID_WEBHOOK`; số dòng `REJECTED` có `signatureVerified=false` tăng đúng 1 (đếm trước và sau); attempt vẫn `PENDING`.
- [ ] Test race `AppointmentHoldSettlementPostgresTest` và `AppointmentHoldPaymentFlowPostgresTest` pass; không đổi assert ngoài phần đã nêu.
- [ ] `./mvnw test`: chỉ còn lỗi có sẵn. Ghi số chạy và số lỗi vào PR.
- [ ] So OpenAPI: no differences.
- [ ] File ngoài danh sách: không. Nếu có `PaymentHoldFixtureSupport.java` (dùng chung với phase 04) thì ghi rõ trong PR.

## Risk Assessment
- **Đối soát giảm:** attempt chỉ nhớ fingerprint của sự kiện gần nhất. Lịch sử đầy đủ nằm ở `audit_logs`, mỗi sự kiện một dòng, trừ replay.
- **Từ chối không còn chống trùng:** endpoint công khai; mỗi payload sai chữ ký sinh một dòng. Tương tự với sự kiện không khớp đơn: không có attempt để giữ fingerprint, nên replay ghi thêm dòng `REJECTED`. Xem Câu hỏi 2.
- **Khoá hoá đơn áp cho cả sự kiện thất bại:** nhánh `FAILED` trước đây không khoá. Thứ tự khoá vẫn là hoá đơn trước, lịch hẹn sau.
- **Test không xoá được nhật ký:** trigger phase 01 chặn DELETE trên `audit_logs`. Test lọc theo mã đơn ngẫu nhiên hoặc `entity_id`. DB test phải reset trước mỗi lần chạy.
- **Đồng hồ cố định:** `MutableClock` trong test hold có thể làm nhiều dòng trùng `occurred_at`. Vì vậy helper trả danh sách đã sắp theo chữ cái, không theo thời gian.
- **Phụ thuộc phase 01:** thiếu `audit_logs`, `AuditLogWriter` hoặc view mới thì V119 và test lỗi ngay.
- **Va chạm file:** `PaymentHoldFixtureSupport.java` do phase 04 cũng sửa (dòng `appointment_audits`). Ai merge sau thì rebase, chỉ giữ dòng của mình.
- **Sau migration:** attempt đã `CONFIRMED` không có fingerprint. Replay của sự kiện cũ vẫn bị bỏ vì `CONFIRMED` là trạng thái cuối, nhưng có thể sinh một dòng `DUPLICATE`. Chấp nhận (dữ liệu demo).

## Câu hỏi còn mở

> **ĐÃ CHỐT 13/11:** Q1 có, ghi `APPLIED`/`DUPLICATE`/`RECONCILIATION_REQUIRED` vào `audit_logs` với `visible = false`. Q2 chấp nhận cho demo, không thêm rate-limit. Q3 đã có: Builder setter cùng tên thành phần + `occurredAt` (phase 01). Q4 giữ `signature_verified` theo mặc định. Q5 theo phase 01. Q6 đúng: dải số migration của đợt refactor là ngoại lệ của AGENTS.md.

Các câu bên dưới giữ lại để tra lý do.
1. Ghi `APPLIED`, `DUPLICATE`, `RECONCILIATION_REQUIRED` vào `audit_logs` (mặc định trong phase này: có, không hiện trong nhật ký). Xác nhận với NghiaDT vì số dòng tăng theo số giao dịch.
2. Từ chối không còn chống trùng: chấp nhận cho demo, hay thêm giới hạn tần suất theo IP?
3. `AuditEntry` (phase 01) chưa liệt kê tên method của Builder và chưa có `occurredAt` (xem phase 09 Câu hỏi 2). Phase này giả định `builder(...).entityId().actorType().outcome().visibleInActivityLog().metadata().build()`. Phải khớp với phase 01 trước khi làm.
4. `signature_verified` luôn TRUE khi có giá trị. Giữ cột theo `schema-33` mục 2.9 (mặc định) hay bỏ?
5. `schema-33` mục 2.11 ghi `entity_type`, `duration_ms`; phase 01 ghi `record_type`, `source`, `visible_in_activity_log`. Phase này theo phase 01.
6. `AGENTS.md` giao Hùng dải migration V044–V069; plan giao V119. Xác nhận đây là ngoại lệ của đợt refactor.
