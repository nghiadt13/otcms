import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/lib/api/apiError";
import { ReceptionProvider } from "./ReceptionProvider";
import { QueueScreen } from "./QueueScreen";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));

const item = (overrides: Record<string, unknown>) => ({
  visitId: "v",
  queueNumber: 1,
  examStatus: "WAITING",
  source: "SCHEDULED",
  appointmentId: "apt",
  patientProfileId: "pat",
  patientName: "Bệnh nhân",
  doctorProfileId: "doc-a",
  doctorName: "BS Nguyễn An",
  roomId: "room-101",
  roomLabel: "Phòng 101",
  serviceId: "svc-kham",
  queuedAt: "2026-11-11T08:05:00+07:00",
  startedAt: null,
  finishedAt: null,
  ...overrides,
});

const queue = [
  item({
    visitId: "v1",
    queueNumber: 1,
    patientName: "Nguyễn Minh Anh",
    source: "SCHEDULED",
  }),
  item({
    visitId: "v2",
    queueNumber: 2,
    patientName: "Trần Thu",
    source: "WALK_IN",
    appointmentId: null,
  }),
  item({
    visitId: "v3",
    queueNumber: 3,
    examStatus: "IN_EXAMINATION",
    patientName: "Lê Hoa",
  }),
  item({
    visitId: "v4",
    queueNumber: 1,
    patientName: "Phạm Dũng",
    doctorProfileId: "doc-b",
    doctorName: "BS Trần Bình",
    roomId: null,
    roomLabel: null,
  }),
  item({
    visitId: "v9",
    queueNumber: null,
    examStatus: "PENDING_PAYMENT",
    patientName: "Võ Lan",
    source: "WALK_IN",
    appointmentId: null,
    queuedAt: null,
  }),
];

beforeEach(() => {
  request.mockReset();
  request.mockImplementation(async (path: string) => {
    if (path === "/api/v1/exam-queue") return { items: queue };
    throw new Error(`Unexpected request ${path}`);
  });
});

function mount() {
  return render(
    <ReceptionProvider>
      <QueueScreen />
    </ReceptionProvider>,
  );
}

describe("QueueScreen (hàng chờ khám, dữ liệu BE)", () => {
  it("nhóm theo bác sĩ, hiện phòng và số đang chờ, không hiện nút điều phối đã bỏ", async () => {
    const user = userEvent.setup();
    mount();

    expect(
      await screen.findByRole("heading", { name: "BS Nguyễn An" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Phòng 101 · 2 đang chờ")).toBeInTheDocument();
    expect(screen.getByText("Chưa có phòng · 1 đang chờ")).toBeInTheDocument();

    const table = screen.getByRole("table", {
      name: "Hàng chờ của BS Nguyễn An",
    });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByText("Nguyễn Minh Anh")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Lịch hẹn")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Trực tiếp")).toBeInTheDocument();
    expect(within(rows[2]).getByText("Đang khám")).toBeInTheDocument();

    expect(
      screen.queryByRole("button", {
        name: /gọi bệnh nhân|vắng mặt|đổi thứ tự|đổi phòng/i,
      }),
    ).not.toBeInTheDocument();
    expect(request).toHaveBeenCalledWith("/api/v1/exam-queue");
    const calls = request.mock.calls.length;
    await user.click(screen.getByRole("button", { name: "Làm mới" }));
    await waitFor(() =>
      expect(request.mock.calls.length).toBeGreaterThan(calls),
    );
  });

  it("lượt chờ thu phí nằm riêng và mở được bước thu phí", async () => {
    mount();

    expect(
      await screen.findByRole("heading", { name: "Chờ thu phí" }),
    ).toBeInTheDocument();
    const pending = screen.getByRole("table", { name: "Lượt chờ thu phí" });
    expect(within(pending).getByText("Võ Lan")).toBeInTheDocument();
    expect(
      within(pending).getByRole("button", { name: "Thu phí" }),
    ).toBeEnabled();
    expect(
      screen.getByRole("table", { name: "Hàng chờ của BS Nguyễn An" }),
    ).not.toHaveTextContent("Võ Lan");
  });

  it("lỗi tải hiện đúng mã MSG và cho thử lại", async () => {
    const user = userEvent.setup();
    request.mockRejectedValueOnce(new ApiError(403, { code: "MSG-38" }));
    mount();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không thể xem danh sách chờ hoặc lượt khám này.",
    );
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(
      await screen.findByRole("heading", { name: "BS Nguyễn An" }),
    ).toBeInTheDocument();
  });

  it("hàng chờ rỗng hiện hướng dẫn thay vì bảng trống", async () => {
    request.mockResolvedValueOnce({ items: [] });
    mount();

    expect(
      await screen.findByText(/Chưa có bệnh nhân trong hàng chờ hôm nay/),
    ).toBeInTheDocument();
  });
});
