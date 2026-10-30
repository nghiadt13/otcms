import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetBookingMock } from "../../api/bookingMockStore";
import { resetPatientAppointmentMock } from "../../api/patientAppointmentApi";

// vi.mock được đưa lên đầu file: biến dùng trong factory phải khai báo bằng vi.hoisted.
const nav = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  search: "",
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  usePathname: () => "/portal/appointments",
  useSearchParams: () => new URLSearchParams(nav.search),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
// Bọc lớp API giả bằng vi.fn để ép một lần lỗi; kho giả vẫn chạy thật.
vi.mock("../../api/patientAppointmentApi", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("../../api/patientAppointmentApi")>();
  return {
    ...original,
    listMyAppointments: vi.fn(original.listMyAppointments),
  };
});

import { listMyAppointments } from "../../api/patientAppointmentApi";
import { PatientAppointmentList } from "./PatientAppointmentList";
import { formatAppMessage } from "@/lib/messages/appMessages";
const NOW = new Date("2026-10-23T07:00:00+07:00");

function idOf(patientCode: string): string {
  const profile = getLinkedProfileRecords().find(
    (item) => item.patientCode === patientCode,
  );
  if (!profile) throw new Error(`thiếu hồ sơ ${patientCode}`);
  return profile.id;
}

/** Các dòng lịch (cả dòng là link tới chi tiết). */
const rows = () =>
  screen
    .queryAllByRole("link")
    .filter((link) =>
      link.getAttribute("href")?.startsWith("/portal/appointments/apt-"),
    );

beforeEach(() => {
  nav.search = "";
  nav.push.mockClear();
  nav.replace.mockClear();
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  resetPatientAppointmentMock();
  vi.mocked(listMyAppointments).mockClear();
});

describe("PatientAppointmentList", () => {
  it("sắp tới: mỗi dòng ghi người khám và quan hệ, cả dòng là link tới chi tiết", async () => {
    render(<PatientAppointmentList />);

    expect(await screen.findAllByText("Đã đặt")).toHaveLength(4);
    expect(rows()).toHaveLength(4);
    // Tên người khám cũng nằm trong ô chọn; chỉ tính dòng là link.
    expect(
      screen.getByRole("link", { name: /Nguyễn Thị Lan · Cha\/Mẹ/ }),
    ).toHaveAttribute("href", "/portal/appointments/apt-1003");
    expect(
      screen.getByRole("link", { name: /Nguyễn Gia Huy · Con/ }),
    ).toHaveAttribute("href", "/portal/appointments/apt-2101");
    expect(rows()[0]).toHaveAttribute("href", "/portal/appointments/apt-1001");
  });

  it("đã qua: đọc tab từ URL, chia trang và nối thêm bằng Xem thêm", async () => {
    const user = userEvent.setup();
    nav.search = "tab=past";
    render(<PatientAppointmentList />);

    expect(await screen.findByRole("tab", { name: "Đã qua" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await waitFor(() => expect(rows()).toHaveLength(10));
    await user.click(screen.getByRole("button", { name: "Xem thêm" }));

    await waitFor(() => expect(rows()).toHaveLength(14));
    expect(
      screen.queryByRole("button", { name: "Xem thêm" }),
    ).not.toBeInTheDocument();
  });

  it("đổi tab đẩy URL ?tab=past", async () => {
    const user = userEvent.setup();
    render(<PatientAppointmentList />);
    await user.click(await screen.findByRole("tab", { name: "Đã qua" }));
    expect(nav.push).toHaveBeenCalledWith("/portal/appointments?tab=past");
  });

  it("chọn người khám thay URL ?profileId=; chỉ hiện lịch của người đó", async () => {
    const user = userEvent.setup();
    const lan = idOf("BN-000456");
    render(<PatientAppointmentList />);

    await user.selectOptions(
      await screen.findByRole("combobox", { name: "Người khám" }),
      lan,
    );
    expect(nav.replace).toHaveBeenCalledWith(
      `/portal/appointments?profileId=${lan}`,
    );
  });

  it("mã người khám trên URL đúng thì lọc; mã lạ bị bỏ qua và hiện tất cả", async () => {
    nav.search = `profileId=${idOf("BN-000456")}`;
    const { unmount } = render(<PatientAppointmentList />);
    await waitFor(() => expect(rows()).toHaveLength(1));
    expect(rows()[0]).toHaveTextContent("Nguyễn Thị Lan · Cha/Mẹ");
    unmount();

    nav.search = "profileId=pat-binh";
    render(<PatientAppointmentList />);
    await waitFor(() => expect(rows()).toHaveLength(4));
  });

  it("chưa có lịch sắp tới: câu dạy cách đặt, không hiện 'Không có dữ liệu'", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    render(<PatientAppointmentList />);
    expect(
      await screen.findByText(
        "Bạn chưa có lịch hẹn sắp tới. Bấm Đặt lịch khám ở trên để chọn giờ phù hợp.",
      ),
    ).toBeInTheDocument();
  });

  it("lỗi tải: nói rõ chuyện gì, Thử lại tải lại danh sách", async () => {
    const user = userEvent.setup();
    vi.mocked(listMyAppointments).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    render(<PatientAppointmentList />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      `Không tải được lịch hẹn. ${formatAppMessage("MSG-04")}`,
    );
    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findAllByText("Đã đặt")).toHaveLength(4);
  });
});
