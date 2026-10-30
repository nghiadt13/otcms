import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AdminServicesLive } from "./AdminServicesLive";
import { ApiError } from "@/lib/api/apiError";
import userEvent from "@testing-library/user-event";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
afterEach(() => request.mockReset());
const service = {
  id: "service-id",
  name: "Khám chỉnh hình",
  price: 200000,
  durationMinutes: 20,
  bufferMinutes: 5,
  active: true,
  onlineBookable: true,
  description: "Khám ngoại trú",
};

it("loads real admin services and keeps entered data when the save is rejected", async () => {
  request.mockImplementation(async (_path, init) => {
    if (init?.method === "PUT")
      throw new ApiError(409, { errorCode: "SERVICE_NOT_FOUND" });
    return { items: [service] };
  });
  render(<AdminServicesLive />);
  await screen.findByText("Khám chỉnh hình");
  expect(request).toHaveBeenCalledWith(
    "/api/v1/admin/clinic-services",
    expect.objectContaining({ signal: expect.any(AbortSignal) }),
  );
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Sửa" }));
  fireEvent.change(screen.getByLabelText("Giá (VND) *"), {
    target: { value: "250000" },
  });
  await user.click(screen.getByRole("button", { name: "Lưu dịch vụ" }));
  await screen.findByRole("alert");
  expect(screen.getByLabelText("Giá (VND) *")).toHaveValue("250000");
  const saved = request.mock.calls.find(([, init]) => init?.method === "PUT");
  expect(saved?.[0]).toBe("/api/v1/admin/clinic-services/service-id");
  expect(JSON.parse(saved?.[1].body)).toMatchObject({
    price: 250000,
    durationMinutes: 20,
    bufferMinutes: 5,
    onlineBookable: true,
  });
});

it("refreshes after an acknowledged mutation rather than only changing memory", async () => {
  request.mockResolvedValue({ items: [service] });
  render(<AdminServicesLive />);
  await screen.findByText(service.name);
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Sửa" }));
  await user.click(screen.getByRole("button", { name: "Lưu dịch vụ" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(
    request.mock.calls.filter(
      ([path]) => path === "/api/v1/admin/clinic-services",
    ),
  ).toHaveLength(2);
});
