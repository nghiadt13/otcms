import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import type { PatientArrivalCheckInResult } from "../../api/patientArrivalCheckInApi";
import type { PatientAppointment } from "../../types/patientAppointment";

// vi.mock được đưa lên đầu file: biến dùng trong factory khai báo bằng vi.hoisted.
// Ranh giới HTTP là apiRequest: lớp API thật chạy, chỉ thay đường gọi mạng.
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

import { PatientArrivalQrCheckIn } from "./PatientArrivalQrCheckIn";

const appointment: PatientAppointment = {
  id: "apt-1003",
  startAt: "2026-10-28T14:00:00+07:00",
  patient: {
    id: "profile-lan",
    fullName: "Nguyễn Thị Lan",
    relationship: "PARENT",
  },
  visitId: null,
  service: { id: "svc-kham-tq", name: "Khám Tổng quát" },
  doctor: { id: "doctor-tuan", fullName: "BS. Trần Anh Tuấn" },
  roomName: "Phòng khám 2",
  isFollowUp: false,
  status: "BOOKED",
  examFee: { amount: 200000, status: "PAID" },
  doctorUnavailable: false,
  allowedActions: ["cancel"],
};

function checkInResult(
  overrides: Partial<PatientArrivalCheckInResult> = {},
): PatientArrivalCheckInResult {
  return {
    appointmentId: "apt-1003",
    patientProfileId: "profile-lan",
    source: "QR",
    state: "CHECKED_IN",
    arrivedAt: "2026-10-28T09:00:00+07:00",
    recordedAt: "2026-10-28T09:00:00+07:00",
    appointmentStatus: "CHECKED_IN",
    visitId: "vis-lan-today",
    checkedInAt: "2026-10-28T09:00:00+07:00",
    checkedIn: true,
    queueNumber: 3,
    doctorName: "BS. Trần Anh Tuấn",
    roomLabel: "Phòng khám 2",
    ...overrides,
  };
}

/** Mở khung quét, dán mã rồi bấm xác nhận. */
async function submitQr(user: ReturnType<typeof userEvent.setup>, proof: string) {
  await user.click(screen.getByRole("button", { name: "Quét QR tại quầy" }));
  await user.type(screen.getByLabelText("Mã QR"), proof);
  await user.click(screen.getByRole("button", { name: "Xác nhận mã QR" }));
}

beforeEach(() => {
  apiRequest.mockReset();
  vi.mocked(toast.success).mockClear();
});

describe("PatientArrivalQrCheckIn: check-in thành công", () => {
  it("gửi mã tới đúng lịch, báo MSG-88 có số thứ tự và phòng, rồi báo cho màn cha", async () => {
    apiRequest.mockResolvedValueOnce(checkInResult());
    const onRecorded = vi.fn();
    const user = userEvent.setup();
    render(
      <PatientArrivalQrCheckIn
        appointment={appointment}
        onRecorded={onRecorded}
      />,
    );

    await submitQr(user, "qr-proof-1");

    await waitFor(() => expect(onRecorded).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/appointments/apt-1003/arrivals",
      { method: "POST", body: JSON.stringify({ proof: "qr-proof-1" }) },
    );
    expect(toast.success).toHaveBeenCalledWith(
      "Đã tiếp nhận bệnh nhân. Số thứ tự 3, phòng Phòng khám 2.",
    );
  });

  it("lịch chưa gắn phòng: MSG-88 ghi phòng là —", async () => {
    apiRequest.mockResolvedValueOnce(
      checkInResult({ queueNumber: 7, roomLabel: null }),
    );
    const user = userEvent.setup();
    render(<PatientArrivalQrCheckIn appointment={appointment} onRecorded={vi.fn()} />);

    await submitQr(user, "qr-proof-2");

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Đã tiếp nhận bệnh nhân. Số thứ tự 7, phòng —.",
      ),
    );
  });
});

describe("PatientArrivalQrCheckIn: check-in bị từ chối", () => {
  it.each([
    [
      "ngoài ngày hẹn",
      "CHECK_IN_TIME_NOT_ALLOWED",
      422,
      "Nếu không đúng ngày hẹn, hãy nhờ lễ tân hỗ trợ tiếp nhận.",
    ],
    [
      "phí khám chưa trả",
      "EXAM_FEE_UNPAID",
      409,
      "Hãy ra quầy lễ tân để thanh toán phí khám, rồi quét lại mã.",
    ],
    [
      "lịch đã check-in hoặc không còn Đã đặt",
      "APPOINTMENT_NOT_CHANGEABLE",
      409,
      "Hãy nhờ lễ tân kiểm tra giúp.",
    ],
    [
      "mã QR hỏng hoặc hết hạn",
      "ARRIVAL_PROOF_INVALID",
      422,
      "Hãy quét mã mới đang hiển thị tại quầy.",
    ],
  ])(
    "%s (%s): báo MSG-37 kèm nhãn hướng dẫn, không báo cho màn cha",
    async (_label, errorCode, status, hint) => {
      apiRequest.mockRejectedValueOnce(new ApiError(status, { errorCode }));
      const onRecorded = vi.fn();
      const user = userEvent.setup();
      render(
        <PatientArrivalQrCheckIn
          appointment={appointment}
          onRecorded={onRecorded}
        />,
      );

      await submitQr(user, "qr-proof-3");

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(formatAppMessage("MSG-37"));
      expect(alert).toHaveTextContent(hint);
      expect(onRecorded).not.toHaveBeenCalled();
      expect(toast.success).not.toHaveBeenCalled();
    },
  );

  it("lỗi không rõ mã: báo MSG-04", async () => {
    apiRequest.mockRejectedValueOnce(new ApiError(500, {}));
    const user = userEvent.setup();
    render(<PatientArrivalQrCheckIn appointment={appointment} onRecorded={vi.fn()} />);

    await submitQr(user, "qr-proof-4");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
  });
});
