import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { collectExamFee } from "../api/checkInApi";
import {
  findPayment,
  forceNextVnpayFailure,
  resetReceptionMockStore,
} from "../api/receptionMockStore";
import type { ExamFeePayment } from "../types/checkIn";
import { useExamFeePayment } from "./useExamFeePayment";

// Hỏi trạng thái VNPay mỗi 3 giây với đồng hồ giả. Tạo khoản bằng timer thật trước (lớp API giả
// dùng setTimeout), rồi mới bật đồng hồ giả cho phần theo dõi.

const FAKE_TIMERS: Parameters<typeof vi.useFakeTimers>[0] = { toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] };

beforeEach(() => resetReceptionMockStore());
afterEach(() => vi.useRealTimers());

async function createVnpayPayment() {
  const payment = await collectExamFee({
    patientId: "pat-an",
    appointmentId: "apt-1001",
    serviceId: null,
    method: "VNPAY",
  });
  if (!payment.vnpay) throw new Error("Khoản VNPay phải có QR.");
  return { payment, expiresAt: payment.vnpay.expiresAt };
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe("useExamFeePayment", () => {
  it("hỏi mỗi 3 giây và báo onSuccess một lần khi BE xác nhận (lần hỏi thứ ba)", async () => {
    const { payment, expiresAt } = await createVnpayPayment();
    vi.useFakeTimers(FAKE_TIMERS);
    const onSuccess = vi.fn<(paid: ExamFeePayment) => void>();
    const onFailed = vi.fn();
    renderHook(() => useExamFeePayment({ id: payment.id, expiresAt }, { onSuccess, onFailed }));

    await advance(2999);
    expect(findPayment(payment.id)?.polls).toBe(0);
    await advance(1);
    expect(findPayment(payment.id)?.polls).toBe(1);
    await advance(6000);
    expect(onSuccess).toHaveBeenCalledTimes(1);
    expect(onSuccess.mock.calls[0][0]).toMatchObject({ id: payment.id, status: "SUCCESS" });
    expect(onFailed).not.toHaveBeenCalled();
  });

  it("BE báo FAILED thì onFailed nhận khoản đó và dừng hỏi", async () => {
    forceNextVnpayFailure();
    const { payment, expiresAt } = await createVnpayPayment();
    vi.useFakeTimers(FAKE_TIMERS);
    const onSuccess = vi.fn();
    const onFailed = vi.fn<(failed: ExamFeePayment | null) => void>();
    renderHook(() => useExamFeePayment({ id: payment.id, expiresAt }, { onSuccess, onFailed }));

    await advance(3000);
    expect(onFailed).toHaveBeenCalledTimes(1);
    expect(onFailed.mock.calls[0][0]).toMatchObject({ status: "FAILED" });
    await advance(30_000);
    expect(onFailed).toHaveBeenCalledTimes(1);
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("hết hạn phía FE thì onFailed(null) đúng lúc hết hạn và dừng hỏi", async () => {
    const { payment } = await createVnpayPayment();
    vi.useFakeTimers(FAKE_TIMERS);
    const expiresAt = new Date(Date.now() + 5000).toISOString();
    const onFailed = vi.fn<(failed: ExamFeePayment | null) => void>();
    renderHook(() =>
      useExamFeePayment({ id: payment.id, expiresAt }, { onSuccess: vi.fn(), onFailed }),
    );

    await advance(4000);
    expect(onFailed).not.toHaveBeenCalled();
    await advance(1000);
    expect(onFailed).toHaveBeenCalledWith(null);
    const pollsAtExpiry = findPayment(payment.id)?.polls;
    await advance(30_000);
    expect(findPayment(payment.id)?.polls).toBe(pollsAtExpiry);
    expect(onFailed).toHaveBeenCalledTimes(1);
  });

  it("đếm ngược theo hạn và giảm theo từng giây", async () => {
    vi.useFakeTimers(FAKE_TIMERS);
    const expiresAt = new Date(Date.now() + 872_000).toISOString();
    const { result } = renderHook(() =>
      useExamFeePayment({ id: "pay-khong-co", expiresAt }, { onSuccess: vi.fn(), onFailed: vi.fn() }),
    );
    expect(result.current.secondsLeft).toBe(872);
    await advance(10_000);
    expect(result.current.secondsLeft).toBe(862);
  });

  it("lỗi khi hỏi không dừng theo dõi mà báo pollFailed; không có khoản thì không hỏi", async () => {
    vi.useFakeTimers(FAKE_TIMERS);
    const expiresAt = new Date(Date.now() + 600_000).toISOString();
    const { result } = renderHook(() =>
      useExamFeePayment({ id: "pay-khong-co", expiresAt }, { onSuccess: vi.fn(), onFailed: vi.fn() }),
    );
    await advance(3000);
    expect(result.current.pollFailed).toBe(true);

    const idle = renderHook(() => useExamFeePayment(null, { onSuccess: vi.fn(), onFailed: vi.fn() }));
    expect(idle.result.current).toEqual({ secondsLeft: null, pollFailed: false });
  });

  it("unmount thì dừng hỏi ngay: không còn lần hỏi nào sau khi rời màn", async () => {
    const { payment, expiresAt } = await createVnpayPayment();
    vi.useFakeTimers(FAKE_TIMERS);
    const { unmount } = renderHook(() =>
      useExamFeePayment({ id: payment.id, expiresAt }, { onSuccess: vi.fn(), onFailed: vi.fn() }),
    );
    await advance(3000);
    expect(findPayment(payment.id)?.polls).toBe(1);
    unmount();
    await advance(30_000);
    expect(findPayment(payment.id)?.polls).toBe(1);
  });
});
