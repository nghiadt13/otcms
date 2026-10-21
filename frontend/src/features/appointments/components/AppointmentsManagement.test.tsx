import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const nav = vi.hoisted(() => ({
  search: "",
  push: vi.fn(),
  replace: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  usePathname: () => "/admin/appointments",
  useSearchParams: () => new URLSearchParams(nav.search),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));
vi.mock("../api/appointmentApi", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("../api/appointmentApi")>();
  return {
    ...original,
    getDoctorAvailability: vi.fn(original.getDoctorAvailability),
  };
});

import { toast } from "sonner";
import {
  getDoctorAvailability,
  resetAppointmentMock,
} from "../api/appointmentApi";
import { DOCTOR_IDS } from "../api/appointmentMockData";
import { AppointmentsManagement } from "./AppointmentsManagement";

const WEEK = "from=2026-10-20&to=2026-10-27";

function open(search: string) {
  nav.search = search;
  return render(<AppointmentsManagement />);
}

async function openRowMenu(patient: string) {
  const trigger = await screen.findByRole("button", {
    name: new RegExp(`^Thao tác cho ${patient}`),
  });
  await userEvent.click(trigger);
}

beforeEach(() => {
  resetAppointmentMock();
  vi.clearAllMocks();
});

describe("AppointmentsManagement - khung màn", () => {
  it("có hai tab, không nút ở header, không có thao tác cấm", async () => {
    open(WEEK);
    expect(
      screen.getByRole("heading", { name: "Lịch hẹn" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("tab", { name: "Danh sách lịch hẹn" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("tab", { name: "Giờ trống của bác sĩ" }),
    ).toBeInTheDocument();
    await screen.findByText("Nguyễn Thị Hoa");
    for (const forbidden of ["Hủy lịch", "Đổi giờ", "Đặt lịch", "Check-in"])
      expect(screen.queryByText(forbidden)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Thêm|Tạo/ }),
    ).not.toBeInTheDocument();
  });

  it("?doctorId=&date= lọc đúng theo bác sĩ và ngày", async () => {
    open(`doctorId=${DOCTOR_IDS.ha}&date=2026-10-21`);
    expect(await screen.findByText("Nguyễn Thị Hoa")).toBeInTheDocument();
    expect(screen.queryByText("Trần Minh Khoa")).not.toBeInTheDocument();
    expect(screen.queryByText("Nguyễn Bảo Châu")).not.toBeInTheDocument();
  });

  it("hiện Chưa chỉ định và dòng phụ người phụ thuộc", async () => {
    open("from=2026-10-21&to=2026-10-21&status=BOOKED");
    const row = (await screen.findByText("Trần Minh Khoa")).closest("tr")!;
    expect(
      within(row).getByText("Đặt cho người phụ thuộc"),
    ).toBeInTheDocument();
    expect(within(row).getByText("Chưa chỉ định")).toBeInTheDocument();
  });

  it("lịch không có allowedActions chỉ có Xem", async () => {
    open("from=2026-10-20&to=2026-10-20");
    await openRowMenu("Phan Thị Ngọc");
    expect(
      await screen.findByRole("menuitem", { name: "Xem" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: "Chuyển bác sĩ" }),
    ).not.toBeInTheDocument();
  });

  it("khoảng ngày sai: MSG-54 dưới Đến ngày, không có dòng nào", async () => {
    open("from=2026-10-25&to=2026-10-21");
    expect(
      await screen.findByText(/Tiêu chí lọc chưa hợp lệ/),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Sửa khoảng ngày để xem lịch hẹn."),
    ).toBeInTheDocument();
  });
});

describe("AppointmentsManagement - chuyển bác sĩ", () => {
  it("chọn bác sĩ rồi chuyển: toast có tên bác sĩ, dòng cập nhật tại chỗ", async () => {
    open("from=2026-10-21&to=2026-10-21");
    await openRowMenu("Nguyễn Thị Hoa");
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Chuyển bác sĩ" }),
    );
    await userEvent.click(
      await screen.findByRole("radio", { name: "BS. Đỗ Minh Tuấn" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Chuyển lịch hẹn" }),
    );
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Đã chuyển lịch hẹn sang bác sĩ BS. Đỗ Minh Tuấn. Bệnh nhân sẽ nhận được thông báo.",
      ),
    );
    const row = screen.getByText("Nguyễn Thị Hoa").closest("tr")!;
    expect(within(row).getByText("BS. Đỗ Minh Tuấn")).toBeInTheDocument();
  });

  it("AF1: hiện hai câu, Báo cho bệnh nhân qua xác nhận, lịch vẫn Đã đặt", async () => {
    open("from=2026-10-21&to=2026-10-21");
    await openRowMenu("Trần Minh Khoa");
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Chuyển bác sĩ" }),
    );
    expect(
      await screen.findByText("Không có bác sĩ nào có ca vào giờ này."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Admin không thể hủy lịch hẹn."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Chuyển lịch hẹn" }),
    ).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "Báo cho bệnh nhân" }),
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Gửi thông báo" }),
    );
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Đã gửi thông báo cho bệnh nhân.",
      ),
    );
    const row = screen.getByText("Trần Minh Khoa").closest("tr")!;
    expect(within(row).getByText("Đã đặt")).toBeInTheDocument();
    // Đã báo: mở lại thì không còn nút Báo cho bệnh nhân.
    await openRowMenu("Trần Minh Khoa");
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Chuyển bác sĩ" }),
    );
    await screen.findByText("Admin không thể hủy lịch hẹn.");
    expect(
      screen.queryByRole("button", { name: "Báo cho bệnh nhân" }),
    ).not.toBeInTheDocument();
  });

  it("409 MSG-32: toast lỗi và đóng Dialog", async () => {
    open("from=2026-10-22&to=2026-10-22");
    await openRowMenu("Cao Thế Vinh");
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Chuyển bác sĩ" }),
    );
    await userEvent.click(
      await screen.findByRole("radio", { name: "BS. Đỗ Minh Tuấn" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Chuyển lịch hẹn" }),
    );
    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        "Lịch hẹn không còn ở trạng thái Đã đặt nên không thể chuyển.",
        expect.anything(),
      ),
    );
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  it("409 MSG-24: toast lỗi, giữ Dialog và tải lại ứng viên", async () => {
    open("from=2026-10-23&to=2026-10-23");
    await openRowMenu("Mai Thị Thu");
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Chuyển bác sĩ" }),
    );
    await userEvent.click(
      await screen.findByRole("radio", { name: "BS. Lê Quang Minh" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Chuyển lịch hẹn" }),
    );
    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        "Bác sĩ vừa chọn không còn khả dụng. Vui lòng chọn người khác.",
        expect.anything(),
      ),
    );
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(
      await screen.findByRole("radio", { name: "BS. Lê Quang Minh" }),
    ).not.toBeChecked();
  });
});

describe("AppointmentsManagement - giờ trống", () => {
  it("?tab=availability&doctorId= tự tải lưới", async () => {
    open(
      `tab=availability&doctorId=${DOCTOR_IDS.tuan}&from=2026-10-21&to=2026-10-21`,
    );
    expect(await screen.findByText("Thứ tư 21/10")).toBeInTheDocument();
    expect(screen.getAllByText("Trống").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Đã đặt").length).toBeGreaterThan(0);
  });

  it("chưa chọn bác sĩ thì không gọi API", async () => {
    open("tab=availability");
    expect(
      await screen.findByText("Chọn bác sĩ để xem giờ trống."),
    ).toBeInTheDocument();
    expect(getDoctorAvailability).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "Xem giờ trống" }),
    ).toBeDisabled();
  });

  it("bác sĩ không nhận đặt lịch: MSG-24 dưới ô bác sĩ, không gọi API", async () => {
    open(`tab=availability&doctorId=${DOCTOR_IDS.yen}`);
    expect(await screen.findByText(/không còn khả dụng/)).toBeInTheDocument();
    expect(getDoctorAvailability).not.toHaveBeenCalled();
  });

  it("không còn khung trống: toast cảnh báo MSG-25 và trạng thái trống", async () => {
    open(
      `tab=availability&doctorId=${DOCTOR_IDS.truc}&from=2026-10-21&to=2026-10-21`,
    );
    expect(
      await screen.findByText(
        "Chưa có giờ trống để hiển thị. Chọn ngày hoặc tuần khác.",
      ),
    ).toBeInTheDocument();
    expect(toast.warning).toHaveBeenCalledWith(
      "Không có giờ hẹn trống trong khoảng thời gian đã chọn. Vui lòng chọn thời gian khác.",
    );
  });

  it("lỗi 5xx: thông báo trong lưới kèm Thử lại, không toast", async () => {
    open(
      `tab=availability&doctorId=${DOCTOR_IDS.bao}&from=2026-10-21&to=2026-10-21`,
    );
    expect(
      await screen.findByRole("button", { name: "Thử lại" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Hệ thống đang gặp sự cố/)).toBeInTheDocument();
    expect(toast.error).not.toHaveBeenCalled();
  });
});
