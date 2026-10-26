import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetBookingMock } from "../../api/bookingMockStore";
import { resetPatientAppointmentMock } from "../../api/patientAppointmentApi";
import { PatientBookingPage } from "./PatientBookingPage";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

// Thứ sáu 23/10/2026, 07:00 giờ VN: hôm nay còn giờ sáng; thứ ba 27/10 còn giờ.
const NOW = new Date("2026-10-23T07:00:00+07:00");

beforeEach(() => {
  vi.mocked(toast.success).mockClear();
  vi.mocked(toast.error).mockClear();
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  resetPatientAppointmentMock();
});

describe("PatientBookingPage", () => {
  it("chưa có hồ sơ nào: không mở luồng đặt, dẫn tới thêm hồ sơ", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    render(<PatientBookingPage />);

    expect(
      await screen.findByText(/Tài khoản của bạn chưa có hồ sơ bệnh nhân\./),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Thêm hồ sơ bệnh nhân" }),
    ).toHaveAttribute("href", "/portal/profiles/new");
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("có nhiều hồ sơ: hỏi 'Ai đi khám?' với đủ người, ghi quan hệ", async () => {
    render(<PatientBookingPage />);

    const choice = await screen.findByRole("radiogroup", {
      name: "Ai đi khám?",
    });
    expect(within(choice).getAllByRole("radio")).toHaveLength(3);
    expect(
      within(choice).getByRole("radio", { name: /Nguyễn Gia Huy · Con/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Thêm hồ sơ bệnh nhân" }),
    ).toHaveAttribute("href", "/portal/profiles/new");
  });

  it("một hồ sơ: bỏ khối 'Ai đi khám?', vào thẳng chọn dịch vụ", async () => {
    resetLinkedProfilesMock({ variant: "selfOnly" });
    render(<PatientBookingPage />);

    expect(
      await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("radiogroup", { name: "Ai đi khám?" }),
    ).not.toBeInTheDocument();
  });

  it("đặt cho người thân rồi hiện kết quả: Xem lịch hẹn và Thanh toán trực tuyến theo allowedActions", async () => {
    const user = userEvent.setup();
    render(<PatientBookingPage />);

    await user.click(
      await screen.findByRole("radio", { name: /Nguyễn Thị Lan · Cha\/Mẹ/ }),
    );
    await user.click(
      await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Thứ ba 27/10, còn giờ" }),
    );
    await user.click(await screen.findByRole("button", { name: "10:00" }));
    await user.click(screen.getByRole("button", { name: "Đặt lịch khám" }));

    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Đã đặt lịch khám",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem lịch hẹn" })).toHaveAttribute(
      "href",
      "/portal/appointments/apt-9001",
    );
    expect(
      await screen.findByRole("link", { name: "Thanh toán trực tuyến" }),
    ).toHaveAttribute("href", "/portal/payments/appointment/apt-9001");
    expect(toast.success).toHaveBeenCalledWith("Đã đặt lịch khám thành công.");
  });
});
