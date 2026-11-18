import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { OrderListScreen } from "./OrderListScreen";

const ingest = vi.hoisted(() => ({ listAssignedImagingUploadWork: vi.fn() }));
const rooms = vi.hoisted(() => ({ listImagingRooms: vi.fn() }));
vi.mock("../api/imagingIngestApi", () => ingest);
vi.mock("../api/imagingRoomApi", () => rooms);

const work = (overrides: Record<string, unknown>) => ({
  requestId: "11111111-aaaa-bbbb-cccc-000000000001",
  attemptId: null,
  visitId: "v1",
  patient: { patientProfileId: "p1", fullName: "Trần Thị B", dateOfBirth: null, gender: null },
  currentStudyRef: null,
  serviceId: "s1",
  serviceName: "MRI cột sống",
  imagingType: "MRI",
  bodyArea: "Cột sống",
  clinicalReason: "Đau",
  technicianInstructions: null,
  roomId: "r1",
  status: "PENDING",
  version: 0,
  createdAt: "2026-11-18T01:00:00Z",
  updatedAt: "2026-11-18T01:00:00Z",
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  rooms.listImagingRooms.mockResolvedValue([
    { id: "r1", code: "MRI-01", name: "Phòng MRI", type: "IMAGING", description: "", capabilities: [], availability: "Hoạt động" },
  ]);
  ingest.listAssignedImagingUploadWork.mockResolvedValue({
    items: [
      work({}),
      work({ requestId: "22222222-aaaa-bbbb-cccc-000000000002", status: "RESULT_AVAILABLE", serviceName: "X-quang gối" }),
    ],
    page: 0,
    size: 100,
    totalElements: 2,
  });
});
afterEach(cleanup);

it("lists real orders with status and payment", async () => {
  render(<OrderListScreen statusMode />);
  expect(await screen.findByText("MRI cột sống")).toBeTruthy();
  expect(screen.getByText("X-quang gối")).toBeTruthy();
  expect(screen.getAllByText("Đã thanh toán").length).toBe(2);
  expect(screen.getAllByText("Đã có kết quả").length).toBe(2); // filter option + badge
});

it("filters by status", async () => {
  render(<OrderListScreen statusMode />);
  await screen.findByText("MRI cột sống");
  fireEvent.change(screen.getByLabelText("Lọc trạng thái"), { target: { value: "RESULT_AVAILABLE" } });
  await waitFor(() => expect(screen.queryByText("MRI cột sống")).toBeNull());
  expect(screen.getByText("X-quang gối")).toBeTruthy();
});

it("shows the mapped error when loading fails", async () => {
  ingest.listAssignedImagingUploadWork.mockRejectedValue(new Error("boom"));
  render(<OrderListScreen statusMode />);
  expect(await screen.findByRole("alert")).toBeTruthy();
});
