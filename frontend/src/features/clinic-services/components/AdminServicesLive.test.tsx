import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AdminServicesLive } from "./AdminServicesLive";
import { ApiError } from "@/lib/api/apiError";
import userEvent from "@testing-library/user-event";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
afterEach(() => request.mockReset());
const service = {
  type: "EXAMINATION" as const,
  modality: null,
  patientNote: null,
  indicators: [],
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

it("creates a laboratory service with three indicators and reloads its persisted values", async () => {
  let saved: Record<string, unknown> | undefined;
  request.mockImplementation(async (_path, init) => {
    if (init?.method === "POST") {
      saved = JSON.parse(init.body);
      return { ...saved, id: "lab", active: true };
    }
    return { items: saved ? [{ ...saved, id: "lab", active: true }] : [] };
  });
  render(<AdminServicesLive />);
  const user = userEvent.setup();
  await screen.findByText(/Không có dịch vụ phù hợp/);
  await user.click(screen.getByRole("button", { name: "Thêm dịch vụ" }));
  fireEvent.change(screen.getByLabelText("Loại dịch vụ *"), {
    target: { value: "LABORATORY" },
  });
  fireEvent.change(screen.getByLabelText("Tên dịch vụ *"), {
    target: { value: "Xét nghiệm máu" },
  });
  fireEvent.change(screen.getByLabelText("Giá (VND) *"), {
    target: { value: "120000" },
  });
  for (let i = 1; i <= 3; i++) {
    if (i > 1)
      await user.click(screen.getByRole("button", { name: "Thêm chỉ số" }));
    for (const [label, value] of [
      ["Tên chỉ số", `Hb${i}`],
      ["Đơn vị", "g/dL"],
      ["Nam từ", "13.5"],
      ["Nam đến", "17"],
      ["Nữ từ", "12"],
      ["Nữ đến", "15"],
    ]) {
      fireEvent.change(screen.getByLabelText(`${label} ${i}`), {
        target: { value },
      });
    }
  }
  await user.click(screen.getByRole("button", { name: "Lưu dịch vụ" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(saved).toMatchObject({
    type: "LABORATORY",
    modality: null,
    indicators: expect.arrayContaining([
      expect.objectContaining({ name: "Hb3", refMale: { min: 13.5, max: 17 } }),
    ]),
  });
  await user.click(screen.getByRole("button", { name: "Sửa" }));
  expect(screen.getByLabelText("Tên chỉ số 3")).toHaveValue("Hb3");
  expect(screen.getByLabelText("Nam từ 1")).toHaveValue("13.5");
});
it("offers only four backend imaging modalities", async () => {
  request.mockResolvedValue({ items: [] });
  render(<AdminServicesLive />);
  await screen.findByText(/Không có dịch vụ phù hợp/);
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Thêm dịch vụ" }));
  fireEvent.change(screen.getByLabelText("Loại dịch vụ *"), {
    target: { value: "IMAGING" },
  });
  const select = screen.getByLabelText(
    "Phương thức chụp *",
  ) as HTMLSelectElement;
  expect(Array.from(select.options).map((option) => option.value)).toEqual([
    "",
    "XRAY",
    "ULTRASOUND",
    "CT",
    "MRI",
  ]);
});
