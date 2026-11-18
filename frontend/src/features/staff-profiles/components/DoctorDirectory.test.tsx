import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { fakePublicDoctorApi, vnInstant } from "@/test/publicDoctorFakeBackend";
import type { PublicDoctor } from "../types/publicDoctor";
import { dayMonthVi } from "../utils/availabilityDayLabel";
import { DoctorDirectory } from "./DoctorDirectory";

// Boundary: the public API client. The open-times API and the hook run for real on top of it.
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ publicApiRequest: request }));

const doctor = (id: string, fullName: string): PublicDoctor => ({
  id,
  fullName,
  photoUrl: null,
  specialties: ["Khớp gối"],
  qualifications: "BSCKI",
  biography: "",
  consultationInfo: "",
});
const DOCTORS = [
  doctor("doc-1", "Trần Văn Minh"),
  doctor("doc-2", "Lê Thị Hà"),
];

const tomorrow = addDays(todayIsoDate(), 1);
const slots = [
  {
    serviceId: "svc-a",
    doctorId: "doc-1",
    startAt: vnInstant(tomorrow, "09:30"),
  },
];

beforeEach(() => {
  request.mockReset();
});

describe("DoctorDirectory - giờ còn trống trên thẻ bác sĩ", () => {
  it("bác sĩ có giờ trống hiện ngày và giờ là liên kết đặt lịch đã chọn sẵn; bác sĩ không có thì ghi hết giờ", async () => {
    request.mockImplementation(
      fakePublicDoctorApi({ serviceIds: ["svc-a"], slots }),
    );
    render(<DoctorDirectory doctors={DOCTORS} />);

    const link = await screen.findByRole("link", {
      name: `Đặt lịch lúc 09:30 ngày ${dayMonthVi(tomorrow)}`,
    });
    expect(link).toHaveAttribute(
      "href",
      `/portal/appointments/new?serviceId=svc-a&doctorId=doc-1&date=${tomorrow}&time=09%3A30`,
    );
    expect(screen.getByText("Trần Văn Minh")).toBeInTheDocument();
    expect(screen.getAllByText("Hết giờ trong 7 ngày tới.")).toHaveLength(1);
  });

  it("đang tải thì hiện trạng thái chờ, tải xong thì trạng thái chờ biến mất", async () => {
    request.mockImplementation(
      fakePublicDoctorApi({ serviceIds: ["svc-a"], slots }),
    );
    render(<DoctorDirectory doctors={DOCTORS} />);

    expect(screen.getByRole("status")).toHaveTextContent(
      "Đang tải giờ còn trống…",
    );
    await screen.findByRole("link", { name: /Đặt lịch lúc 09:30/ });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("tải giờ lỗi: hiện MSG-04 kèm Thử lại; bấm lại thì có giờ trống", async () => {
    const user = userEvent.setup();
    request.mockImplementation(
      fakePublicDoctorApi({
        serviceIds: ["svc-a"],
        slots,
        failingAvailabilityCalls: 1,
      }),
    );
    render(<DoctorDirectory doctors={DOCTORS} />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.",
    );
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(
      await screen.findByRole("link", { name: /Đặt lịch lúc 09:30/ }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("chưa có bác sĩ công khai: câu trống và không gọi API giờ trống", () => {
    render(<DoctorDirectory doctors={[]} />);
    expect(
      screen.getByText("Chưa có bác sĩ nào được công khai hồ sơ."),
    ).toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
  });
});
