import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { DashboardScreen } from "./DashboardScreen";

const queue = vi.hoisted(() => ({ getServiceWorklist: vi.fn(), callNextService: vi.fn() }));
const ingest = vi.hoisted(() => ({ listAssignedImagingUploadWork: vi.fn() }));
const rooms = vi.hoisted(() => ({ listImagingRooms: vi.fn() }));
vi.mock("../api/imagingQueueApi", () => queue);
vi.mock("../api/imagingIngestApi", () => ingest);
vi.mock("../api/imagingRoomApi", () => rooms);

const entry = (status: string, number: number, name: string) => ({
  entryId: `e${number}`,
  visitId: `v${number}`,
  queueNumber: number,
  status,
  sortOrder: number,
  queuedAt: null,
  calledAt: null,
  patient: { patientProfileId: `p${number}`, fullName: name, dateOfBirth: null, age: 30, gender: null },
  orders: [{ orderId: `o${number}`, orderCode: `C${number}`, serviceName: "X-quang", serviceType: "IMAGING", status: "ORDERED", paid: true, clinicalReason: null, bodyArea: null, instructions: null, executionStatus: "PENDING" }],
});

beforeEach(() => {
  vi.clearAllMocks();
  rooms.listImagingRooms.mockResolvedValue([
    { id: "r1", code: "XR-01", name: "Phòng X-quang", type: "IMAGING", description: "", capabilities: [], availability: "Hoạt động" },
  ]);
  queue.getServiceWorklist.mockResolvedValue({
    roomId: "r1",
    entries: [entry("CALLED", 1, "Người đang được gọi"), entry("WAITING", 2, "Người chờ"), entry("ABSENT", 3, "Người vắng")],
  });
  queue.callNextService.mockResolvedValue({ messageCode: "MSG-43" });
  ingest.listAssignedImagingUploadWork.mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0 });
});
afterEach(cleanup);

it("summarises the real queue of the room", async () => {
  render(<DashboardScreen />);
  expect(await screen.findByText("Phòng X-quang")).toBeTruthy();
  expect(screen.getByText(/Đang phục vụ: 1 · Người đang được gọi/)).toBeTruthy();
  expect(screen.getByText("Người chờ")).toBeTruthy();
});

it("calls the next patient of the room", async () => {
  render(<DashboardScreen />);
  fireEvent.click(await screen.findByRole("button", { name: /Gọi bệnh nhân tiếp theo/ }));
  await waitFor(() => expect(queue.callNextService).toHaveBeenCalledWith("r1"));
});
