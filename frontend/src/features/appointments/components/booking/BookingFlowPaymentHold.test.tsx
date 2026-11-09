import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { toBookingPatient } from "../../schema/bookingSelection";
import {
  BOOKING_TOO_SOON_TEXT,
} from "../../schema/bookingSubmitOutcome";
import {
  HOLD_CANCELLED_BY_USER_TEXT,
} from "../../schema/paymentHoldMessages";
import type { BookedAppointment, PaymentStepArgs } from "../../types/booking";

// Ranh giới ngoài: lớp API thật gọi backend. Trong test nó trả dữ liệu của kho giả dùng chung.
vi.mock("../../api/bookingLiveApi", async () => {
  const mock = await import("../../api/bookingApi");
  return {
    listBookableServices: mock.listBookableServices,
    getOpenTimes: mock.getOpenTimes,
    createAppointment: mock.createAppointment,
  };
});
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
// Bọc lớp API giả bằng vi.fn để đếm lời gọi và ép một lần kết quả; kho giả vẫn chạy thật.
vi.mock("../../api/bookingApi", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("../../api/bookingApi")>();
  return {
    ...original,
    getOpenTimes: vi.fn(original.getOpenTimes),
    createAppointment: vi.fn(original.createAppointment),
  };
});

import { createAppointment, getOpenTimes } from "../../api/bookingApi";
import { resetBookingMock } from "../../api/bookingMockStore";
import { BookingFlow } from "./BookingFlow";

// Thứ sáu 23/10/2026, 07:00 giờ VN; ngày 27/10 (thứ ba) còn giờ, 10:00 cách xa 15 phút.
const NOW = new Date("2026-10-23T07:00:00+07:00");
const KEY = "otcms:payos:idempotency:inv-1";

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

beforeEach(() => {
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  vi.clearAllMocks();
  window.sessionStorage.clear();
});

// Gỡ bước thanh toán còn giữ chỗ hẹn một lệnh hủy: chờ nó chạy hết trong test này.
afterEach(async () => {
  cleanup();
  await wait(15);
});

/** Màn thanh toán giả lộ các callback bước 5 mà màn thanh toán thật nhận. */
function fakePayment(args: PaymentStepArgs) {
  return (
    <div>
      <p>Thanh toán {args.appointment.id}</p>
      <p>Hạn {args.paymentDeadline ?? "không"}</p>
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

function renderFlow() {
  const onBooked = vi.fn();
  const self = getLinkedProfileRecords().filter(
    (profile) => profile.relationship === "SELF",
  );
  render(
    <BookingFlow
      onBooked={onBooked}
      patients={self.map(toBookingPatient)}
      renderPayment={fakePayment}
      variant="patient"
    />,
  );
  return { onBooked };
}

/** Một hồ sơ: bước Người khám đã chọn sẵn. Đi hết wizard tới khi bấm "Đặt lịch khám". */
async function submitBooking(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
  await user.click(
    await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }),
  );
  await user.click(
    await screen.findByRole("button", { name: "Thứ ba 27/10, còn giờ" }),
  );
  await user.click(await screen.findByRole("button", { name: "10:00" }));
  await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
  await user.type(
    screen.getByRole("textbox", { name: "Lý do khám" }),
    "Đau gối phải",
  );
  await user.click(screen.getByRole("button", { name: "Đặt lịch khám" }));
}

describe("BookingFlow: rẽ nhánh theo trạng thái BE trả khi đặt", () => {
  it("AWAITING_PAYMENT (dịch vụ có phí): sang bước 5, chưa báo MSG-26, truyền hạn giữ giờ", async () => {
    const user = userEvent.setup();
    const { onBooked } = renderFlow();

    await submitBooking(user);

    expect(await screen.findByText("Thanh toán apt-9001")).toBeInTheDocument();
    expect(screen.getByText(/Hạn 20\d\d-/)).toBeInTheDocument();
    const nav = screen.getByRole("navigation", { name: "Các bước đặt lịch" });
    expect(nav.querySelector('[aria-current="step"]')).toHaveTextContent(
      "Thanh toán",
    );
    expect(toast.success).not.toHaveBeenCalled();
    expect(onBooked).not.toHaveBeenCalled();
    // Đặt online được báo cho lớp API (kho giả dùng để chọn giữ giờ hay xác nhận ngay).
    expect(vi.mocked(createAppointment)).toHaveBeenCalledWith(
      expect.objectContaining({ bookedOnline: true }),
    );
  });

  it("CONFIRMED (dịch vụ miễn phí): báo MSG-26 ngay, bỏ qua bước 5", async () => {
    const user = userEvent.setup();
    const free: BookedAppointment = {
      id: "apt-free",
      startAt: "2026-10-27T10:00:00+07:00",
      patient: { id: "pat-1", fullName: "Nguyễn Văn An" },
      service: { id: "svc-free", name: "Khám miễn phí" },
      doctor: { id: "doc-1", fullName: "BS. Hà" },
      roomName: "Phòng 1",
      status: "BOOKED",
      paymentDeadline: null,
      examFee: { amount: 0, status: "NOT_REQUIRED" },
      isFollowUp: false,
    };
    vi.mocked(createAppointment).mockResolvedValueOnce(free);
    const { onBooked } = renderFlow();

    await submitBooking(user);

    await waitFor(() => expect(onBooked).toHaveBeenCalledWith(free));
    expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-26"));
    expect(screen.queryByText(/^Thanh toán apt-/)).not.toBeInTheDocument();
  });

  it("đặt lịch mới xóa khóa idempotency PayOS còn sót", async () => {
    window.sessionStorage.setItem(KEY, "key-cu");
    const user = userEvent.setup();
    renderFlow();

    await submitBooking(user);

    await screen.findByText("Thanh toán apt-9001");
    expect(window.sessionStorage.getItem(KEY)).toBeNull();
  });

  it("BOOKING_TOO_SOON: báo chữ không mã, bỏ giờ đã chọn và tải lại giờ trống", async () => {
    const user = userEvent.setup();
    vi.mocked(createAppointment).mockRejectedValueOnce(
      new ApiError(400, { errorCode: "BOOKING_TOO_SOON" }),
    );
    renderFlow();
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    await user.click(
      await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Thứ ba 27/10, còn giờ" }),
    );
    await user.click(await screen.findByRole("button", { name: "10:00" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    await user.type(
      screen.getByRole("textbox", { name: "Lý do khám" }),
      "Đau gối phải",
    );
    const loadsBefore = vi.mocked(getOpenTimes).mock.calls.length;

    await user.click(screen.getByRole("button", { name: "Đặt lịch khám" }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(BOOKING_TOO_SOON_TEXT),
    );
    expect(BOOKING_TOO_SOON_TEXT).toBe(
      "Cần đặt trước giờ khám ít nhất 15 phút",
    );
    await waitFor(() =>
      expect(vi.mocked(getOpenTimes).mock.calls.length).toBeGreaterThan(
        loadsBefore,
      ),
    );
    // Về lại bước chọn lịch để chọn giờ khác, không sang bước thanh toán.
    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Chọn loại khám và lịch hẹn",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/^Thanh toán apt-/)).not.toBeInTheDocument();
  });
});

describe("BookingFlow: kết thúc giữ chỗ ở bước 5", () => {
  it("Hủy giữ chỗ: hủy ở BE, reset wizard về bước đầu và giờ được nhả", async () => {
    const user = userEvent.setup();
    const { onBooked } = renderFlow();
    await submitBooking(user);
    await screen.findByText("Thanh toán apt-9001");
    window.sessionStorage.setItem(KEY, "key-1");

    await user.click(screen.getByRole("button", { name: "Hủy giữ chỗ" }));

    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Chọn người cần khám",
      }),
    ).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith(HOLD_CANCELLED_BY_USER_TEXT);
    expect(window.sessionStorage.getItem(KEY)).toBeNull();
    expect(onBooked).not.toHaveBeenCalled();

    // Giờ cũ đặt lại được: lần đặt thứ hai không bị MSG-30.
    await submitBooking(user);
    expect(await screen.findByText("Thanh toán apt-9002")).toBeInTheDocument();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it("Đặt lịch lại (hết thời gian giữ lịch): về bước đầu", async () => {
    const user = userEvent.setup();
    renderFlow();
    await submitBooking(user);
    await screen.findByText("Thanh toán apt-9001");

    await user.click(screen.getByRole("button", { name: "Đặt lịch lại" }));

    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Chọn người cần khám",
      }),
    ).toBeInTheDocument();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it("Thanh toán xong: MSG-26 và onBooked với lịch đã xác nhận, phí đã trả", async () => {
    const user = userEvent.setup();
    const { onBooked } = renderFlow();
    await submitBooking(user);
    await screen.findByText("Thanh toán apt-9001");

    await user.click(screen.getByRole("button", { name: "Đã trả xong" }));

    expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-26"));
    expect(onBooked).toHaveBeenCalledTimes(1);
    const booked = onBooked.mock.calls[0][0] as BookedAppointment;
    expect(booked.status).toBe("BOOKED");
    expect(booked.paymentDeadline).toBeNull();
    expect(booked.examFee.status).toBe("PAID");
  });

  it("Bước 5 khóa thanh điều hướng các bước trước (không quay lại sửa lịch khi đang giữ chỗ)", async () => {
    const user = userEvent.setup();
    renderFlow();
    await submitBooking(user);
    await screen.findByText("Thanh toán apt-9001");

    const nav = screen.getByRole("navigation", { name: "Các bước đặt lịch" });
    expect(within(nav).queryByRole("button")).not.toBeInTheDocument();
  });
});
