import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

import {
  clearPayOsIdempotencyKey,
  clearPayOsIdempotencyKeyForAppointment,
  createAppointmentInvoice,
  createPayOsAttempt,
  INVOICE_NOT_PAYABLE_MESSAGE,
  InvoiceNotPayableError,
  payOsIdempotencyKey,
} from "./billingApi";

beforeEach(() => {
  request.mockReset();
  window.sessionStorage.clear();
});

describe("createPayOsAttempt", () => {
  it("INVOICE_NOT_PAYABLE → InvoiceNotPayableError với thông điệp rõ", async () => {
    request.mockRejectedValue(
      new ApiError(409, { errorCode: "INVOICE_NOT_PAYABLE" }),
    );
    const failure = await createPayOsAttempt("inv-1", "key").catch(
      (cause: unknown) => cause,
    );
    expect(failure).toBeInstanceOf(InvoiceNotPayableError);
    expect((failure as Error).message).toBe(INVOICE_NOT_PAYABLE_MESSAGE);
  });

  it("lỗi khác giữ nguyên, không bị đổi thành hết hạn", async () => {
    const original = new ApiError(500, { errorCode: "PAYMENT_GATEWAY_ERROR" });
    request.mockRejectedValue(original);
    await expect(createPayOsAttempt("inv-1", "key")).rejects.toBe(original);
  });

  it("409 IDEMPOTENCY_CONFLICT không bị coi là hết hạn", async () => {
    const original = new ApiError(409, { errorCode: "IDEMPOTENCY_CONFLICT" });
    request.mockRejectedValue(original);
    await expect(createPayOsAttempt("inv-1", "key")).rejects.toBe(original);
  });
});

describe("createAppointmentInvoice", () => {
  it("SOURCE_NOT_BILLABLE / INVOICE_NOT_PAYABLE → InvoiceNotPayableError", async () => {
    for (const errorCode of ["SOURCE_NOT_BILLABLE", "INVOICE_NOT_PAYABLE"]) {
      request.mockRejectedValueOnce(new ApiError(409, { errorCode }));
      await expect(createAppointmentInvoice("apt-1")).rejects.toBeInstanceOf(
        InvoiceNotPayableError,
      );
    }
  });

  it("trả hóa đơn VOID nguyên trạng để màn hình tự xử lý", async () => {
    request.mockResolvedValue({ id: "inv-1", status: "VOID" });
    await expect(createAppointmentInvoice("apt-1")).resolves.toMatchObject({
      status: "VOID",
    });
  });
});

describe("khóa idempotency PayOS", () => {
  it("dùng lại khóa cũ, đổi khóa khi renew", () => {
    const first = payOsIdempotencyKey("inv-1");
    expect(payOsIdempotencyKey("inv-1")).toBe(first);
    expect(payOsIdempotencyKey("inv-1", true)).not.toBe(first);
  });

  it("clearPayOsIdempotencyKey xóa khóa của đúng hóa đơn", () => {
    const keep = payOsIdempotencyKey("inv-2");
    const removed = payOsIdempotencyKey("inv-1");
    clearPayOsIdempotencyKey("inv-1");
    expect(payOsIdempotencyKey("inv-2")).toBe(keep);
    expect(payOsIdempotencyKey("inv-1")).not.toBe(removed);
  });

  it("clearPayOsIdempotencyKeyForAppointment xóa khóa của hóa đơn thuộc lịch", async () => {
    request.mockResolvedValue({ id: "inv-9", status: "OPEN" });
    await createAppointmentInvoice("apt-9");
    const before = payOsIdempotencyKey("inv-9");
    clearPayOsIdempotencyKeyForAppointment("apt-9");
    expect(
      window.sessionStorage.getItem("otcms:payos:idempotency:inv-9"),
    ).toBeNull();
    expect(payOsIdempotencyKey("inv-9")).not.toBe(before);
  });

  it("clearPayOsIdempotencyKeyForAppointment an toàn khi chưa có hóa đơn nào", () => {
    expect(() =>
      clearPayOsIdempotencyKeyForAppointment("apt-unknown"),
    ).not.toThrow();
  });
});
