import { render, screen, waitFor } from "@testing-library/react";
import { toBookingPatient } from "../../schema/bookingSelection";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import type { BookedAppointment } from "../../types/booking";

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
// Bọc lớp API giả bằng vi.fn để đếm lời gọi và ép một lần lỗi; kho giả vẫn chạy thật.
vi.mock("../../api/bookingApi", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("../../api/bookingApi")>();
  return {
    ...original,
    listBookableServices: vi.fn(original.listBookableServices),
    getOpenTimes: vi.fn(original.getOpenTimes),
    createAppointment: vi.fn(original.createAppointment),
  };
});

import { getOpenTimes } from "../../api/bookingApi";
import { resetBookingMock } from "../../api/bookingMockStore";
import { BookingResult } from "../BookingResult";
import { BookingFlow } from "./BookingFlow";

// Thứ sáu 23/10/2026, 07:00 giờ VN: hôm nay còn đủ giờ sáng; ngày 27/10 là thứ ba.
const NOW = new Date("2026-10-23T07:00:00+07:00");

beforeEach(() => {
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  vi.clearAllMocks();
});

describe("BookingFlow - khu bệnh nhân (variant patient, wizard 5 bước)", () => {
  const next = (user: ReturnType<typeof userEvent.setup>) =>
    user.click(screen.getByRole("button", { name: "Tiếp tục" }));

  it("luôn mở ở bước Người khám và khóa Tiếp tục tới khi chọn hồ sơ", () => {
    render(
      <BookingFlow
        variant="patient"
        patients={getLinkedProfileRecords().map(toBookingPatient)}
        onBooked={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("heading", { level: 2, name: "Chọn người cần khám" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tiếp tục" })).toBeDisabled();
  });

  it("chọn người khám rồi dịch vụ; giờ trống được tra theo đúng hồ sơ đã chọn", async () => {
    const user = userEvent.setup();
    const profiles = getLinkedProfileRecords();
    const lan = profiles.find((profile) => profile.patientCode === "BN-000456");
    if (!lan) throw new Error("thiếu hồ sơ mẫu BN-000456");
    render(
      <BookingFlow
        variant="patient"
        patients={profiles.map(toBookingPatient)}
        onBooked={vi.fn()}
      />,
    );

    await user.click(
      screen.getByRole("radio", { name: /Nguyễn Thị Lan · Cha\/Mẹ/ }),
    );
    await next(user);
    await user.click(
      await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }),
    );

    await waitFor(() =>
      expect(getOpenTimes).toHaveBeenCalledWith(
        expect.objectContaining({
          patientId: lan.id,
          serviceId: "svc-kham-tq",
        }),
        expect.anything(),
      ),
    );
  });

  it("một hồ sơ: bước Người khám đã chọn sẵn hồ sơ đó, đi tiếp được ngay", async () => {
    const user = userEvent.setup();
    const self = getLinkedProfileRecords().filter(
      (profile) => profile.relationship === "SELF",
    );
    render(
      <BookingFlow
        variant="patient"
        patients={self.map(toBookingPatient)}
        onBooked={vi.fn()}
      />,
    );

    expect(screen.getByRole("radio", { name: /Chủ tài khoản/ })).toBeChecked();
    await next(user);
    expect(
      await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }),
    ).toBeInTheDocument();
  });

  it("bước Xác nhận gom đủ lựa chọn; Sửa đưa về đúng bước và giữ lựa chọn", async () => {
    const user = userEvent.setup();
    render(
      <BookingFlow
        variant="patient"
        patients={getLinkedProfileRecords().map(toBookingPatient)}
        onBooked={vi.fn()}
      />,
    );
    await user.click(
      screen.getByRole("radio", { name: /Nguyễn Thị Lan · Cha\/Mẹ/ }),
    );
    await next(user);
    await user.click(
      await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Thứ ba 27/10, còn giờ" }),
    );
    await user.click(await screen.findByRole("button", { name: "10:00" }));
    await next(user);

    expect(
      screen.getByRole("heading", { level: 2, name: "Kiểm tra lại lịch hẹn" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Nguyễn Thị Lan · Cha\/Mẹ/)).toBeInTheDocument();
    expect(screen.getByText("Phòng khám sắp xếp bác sĩ")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Đặt lịch khám" }),
    ).toBeDisabled();
    await user.type(
      screen.getByRole("textbox", { name: "Lý do khám" }),
      "Đau gối phải",
    );
    expect(screen.getByRole("button", { name: "Đặt lịch khám" })).toBeEnabled();

    await user.click(screen.getByRole("button", { name: "Sửa người khám" }));
    expect(
      screen.getByRole("radio", { name: /Nguyễn Thị Lan · Cha\/Mẹ/ }),
    ).toBeChecked();
  });

  it("không có hồ sơ nào: không mở luồng, báo thêm hồ sơ trước", () => {
    render(<BookingFlow variant="patient" patients={[]} onBooked={vi.fn()} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Chưa có hồ sơ bệnh nhân để đặt lịch.",
    );
  });
});

describe("BookingResult", () => {
  it("hiện người khám, dịch vụ, giờ, bác sĩ, phòng, phí và trạng thái; actions do trang truyền vào", () => {
    const booked: BookedAppointment = {
      id: "apt-9001",
      startAt: "2026-10-27T10:00:00+07:00",
      patient: { id: "pat-binh", fullName: "Trần Thị Bình" },
      service: { id: "svc-kham-cs", name: "Khám cột sống" },
      doctor: { id: "doc-ha", fullName: "BS. Phạm Thu Hà" },
      roomName: "Phòng khám 2",
      status: "BOOKED",
      examFee: { amount: 250000, status: "PENDING_PAYMENT" },
      isFollowUp: false,
    };
    render(
      <BookingResult
        appointment={booked}
        actions={<button type="button">Về danh sách</button>}
      />,
    );

    expect(
      screen.getByRole("heading", { level: 2, name: "Đã đặt lịch khám" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Trần Thị Bình · Khám cột sống"),
    ).toBeInTheDocument();
    const info = screen.getByRole("region", { name: "Thông tin lịch hẹn" });
    expect(info).toHaveTextContent("Thứ ba 27/10/2026 lúc 10:00");
    expect(info).toHaveTextContent("BS. Phạm Thu Hà");
    expect(info).toHaveTextContent("Phòng khám 2");
    const fee = screen.getByRole("region", { name: "Phí khám" });
    expect(fee).toHaveTextContent("250.000 ₫");
    expect(fee).toHaveTextContent("Chưa thanh toán");
    expect(screen.getByText("Đã đặt")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Về danh sách" }),
    ).toBeInTheDocument();
  });
});
