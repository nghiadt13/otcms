import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { StaffRoomsScreen } from "./StaffRoomsScreen";

const api = vi.hoisted(() => ({
  listStaffRooms: vi.fn(),
  getStaffRoom: vi.fn(),
}));
vi.mock("@/lib/api/staffRoomsApi", () => api);

const room = (overrides: Record<string, unknown>) => ({
  id: "r1",
  code: "E-1",
  name: "Phòng khám 1",
  type: "EXAMINATION",
  description: "",
  capabilities: ["Khám chỉnh hình"],
  supportedServiceCount: 1,
  active: true,
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  api.listStaffRooms.mockResolvedValue([
    room({}),
    room({
      id: "r2",
      code: "X-1",
      name: "Phòng X-quang",
      type: "IMAGING",
      active: false,
    }),
  ]);
});
afterEach(cleanup);

it("lists the rooms of the role and filters by availability", async () => {
  render(<StaffRoomsScreen />);
  expect(await screen.findByText("Phòng khám 1")).toBeTruthy();
  expect(screen.getByText("Phòng X-quang")).toBeTruthy();
  fireEvent.change(screen.getByLabelText("Tình trạng"), {
    target: { value: "inactive" },
  });
  await waitFor(() => expect(screen.queryByText("Phòng khám 1")).toBeNull());
  expect(screen.getByText("Phòng X-quang")).toBeTruthy();
});

it("opens a room detail and shows the MSG-50 text when the room is out of scope", async () => {
  api.getStaffRoom.mockResolvedValueOnce(room({ description: "Tầng 2" }));
  render(<StaffRoomsScreen />);
  fireEvent.click(
    (await screen.findAllByRole("button", { name: "Chi tiết" }))[0],
  );
  expect(await screen.findByText("Tầng 2")).toBeTruthy();

  api.getStaffRoom.mockRejectedValueOnce(
    Object.assign(new Error("x"), { code: "MSG-50", name: "ApiError" }),
  );
  fireEvent.click(screen.getAllByRole("button", { name: "Chi tiết" })[1]);
  await waitFor(() => expect(api.getStaffRoom).toHaveBeenCalledTimes(2));
});

it("shows an error with retry when loading fails", async () => {
  api.listStaffRooms.mockRejectedValueOnce(new Error("down"));
  render(<StaffRoomsScreen />);
  expect(await screen.findByRole("alert")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  expect(await screen.findByText("Phòng khám 1")).toBeTruthy();
});
