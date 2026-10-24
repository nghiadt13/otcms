import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const nav = vi.hoisted(() => ({
  search: "",
  push: vi.fn(),
  replace: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  usePathname: () => "/staff/appointments",
  useSearchParams: () => new URLSearchParams(nav.search),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));
// Giữ nguyên lớp API giả (kho thật), chỉ bọc vi.fn để đếm lời gọi và ép một lần lỗi.
vi.mock("../../api/staffAppointmentApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../api/staffAppointmentApi")>();
  return {
    ...original,
    listStaffAppointments: vi.fn(original.listStaffAppointments),
    listStaffDoctors: vi.fn(original.listStaffDoctors),
  };
});

import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { DOCTOR_IDS } from "../../api/appointmentMockData";
import { resetBookingMock } from "../../api/bookingMockStore";
import { listStaffAppointments } from "../../api/staffAppointmentApi";
import { resetStaffAppointmentMock } from "../../api/staffAppointmentMockStore";
import { StaffAppointmentList } from "./StaffAppointmentList";

// Thứ bảy 24/10/2026, 09:00 giờ VN. Ngày giờ được cố định để "hôm nay" của FE và kho giả khớp nhau.
const NOW = new Date("2026-10-24T09:00:00+07:00");
const DAY = "2026-10-24";

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
  resetBookingMock({ now: NOW });
  resetStaffAppointmentMock();
  nav.search = `date=${DAY}`;
  vi.clearAllMocks();
});

afterEach(() => {
  vi.useRealTimers();
});

async function openRowMenu(user: ReturnType<typeof userEvent.setup>, patient: string, time: string) {
  await user.click(
    await screen.findByRole("button", { name: `Thao tác cho ${patient} lúc ${time}` }),
  );
}

describe("StaffAppointmentList - khung và dữ liệu", () => {
  it("mở ngày trên URL: sáu lịch theo giờ, có nút Đặt lịch, không có hủy hay đổi giờ", async () => {
    render(<StaffAppointmentList />);
    expect(screen.getByRole("heading", { level: 1, name: "Lịch hẹn" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đặt lịch" })).toHaveAttribute(
      "href",
      "/staff/appointments/new",
    );
    // Cường có hai lịch hôm nay (08:00 và 16:45) nên tên xuất hiện hai lần.
    await screen.findAllByText("Lê Văn Cường");
    expect(screen.getAllByRole("button", { name: /^Thao tác cho / })).toHaveLength(6);
    for (const name of ["Trần Thị Bình", "Bùi Thị Em", "Lê Văn Cường"])
      expect(screen.getAllByText(name).length).toBeGreaterThan(0);
    expect(screen.queryByText(/Hủy lịch|Đổi giờ/)).not.toBeInTheDocument();
    expect(listStaffAppointments).toHaveBeenCalledWith(
      expect.objectContaining({ date: DAY }),
      expect.anything(),
    );
  });

  it("menu theo allowedActions: đã tiếp nhận chỉ có xem; đã đặt hôm nay có Tiếp nhận và Chuyển bác sĩ", async () => {
    const user = userEvent.setup();
    render(<StaffAppointmentList />);
    await openRowMenu(user, "Trần Thị Bình", "08:00");
    expect(screen.getByRole("menuitem", { name: "Xem chi tiết" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Chuyển bác sĩ" })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Tiếp nhận" })).not.toBeInTheDocument();

    await user.keyboard("{Escape}");
    await openRowMenu(user, "Lê Văn Cường", "16:45");
    expect(screen.getByRole("menuitem", { name: "Tiếp nhận" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Chuyển bác sĩ" })).toBeInTheDocument();
  });

  it("Tiếp nhận chuyển sang trang tiếp nhận kèm mã lịch", async () => {
    const user = userEvent.setup();
    render(<StaffAppointmentList />);
    await openRowMenu(user, "Lê Văn Cường", "16:45");
    await user.click(screen.getByRole("menuitem", { name: "Tiếp nhận" }));
    expect(nav.push).toHaveBeenCalledWith("/staff/check-in?appointmentId=apt-1007");
  });

  it("lọc trạng thái ghi lên URL bằng replace; ô tìm không lên URL và lọc ngay trên danh sách", async () => {
    const user = userEvent.setup();
    render(<StaffAppointmentList />);
    await screen.findAllByText("Lê Văn Cường");

    await user.selectOptions(screen.getByLabelText("Trạng thái"), "BOOKED");
    expect(nav.replace).toHaveBeenCalledWith(`/staff/appointments?date=${DAY}&status=BOOKED`);

    await user.type(
      screen.getByLabelText("Tìm bệnh nhân theo tên hoặc số điện thoại"),
      "Bình",
    );
    await waitFor(() =>
      expect(screen.queryByText("Lê Văn Cường")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("Trần Thị Bình")).toBeInTheDocument();
    for (const [url] of nav.replace.mock.calls) expect(url).not.toMatch(/Bình|q=/);
  });

  it("bộ lọc bác sĩ là combobox có nhãn, mặc định Tất cả bác sĩ", async () => {
    render(<StaffAppointmentList />);
    expect(await screen.findByRole("combobox", { name: "Bác sĩ" })).toHaveTextContent(
      "Tất cả bác sĩ",
    );
  });

  it("ngày không có lịch: câu dạy cách dùng, không ghi Không có dữ liệu", async () => {
    nav.search = "date=2026-10-25";
    render(<StaffAppointmentList />);
    expect(
      await screen.findByText(/Ngày 25\/10\/2026 chưa có lịch hẹn nào/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Không có dữ liệu/)).not.toBeInTheDocument();
  });

  it("bộ lọc không khớp: nói rõ và Xóa bộ lọc đưa URL về ngày hiện tại", async () => {
    const user = userEvent.setup();
    nav.search = `date=${DAY}&doctorId=${DOCTOR_IDS.tuan}&status=NO_SHOW`;
    render(<StaffAppointmentList />);
    expect(
      await screen.findByText(/Không có lịch hẹn khớp bộ lọc/),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xóa bộ lọc" }));
    expect(nav.replace).toHaveBeenCalledWith(`/staff/appointments?date=${DAY}`);
  });

  it("lỗi tải: báo bằng chữ đỏ và Thử lại tải lại danh sách", async () => {
    const user = userEvent.setup();
    vi.mocked(listStaffAppointments).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    render(<StaffAppointmentList />);
    expect(await screen.findByRole("alert")).toHaveTextContent(formatAppMessage("MSG-04"));
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findAllByText("Lê Văn Cường");
  });
});
