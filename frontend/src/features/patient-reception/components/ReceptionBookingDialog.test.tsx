import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { ReceptionProvider } from "./ReceptionProvider";
import { ReceptionBookingDialog } from "./ReceptionBookingDialog";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));

const slot = {
  serviceId: "svc-kham",
  doctorId: "doc-1",
  doctorDisplayName: "BS Nguyễn An",
  startTime: "2026-11-12T09:00:00+07:00",
  endTime: "2026-11-12T09:15:00+07:00",
  occupiedUntil: "2026-11-12T09:15:00+07:00",
};

beforeEach(() => {
  vi.clearAllMocks();
  request.mockReset();
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    if (path.startsWith("/api/v1/patient-profiles?"))
      return {
        content: [
          {
            patientProfileId: "pat-1",
            fullName: "Nguyễn Minh Anh",
            phoneCanonical: "0900000001",
            dateOfBirth: "1990-01-01",
          },
        ],
      };
    if (path === "/api/v1/staff/clinic-services")
      return {
        items: [
          {
            id: "svc-kham",
            name: "Khám nội",
            type: "EXAMINATION",
            price: 250000,
            active: true,
          },
        ],
      };
    if (path.startsWith("/api/v1/staff/appointments/availability?"))
      return { slots: [slot] };
    if (path === "/api/v1/appointments" && method === "POST")
      return { id: "apt-new", status: "CONFIRMED" };
    throw new Error(`Unexpected request ${method} ${path}`);
  });
});

describe("ReceptionBookingDialog (đặt lịch hộ)", () => {
  it("đặt lịch cho bệnh nhân đã chọn theo khung giờ và bác sĩ đi kèm, rồi báo MSG-26", async () => {
    const user = userEvent.setup();
    const onBooked = vi.fn();
    render(
      <ReceptionProvider>
        <ReceptionBookingDialog onBooked={onBooked} onClose={vi.fn()} />
      </ReceptionProvider>,
    );

    await user.type(screen.getByLabelText("Tên hoặc số điện thoại"), "Minh");
    await user.click(
      await screen.findByRole("button", { name: /Nguyễn Minh Anh/ }),
    );
    await user.selectOptions(screen.getByLabelText("Dịch vụ khám"), "svc-kham");
    const slotSelect = await screen.findByLabelText("Khung giờ còn trống");
    await waitFor(() =>
      expect(
        within(slotSelect).getByRole("option", {
          name: "09:00 · BS Nguyễn An",
        }),
      ).toBeInTheDocument(),
    );
    await user.selectOptions(slotSelect, "09:00 · BS Nguyễn An");
    await user.click(screen.getByRole("button", { name: "Đặt lịch khám" }));

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Đã đặt lịch khám thành công.",
      ),
    );
    expect(onBooked).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith(
      "/api/v1/appointments",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          patientProfileId: "pat-1",
          serviceId: "svc-kham",
          doctorId: "doc-1",
          startTime: slot.startTime,
          purpose: "Khám nội",
        }),
      }),
    );
  });

  it("chưa chọn bệnh nhân thì không cho đặt lịch", async () => {
    render(
      <ReceptionProvider>
        <ReceptionBookingDialog onBooked={vi.fn()} onClose={vi.fn()} />
      </ReceptionProvider>,
    );
    expect(
      await screen.findByRole("button", { name: "Đặt lịch khám" }),
    ).toBeDisabled();
    expect(request).not.toHaveBeenCalledWith(
      "/api/v1/appointments",
      expect.anything(),
    );
  });
});
