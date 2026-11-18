import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { fakePublicDoctorApi, vnInstant } from "@/test/publicDoctorFakeBackend";
import type { PublicDoctor } from "../types/publicDoctor";
import { dayMonthVi } from "../utils/availabilityDayLabel";
import { DoctorPublicProfile } from "./DoctorPublicProfile";

// Boundary: the public API client. The open-times API and the section hook run for real on top of it.
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ publicApiRequest: request }));

const DOCTOR: PublicDoctor = {
  id: "doc-1",
  fullName: "Trần Văn Minh",
  photoUrl: null,
  specialties: ["Khớp gối"],
  qualifications: "BSCKI",
  biography: "Giới thiệu",
  consultationInfo: "Thông tin khám",
};

const tomorrow = addDays(todayIsoDate(), 1);
const schedule = () =>
  screen.getByRole("region", { name: "Lịch nhận khám 7 ngày tới" });

beforeEach(() => {
  request.mockReset();
});

describe("DoctorPublicProfile - lịch 7 ngày với giờ còn trống", () => {
  it("hiện 7 ngày; giờ trống là liên kết đặt lịch với bác sĩ đã chọn; trùng giờ giữ dịch vụ đầu tiên", async () => {
    request.mockImplementation(
      fakePublicDoctorApi({
        serviceIds: ["svc-a", "svc-b"],
        slots: [
          {
            serviceId: "svc-a",
            doctorId: "doc-1",
            startAt: vnInstant(tomorrow, "09:30"),
          },
          {
            serviceId: "svc-b",
            doctorId: "doc-1",
            startAt: vnInstant(tomorrow, "09:30"),
          },
          {
            serviceId: "svc-b",
            doctorId: "doc-1",
            startAt: vnInstant(tomorrow, "10:00"),
          },
        ],
      }),
    );
    render(<DoctorPublicProfile doctor={DOCTOR} />);

    const first = await within(schedule()).findByRole("link", {
      name: `Đặt lịch lúc 09:30 ngày ${dayMonthVi(tomorrow)}`,
    });
    expect(first).toHaveAttribute(
      "href",
      `/portal/appointments/new?serviceId=svc-a&doctorId=doc-1&date=${tomorrow}&time=09%3A30`,
    );
    expect(
      within(schedule()).getByRole("link", {
        name: `Đặt lịch lúc 10:00 ngày ${dayMonthVi(tomorrow)}`,
      }),
    ).toHaveAttribute(
      "href",
      `/portal/appointments/new?serviceId=svc-b&doctorId=doc-1&date=${tomorrow}&time=10%3A00`,
    );
    // Two free times on one day, so the other six days read "Hết giờ".
    expect(within(schedule()).getAllByRole("link")).toHaveLength(2);
    expect(within(schedule()).getAllByText("Hết giờ")).toHaveLength(6);
  });

  it("nút đặt lịch chung vẫn mở màn đặt lịch không chọn sẵn", async () => {
    request.mockImplementation(
      fakePublicDoctorApi({ serviceIds: [], slots: [] }),
    );
    render(<DoctorPublicProfile doctor={DOCTOR} />);
    expect(screen.getByRole("link", { name: /Đặt lịch khám/ })).toHaveAttribute(
      "href",
      "/portal/appointments/new",
    );
    expect(await within(schedule()).findAllByText("Hết giờ")).toHaveLength(7);
  });

  it("tải giờ lỗi: hiện MSG-04 kèm Thử lại; bấm lại thì có giờ", async () => {
    const user = userEvent.setup();
    request.mockImplementation(
      fakePublicDoctorApi({
        serviceIds: ["svc-a"],
        slots: [
          {
            serviceId: "svc-a",
            doctorId: "doc-1",
            startAt: vnInstant(tomorrow, "09:30"),
          },
        ],
        failingAvailabilityCalls: 1,
      }),
    );
    render(<DoctorPublicProfile doctor={DOCTOR} />);

    expect(await within(schedule()).findByRole("alert")).toHaveTextContent(
      "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.",
    );
    await user.click(
      within(schedule()).getByRole("button", { name: "Thử lại" }),
    );
    expect(
      await within(schedule()).findByRole("link", {
        name: /Đặt lịch lúc 09:30/,
      }),
    ).toBeInTheDocument();
  });
});
