import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import type { RoomQueueEntry } from "../api/receptionLiveApi";
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
    examStatus: "CALLED",
    patientName: "Nguyễn Minh Anh",
  }),
  item({
    visitId: "v2",
    queueNumber: 2,
    patientName: "Trần Thu",
    source: "WALK_IN",
    appointmentId: null,
  }),
  item({ visitId: "v3", queueNumber: 3, patientName: "Lê Hoa" }),
  item({
    visitId: "v4",
    queueNumber: 4,
    examStatus: "ABSENT",
    patientName: "Phạm Dũng",
  }),
  item({
    visitId: "v5",
    queueNumber: 5,
    examStatus: "AWAITING_CONCLUSION",
    patientName: "Hoàng Nam",
  }),
  item({
    visitId: "v6",
    queueNumber: 1,
    patientName: "Đỗ Hà",
    roomId: "room-202",
    roomLabel: "Phòng 202",
    doctorName: "BS Trần Bình",
  }),
  item({
    visitId: "v8",
    queueNumber: 6,
    patientName: "Mai Anh",
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

const entry = (
  id: string,
  visitId: string,
  queueNumber: number,
  status: string,
  sortOrder: number,
): RoomQueueEntry => ({
  id,
  visitId,
  queueNumber,
  stage: "EXAM",
  status,
  sortOrder,
});

/** Hàng của phòng 101 theo thứ tự BE: một người đang được gọi, hai người chờ, một người vắng. */
let roomRows: RoomQueueEntry[];

beforeEach(() => {
  request.mockReset();
  vi.mocked(toast.success).mockClear();
  vi.mocked(toast.error).mockClear();
  roomRows = [
    entry("e1", "v1", 1, "CALLED", 1000),
    entry("e2", "v2", 2, "WAITING", 2000),
    entry("e3", "v3", 3, "WAITING", 3000),
    entry("e4", "v4", 4, "ABSENT", 4000),
    entry("e5", "v5", 5, "AWAITING_CONCLUSION", 5000),
  ];
  request.mockImplementation(async (path: string) => {
    if (path === "/api/v1/exam-queue") return { items: queue };
    if (path === "/api/v1/queue/rooms/room-101?stage=EXAM") return roomRows;
    if (path === "/api/v1/queue/rooms/room-202?stage=EXAM") return [];
    if (path.startsWith("/api/v1/queue/"))
      return { messageCode: "MSG-43", entry: roomRows[0] };
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

const tableNamed = (name: string) => screen.findByRole("table", { name });
const waitingTable = () => tableNamed("Đang chờ ở Phòng 101");
const servingTable = () => tableNamed("Đang phục vụ ở Phòng 101");
const absentTable = () => tableNamed("Vắng mặt ở Phòng 101");

describe("QueueScreen (hàng chờ theo phòng, UC-32/UC-33)", () => {
  it("chọn phòng đầu tiên, chia hàng theo nhóm và không có nút đổi vị trí người đang có mặt", async () => {
    mount();

    const waiting = await waitingTable();
    const rows = within(waiting).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText("Trần Thu")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Trực tiếp")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Lê Hoa")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Lịch hẹn")).toBeInTheDocument();

    expect(
      within(await servingTable()).getByText("Nguyễn Minh Anh"),
    ).toBeInTheDocument();
    expect(
      within(await absentTable()).getByRole("button", { name: "Đưa lại" }),
    ).toBeEnabled();
    expect(
      within(await tableNamed("Chờ kết luận ở Phòng 101")).getByText(
        "Hoàng Nam",
      ),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole("button", {
        name: /đổi thứ tự|đổi phòng|đổi vị trí/i,
      }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Gọi tiếp" })).toBeDisabled();
    expect(request).toHaveBeenCalledWith("/api/v1/exam-queue");
    expect(request).toHaveBeenCalledWith(
      "/api/v1/queue/rooms/room-101?stage=EXAM",
      expect.anything(),
    );

    const calls = request.mock.calls.length;
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Làm mới" }));
    await waitFor(() =>
      expect(request.mock.calls.length).toBeGreaterThan(calls),
    );
  });

  it("lượt chờ thu phí và lượt chưa có phòng nằm riêng", async () => {
    mount();

    expect(
      await screen.findByRole("heading", { name: "Chờ thu phí" }),
    ).toBeInTheDocument();
    const pending = await tableNamed("Lượt chờ thu phí");
    expect(within(pending).getByText("Võ Lan")).toBeInTheDocument();
    expect(
      within(pending).getByRole("button", { name: "Thu phí" }),
    ).toBeEnabled();
    expect(await tableNamed("Lượt chưa có phòng")).toHaveTextContent("Mai Anh");
    expect(await servingTable()).not.toHaveTextContent("Võ Lan");
  });

  it("Gọi tiếp khi phòng trống gửi call-next đúng phòng và báo MSG-43", async () => {
    const user = userEvent.setup();
    roomRows = roomRows.map((row) =>
      row.id === "e1" ? { ...row, status: "WAITING" } : row,
    );
    mount();

    const callNext = await screen.findByRole("button", { name: "Gọi tiếp" });
    expect(callNext).toBeEnabled();
    await user.click(callNext);

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        "/api/v1/queue/rooms/room-101/call-next?stage=EXAM",
        { method: "POST" },
      ),
    );
    expect(toast.success).toHaveBeenCalledWith(APP_MESSAGES["MSG-43"]);
  });

  it("Vắng gửi absence cho người đang được gọi", async () => {
    const user = userEvent.setup();
    mount();

    await user.click(
      within(await servingTable()).getByRole("button", { name: "Vắng" }),
    );

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith("/api/v1/queue/entries/e1/absence", {
        method: "POST",
      }),
    );
    expect(toast.success).toHaveBeenCalledWith(APP_MESSAGES["MSG-43"]);
  });

  it("Đưa lại bắt buộc có lý do: thiếu lý do báo MSG-39 và không gửi", async () => {
    const user = userEvent.setup();
    mount();

    await user.click(
      within(await absentTable()).getByRole("button", { name: "Đưa lại" }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Đưa lại" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-39"],
    );
    expect(
      request.mock.calls.some(([path]) => String(path).endsWith("/return")),
    ).toBe(false);
  });

  it("Đưa lại chèn trước người đang chờ đã chọn và gửi lý do đã cắt khoảng trắng", async () => {
    const user = userEvent.setup();
    mount();

    await user.click(
      within(await absentTable()).getByRole("button", { name: "Đưa lại" }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.selectOptions(
      within(dialog).getByLabelText("Vị trí trong hàng"),
      "e3",
    );
    await user.type(
      within(dialog).getByLabelText("Lý do"),
      "  Quay lại sau khi đi xét nghiệm  ",
    );
    await user.click(within(dialog).getByRole("button", { name: "Đưa lại" }));

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith("/api/v1/queue/entries/e4/return", {
        method: "POST",
        body: JSON.stringify({
          beforeEntryId: "e3",
          reason: "Quay lại sau khi đi xét nghiệm",
        }),
      }),
    );
    expect(toast.success).toHaveBeenCalledWith(APP_MESSAGES["MSG-43"]);
  });

  it("Đưa lại chọn cuối hàng gửi beforeEntryId rỗng", async () => {
    const user = userEvent.setup();
    mount();

    await user.click(
      within(await absentTable()).getByRole("button", { name: "Đưa lại" }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Lý do"), "Đi vệ sinh");
    await user.click(within(dialog).getByRole("button", { name: "Đưa lại" }));

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith("/api/v1/queue/entries/e4/return", {
        method: "POST",
        body: JSON.stringify({ beforeEntryId: null, reason: "Đi vệ sinh" }),
      }),
    );
  });

  it("lỗi nghiệp vụ của thao tác hiện đúng mã MSG", async () => {
    const user = userEvent.setup();
    roomRows = roomRows.map((row) =>
      row.id === "e1" ? { ...row, status: "WAITING" } : row,
    );
    request.mockImplementation(async (path: string) => {
      if (path === "/api/v1/exam-queue") return { items: queue };
      if (path === "/api/v1/queue/rooms/room-101?stage=EXAM") return roomRows;
      throw new ApiError(409, { code: "MSG-42" });
    });
    mount();

    await user.click(await screen.findByRole("button", { name: "Gọi tiếp" }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        APP_MESSAGES["MSG-42"],
        expect.anything(),
      ),
    );
  });

  it("lỗi tải hiện đúng mã MSG và cho thử lại", async () => {
    const user = userEvent.setup();
    request.mockRejectedValueOnce(new ApiError(403, { code: "MSG-38" }));
    mount();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-38"],
    );
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await waitingTable()).toBeInTheDocument();
  });

  it("hàng chờ rỗng hiện hướng dẫn thay vì bảng trống", async () => {
    request.mockResolvedValueOnce({ items: [] });
    mount();

    expect(
      await screen.findByText(/Chưa có bệnh nhân trong hàng chờ hôm nay/),
    ).toBeInTheDocument();
  });

  it("đổi bác sĩ ở hàng đang chờ mở hộp thoại đổi bác sĩ cho đúng lượt", async () => {
    const user = userEvent.setup();
    mount();

    const waiting = await waitingTable();
    await user.click(
      within(waiting).getAllByRole("button", { name: "Đổi bác sĩ" })[0],
    );

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Bệnh nhân Trần Thu/)).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận đổi" }),
    ).toBeDisabled();
  });
});
