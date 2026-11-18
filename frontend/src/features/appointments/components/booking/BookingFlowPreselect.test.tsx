import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import type { BookingSlotQuery } from "@/lib/routes/bookingSlotLink";
import { getOpenTimes } from "../../api/bookingApi";
import { resetBookingMock } from "../../api/bookingMockStore";
import { toBookingPatient } from "../../schema/bookingSelection";
import { hhmmOfStart } from "../../schema/bookingTime";
import { BookingFlow } from "./BookingFlow";

// Outer boundary: the live API would call the backend. Here it serves the same mock store as the wizard tests.
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

// Friday 23/10/2026, 07:00 Vietnam time (same clock as BookingFlow.test).
const NOW = new Date("2026-10-23T07:00:00+07:00");
const SERVICE_ID = "svc-kham-tq";
const MSG_24 = "Bác sĩ này hiện không khả dụng. Vui lòng chọn bác sĩ khác.";
const MSG_29 =
  "Không thể thực hiện với lịch hẹn hoặc thông tin đã chọn. Vui lòng kiểm tra lại.";
const MSG_30 = "Giờ hẹn đã chọn không còn trống. Vui lòng chọn giờ khác.";

beforeEach(() => {
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  vi.clearAllMocks();
});

/** A real open time from the mock store, so the tests do not depend on the seed details. */
async function openSlot(patientId: string) {
  const open = await getOpenTimes({ serviceId: SERVICE_ID, patientId });
  for (const day of open.days) {
    const time = day.times.find((item) => item.doctors.length > 0);
    if (time) return { day, time };
  }
  throw new Error("thiếu giờ trống trong dữ liệu mẫu");
}

const linkFor = (
  slot: Pick<BookingSlotQuery, "date" | "time"> & Partial<BookingSlotQuery>,
): BookingSlotQuery => ({
  serviceId: SERVICE_ID,
  doctorId: null,
  ...slot,
});

const selfProfiles = () =>
  getLinkedProfileRecords()
    .filter((profile) => profile.relationship === "SELF")
    .map(toBookingPatient);

describe("BookingFlow - chọn sẵn khung giờ từ link trang bác sĩ", () => {
  it("giờ còn trống: mở ở bước Dịch vụ và lịch, tiếp tục tới xác nhận với bác sĩ đã chọn", async () => {
    const user = userEvent.setup();
    const patients = selfProfiles();
    const { day, time } = await openSlot(patients[0].id);
    const doctor = time.doctors[0];

    render(
      <BookingFlow
        variant="patient"
        patients={patients}
        onBooked={vi.fn()}
        preselect={linkFor({
          doctorId: doctor.id,
          date: day.date,
          time: hhmmOfStart(time.startAt),
        })}
      />,
    );

    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Chọn loại khám và lịch hẹn",
      }),
    ).toBeInTheDocument();
    // The schedule step opens before the open times arrive; continue only once the slot is applied.
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Tiếp tục" })).toBeEnabled(),
    );
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Kiểm tra lại lịch hẹn",
      }),
    ).toBeInTheDocument();
    expect(screen.getAllByText(doctor.fullName).length).toBeGreaterThan(0);
    expect(toast.error).not.toHaveBeenCalled();
  });

  it("giờ đã hết: báo MSG-30, giữ ngày đã chọn và bắt chọn lại giờ", async () => {
    const patients = selfProfiles();
    const { day } = await openSlot(patients[0].id);

    render(
      <BookingFlow
        variant="patient"
        patients={patients}
        onBooked={vi.fn()}
        preselect={linkFor({ date: day.date, time: "23:45" })}
      />,
    );

    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Chọn loại khám và lịch hẹn",
      }),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(MSG_30, expect.anything()),
    );
    expect(screen.getByRole("button", { name: "Tiếp tục" })).toBeDisabled();
  });

  it("bác sĩ không còn ở giờ đó: báo MSG-24, giữ giờ và để phòng khám sắp xếp bác sĩ", async () => {
    const user = userEvent.setup();
    const patients = selfProfiles();
    const { day, time } = await openSlot(patients[0].id);

    render(
      <BookingFlow
        variant="patient"
        patients={patients}
        onBooked={vi.fn()}
        preselect={linkFor({
          doctorId: "doc-khong-con-trong",
          date: day.date,
          time: hhmmOfStart(time.startAt),
        })}
      />,
    );

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(MSG_24, expect.anything()),
    );
    await user.click(await screen.findByRole("button", { name: "Tiếp tục" }));
    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Kiểm tra lại lịch hẹn",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("Phòng khám sắp xếp bác sĩ")).toBeInTheDocument();
  });

  it("dịch vụ trong link không đặt được: báo MSG-29 và không chọn dịch vụ", async () => {
    const patients = selfProfiles();
    const { day, time } = await openSlot(patients[0].id);

    render(
      <BookingFlow
        variant="patient"
        patients={patients}
        onBooked={vi.fn()}
        preselect={{
          serviceId: "svc-khong-dat-duoc",
          doctorId: null,
          date: day.date,
          time: hhmmOfStart(time.startAt),
        }}
      />,
    );

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(MSG_29, expect.anything()),
    );
    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Chọn loại khám và lịch hẹn",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tiếp tục" })).toBeDisabled();
  });

  it("nhiều người khám: chọn sẵn chờ đến khi người dùng chọn người khám rồi tiếp tục", async () => {
    const user = userEvent.setup();
    const profiles = getLinkedProfileRecords();
    const lan = profiles.find((profile) => profile.patientCode === "BN-000456");
    if (!lan) throw new Error("thiếu hồ sơ mẫu BN-000456");
    const { day, time } = await openSlot(lan.id);

    render(
      <BookingFlow
        variant="patient"
        patients={profiles.map(toBookingPatient)}
        onBooked={vi.fn()}
        preselect={linkFor({
          doctorId: time.doctors[0].id,
          date: day.date,
          time: hhmmOfStart(time.startAt),
        })}
      />,
    );

    expect(
      screen.getByRole("heading", { level: 2, name: "Chọn người cần khám" }),
    ).toBeInTheDocument();
    // Nothing is applied, and no warning is raised, before a patient is chosen.
    expect(toast.error).not.toHaveBeenCalled();

    await user.click(
      screen.getByRole("radio", { name: /Nguyễn Thị Lan · Cha\/Mẹ/ }),
    );
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Chọn loại khám và lịch hẹn",
      }),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Tiếp tục" })).toBeEnabled(),
    );
  });
});
