import { render, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import {
  fetchCurrentAccount,
  peekCurrentAccount,
  type CurrentAccount,
} from "@/lib/auth/currentAccountApi";
import {
  HOLD_ALREADY_PAID_TEXT,
  HOLD_CANCELLED_ON_LEAVE_TEXT,
} from "../../schema/paymentHoldMessages";

const pathname = vi.hoisted(() => ({ current: "/portal/appointments/new" }));
vi.mock("next/navigation", () => ({ usePathname: () => pathname.current }));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

// Ranh giới ngoài: `GET /api/v1/me`. Mặc định là bệnh nhân đã đăng nhập.
const patient: CurrentAccount = {
  id: "11111111-1111-1111-1111-111111111111",
  accountType: "PATIENT",
  email: "an@example.com",
  displayName: "Trần Minh Khoa",
};
vi.mock("@/lib/auth/currentAccountApi", () => ({
  fetchCurrentAccount: vi.fn(async () => patient),
  peekCurrentAccount: vi.fn(() => patient),
}));

// Ranh giới ngoài: lớp API (trong test là kho giả); bọc vi.fn để đếm lời gọi và ép kết quả.
vi.mock("../../api/patientAppointmentApi", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("../../api/patientAppointmentApi")>();
  return {
    ...original,
    cancelPendingHold: vi.fn(async () => ({
      cancelledCount: 0,
      alreadyPaid: false,
    })),
  };
});

import { cancelPendingHold } from "../../api/patientAppointmentApi";
import { PendingHoldAutoCancel } from "./PendingHoldAutoCancel";

beforeEach(() => {
  vi.clearAllMocks();
  pathname.current = "/portal/appointments/new";
  window.sessionStorage.clear();
  vi.mocked(peekCurrentAccount).mockReturnValue(patient);
  vi.mocked(fetchCurrentAccount).mockResolvedValue(patient);
});

describe("PendingHoldAutoCancel (rời trang = hủy thanh toán)", () => {
  it("nạp layout: gọi hủy giữ chỗ đúng một lần, kể cả trong StrictMode và khi render lại", async () => {
    const { rerender } = render(
      <StrictMode>
        <PendingHoldAutoCancel />
      </StrictMode>,
    );
    await waitFor(() => expect(cancelPendingHold).toHaveBeenCalledTimes(1));

    pathname.current = "/portal/appointments";
    rerender(
      <StrictMode>
        <PendingHoldAutoCancel />
      </StrictMode>,
    );
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(cancelPendingHold).toHaveBeenCalledTimes(1);
  });

  it("hủy được giữ chỗ: toast cảnh báo rời trang thanh toán và xóa khóa idempotency PayOS", async () => {
    window.sessionStorage.setItem("otcms:payos:idempotency:inv-1", "key-1");
    window.sessionStorage.setItem("khac", "giu-nguyen");
    vi.mocked(cancelPendingHold).mockResolvedValueOnce({
      cancelledCount: 1,
      alreadyPaid: false,
    });

    render(<PendingHoldAutoCancel />);

    await waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith(HOLD_CANCELLED_ON_LEAVE_TEXT),
    );
    expect(window.sessionStorage.getItem("otcms:payos:idempotency:inv-1")).toBe(
      null,
    );
    expect(window.sessionStorage.getItem("khac")).toBe("giu-nguyen");
  });

  it("alreadyPaid: toast đã thanh toán, không báo hủy", async () => {
    vi.mocked(cancelPendingHold).mockResolvedValueOnce({
      cancelledCount: 0,
      alreadyPaid: true,
    });

    render(<PendingHoldAutoCancel />);

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(HOLD_ALREADY_PAID_TEXT),
    );
    expect(toast.warning).not.toHaveBeenCalled();
  });

  it("không có giữ chỗ nào (cancelledCount 0): không toast", async () => {
    render(<PendingHoldAutoCancel />);

    await waitFor(() => expect(cancelPendingHold).toHaveBeenCalledTimes(1));
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(toast.warning).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("trang kết quả PayOS trả về: không hủy", async () => {
    pathname.current = "/portal/payments/result";

    render(<PendingHoldAutoCancel />);

    await new Promise((resolve) => setTimeout(resolve, 30));
    expect(cancelPendingHold).not.toHaveBeenCalled();
  });

  it("vào bằng trang kết quả rồi đi trang khác trong app: vẫn không hủy (chỉ xét lúc nạp layout)", async () => {
    pathname.current = "/portal/payments/result";
    const { rerender } = render(<PendingHoldAutoCancel />);
    await new Promise((resolve) => setTimeout(resolve, 20));

    pathname.current = "/portal/appointments";
    rerender(<PendingHoldAutoCancel />);
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(cancelPendingHold).not.toHaveBeenCalled();
  });

  it("khách chưa đăng nhập: không gọi API", async () => {
    vi.mocked(peekCurrentAccount).mockReturnValue(null);
    vi.mocked(fetchCurrentAccount).mockResolvedValue(null);

    render(<PendingHoldAutoCancel />);

    await new Promise((resolve) => setTimeout(resolve, 30));
    expect(cancelPendingHold).not.toHaveBeenCalled();
  });

  it("API lỗi: không ném, không toast lỗi", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    vi.mocked(cancelPendingHold).mockRejectedValueOnce(new Error("mất mạng"));

    render(<PendingHoldAutoCancel />);

    await waitFor(() => expect(warn).toHaveBeenCalled());
    expect(toast.error).not.toHaveBeenCalled();
    warn.mockRestore();
  });
});
