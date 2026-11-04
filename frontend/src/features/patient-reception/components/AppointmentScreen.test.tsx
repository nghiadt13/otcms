import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { ReceptionProvider } from "./ReceptionProvider";
import { AppointmentScreen } from "./AppointmentScreen";
import { listReceptionAppointments } from "../api/receptionLiveApi";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("../api/receptionLiveApi", () => ({
  listReceptionAppointments: vi.fn(),
}));

const today = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Ho_Chi_Minh",
}).format(new Date());
const appointment = (over: object) => ({
  id: "11111111-aaaa-bbbb-cccc-000000000001",
  patientProfileId: "22222222-aaaa-bbbb-cccc-000000000001",
  patientName: "Lê Thị Hạnh",
  serviceId: "s1",
  serviceName: "Khám xương khớp",
  servicePrice: 200000,
  doctorId: "d1",
  doctorDisplayName: "BS. Trần Văn An",
  status: "CONFIRMED",
  startTime: `${today}T09:20:00+07:00`,
  visitId: null,
  examFeePaid: null,
  ...over,
});
function mount() {
  return render(
    <ReceptionProvider>
      <AppointmentScreen />
    </ReceptionProvider>,
  );
}

describe("reception appointment list (live API)", () => {
  beforeEach(() => {
    vi.mocked(listReceptionAppointments).mockResolvedValue({
      items: [
        appointment({}),
        appointment({
          id: "11111111-aaaa-bbbb-cccc-000000000002",
          patientName: "Phạm Quốc Bảo",
          status: "CHECKED_IN",
          startTime: `${today}T10:00:00+07:00`,
        }),
      ],
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 1,
    });
  });

  it("asks the API for today's appointments and shows patient name, doctor and status", async () => {
    mount();
    expect(await screen.findByText("Lê Thị Hạnh")).toBeInTheDocument();
    expect(listReceptionAppointments).toHaveBeenCalledWith(
      expect.objectContaining({ from: today, to: today }),
      expect.anything(),
    );
    const row = screen.getByText("Lê Thị Hạnh").closest("tr")!;
    expect(within(row).getByText("BS. Trần Văn An")).toBeInTheDocument();
    expect(within(row).getByText("Đã đặt lịch")).toBeInTheDocument();
    expect(
      screen.getByText("Phạm Quốc Bảo").closest("tr")!,
    ).toHaveTextContent("Đã tiếp nhận");
  });

  it("shows backend UTC instants in clinic (Vietnam) time", async () => {
    vi.mocked(listReceptionAppointments).mockResolvedValue({
      items: [appointment({ startTime: `${today}T12:00:00Z` })],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    mount();
    const row = (await screen.findByText("Lê Thị Hạnh")).closest("tr")!;
    expect(within(row).getByText("19:00")).toBeInTheDocument();
  });

  it("offers check-in only for confirmed appointments today", async () => {
    mount();
    await screen.findByText("Lê Thị Hạnh");
    const confirmed = screen.getByText("Lê Thị Hạnh").closest("tr")!;
    expect(within(confirmed).getByRole("link", { name: "Tiếp nhận" })).toHaveAttribute(
      "href",
      "/reception?patientId=22222222-aaaa-bbbb-cccc-000000000001&appointmentId=11111111-aaaa-bbbb-cccc-000000000001",
    );
    const checkedIn = screen.getByText("Phạm Quốc Bảo").closest("tr")!;
    expect(
      within(checkedIn).queryByRole("link", { name: "Tiếp nhận" }),
    ).not.toBeInTheDocument();
  });

  it("filters by patient name and opens read-only detail", async () => {
    mount();
    await screen.findByText("Lê Thị Hạnh");
    fireEvent.change(screen.getByLabelText("Tên bệnh nhân / mã lịch"), {
      target: { value: "bảo" },
    });
    expect(screen.queryByText("Lê Thị Hạnh")).not.toBeInTheDocument();
    const row = screen.getByText("Phạm Quốc Bảo").closest("tr")!;
    fireEvent.click(within(row).getByRole("button", { name: "Chi tiết" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Khám xương khớp")).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("button", { name: /hủy|đổi/i }),
    ).not.toBeInTheDocument();
  });
});
