import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, vi } from "vitest";
import { toast } from "sonner";
import { Toaster } from "@/components/ui/sonner";
import { resetReceptionMockStore } from "../api/receptionMockStore";
import type { CheckInTarget } from "../types/checkIn";
import { CheckInPanel } from "./CheckInPanel";

// Tiện ích dùng chung cho test của panel tiếp nhận: đồng hồ giả, dựng panel, và vòng đời test.

/** Đồng hồ giả: chỉ các timer và Date mà hook theo dõi VNPay dùng. */
export const FAKE_TIMERS: Parameters<typeof vi.useFakeTimers>[0] = { toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] };

/** Mỗi test bắt đầu từ kho giả sạch; sau test thì trả đồng hồ thật và xóa thông báo của sonner. */
export function registerPanelTestLifecycle() {
  beforeEach(() => resetReceptionMockStore());
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    // Thông báo của sonner nằm trong kho chung của module: xóa để test sau không thấy lại.
    toast.dismiss();
  });
}

export function renderPanel(target: CheckInTarget) {
  const handlers = { onInvalidTarget: vi.fn(), onCheckedIn: vi.fn(), onNext: vi.fn() };
  render(
    <>
      <CheckInPanel target={target} {...handlers} />
      <Toaster />
    </>,
  );
  return handlers;
}

export const appointmentTarget = (id: string): CheckInTarget => ({ kind: "appointment", id });
export const patientTarget = (id: string): CheckInTarget => ({ kind: "patient", id });
export const tickPresent = async (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole("checkbox", { name: /Đã kiểm tra đúng người/ }));
export const checkInButton = () => screen.queryByRole("button", { name: "Tiếp nhận" });

/** Chạy đồng hồ giả và để các promise của lớp API giả được xử lý. */
export async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** Để chuỗi promise của lớp API giả và cập nhật state chạy hết (không dùng timer). */
export async function flush() {
  await act(async () => {
    for (let tick = 0; tick < 20; tick += 1) await Promise.resolve();
  });
}
