import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import type { ExamQueueItem } from "@/lib/api/examQueueApi";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetBookingMock } from "../../api/bookingMockStore";
import { resetPatientAppointmentMock } from "../../api/patientAppointmentApi";
import type { PatientArrivalCheckInResult } from "../../api/patientArrivalCheckInApi";

// vi.mock được đưa lên đầu file: biến dùng trong factory phải khai báo bằng vi.hoisted.
const nav = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  // Không ném lỗi (khác với Next) để test kiểm tra được lời gọi.
  notFound: vi.fn(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
// Ranh giới HTTP: check-in QR và hàng chờ đi qua apiRequest (lớp API thật, chỉ thay đường gọi mạng).
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/client")>()),
  apiRequest,
}));
// Bọc lớp API giả bằng vi.fn để ép lỗi một lần; kho giả vẫn chạy thật.
vi.mock("../../api/patientAppointmentApi", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("../../api/patientAppointmentApi")>();
  return {
    ...original,
    getMyAppointment: vi.fn(original.getMyAppointment),
    cancelMyAppointment: vi.fn(original.cancelMyAppointment),
  };
});

import { notFound } from "next/navigation";
import {
  cancelMyAppointment,
  getMyAppointment,
} from "../../api/patientAppointmentApi";
import { PatientAppointmentDetail } from "./PatientAppointmentDetail";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Thứ bảy 24/10/2026: lịch mẫu tính theo ngày này (apt-1001 thứ hai 26/10, apt-1003 thứ tư 28/10).
const NOW = new Date("2026-10-24T09:00:00+07:00");

const dialog = () => screen.getByRole("alertdialog");

// Số tiền có khoảng trắng không ngắt (U+00A0); Testing Library chuẩn hóa DOM nhưng không chuẩn hóa
// chuỗi tìm, nên chuẩn hóa cả chuỗi kỳ vọng (cùng cách với test thanh toán).
const money = (amount: number) => formatCurrencyVnd(amount).replace(/\s/g, " ");

const QUEUE_PATH = "/api/v1/visits/vis-an-today/exam-queue-entry";
const ARRIVAL_PATH = "/api/v1/appointments/apt-1003/arrivals";

/** Đặt giờ hiện tại là `at`. Lịch mẫu tạo lại theo NOW trước, rồi giữ nguyên khi đổi giờ. */
function clockAt(at: string): void {
  vi.setSystemTime(NOW);
  resetBookingMock();
  vi.setSystemTime(new Date(at));
}

/** Trả lời theo đường dẫn; đường không có trong bảng là lỗi, để test không gọi mạng ngoài ý muốn. */
function answerApi(routes: Record<string, () => Promise<unknown>>): void {
  apiRequest.mockImplementation((path: string) => {
    const route = routes[path];
    return route ? route() : Promise.reject(new Error(`không có mock cho ${path}`));
  });
}

function queueEntry(overrides: Partial<ExamQueueItem> = {}): ExamQueueItem {
  return {
    visitId: "vis-an-today",
    queueNumber: 3,
    examStatus: "WAITING",
    source: "SCHEDULED",
    appointmentId: "apt-2001",
    patientProfileId: "profile-an",
    patientName: "Nguyễn Văn An",
    doctorProfileId: "doctor-ha",
    doctorName: "BS. Phạm Thu Hà",
    roomId: null,
    roomLabel: "Phòng khám 2",
    serviceId: "svc-kham-tq",
    queuedAt: "2026-10-23T08:30:00+07:00",
    startedAt: null,
    finishedAt: null,
    ...overrides,
  };
}

function checkInResult(
  overrides: Partial<PatientArrivalCheckInResult> = {},
): PatientArrivalCheckInResult {
  return {
    appointmentId: "apt-1003",
    patientProfileId: "profile-lan",
    source: "QR",
    state: "CHECKED_IN",
    arrivedAt: "2026-10-28T09:00:00+07:00",
    recordedAt: "2026-10-28T09:00:00+07:00",
    appointmentStatus: "CHECKED_IN",
    visitId: "vis-lan-today",
    checkedInAt: "2026-10-28T09:00:00+07:00",
    checkedIn: true,
    queueNumber: 3,
    doctorName: "BS. Trần Anh Tuấn",
    roomLabel: "Phòng khám 2",
    ...overrides,
  };
}

beforeEach(() => {
  nav.replace.mockClear();
  nav.push.mockClear();
  vi.mocked(toast.success).mockClear();
  vi.mocked(toast.error).mockClear();
  vi.mocked(notFound).mockClear();
  vi.mocked(getMyAppointment).mockClear();
  vi.mocked(cancelMyAppointment).mockClear();
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  resetPatientAppointmentMock();
  // Giờ máy khớp kho giả (cố định ở NOW): mốc của màn chi tiết và của kho cùng một lúc.
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
  apiRequest.mockReset();
  answerApi({ [QUEUE_PATH]: async () => queueEntry() });
});

afterEach(() => {
  vi.useRealTimers();
});

describe("PatientAppointmentDetail: lịch đã thanh toán (Lan)", () => {
  it("ghi người khám và quan hệ, phí đã trả; không có nút thanh toán; có nút hủy và câu không đổi giờ", async () => {
    render(<PatientAppointmentDetail id="apt-1003" />);

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Lịch khám ngày 28/10/2026",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("Nguyễn Thị Lan · Cha/Mẹ")).toBeInTheDocument();
    expect(
      screen.getByText(`Phí khám ${money(200000)} · Đã thanh toán`),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Thanh toán trực tuyến" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Hủy lịch hẹn" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Muốn đổi giờ? Hủy lịch này rồi đặt lịch mới."),
    ).toBeInTheDocument();
  });

  it("nhắc mã BN của người khám này ở quầy", async () => {
    render(<PatientAppointmentDetail id="apt-1003" />);
    expect(
      await screen.findByText(/báo mã bệnh nhân BN-000456 hoặc số điện thoại/),
    ).toBeInTheDocument();
  });

  it("hộp thoại hủy nói rõ phí đã trả không được hoàn lại (BR-53)", async () => {
    const user = userEvent.setup();
    render(<PatientAppointmentDetail id="apt-1003" />);
    await user.click(
      await screen.findByRole("button", { name: "Hủy lịch hẹn" }),
    );

    expect(dialog()).toHaveTextContent(
      `Phí khám ${money(200000)} đã thanh toán trực tuyến sẽ không được hoàn lại.`,
    );
    expect(dialog()).toHaveTextContent("giờ này được mở cho người khác");
  });
});

describe("PatientAppointmentDetail: lịch chưa thanh toán (An)", () => {
  it("có nút thanh toán dẫn tới trang thanh toán của lịch; hộp thoại hủy không nói về hoàn tiền", async () => {
    const user = userEvent.setup();
    render(<PatientAppointmentDetail id="apt-1001" />);

    expect(
      await screen.findByRole("link", { name: "Thanh toán trực tuyến" }),
    ).toHaveAttribute("href", "/portal/payments/appointment/apt-1001");
    await user.click(screen.getByRole("button", { name: "Hủy lịch hẹn" }));
    expect(dialog()).not.toHaveTextContent("không được hoàn lại");
  });

  it("hủy thành công: toast MSG-28, trạng thái Đã hủy, không còn nút hủy", async () => {
    const user = userEvent.setup();
    render(<PatientAppointmentDetail id="apt-1001" />);
    await user.click(
      await screen.findByRole("button", { name: "Hủy lịch hẹn" }),
    );
    await user.click(
      within(dialog()).getByRole("button", { name: "Hủy lịch hẹn" }),
    );

    expect(await screen.findByText("Đã hủy")).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith("Đã hủy lịch hẹn khám.");
    expect(
      screen.queryByRole("button", { name: "Hủy lịch hẹn" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("Muốn đổi giờ? Hủy lịch này rồi đặt lịch mới."),
    ).not.toBeInTheDocument();
  });

  it("giữ lịch hẹn: đóng hộp thoại, lịch không đổi", async () => {
    const user = userEvent.setup();
    render(<PatientAppointmentDetail id="apt-1001" />);
    await user.click(
      await screen.findByRole("button", { name: "Hủy lịch hẹn" }),
    );
    await user.click(
      within(dialog()).getByRole("button", { name: "Giữ lịch hẹn" }),
    );

    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("Đã đặt")).toBeInTheDocument();
    expect(cancelMyAppointment).not.toHaveBeenCalled();
  });

  it("lỗi 5xx khi hủy: báo MSG-04 và giữ hộp thoại để thử lại", async () => {
    const user = userEvent.setup();
    vi.mocked(cancelMyAppointment).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    render(<PatientAppointmentDetail id="apt-1001" />);
    await user.click(
      await screen.findByRole("button", { name: "Hủy lịch hẹn" }),
    );
    await user.click(
      within(dialog()).getByRole("button", { name: "Hủy lịch hẹn" }),
    );

    // notifyError gọi toast.error(câu, tùy chọn): chỉ kiểm tham số đầu.
    await waitFor(() =>
      expect(vi.mocked(toast.error).mock.calls[0]?.[0]).toBe(
        formatAppMessage("MSG-04"),
      ),
    );
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
  });
});

describe("PatientAppointmentDetail: trạng thái đặc biệt", () => {
  it("MSG-32 khi hủy (đã check-in ở quầy): toast theo đặc tả R3, đóng hộp thoại và tải lại chi tiết", async () => {
    const user = userEvent.setup();
    render(<PatientAppointmentDetail id="apt-race" />);
    await user.click(
      await screen.findByRole("button", { name: "Hủy lịch hẹn" }),
    );
    await user.click(
      within(dialog()).getByRole("button", { name: "Hủy lịch hẹn" }),
    );

    // Chữ lấy từ bảng MSG chung (notifyError gọi toast.error(câu, tùy chọn)): chỉ kiểm tham số đầu.
    await waitFor(() =>
      expect(vi.mocked(toast.error).mock.calls[0]?.[0]).toBe(
        formatAppMessage("MSG-32"),
      ),
    );
    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("Đã đặt")).toBeInTheDocument();
    expect(getMyAppointment).toHaveBeenCalledTimes(2);
  });

  it("bác sĩ vắng không có người thay: hiện cảnh báo và vẫn cho hủy", async () => {
    render(<PatientAppointmentDetail id="apt-2101" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bác sĩ của lịch hẹn này không thể khám và chưa có bác sĩ thay thế. Bạn có thể hủy lịch hẹn.",
    );
    expect(
      screen.getByRole("button", { name: "Hủy lịch hẹn" }),
    ).toBeInTheDocument();
  });

  it("đã hủy lịch bác sĩ vắng: cảnh báo không còn, vì không cần hủy nữa", async () => {
    const user = userEvent.setup();
    render(<PatientAppointmentDetail id="apt-2101" />);
    await user.click(
      await screen.findByRole("button", { name: "Hủy lịch hẹn" }),
    );
    await user.click(
      within(dialog()).getByRole("button", { name: "Hủy lịch hẹn" }),
    );

    expect(await screen.findByText("Đã hủy")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("lượt đã check-in: có link tiến độ lượt khám, không có nút hủy", async () => {
    render(<PatientAppointmentDetail id="apt-2001" />);
    expect(
      await screen.findByRole("link", { name: "Xem tiến độ lượt khám" }),
    ).toHaveAttribute("href", "/portal/visits/vis-an-today");
    expect(screen.getByText("Đã tiếp nhận")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Hủy lịch hẹn" }),
    ).not.toBeInTheDocument();
  });

  it("lịch không có hoặc ngoài phạm vi (MSG-29): chuyển sang trang không tìm thấy của khu", async () => {
    render(<PatientAppointmentDetail id="apt-1004" />);
    await waitFor(() => expect(notFound).toHaveBeenCalled());
  });

  it("lỗi tải khác: nói rõ lỗi và có nút Thử lại", async () => {
    const user = userEvent.setup();
    vi.mocked(getMyAppointment).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    render(<PatientAppointmentDetail id="apt-1001" />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { level: 1 })).toHaveTextContent(
      "Lịch khám ngày 26/10/2026",
    );
  });
});

describe("PatientAppointmentDetail: hạn tự hủy 120 phút", () => {
  it("đúng 120 phút trước giờ hẹn: vẫn có nút hủy", async () => {
    clockAt("2026-10-26T06:30:00+07:00");
    // Premise của test: lịch mẫu 26/10 lúc 08:30.
    expect((await getMyAppointment("apt-1001")).startAt).toBe(
      "2026-10-26T08:30:00+07:00",
    );
    render(<PatientAppointmentDetail id="apt-1001" />);
    expect(
      await screen.findByRole("button", { name: "Hủy lịch hẹn" }),
    ).toBeInTheDocument();
  });

  it("còn dưới 120 phút: ẩn nút hủy và hiện ghi chú MSG-32", async () => {
    clockAt("2026-10-26T06:45:00+07:00");
    render(<PatientAppointmentDetail id="apt-1001" />);
    expect(
      await screen.findByText(formatAppMessage("MSG-32")),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Hủy lịch hẹn" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("Muốn đổi giờ? Hủy lịch này rồi đặt lịch mới."),
    ).not.toBeInTheDocument();
  });
});

describe("PatientAppointmentDetail: quét QR tại quầy", () => {
  it("lịch không phải hôm nay: không có nút quét QR", async () => {
    render(<PatientAppointmentDetail id="apt-1003" />);
    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Lịch khám ngày 28/10/2026",
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Quét QR tại quầy" }),
    ).not.toBeInTheDocument();
  });

  it("lịch hôm nay đã trả phí: quét mã thì báo MSG-88 có số thứ tự và phòng", async () => {
    clockAt("2026-10-28T09:00:00+07:00");
    answerApi({ [ARRIVAL_PATH]: async () => checkInResult() });
    const user = userEvent.setup();
    render(<PatientAppointmentDetail id="apt-1003" />);

    await user.click(
      await screen.findByRole("button", { name: "Quét QR tại quầy" }),
    );
    await user.type(screen.getByLabelText("Mã QR"), "qr-proof-1");
    await user.click(screen.getByRole("button", { name: "Xác nhận mã QR" }));

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Đã tiếp nhận bệnh nhân. Số thứ tự 3, phòng Phòng khám 2.",
      ),
    );
    expect(apiRequest).toHaveBeenCalledWith(ARRIVAL_PATH, {
      method: "POST",
      body: JSON.stringify({ proof: "qr-proof-1" }),
    });
  });
});

describe("PatientAppointmentDetail: số thứ tự khám", () => {
  it("lượt đã check-in: hiện MSG-88 và bác sĩ phụ trách lấy từ hàng chờ", async () => {
    render(<PatientAppointmentDetail id="apt-2001" />);
    expect(
      await screen.findByText(
        "Đã tiếp nhận bệnh nhân. Số thứ tự 3, phòng Phòng khám 2.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Bác sĩ phụ trách: BS. Phạm Thu Hà"),
    ).toBeInTheDocument();
  });

  it("phòng chưa gắn: MSG-88 ghi phòng là —", async () => {
    answerApi({ [QUEUE_PATH]: async () => queueEntry({ roomLabel: null }) });
    render(<PatientAppointmentDetail id="apt-2001" />);
    expect(
      await screen.findByText("Đã tiếp nhận bệnh nhân. Số thứ tự 3, phòng —."),
    ).toBeInTheDocument();
  });

  it("lượt chưa vào hàng chờ (404): không hiện số và không báo lỗi", async () => {
    answerApi({
      [QUEUE_PATH]: async () => {
        throw new ApiError(404, { code: "MSG-41" });
      },
    });
    render(<PatientAppointmentDetail id="apt-2001" />);
    expect(
      await screen.findByRole("link", { name: "Xem tiến độ lượt khám" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Số thứ tự/)).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("lỗi tải hàng chờ: báo bằng MSG, trang vẫn dùng được", async () => {
    answerApi({
      [QUEUE_PATH]: async () => {
        throw new ApiError(500, { code: "MSG-04" });
      },
    });
    render(<PatientAppointmentDetail id="apt-2001" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    expect(
      screen.getByRole("link", { name: "Xem tiến độ lượt khám" }),
    ).toBeInTheDocument();
  });
});
