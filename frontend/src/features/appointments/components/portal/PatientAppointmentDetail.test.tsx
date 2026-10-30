import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetBookingMock } from "../../api/bookingMockStore";
import { resetPatientAppointmentMock } from "../../api/patientAppointmentApi";

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

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        "Lịch hẹn này không còn đủ điều kiện để hủy hoặc chuyển bác sĩ. Vui lòng liên hệ phòng khám.",
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
