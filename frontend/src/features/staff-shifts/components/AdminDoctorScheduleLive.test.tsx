import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { AdminDoctorScheduleLive } from "./AdminDoctorScheduleLive";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
const doctor = {
  doctorProfileId: "doctor",
  fullName: "BS. Nguyễn An",
  inActiveDirectory: true,
};
afterEach(() => request.mockReset());

it("does not save a time-off that would affect booked patients", async () => {
  request.mockImplementation(async (path) => {
    if (path === "/api/v1/admin/doctor-profiles") return { items: [doctor] };
    if (path.endsWith("/mutation-impact"))
      return { blocked: true, affectedBookingCount: 2, activeCareBlocks: true };
    return { items: [] };
  });
  const user = userEvent.setup();
  render(<AdminDoctorScheduleLive />);
  await screen.findByRole("option", { name: doctor.fullName });
  await user.selectOptions(screen.getByLabelText("Loại lịch"), "time-offs");
  await user.click(screen.getByRole("button", { name: "Thêm khoảng nghỉ" }));
  await user.click(screen.getByRole("button", { name: "Lưu khoảng nghỉ" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "2 lịch hẹn bị ảnh hưởng",
  );
  expect(
    request.mock.calls.some(
      ([path, init]) => path.endsWith("/time-offs") && init?.method === "POST",
    ),
  ).toBe(false);
  expect(screen.getByLabelText("Bắt đầu *")).toHaveValue(
    `${addDays(todayIsoDate(), 1)}T08:00`,
  );
});

it("rejects reversed date filters without querying invalid backend ranges", async () => {
  request.mockImplementation(async (path) => ({
    items: path === "/api/v1/admin/doctor-profiles" ? [doctor] : [],
  }));
  render(<AdminDoctorScheduleLive />);
  await screen.findByRole("option", { name: doctor.fullName });
  fireEvent.change(screen.getByLabelText("Đến hết ngày"), {
    target: { value: "2000-01-01" },
  });
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "ngày kết thúc không được trước ngày bắt đầu",
  );
  await waitFor(() =>
    expect(
      request.mock.calls.some(([path]) => String(path).includes("2000-01-01")),
    ).toBe(false),
  );
});
