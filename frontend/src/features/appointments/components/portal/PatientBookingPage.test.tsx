import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import {
  fetchCurrentAccount,
  peekCurrentAccount,
} from "@/lib/auth/currentAccountApi";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetBookingMock } from "../../api/bookingMockStore";
import { resetPatientAppointmentMock } from "../../api/patientAppointmentApi";
import { PatientBookingPage } from "./PatientBookingPage";

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

// Ranh giới ngoài: `GET /api/v1/me`. Mặc định là bệnh nhân đã đăng nhập; test khách đổi sang null.
vi.mock("@/lib/auth/currentAccountApi", () => {
  const account = {
    id: "11111111-1111-1111-1111-111111111111",
    accountType: "PATIENT",
    email: "an@example.com",
    displayName: "Trần Minh Khoa",
  };
  return {
    fetchCurrentAccount: vi.fn(async () => account),
    peekCurrentAccount: vi.fn(() => account),
  };
});

// Thứ sáu 23/10/2026, 07:00 giờ VN: hôm nay còn giờ sáng; thứ ba 27/10 còn giờ.
const NOW = new Date("2026-10-23T07:00:00+07:00");

beforeEach(() => {
  vi.mocked(toast.success).mockClear();
  vi.mocked(toast.error).mockClear();
  vi.mocked(fetchCurrentAccount).mockClear();
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  resetPatientAppointmentMock();
});

describe("PatientBookingPage", () => {
  it("bước 2 chỉ có chính tài khoản, hiện họ tên từ backend, chọn sẵn", async () => {
    render(<PatientBookingPage />);

    const choice = await screen.findByRole("radiogroup", {
      name: "Ai đi khám?",
    });
    const radios = within(choice).getAllByRole("radio");
    expect(radios).toHaveLength(1);
    expect(radios[0]).toBeChecked();
    expect(
      within(choice).getByRole("radio", { name: "Trần Minh Khoa" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Nguyễn Gia Huy/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Sinh ngày/)).not.toBeInTheDocument();
  });

  it("đã đăng nhập: mở thẳng bước 2 Người khám, bước Tài khoản đã xong", async () => {
    render(<PatientBookingPage />);

    const nav = await screen.findByRole("navigation", {
      name: "Các bước đặt lịch",
    });
    expect(within(nav).getAllByRole("listitem")).toHaveLength(4);
    expect(nav.querySelector('[aria-current="step"]')).toHaveTextContent(
      "Người khám",
    );
    expect(
      within(nav).getByRole("button", { name: /Tài khoản/ }),
    ).toBeInTheDocument();
  });

  it("chưa đăng nhập: dừng ở bước 1, chỉ có Đăng nhập và Đăng ký quay lại màn đặt lịch", async () => {
    vi.mocked(peekCurrentAccount).mockReturnValueOnce(null);
    vi.mocked(fetchCurrentAccount).mockResolvedValueOnce(null);
    render(<PatientBookingPage />);

    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: /Đăng nhập để đặt lịch khám/,
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập" })).toHaveAttribute(
      "href",
      "/login?next=%2Fportal%2Fappointments%2Fnew",
    );
    expect(
      screen.getByRole("link", { name: "Đăng ký tài khoản" }),
    ).toHaveAttribute("href", "/register?next=%2Fportal%2Fappointments%2Fnew");
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Tiếp tục" }),
    ).not.toBeInTheDocument();
  });

  it("đặt xong hiện kết quả: hiện kết quả với liên kết Xem lịch hẹn", async () => {
    const user = userEvent.setup();
    render(<PatientBookingPage />);

    await user.click(await screen.findByRole("button", { name: "Tiếp tục" }));
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
    expect(toast.success).toHaveBeenCalledWith("Đã đặt lịch khám thành công.");
  });
});
