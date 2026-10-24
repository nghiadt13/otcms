import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { addDays } from "@/lib/format/formatDate";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import type { BookedAppointment, BookingPatient } from "../../types/booking";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
// Bọc lớp API giả bằng vi.fn để đếm lời gọi và ép một lần lỗi; kho giả vẫn chạy thật.
vi.mock("../../api/bookingApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../api/bookingApi")>();
  return {
    ...original,
    listBookableServices: vi.fn(original.listBookableServices),
    getOpenTimes: vi.fn(original.getOpenTimes),
    createAppointment: vi.fn(original.createAppointment),
  };
});

import { DOCTOR_IDS } from "../../api/appointmentMockData";
import { getOpenTimes, listBookableServices } from "../../api/bookingApi";
import { resetBookingMock } from "../../api/bookingMockStore";
import { BookingResult } from "../BookingResult";
import { BookingFlow } from "./BookingFlow";

// Thứ sáu 23/10/2026, 07:00 giờ VN: hôm nay còn đủ giờ sáng; ngày 27/10 là thứ ba.
const NOW = new Date("2026-10-23T07:00:00+07:00");
const TODAY = "2026-10-23";
const BINH: BookingPatient = {
  id: "pat-binh",
  fullName: "Trần Thị Bình",
  dateOfBirth: "1985-09-02",
  relationship: null,
};

const errorTexts = () =>
  vi.mocked(toast.error).mock.calls.map((call) => call[0]);

beforeEach(() => {
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  vi.clearAllMocks();
});

describe("BookingFlow - khu Lễ tân (variant staff)", () => {
  it("luồng đủ: dịch vụ, ngày, giờ, phòng khám sắp xếp bác sĩ → onBooked có bác sĩ và toast MSG-26", async () => {
    const user = userEvent.setup();
    const onBooked = vi.fn();
    render(<BookingFlow variant="staff" patient={BINH} onBooked={onBooked} />);

    await user.click(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }));
    await user.click(await screen.findByRole("button", { name: "Thứ ba 27/10, còn giờ" }));
    await user.click(await screen.findByRole("button", { name: "10:00" }));
    expect(screen.getByRole("radio", { name: "Phòng khám sắp xếp bác sĩ" })).toBeChecked();
    await user.click(screen.getByRole("button", { name: "Đặt lịch khám" }));

    await waitFor(() => expect(onBooked).toHaveBeenCalledTimes(1));
    const booked = onBooked.mock.calls[0][0] as BookedAppointment;
    expect(booked.doctor.id).toBeTruthy();
    expect(booked.patient).toEqual({ id: "pat-binh", fullName: "Trần Thị Bình" });
    expect(booked.examFee).toEqual({ amount: 200000, status: "PENDING_PAYMENT" });
    expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-26"));
  });

  it("nút Đặt lịch khám bị khóa cho tới khi chọn đủ giờ", async () => {
    const user = userEvent.setup();
    render(<BookingFlow variant="staff" patient={BINH} onBooked={vi.fn()} />);

    await user.click(await screen.findByRole("radio", { name: /Khám cột sống/ }));
    await user.click(await screen.findByRole("button", { name: "Thứ ba 27/10, còn giờ" }));
    expect(screen.getByRole("button", { name: "Đặt lịch khám" })).toBeDisabled();

    await user.click(await screen.findByRole("button", { name: "10:00" }));
    expect(screen.getByRole("button", { name: "Đặt lịch khám" })).toBeEnabled();
  });

  it("MSG-30: giờ vừa bị đặt thì bỏ chọn, tải lại giờ và đưa focus về lưới giờ", async () => {
    const user = userEvent.setup();
    const onBooked = vi.fn();
    render(<BookingFlow variant="staff" patient={BINH} onBooked={onBooked} />);

    await user.click(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }));
    await user.click(await screen.findByRole("button", { name: "Thứ sáu 23/10, còn giờ" }));
    await user.click(await screen.findByRole("button", { name: "09:00" }));
    await user.click(screen.getByRole("button", { name: "Đặt lịch khám" }));

    await waitFor(() =>
      expect(errorTexts()).toContain(formatAppMessage("MSG-30")),
    );
    await waitFor(() =>
      expect(screen.getByRole("group", { name: "Giờ khám" })).toHaveFocus(),
    );
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "09:00" })).not.toBeInTheDocument(),
    );
    expect(onBooked).not.toHaveBeenCalled();
  });

  it("cả 14 ngày không còn giờ: báo MSG-25 và ghi chữ trong khối", async () => {
    const user = userEvent.setup();
    const empty = Array.from({ length: 14 }, (_, index) => ({
      date: addDays(TODAY, index),
      times: [],
    }));
    vi.mocked(getOpenTimes).mockResolvedValueOnce({
      serviceId: "svc-kham-tq",
      from: TODAY,
      to: addDays(TODAY, 13),
      slotMinutes: 15,
      serverNow: "2026-10-23T07:00:00+07:00",
      days: empty,
    });
    render(<BookingFlow variant="staff" patient={BINH} onBooked={vi.fn()} />);

    await user.click(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }));
    await waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith(formatAppMessage("MSG-25")),
    );
    expect(screen.getByRole("status")).toHaveTextContent(formatAppMessage("MSG-25"));
    expect(screen.getByRole("button", { name: "Thứ ba 27/10, hết giờ" })).toBeInTheDocument();
  });

  it("ngày Hết giờ vẫn bấm được và khối giờ ghi câu gợi ý chọn ngày khác", async () => {
    const user = userEvent.setup();
    render(<BookingFlow variant="staff" patient={BINH} onBooked={vi.fn()} />);

    await user.click(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }));
    await user.click(await screen.findByRole("button", { name: "Chủ nhật 25/10, hết giờ" }));
    expect(
      await screen.findByText("Ngày này đã kín lịch. Chọn ngày khác có chữ Còn giờ."),
    ).toBeInTheDocument();
  });

  it("bàn phím: Space chọn giờ khám (nút có aria-pressed)", async () => {
    const user = userEvent.setup();
    render(<BookingFlow variant="staff" patient={BINH} onBooked={vi.fn()} />);

    await user.click(await screen.findByRole("radio", { name: /Khám cột sống/ }));
    await user.click(await screen.findByRole("button", { name: "Thứ ba 27/10, còn giờ" }));
    const slot = await screen.findByRole("button", { name: "10:00" });
    slot.focus();
    await user.keyboard(" ");
    // Khối giờ đã chọn thu gọn thành dòng tóm tắt có "Đổi"; nút đặt bật lên.
    expect(await screen.findByRole("button", { name: "Đổi: Khám lúc mấy giờ?" })).toBeInTheDocument();
    expect(screen.getByText("10:00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đặt lịch khám" })).toBeEnabled();
  });

  it("MSG-24: bác sĩ đã chọn không còn nhận thì về phòng khám sắp xếp và tải lại giờ", async () => {
    const user = userEvent.setup();
    const actual = await vi.importActual<typeof import("../../api/bookingApi")>(
      "../../api/bookingApi",
    );
    // Danh sách lần tải đầu còn ghi BS. Tuấn ở 09:00 ngày 24/10, dù kho giả đã loại người này ngày mai.
    vi.mocked(getOpenTimes).mockImplementationOnce(async (query, signal) => {
      const response = await actual.getOpenTimes(query, signal);
      const slot = response.days
        .find((day) => day.date === "2026-10-24")
        ?.times.find((time) => time.startAt === "2026-10-24T09:00:00+07:00");
      if (!slot) throw new Error("thiếu giờ 09:00 ngày 24/10 trong dữ liệu giả");
      slot.doctors.push({ id: DOCTOR_IDS.tuan, fullName: "BS. Đỗ Minh Tuấn" });
      return response;
    });
    render(<BookingFlow variant="staff" patient={BINH} onBooked={vi.fn()} />);

    await user.click(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }));
    await user.click(await screen.findByRole("button", { name: "Thứ bảy 24/10, còn giờ" }));
    await user.click(await screen.findByRole("button", { name: "09:00" }));
    await user.click(await screen.findByRole("radio", { name: "BS. Đỗ Minh Tuấn" }));
    await user.click(screen.getByRole("button", { name: "Đặt lịch khám" }));

    await waitFor(() => expect(errorTexts()).toContain(formatAppMessage("MSG-24")));
    await waitFor(() =>
      expect(screen.getByRole("radio", { name: "Phòng khám sắp xếp bác sĩ" })).toBeChecked(),
    );
  });

  it("lỗi tải dịch vụ: báo lỗi trong khối và Thử lại tải lại danh sách", async () => {
    const user = userEvent.setup();
    vi.mocked(listBookableServices).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    render(<BookingFlow variant="staff" patient={BINH} onBooked={vi.fn()} />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không tải được danh sách dịch vụ.",
    );
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("radio", { name: /Khám cột sống/ })).toBeInTheDocument();
  });

  it("MSG-29 khi lấy giờ (người không đặt được): báo lỗi và bỏ chọn dịch vụ", async () => {
    const user = userEvent.setup();
    render(
      <BookingFlow
        variant="staff"
        patient={{ ...BINH, id: "pat-locked", fullName: "Hồ Thị Khóa" }}
        onBooked={vi.fn()}
      />,
    );

    await user.click(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }));
    await waitFor(() =>
      expect(errorTexts()).toContain(formatAppMessage("MSG-29")),
    );
    expect(await screen.findByRole("radio", { name: /Khám cột sống/ })).not.toBeChecked();
  });
});

describe("BookingFlow - khu bệnh nhân (variant patient)", () => {
  it("chọn 'Ai đi khám?' trước; giờ trống được tra theo đúng hồ sơ đã chọn", async () => {
    const user = userEvent.setup();
    const profiles = getLinkedProfileRecords();
    const lan = profiles.find((profile) => profile.patientCode === "BN-000456");
    if (!lan) throw new Error("thiếu hồ sơ mẫu BN-000456");
    render(<BookingFlow variant="patient" profiles={profiles} onBooked={vi.fn()} />);

    await user.click(await screen.findByRole("radio", { name: /Nguyễn Thị Lan · Cha\/Mẹ/ }));
    await user.click(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ }));

    await waitFor(() =>
      expect(getOpenTimes).toHaveBeenCalledWith(
        expect.objectContaining({ patientId: lan.id, serviceId: "svc-kham-tq" }),
        expect.anything(),
      ),
    );
    expect(screen.getByRole("button", { name: "Đổi: Ai đi khám?" })).toBeInTheDocument();
  });

  it("một hồ sơ: đã chọn sẵn, không hỏi lại và không có nút Đổi", async () => {
    const self = getLinkedProfileRecords().filter((profile) => profile.relationship === "SELF");
    render(<BookingFlow variant="patient" profiles={self} onBooked={vi.fn()} />);

    expect(screen.queryByRole("radio", { name: /Bản thân/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đổi: Ai đi khám?" })).not.toBeInTheDocument();
    expect(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ })).toBeInTheDocument();
  });

  it("initialPatientId có trong hồ sơ đã gắn: chọn sẵn người khám; mã không có trong hồ sơ thì bỏ qua", async () => {
    const profiles = getLinkedProfileRecords();
    const lan = profiles.find((profile) => profile.patientCode === "BN-000456");
    if (!lan) throw new Error("thiếu hồ sơ mẫu BN-000456");
    const { unmount } = render(
      <BookingFlow initialPatientId={lan.id} onBooked={vi.fn()} profiles={profiles} variant="patient" />,
    );
    expect(screen.getByRole("button", { name: "Đổi: Ai đi khám?" })).toBeInTheDocument();
    expect(await screen.findByRole("radio", { name: /Khám chỉnh hình tổng quát/ })).toBeInTheDocument();
    unmount();

    render(
      <BookingFlow initialPatientId="pat-khong-co" onBooked={vi.fn()} profiles={profiles} variant="patient" />,
    );
    expect(screen.queryByRole("button", { name: "Đổi: Ai đi khám?" })).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Nguyễn Thị Lan · Cha\/Mẹ/ })).not.toBeChecked();
  });

  it("không có hồ sơ nào: không mở luồng, báo thêm hồ sơ trước", () => {
    render(<BookingFlow variant="patient" profiles={[]} onBooked={vi.fn()} />);
    expect(screen.getByRole("status")).toHaveTextContent("Chưa có hồ sơ bệnh nhân để đặt lịch.");
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

    expect(screen.getByRole("heading", { level: 2, name: "Đã đặt lịch khám" })).toBeInTheDocument();
    expect(screen.getByText("Trần Thị Bình · Khám cột sống")).toBeInTheDocument();
    expect(
      screen.getByText("Thứ ba 27/10/2026 lúc 10:00 · BS. Phạm Thu Hà · Phòng khám 2"),
    ).toBeInTheDocument();
    expect(screen.getByText(/Phí khám/)).toHaveTextContent("250.000 ₫ · Chưa thanh toán");
    expect(screen.getByText("Đã đặt")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Về danh sách" })).toBeInTheDocument();
  });
});
