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
  usePathname: () => "/staff/appointments/new",
  useSearchParams: () => new URLSearchParams(nav.search),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));

import { toast } from "sonner";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { resetBookingMock } from "../../api/bookingMockStore";
import { resetStaffAppointmentMock } from "../../api/staffAppointmentMockStore";
import { StaffBookingPanel } from "./StaffBookingPanel";

// Đồng hồ cố định: thứ Bảy 24/10/2026, 09:00 giờ VN. Ô SĐT giả lập bằng một ô tel có nhãn để kiểm
// focus; màn thật là PatientLookup (route ghép).
const NOW = new Date("2026-10-24T09:00:00+07:00");
const LOOKUP = (
  <div>
    <label htmlFor="fake-phone">Số điện thoại</label>
    <input id="fake-phone" type="tel" />
  </div>
);

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
  resetBookingMock({ now: NOW });
  resetStaffAppointmentMock();
  nav.search = "";
  vi.clearAllMocks();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("StaffBookingPanel", () => {
  it("không có ?patientId: chỉ có ô tìm, không có khối đặt lịch", () => {
    render(<StaffBookingPanel lookupSlot={LOOKUP} />);
    expect(screen.getByLabelText("Số điện thoại")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Đặt lịch khám" })).not.toBeInTheDocument();
  });

  it("có ?patientId: hiện người khám và luồng đặt lịch cho đúng người đó", async () => {
    nav.search = "patientId=pat-binh";
    render(<StaffBookingPanel lookupSlot={LOOKUP} />);
    expect(await screen.findByRole("heading", { name: "Đặt lịch khám" })).toBeInTheDocument();
    expect(screen.getByText("Trần Thị Bình")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đổi người" })).toBeInTheDocument();
    expect(
      await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }),
    ).toBeInTheDocument();
  });

  it("mã không dùng được (404 MSG-29): báo một lần và bỏ mã khỏi URL", async () => {
    nav.search = "patientId=pat-khong-co";
    render(<StaffBookingPanel lookupSlot={LOOKUP} />);
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(vi.mocked(toast.error).mock.calls.map((call) => call[0])).toEqual([
      formatAppMessage("MSG-29"),
    ]);
    expect(nav.replace).toHaveBeenCalledWith("/staff/appointments/new");
  });

  it("Đổi người: xóa mã khỏi URL và đưa con trỏ vào ô SĐT", async () => {
    const user = userEvent.setup();
    nav.search = "patientId=pat-binh";
    render(<StaffBookingPanel lookupSlot={LOOKUP} />);
    await user.click(await screen.findByRole("button", { name: "Đổi người" }));
    expect(nav.replace).toHaveBeenCalledWith("/staff/appointments/new");
    expect(screen.getByLabelText("Số điện thoại")).toHaveFocus();
  });

  it("phím / đưa con trỏ vào ô SĐT khi không đang gõ ô nào", async () => {
    const user = userEvent.setup();
    render(<StaffBookingPanel lookupSlot={LOOKUP} />);
    await user.keyboard("/");
    expect(screen.getByLabelText("Số điện thoại")).toHaveFocus();
  });

  it("đặt xong lịch hôm nay: có Tiếp nhận ngay; Đặt lịch khác xóa mã khỏi URL", async () => {
    const user = userEvent.setup();
    nav.search = "patientId=pat-binh";
    render(<StaffBookingPanel lookupSlot={LOOKUP} />);

    await user.click(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }));
    // Ngày đầu của dải là hôm nay. 09:00 là giờ kịch bản MSG-30 của kho giả nên chọn 10:00.
    const todayChip = (await screen.findAllByRole("button", { name: /còn giờ/ }))[0];
    await user.click(todayChip);
    await user.click(await screen.findByRole("button", { name: "10:00" }));
    await user.click(screen.getByRole("button", { name: "Đặt lịch khám" }));

    const next = await screen.findByRole("link", { name: "Tiếp nhận ngay" });
    expect(next.getAttribute("href")).toMatch(/^\/staff\/check-in\?appointmentId=apt-\d+$/);
    expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-26"));

    await user.click(screen.getByRole("button", { name: "Đặt lịch khác" }));
    expect(nav.replace).toHaveBeenCalledWith("/staff/appointments/new");
  });
});
