import { render, screen, waitFor, within } from "@testing-library/react";
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
// Giữ nguyên lớp API giả (kho thật), chỉ bọc vi.fn để đếm lời gọi.
vi.mock("../../api/staffAppointmentApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../api/staffAppointmentApi")>();
  return {
    ...original,
    listStaffEligibleDoctors: vi.fn(original.listStaffEligibleDoctors),
    reassignStaffAppointmentDoctor: vi.fn(original.reassignStaffAppointmentDoctor),
    notifyStaffNoReplacement: vi.fn(original.notifyStaffNoReplacement),
  };
});

import { toast } from "sonner";
import { DOCTOR_IDS } from "../../api/appointmentMockData";
import { createAppointment } from "../../api/bookingApi";
import { resetBookingMock } from "../../api/bookingMockStore";
import {
  notifyStaffNoReplacement,
  reassignStaffAppointmentDoctor,
} from "../../api/staffAppointmentApi";
import { resetStaffAppointmentMock } from "../../api/staffAppointmentMockStore";
import {
  NOTIFY_SUCCESS_TEXT,
  reassignSuccessText,
} from "../../schema/appointmentMessages";
import { StaffAppointmentList } from "./StaffAppointmentList";

// Thứ bảy 24/10/2026, 09:00 giờ VN (cùng đồng hồ với StaffAppointmentList.test.tsx).
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

describe("StaffAppointmentList - chi tiết lịch", () => {
  it("Xem chi tiết mở Sheet; footer chỉ có nút theo allowedActions", async () => {
    const user = userEvent.setup();
    render(<StaffAppointmentList />);
    await openRowMenu(user, "Lê Văn Cường", "16:45");
    await user.click(screen.getByRole("menuitem", { name: "Xem chi tiết" }));
    const sheet = await screen.findByRole("dialog", { name: "Chi tiết lịch hẹn" });
    expect(within(sheet).getByRole("button", { name: "Tiếp nhận" })).toBeInTheDocument();
    expect(within(sheet).getByRole("button", { name: "Chuyển bác sĩ" })).toBeInTheDocument();
    expect(within(sheet).queryByRole("button", { name: /Hủy|Đổi giờ/ })).not.toBeInTheDocument();
  });

  it("lịch đã tiếp nhận: Sheet không có nút nào ở footer", async () => {
    const user = userEvent.setup();
    render(<StaffAppointmentList />);
    await openRowMenu(user, "Trần Thị Bình", "08:00");
    await user.click(screen.getByRole("menuitem", { name: "Xem chi tiết" }));
    const sheet = await screen.findByRole("dialog", { name: "Chi tiết lịch hẹn" });
    expect(within(sheet).queryByRole("button", { name: "Tiếp nhận" })).not.toBeInTheDocument();
    expect(within(sheet).queryByRole("button", { name: "Chuyển bác sĩ" })).not.toBeInTheDocument();
  });
});

describe("StaffAppointmentList - chuyển bác sĩ và báo bệnh nhân", () => {
  it("chuyển bác sĩ: chọn bác sĩ có ca cùng giờ, gọi endpoint khu Lễ tân, dòng đổi bác sĩ", async () => {
    const user = userEvent.setup();
    render(<StaffAppointmentList />);
    await openRowMenu(user, "Lê Văn Cường", "16:45");
    await user.click(screen.getByRole("menuitem", { name: "Chuyển bác sĩ" }));

    const dialog = await screen.findByRole("dialog");
    await user.click(await within(dialog).findByRole("radio", { name: "BS. Phạm Thu Hà" }));
    await user.click(within(dialog).getByRole("button", { name: "Chuyển lịch hẹn" }));

    await waitFor(() =>
      expect(reassignStaffAppointmentDoctor).toHaveBeenCalledWith("apt-1007", DOCTOR_IDS.ha),
    );
    expect(toast.success).toHaveBeenCalledWith(reassignSuccessText("BS. Phạm Thu Hà"));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getAllByText("BS. Phạm Thu Hà").length).toBeGreaterThan(0);
  });

  it("không có bác sĩ thay: nêu rõ bằng câu của Lễ tân (không nhắc admin), báo bệnh nhân qua xác nhận", async () => {
    const user = userEvent.setup();
    // Hà kín lúc 16:45 bởi một lịch khác (đặt qua API giả của P03): Cường không còn ai thay.
    await createAppointment({
      patientId: "pat-binh",
      serviceId: "svc-kham-cs",
      startAt: `${DAY}T16:45:00+07:00`,
      doctorId: DOCTOR_IDS.ha,
    });
    render(<StaffAppointmentList />);
    await openRowMenu(user, "Lê Văn Cường", "16:45");
    await user.click(screen.getByRole("menuitem", { name: "Chuyển bác sĩ" }));

    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText("Không có bác sĩ nào có ca vào giờ này."),
    ).toBeInTheDocument();
    expect(within(dialog).queryByText(/Admin/)).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Báo cho bệnh nhân" }));
    const confirm = await screen.findByRole("alertdialog");
    await user.click(within(confirm).getByRole("button", { name: "Gửi thông báo" }));

    await waitFor(() => expect(notifyStaffNoReplacement).toHaveBeenCalledWith("apt-1007"));
    expect(toast.success).toHaveBeenCalledWith(NOTIFY_SUCCESS_TEXT);
  });
});
