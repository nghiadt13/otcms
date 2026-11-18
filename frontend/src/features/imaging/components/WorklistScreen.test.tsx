import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { WorklistScreen } from "./WorklistScreen";

const queue = vi.hoisted(() => ({
  getServiceWorklist: vi.fn(),
  callNextService: vi.fn(),
  markServiceEntryAbsent: vi.fn(),
  returnServiceEntry: vi.fn(),
  reportCannotPerform: vi.fn(),
}));
const rooms = vi.hoisted(() => ({ listImagingRooms: vi.fn() }));
vi.mock("../api/imagingQueueApi", () => queue);
vi.mock("../api/imagingRoomApi", () => rooms);

const entry = (overrides: Record<string, unknown> = {}) => ({
  entryId: "e1",
  visitId: "v1",
  queueNumber: 7,
  status: "WAITING",
  sortOrder: 1,
  queuedAt: null,
  calledAt: null,
  patient: { patientProfileId: "p1", fullName: "Nguyễn Văn A", dateOfBirth: null, age: 42, gender: "MALE" },
  orders: [
    {
      orderId: "o1",
      orderCode: "CT-001",
      serviceName: "X-quang gối",
      serviceType: "IMAGING",
      status: "ORDERED",
      paid: true,
      clinicalReason: "Đau",
      bodyArea: "Gối trái",
      instructions: "Tháo trang sức",
      executionStatus: "PENDING",
    },
  ],
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  rooms.listImagingRooms.mockResolvedValue([
    { id: "r1", code: "XR-01", name: "Phòng X-quang", type: "IMAGING", description: "", capabilities: [], availability: "Hoạt động" },
  ]);
  queue.getServiceWorklist.mockResolvedValue({ roomId: "r1", entries: [entry()] });
  queue.callNextService.mockResolvedValue({ messageCode: "MSG-43" });
});
afterEach(cleanup);

it("shows the real queue with service name and instructions", async () => {
  render(<WorklistScreen />);
  expect(await screen.findByText("Nguyễn Văn A")).toBeTruthy();
  expect(screen.getByText("X-quang gối")).toBeTruthy();
  expect(screen.getByText(/Tháo trang sức/)).toBeTruthy();
  expect(queue.getServiceWorklist).toHaveBeenCalledWith("r1", expect.anything());
});

it("calls the next patient of the room and reloads", async () => {
  render(<WorklistScreen />);
  await screen.findByText("Nguyễn Văn A");
  fireEvent.click(screen.getByRole("button", { name: /Gọi tiếp theo/ }));
  await waitFor(() => expect(queue.callNextService).toHaveBeenCalledWith("r1"));
  await waitFor(() => expect(queue.getServiceWorklist.mock.calls.length).toBeGreaterThan(1));
});

it("shows MSG-95 and no link for an unpaid order", async () => {
  queue.getServiceWorklist.mockResolvedValue({
    roomId: "r1",
    entries: [entry({ orders: [{ ...entry().orders[0], paid: false }] })],
  });
  render(<WorklistScreen />);
  expect(await screen.findByText(/Chỉ định chưa được thanh toán/)).toBeTruthy();
  expect(screen.queryByRole("link", { name: "X-quang gối" })).toBeNull();
});

it("marks a called patient absent and returns an absent one with a reason", async () => {
  queue.getServiceWorklist.mockResolvedValue({ roomId: "r1", entries: [entry({ status: "CALLED" })] });
  render(<WorklistScreen />);
  fireEvent.click(await screen.findByRole("button", { name: "Đánh dấu vắng" }));
  await waitFor(() => expect(queue.markServiceEntryAbsent).toHaveBeenCalledWith("e1"));

  queue.getServiceWorklist.mockResolvedValue({ roomId: "r1", entries: [entry({ status: "ABSENT" })] });
  cleanup();
  render(<WorklistScreen />);
  fireEvent.click(await screen.findByRole("tab", { name: /Vắng mặt/ }));
  fireEvent.click(await screen.findByRole("button", { name: "Trở lại hàng đợi" }));
  fireEvent.change(screen.getByLabelText("Lý do đưa trở lại hàng"), { target: { value: "Đã quay lại" } });
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
  await waitFor(() => expect(queue.returnServiceEntry).toHaveBeenCalledWith("e1", "Đã quay lại"));
});

it("reports that a called patient's order cannot be performed with a reason", async () => {
  queue.reportCannotPerform.mockResolvedValue(undefined);
  queue.getServiceWorklist.mockResolvedValue({ roomId: "r1", entries: [entry({ status: "CALLED" })] });
  render(<WorklistScreen />);
  fireEvent.click(await screen.findByRole("button", { name: "Không thực hiện được" }));
  fireEvent.change(screen.getByLabelText("Lý do không thực hiện được"), { target: { value: "Có kim loại" } });
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
  await waitFor(() => expect(queue.reportCannotPerform).toHaveBeenCalledWith("o1", "Có kim loại"));
});
