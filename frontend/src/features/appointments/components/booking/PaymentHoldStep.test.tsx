import {
  act,
  cleanup,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Link from "next/link";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import {
  HOLD_ALREADY_PAID_TEXT,
  HOLD_CANCELLED_BY_USER_TEXT,
  HOLD_CANCELLED_ELSEWHERE_TEXT,
  HOLD_CANCELLED_ON_LEAVE_TEXT,
  HOLD_LEAVE_DIALOG,
} from "../../schema/paymentHoldMessages";
import type {
  BookedAppointment,
  PaymentStepArgs,
} from "../../types/booking";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

// Ranh giới ngoài: phiên đăng nhập (token + CSRF) dùng cho yêu cầu hủy khi đóng tab.
vi.mock("@/lib/auth/authSession", () => ({
  getAccessToken: vi.fn(async () => "access-token"),
  getCsrfToken: vi.fn(async () => "csrf-token"),
  peekSession: vi.fn(() => ({ accessToken: "access-token" })),
  peekCsrfToken: vi.fn(() => "csrf-token"),
}));

// Ranh giới ngoài: lớp API (trong test là kho giả); bọc vi.fn để đếm lời gọi và ép kết quả.
vi.mock("../../api/patientAppointmentApi", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("../../api/patientAppointmentApi")>();
  return {
    ...original,
    getMyAppointment: vi.fn(original.getMyAppointment),
    cancelPendingHold: vi.fn(async () => ({
      cancelledCount: 1,
      alreadyPaid: false,
    })),
  };
});

import {
  cancelPendingHold,
  getMyAppointment,
} from "../../api/patientAppointmentApi";
import { PaymentHoldStep } from "./PaymentHoldStep";

const KEY = "otcms:payos:idempotency:inv-1";

function hold(overrides: Partial<BookedAppointment> = {}): BookedAppointment {
  return {
    id: "apt-gone", // không có trong kho giả: dò trạng thái sẽ gặp 404 (đã bị hủy)
    startAt: "2099-01-01T10:00:00+07:00",
    patient: { id: "pat-1", fullName: "Nguyễn Văn An" },
    service: { id: "svc-1", name: "Khám cột sống" },
    doctor: { id: "doc-1", fullName: "BS. Hà" },
    roomName: "Phòng 1",
    status: "AWAITING_PAYMENT",
    paymentDeadline: "2099-01-01T09:00:00+07:00",
    examFee: { amount: 250000, status: "PENDING_PAYMENT" },
    isFollowUp: false,
    ...overrides,
  };
}

/** Màn thanh toán giả: lộ ba callback bước 5 cấp cho màn thanh toán thật. */
function fakePayment(args: PaymentStepArgs) {
  return (
    <div>
      <p>deadline: {args.paymentDeadline ?? "none"}</p>
      <Link href="/portal/profiles">Hồ sơ</Link>
      <button onClick={() => void args.onCancelHold()} type="button">
        Hủy giữ chỗ
      </button>
      <button onClick={args.onBookAgain} type="button">
        Đặt lịch lại
      </button>
      <button onClick={() => args.onFinish({ paid: true })} type="button">
        Đã trả xong
      </button>
    </div>
  );
}

function renderStep(
  props: {
    appointment?: BookedAppointment;
    strict?: boolean;
  } = {},
) {
  const onFinish = vi.fn();
  const onReleased = vi.fn();
  const element = (
    <PaymentHoldStep
      appointment={props.appointment ?? hold()}
      onFinish={onFinish}
      onReleased={onReleased}
      renderPayment={fakePayment}
    />
  );
  const view = render(props.strict ? <StrictMode>{element}</StrictMode> : element);
  return { ...view, onFinish, onReleased };
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(cancelPendingHold).mockResolvedValue({
    cancelledCount: 1,
    alreadyPaid: false,
  });
  window.sessionStorage.clear();
});

// Gỡ component còn giữ chỗ sẽ hẹn một lệnh hủy (rời trang = hủy): chờ nó chạy hết trong test này để
// không rơi sang test sau.
afterEach(async () => {
  vi.useRealTimers();
  cleanup();
  await wait(15);
});

describe("PaymentHoldStep: hủy chủ động", () => {
  it("truyền hạn giữ giờ cho màn thanh toán", () => {
    renderStep();
    expect(screen.getByText(/deadline: 2099-01-01T09:00:00\+07:00/)).toBeInTheDocument();
  });

  it("onCancelHold: gọi hủy giữ chỗ, xóa khóa idempotency, báo và về bước 1", async () => {
    window.sessionStorage.setItem(KEY, "k");
    const user = userEvent.setup();
    const { onReleased, onFinish } = renderStep();

    await user.click(screen.getByRole("button", { name: "Hủy giữ chỗ" }));

    await waitFor(() => expect(onReleased).toHaveBeenCalledTimes(1));
    expect(cancelPendingHold).toHaveBeenCalledTimes(1);
    expect(toast.success).toHaveBeenCalledWith(HOLD_CANCELLED_BY_USER_TEXT);
    expect(window.sessionStorage.getItem(KEY)).toBeNull();
    expect(onFinish).not.toHaveBeenCalled();
  });

  it("onCancelHold gặp ca đã thanh toán: báo đã thanh toán và kết thúc như đã trả, không reset", async () => {
    vi.mocked(cancelPendingHold).mockResolvedValueOnce({
      cancelledCount: 0,
      alreadyPaid: true,
    });
    const user = userEvent.setup();
    const { onReleased, onFinish } = renderStep();

    await user.click(screen.getByRole("button", { name: "Hủy giữ chỗ" }));

    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
    expect(onFinish).toHaveBeenCalledWith({ paid: true, announced: true });
    expect(toast.success).toHaveBeenCalledWith(HOLD_ALREADY_PAID_TEXT);
    expect(onReleased).not.toHaveBeenCalled();
  });

  it("hủy lỗi: báo lỗi, giữ nguyên bước thanh toán", async () => {
    vi.mocked(cancelPendingHold).mockRejectedValueOnce(new Error("mất mạng"));
    const user = userEvent.setup();
    const { onReleased } = renderStep();

    await user.click(screen.getByRole("button", { name: "Hủy giữ chỗ" }));

    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(onReleased).not.toHaveBeenCalled();
  });

  it("onBookAgain: hủy lặng lẽ (không toast) rồi về bước 1, kể cả khi hủy lỗi", async () => {
    vi.mocked(cancelPendingHold).mockRejectedValueOnce(new Error("hết hạn"));
    const user = userEvent.setup();
    const { onReleased } = renderStep();

    await user.click(screen.getByRole("button", { name: "Đặt lịch lại" }));

    await waitFor(() => expect(onReleased).toHaveBeenCalledTimes(1));
    expect(toast.error).not.toHaveBeenCalled();
    expect(toast.warning).not.toHaveBeenCalled();
  });

  it("thanh toán xong: onFinish, không gọi hủy khi gỡ component", async () => {
    const user = userEvent.setup();
    const { onFinish, unmount } = renderStep();

    await user.click(screen.getByRole("button", { name: "Đã trả xong" }));
    expect(onFinish).toHaveBeenCalledWith({ paid: true, announced: false });
    unmount();
    await wait(20);

    expect(cancelPendingHold).not.toHaveBeenCalled();
  });
});

describe("PaymentHoldStep: rời bước thanh toán", () => {
  it("bấm liên kết sang trang khác: hỏi xác nhận; Tiếp tục thanh toán thì ở lại, không hủy", async () => {
    const user = userEvent.setup();
    renderStep();
    const navigated = vi.fn((event: Event) => event.preventDefault());
    screen.getByRole("link", { name: "Hồ sơ" }).addEventListener("click", navigated);

    await user.click(screen.getByRole("link", { name: "Hồ sơ" }));

    expect(
      await screen.findByRole("alertdialog", { name: HOLD_LEAVE_DIALOG.title }),
    ).toBeInTheDocument();
    expect(navigated).not.toHaveBeenCalled();

    await user.click(
      screen.getByRole("button", { name: HOLD_LEAVE_DIALOG.stay }),
    );
    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
    expect(cancelPendingHold).not.toHaveBeenCalled();
    expect(navigated).not.toHaveBeenCalled();
  });

  it("đồng ý Hủy lịch: hủy giữ chỗ, toast rời trang, rồi mới đi tiếp theo liên kết đã bấm", async () => {
    const user = userEvent.setup();
    const { onReleased } = renderStep();
    const navigated = vi.fn((event: Event) => event.preventDefault());
    screen.getByRole("link", { name: "Hồ sơ" }).addEventListener("click", navigated);

    await user.click(screen.getByRole("link", { name: "Hồ sơ" }));
    await user.click(
      await screen.findByRole("button", { name: HOLD_LEAVE_DIALOG.leave }),
    );

    await waitFor(() => expect(navigated).toHaveBeenCalledTimes(1));
    expect(cancelPendingHold).toHaveBeenCalledTimes(1);
    expect(toast.warning).toHaveBeenCalledWith(HOLD_CANCELLED_ON_LEAVE_TEXT);
    // Reset wizard về bước 1 trước khi đi tiếp, để lỡ không đi được thì không còn QR của lịch đã hủy.
    expect(onReleased).toHaveBeenCalledTimes(1);
    // Sau khi hủy, rời trang không hỏi lại.
    await user.click(screen.getByRole("link", { name: "Hồ sơ" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(navigated).toHaveBeenCalledTimes(2);
  });

  it("liên kết mở tab mới hoặc bấm kèm Ctrl: không chặn", async () => {
    const user = userEvent.setup();
    renderStep();
    const link = screen.getByRole("link", { name: "Hồ sơ" });
    const navigated = vi.fn((event: Event) => event.preventDefault());
    link.addEventListener("click", navigated);

    await user.keyboard("{Control>}");
    await user.click(link);
    await user.keyboard("{/Control}");

    expect(navigated).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("Back (popstate): hỏi xác nhận; đồng ý thì hủy rồi lùi một bước", async () => {
    const user = userEvent.setup();
    const back = vi.spyOn(window.history, "back").mockImplementation(() => undefined);
    renderStep();

    act(() => {
      window.dispatchEvent(new PopStateEvent("popstate"));
    });
    await user.click(
      await screen.findByRole("button", { name: HOLD_LEAVE_DIALOG.leave }),
    );

    await waitFor(() => expect(back).toHaveBeenCalledTimes(1));
    expect(cancelPendingHold).toHaveBeenCalledTimes(1);
    back.mockRestore();
  });

  it("Back ở mục đầu lịch sử (không lùi được): vẫn reset wizard về bước 1, không còn QR", async () => {
    const user = userEvent.setup();
    // history.back không làm gì (tab mới, không có trang trước) và không bắn popstate.
    const back = vi.spyOn(window.history, "back").mockImplementation(() => undefined);
    const { onReleased } = renderStep();

    act(() => {
      window.dispatchEvent(new PopStateEvent("popstate"));
    });
    await user.click(
      await screen.findByRole("button", { name: HOLD_LEAVE_DIALOG.leave }),
    );

    await waitFor(() => expect(onReleased).toHaveBeenCalledTimes(1));
    expect(cancelPendingHold).toHaveBeenCalledTimes(1);
    expect(back).toHaveBeenCalledTimes(1);
    back.mockRestore();
  });

  it("cancelledCount 0 (không còn gì để hủy): rời trang không toast 'đã hủy'", async () => {
    vi.mocked(cancelPendingHold).mockResolvedValueOnce({
      cancelledCount: 0,
      alreadyPaid: false,
    });
    const user = userEvent.setup();
    const { onReleased } = renderStep();
    screen
      .getByRole("link", { name: "Hồ sơ" })
      .addEventListener("click", (event) => event.preventDefault());

    await user.click(screen.getByRole("link", { name: "Hồ sơ" }));
    await user.click(
      await screen.findByRole("button", { name: HOLD_LEAVE_DIALOG.leave }),
    );

    await waitFor(() => expect(onReleased).toHaveBeenCalledTimes(1));
    expect(toast.warning).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("gỡ component khi cancelledCount 0: không toast 'đã hủy'", async () => {
    vi.mocked(cancelPendingHold).mockResolvedValueOnce({
      cancelledCount: 0,
      alreadyPaid: false,
    });
    const { unmount } = renderStep();

    unmount();

    await waitFor(() => expect(cancelPendingHold).toHaveBeenCalledTimes(1));
    await wait(20);
    expect(toast.warning).not.toHaveBeenCalled();
  });

  it("Back rồi Tiếp tục thanh toán: đẩy lại mục đệm để canh tiếp", async () => {
    const user = userEvent.setup();
    renderStep();
    const push = vi.spyOn(window.history, "pushState");

    act(() => {
      window.dispatchEvent(new PopStateEvent("popstate"));
    });
    await user.click(
      await screen.findByRole("button", { name: HOLD_LEAVE_DIALOG.stay }),
    );

    expect(push).toHaveBeenCalledTimes(1);
    expect(cancelPendingHold).not.toHaveBeenCalled();
    push.mockRestore();
  });

  it("gỡ component khi còn giữ chỗ (đổi route bằng code): hủy kèm toast; StrictMode lúc mount thì không hủy", async () => {
    const strict = renderStep({ strict: true });
    await wait(30);
    expect(cancelPendingHold).not.toHaveBeenCalled();

    strict.unmount();

    await waitFor(() => expect(cancelPendingHold).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith(HOLD_CANCELLED_ON_LEAVE_TEXT),
    );
  });

  it("pagehide: gửi POST hủy bằng fetch keepalive kèm Authorization và CSRF", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 200 }));
    renderStep();
    await act(async () => {
      await wait(10); // đợi lấy sẵn token
    });

    act(() => {
      window.dispatchEvent(new Event("pagehide"));
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/v1\/appointments\/pending-hold\/cancel$/);
    expect(init.method).toBe("POST");
    expect(init.keepalive).toBe(true);
    expect(init.headers).toMatchObject({
      Authorization: "Bearer access-token",
      "X-CSRF-TOKEN": "csrf-token",
    });
    fetchMock.mockRestore();
  });
});

describe("PaymentHoldStep: tab khác hủy giữ chỗ", () => {
  it("dò thấy lịch không còn trước hạn: báo, xóa khóa idempotency và về bước 1, không crash", async () => {
    vi.useFakeTimers();
    window.sessionStorage.setItem(KEY, "k");
    const { onReleased } = renderStep();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_100);
    });

    expect(onReleased).toHaveBeenCalledTimes(1);
    expect(toast.warning).toHaveBeenCalledWith(HOLD_CANCELLED_ELSEWHERE_TEXT);
    expect(window.sessionStorage.getItem(KEY)).toBeNull();
    // Không tự gọi hủy: giữ chỗ đã bị hủy ở nơi khác.
    expect(cancelPendingHold).not.toHaveBeenCalled();
  });

  it("đã trả xong (dò thấy CONFIRMED): kết thúc giữ chỗ, không reset; bấm liên kết rời trang không hỏi và không hủy", async () => {
    vi.useFakeTimers();
    vi.mocked(getMyAppointment).mockResolvedValueOnce({
      status: "BOOKED", // kho giả dùng trạng thái màn hình; nguồn đổi thành CONFIRMED như BE
    } as Awaited<ReturnType<typeof getMyAppointment>>);
    const { onReleased, unmount } = renderStep();
    const navigated = vi.fn((event: Event) => event.preventDefault());
    screen.getByRole("link", { name: "Hồ sơ" }).addEventListener("click", navigated);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_100);
    });
    // userEvent cần timer thật: chuyển sang bấm trực tiếp.
    act(() => {
      screen.getByRole("link", { name: "Hồ sơ" }).click();
    });

    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(navigated).toHaveBeenCalledTimes(1);
    expect(onReleased).not.toHaveBeenCalled();
    expect(toast.warning).not.toHaveBeenCalled();
    // Gỡ component sau khi đã trả xong cũng không hủy.
    unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
    });
    expect(cancelPendingHold).not.toHaveBeenCalled();
  });

  it("đã quá hạn: giữ nguyên để màn thanh toán hiện khối Hết thời gian giữ lịch", async () => {
    vi.useFakeTimers();
    const { onReleased } = renderStep({
      appointment: hold({ paymentDeadline: "2000-01-01T00:00:00+07:00" }),
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_100);
    });

    expect(onReleased).not.toHaveBeenCalled();
    expect(toast.warning).not.toHaveBeenCalled();
  });
});
